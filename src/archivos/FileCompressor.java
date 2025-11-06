package archivos;

import crypto.Encryptor;
import crypto.Decryptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Contenedor SFE1:
 *  MAGIC "SFE1"
 *  FLAGS bit0=1 => STORED (no comprimido); bit1=1 => ENCRYPTED
 *
 * Si NO es STORED, el payload inicia con 1 byte marcador:
 *   'L' (0x4C) => LZSS
 *   'D' (0x44) => DEFLATE (raw)
 * Compatibilidad hacia atrás:
 *   - Si el primer byte del payload es 0 o 1, se interpreta como LZSS antiguo (sin marcador).
 */
public class FileCompressor {

    // ====== Constantes contenedor ======
    private static final byte[] MAGIC = new byte[]{'S','F','E','1'};
    private static final byte ALG_LZSS = 0x4C; // 'L'
    private static final byte ALG_DEFL = 0x44; // 'D'

    // ====== Heurísticas de velocidad/entropía ======
    private static final int LARGE_SIZE = 32 * 1024 * 1024; // 32 MB
    private static final double THRESH = 0.98;              // si no mejora ≥2% -> STORED

    // ====== API PÚBLICA ======

    /** Comprime el archivo con LZSS/DEFLATE y elige el mejor; si no mejora, STORED. */
    public static void compressFile(Path in, Path out) throws IOException {
        byte[] original = Files.readAllBytes(in);
        Result r = tryStrategies(original);
        byte flags = (r.stored ? (byte) 1 : (byte) 0);
        writeContainer(out, flags, r.payload);
        log(in, out, "COMPRESS", original.length, r.payload.length);
    }

    /** Descomprime .cmp (detecta STORED, LZSS legado, LZSS marcado o DEFLATE marcado). */
    public static void decompressFile(Path in, Path out) throws IOException {
        Container c = readContainer(in);
        byte[] data = ((c.flags & 1) != 0) ? c.payload : expandFromMarkedOrLegacy(c.payload);
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        Files.write(out, data);
        log(in, out, "DECOMPRESS", c.payload.length, data.length);
    }

    /** Comprimir y luego encriptar (elige mejor compresión; si no mejora, STORED + cifrado). */
    public static void compressEncrypt(Path in, Path out, String password) throws IOException {
        byte[] original = Files.readAllBytes(in);
        Result r = tryStrategies(original);
        byte[] cipher = Encryptor.encrypt(r.payload, password);

        byte flags = 0b0000_0010; // ENCRYPTED
        if (r.stored) flags |= 1; // STORED

        writeContainer(out, flags, cipher);
        log(in, out, "COMPRESS+ENCRYPT", original.length, cipher.length);
    }

    /** Desencriptar y descomprimir. */
    public static void decryptDecompress(Path in, Path out, String password) throws IOException {
        Container c = readContainer(in);
        if ((c.flags & 0b10) == 0) throw new IOException("El archivo no está encriptado; usa decompressFile.");
        byte[] plain = Decryptor.decrypt(c.payload, password);
        byte[] data = ((c.flags & 1) != 0) ? plain : expandFromMarkedOrLegacy(plain);
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        Files.write(out, data);
        log(in, out, "DECRYPT+DECOMPRESS", c.payload.length, data.length);
    }

    /** Cambia la extensión conservando la carpeta. */
    public static Path changeExt(Path src, String newExt) {
        String name = src.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name;
        return (src.getParent() == null) ? Paths.get(base + newExt) : src.getParent().resolve(base + newExt);
    }

    // ====== Heurísticas ======

    /** Estimación simple de entropía (muestra hasta 1MB). */
    private static boolean isHighEntropy(byte[] data) {
        int n = Math.min(data.length, 1_000_000);
        int[] freq = new int[256];
        for (int i = 0; i < n; i++) freq[data[i] & 0xFF]++;
        double h = 0.0;
        for (int f : freq) if (f > 0) {
            double p = (double) f / n;
            h -= p * (Math.log(p) / Math.log(2));
        }
        // ~8 bits/byte aleatorio; >7.8 lo consideramos “duro”
        return h > 7.8;
    }

    private record Result(boolean stored, byte[] payload) {}

    /** Prueba estrategias con atajos de rendimiento; si no mejora ≥2%, STORED. */
    private static Result tryStrategies(byte[] original) {
        boolean huge = original.length >= LARGE_SIZE;
        boolean highH = isHighEntropy(original);

        // Ruta rápida: archivo grande o muy aleatorio → DEFLATE rápido; si no mejora, STORED.
        if (huge || highH) {
            try {
                byte[] defFast = deflateCompress(original, /*bestSpeed=*/true);
                byte[] marked = addMarker(ALG_DEFL, defFast);
                if (marked.length < (int) Math.ceil(original.length * THRESH)) {
                    return new Result(false, marked);
                }
            } catch (Exception ignored) {}
            return new Result(true, original);
        }

        // Ruta normal: probar LZSS y DEFLATE (best compression) y elegir el mejor.
        int bestSize = Integer.MAX_VALUE;
        byte[] best = null;

        try {
            byte[] lz = lzssCompress(original);
            byte[] test = lzssDecompress(lz); // verificación rápida de integridad
            if (Arrays.equals(test, original)) {
                byte[] m = addMarker(ALG_LZSS, lz);
                if (m.length < bestSize) { bestSize = m.length; best = m; }
            }
        } catch (Exception ignored) {}

        try {
            byte[] df = deflateCompress(original, /*bestSpeed=*/false);
            byte[] m = addMarker(ALG_DEFL, df);
            if (m.length < bestSize) { bestSize = m.length; best = m; }
        } catch (Exception ignored) {}

        if (best == null || best.length >= (int) Math.ceil(original.length * THRESH)) {
            return new Result(true, original);
        }
        return new Result(false, best);
    }

    // ====== Expansión según marcador/legado ======

    private static byte[] expandFromMarkedOrLegacy(byte[] payload) throws IOException {
        if (payload.length == 0) return payload;
        int b0 = payload[0] & 0xFF;
        if (b0 == 0 || b0 == 1) {
            // LZSS antiguo (sin marcador, tu formato previo)
            return lzssDecompress(payload);
        }
        if (payload[0] == ALG_LZSS) {
            return lzssDecompress(Arrays.copyOfRange(payload, 1, payload.length));
        } else if (payload[0] == ALG_DEFL) {
            return deflateDecompress(Arrays.copyOfRange(payload, 1, payload.length));
        } else {
            // Intentar compat: primero LZSS, luego DEFLATE
            try { return lzssDecompress(payload); } catch (Exception ignore) {}
            return deflateDecompress(payload);
        }
    }

    private static byte[] addMarker(byte marker, byte[] data) {
        byte[] out = new byte[data.length + 1];
        out[0] = marker;
        System.arraycopy(data, 0, out, 1, data.length);
        return out;
    }

    // ====== Contenedor SFE1 ======

    private record Container(byte flags, byte[] payload) {}

    private static void writeContainer(Path out, byte flags, byte[] payload) throws IOException {
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        try (OutputStream os = Files.newOutputStream(out)) {
            os.write(MAGIC);
            os.write(flags);
            int len = payload.length;
            os.write((len >>> 24) & 0xFF);
            os.write((len >>> 16) & 0xFF);
            os.write((len >>> 8) & 0xFF);
            os.write(len & 0xFF);
            os.write(payload);
        }
    }

    private static Container readContainer(Path in) throws IOException {
        byte[] all = Files.readAllBytes(in);
        if (all.length < 9) throw new IOException("Archivo muy corto");
        if (!Arrays.equals(Arrays.copyOfRange(all, 0, 4), MAGIC)) throw new IOException("MAGIC inválido (no SFE1)");
        byte flags = all[4];
        int len = ((all[5] & 0xFF) << 24) | ((all[6] & 0xFF) << 16) | ((all[7] & 0xFF) << 8) | (all[8] & 0xFF);
        if (9 + len != all.length) throw new IOException("Longitud inconsistente");
        return new Container(flags, Arrays.copyOfRange(all, 9, 9 + len));
    }

    // ====== LZSS ======
    private static final int WINDOW = 4096;
    private static final int LOOK = 18;
    private static final int MINLEN = 4;

    // Empaquetado LZSS: [flag(1)][literal: byte][ref: dist(2) len(1)]
    // flag: 1 = literal, 0 = referencia
    private static byte[] lzssCompress(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        while (pos < in.length) {
            int bestDist = 0, bestLen = 0;
            int start = Math.max(0, pos - WINDOW);
            for (int j = start; j < pos; j++) {
                int len = 0;
                while (len < LOOK && pos + len < in.length && in[j + len] == in[pos + len]) len++;
                if (len > bestLen) { bestLen = len; bestDist = pos - j; if (bestLen == LOOK) break; }
            }
            if (bestLen >= MINLEN) {
                out.write(0);
                out.write((bestDist >>> 8) & 0xFF);
                out.write(bestDist & 0xFF);
                out.write(bestLen);
                pos += bestLen;
            } else {
                out.write(1);
                out.write(in[pos] & 0xFF);
                pos++;
            }
        }
        return out.toByteArray();
    }

    // Descompresión LZSS optimizada (sin out.toByteArray() por iteración)
    private static byte[] lzssDecompress(byte[] in) throws IOException {
        byte[] out = new byte[Math.max(1024, in.length * 2)];
        int outLen = 0;

        int i = 0;
        while (i < in.length) {
            int flag = in[i++] & 0xFF;
            if (flag == 1) {
                if (i >= in.length) throw new IOException("LZSS literal fuera de rango");
                if (outLen >= out.length) out = Arrays.copyOf(out, out.length * 2);
                out[outLen++] = in[i++];
            } else if (flag == 0) {
                if (i + 2 >= in.length) throw new IOException("LZSS referencia truncada");
                int dist = ((in[i++] & 0xFF) << 8) | (in[i++] & 0xFF);
                int len = in[i++] & 0xFF;
                int start = outLen - dist;
                if (start < 0) throw new IOException("Distancia inválida en LZSS");
                for (int k = 0; k < len; k++) {
                    if (outLen >= out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[outLen++] = out[start + k];
                }
            } else {
                throw new IOException("Flag LZSS inválido: " + flag);
            }
        }
        return Arrays.copyOf(out, outLen);
    }

    // ====== DEFLATE (raw) ======

    // BEST_SPEED para archivos grandes; BEST_COMPRESSION para chicos
    private static byte[] deflateCompress(byte[] input, boolean bestSpeed) throws IOException {
        int level = bestSpeed ? Deflater.BEST_SPEED : Deflater.BEST_COMPRESSION;
        Deflater def = new Deflater(level, true); // raw (sin zlib header)
        def.setInput(input);
        def.finish();

        byte[] buf = new byte[64 * 1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (!def.finished()) {
            int n = def.deflate(buf);
            if (n == 0 && def.needsInput()) break;
            baos.write(buf, 0, n);
        }
        def.end();
        return baos.toByteArray();
    }

    private static byte[] deflateDecompress(byte[] input) throws IOException {
        Inflater inf = new Inflater(true); // raw
        byte[] buf = new byte[64 * 1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            inf.setInput(input);
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && inf.needsInput()) break;
                baos.write(buf, 0, n);
            }
        } catch (Exception e) {
            throw new IOException("DEFLATE inválido: " + e.getMessage(), e);
        } finally {
            inf.end();
        }
        return baos.toByteArray();
    }

    // ====== Log (7 parámetros) ======
    private static void log(Path in, Path out, String op, long sizeIn, long sizeOut) {
        try {
            double ratio = (sizeIn == 0) ? 0.0 : (double) sizeOut / (double) sizeIn;
            String timestamp = LocalDateTime.now().toString();
            log.OperacionLog.registrarOperacion(
                    in.toString(), out.toString(), op, sizeIn, sizeOut, ratio, timestamp
            );
        } catch (Throwable t) {
            System.err.println("[LOG] " + t.getMessage());
        }
    }

    // ====== Wrappers en español (compat) ======
    public static void comprimirArchivo(String rutaEntrada, String rutaSalida) throws IOException {
        compressFile(Paths.get(rutaEntrada), Paths.get(rutaSalida));
    }
    public static void descomprimirArchivo(String rutaEntrada, String rutaSalida) throws IOException {
        decompressFile(Paths.get(rutaEntrada), Paths.get(rutaSalida));
    }
    public static void comprimirYEncriptarArchivo(String rutaEntrada, String rutaSalida, String password) throws IOException {
        compressEncrypt(Paths.get(rutaEntrada), Paths.get(rutaSalida), password);
    }
    public static void descomprimirYDesencriptarArchivo(String rutaEntrada, String rutaSalida, String password) throws IOException {
        decryptDecompress(Paths.get(rutaEntrada), Paths.get(rutaSalida), password);
    }
    // Alias con el otro orden de palabras
    public static void desencriptarYDescomprimirArchivo(String rutaEntrada, String rutaSalida, String password) throws IOException {
        decryptDecompress(Paths.get(rutaEntrada), Paths.get(rutaSalida), password);
    }
}
