package archivos;

import compressor.LZSSCompressor;
import compressor.LZSSDecompressor;
import compressor.Token;
import crypto.Encryptor;
import crypto.Decryptor;
import log.OperacionLog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileCompressor {

    private static String leerArchivoComoString(String ruta) throws IOException {
        return Files.readString(Paths.get(ruta), StandardCharsets.UTF_8);
    }

    private static void escribirStringEnArchivo(String ruta, String contenido) throws IOException {
        Files.writeString(Paths.get(ruta), contenido, StandardCharsets.UTF_8);
    }

    // .cmp (texto): Literal: "1 <codePoint>", Ref: "0 <dist> <len>"
    private static void escribirTokensComoCmp(String rutaSalida, List<Token> tokens) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(rutaSalida, StandardCharsets.UTF_8))) {
            for (Token t : tokens) {
                if (t.isLiteral()) {
                    int cp = t.getCh();
                    bw.write("1 " + cp);
                } else {
                    bw.write("0 " + t.getDistance() + " " + t.getLength());
                }
                bw.newLine();
            }
        }
    }

    private static List<Token> leerCmpComoTokens(String rutaEntrada) throws IOException {
        List<Token> tokens = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(rutaEntrada, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                String[] partes = linea.split("\\s+");
                if (partes[0].equals("1")) {
                    if (partes.length < 2) throw new IllegalArgumentException("Formato literal inválido: '" + linea + "'");
                    int codePoint = Integer.parseInt(partes[1]);
                    tokens.add(Token.literal((char) codePoint));
                } else if (partes[0].equals("0")) {
                    if (partes.length < 3) throw new IllegalArgumentException("Formato referencia inválido: '" + linea + "'");
                    int dist = Integer.parseInt(partes[1]);
                    int len  = Integer.parseInt(partes[2]);
                    tokens.add(Token.ref(dist, len));
                } else {
                    throw new IllegalArgumentException("Prefijo desconocido: '" + linea + "'");
                }
            }
        }
        return tokens;
    }

    // Serialización a bytes (para cifrado) usando el mismo formato .cmp (en memoria)
    private static byte[] tokensToBytes(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (t.isLiteral()) {
                sb.append("1 ").append((int) t.getCh()).append("\n");
            } else {
                sb.append("0 ").append(t.getDistance()).append(" ").append(t.getLength()).append("\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static List<Token> bytesToTokens(byte[] data) {
        List<Token> tokens = new ArrayList<>();
        String contenido = new String(data, StandardCharsets.UTF_8);
        String[] lineas = contenido.split("\\r?\\n");
        for (String l : lineas) {
            if (l == null) continue;
            l = l.trim();
            if (l.isEmpty()) continue;
            String[] partes = l.split("\\s+");
            if (partes[0].equals("1")) {
                if (partes.length < 2) throw new IllegalArgumentException("Formato literal inválido: '" + l + "'");
                int codePoint = Integer.parseInt(partes[1]);
                tokens.add(Token.literal((char) codePoint));
            } else if (partes[0].equals("0")) {
                if (partes.length < 3) throw new IllegalArgumentException("Formato referencia inválido: '" + l + "'");
                int dist = Integer.parseInt(partes[1]);
                int len  = Integer.parseInt(partes[2]);
                tokens.add(Token.ref(dist, len));
            } else {
                throw new IllegalArgumentException("Prefijo desconocido: '" + l + "'");
            }
        }
        return tokens;
    }

    // ===== Fase 2/3 =====

    public static void comprimirArchivo(String rutaEntrada, String rutaSalidaCmp) {
        try {
            String original = leerArchivoComoString(rutaEntrada);
            List<Token> tokens = LZSSCompressor.comprimir(original);
            escribirTokensComoCmp(rutaSalidaCmp, tokens);

            long tamOriginal = Files.size(Paths.get(rutaEntrada));
            long tamFinal    = Files.size(Paths.get(rutaSalidaCmp));
            double ratio = 100.0 * (1.0 - ((double) tamFinal / (double) tamOriginal));

            OperacionLog.registrarOperacion(rutaEntrada, rutaSalidaCmp, "LZSS", tamOriginal, tamFinal, ratio, "COMPRESION");

            System.out.println("Comprimir: " + rutaEntrada + " -> " + rutaSalidaCmp);
            System.out.println("Listo: " + rutaSalidaCmp + " creado.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al comprimirArchivo: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void descomprimirArchivo(String rutaEntradaCmp, String rutaSalidaTxt) {
        try {
            List<Token> tokens = leerCmpComoTokens(rutaEntradaCmp);
            String texto = LZSSDecompressor.descomprimir(tokens);
            escribirStringEnArchivo(rutaSalidaTxt, texto);

            long tamOriginal = Files.size(Paths.get(rutaEntradaCmp));
            long tamFinal    = Files.size(Paths.get(rutaSalidaTxt));
            double ratio = 100.0 * (1.0 - ((double) tamFinal / (double) tamOriginal));

            OperacionLog.registrarOperacion(rutaEntradaCmp, rutaSalidaTxt, "LZSS", tamOriginal, tamFinal, ratio, "DESCOMPRESION");

            System.out.println("Descomprimir: " + rutaEntradaCmp + " -> " + rutaSalidaTxt);
            System.out.println("Listo: " + rutaSalidaTxt + " creado.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al descomprimirArchivo: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ===== Fase 4 =====

    public static void comprimirYEncriptarArchivo(String rutaEntrada, String rutaSalidaEncriptado, String password) {
        try {
            String original = leerArchivoComoString(rutaEntrada);
            List<Token> tokens = LZSSCompressor.comprimir(original);

            byte[] cmpBytes = tokensToBytes(tokens);
            byte[] cifrado  = Encryptor.encrypt(cmpBytes, password);

            try (FileOutputStream fos = new FileOutputStream(rutaSalidaEncriptado)) {
                fos.write(cifrado);
            }

            long tamOriginal = Files.size(Paths.get(rutaEntrada));
            long tamFinal    = Files.size(Paths.get(rutaSalidaEncriptado));
            double ratio = 100.0 * (1.0 - ((double) tamFinal / (double) tamOriginal));

            OperacionLog.registrarOperacion(rutaEntrada, rutaSalidaEncriptado, "LZSS", tamOriginal, tamFinal, ratio, "COMPRESION+ENCRIPTACION");

            System.out.println("Comprimir+Encriptar: " + rutaEntrada + " -> " + rutaSalidaEncriptado);
            System.out.println("Listo: " + rutaSalidaEncriptado + " creado.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al comprimirYEncriptarArchivo: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void desencriptarYDescomprimirArchivo(String rutaEntradaEncriptado, String rutaSalidaTxt, String password) {
        try {
            byte[] contenidoCifrado    = Files.readAllBytes(Paths.get(rutaEntradaEncriptado));
            byte[] contenidoDescifrado = Decryptor.decrypt(contenidoCifrado, password);

            List<Token> tokens = bytesToTokens(contenidoDescifrado);
            String texto = LZSSDecompressor.descomprimir(tokens);
            escribirStringEnArchivo(rutaSalidaTxt, texto);

            long tamOriginal = Files.size(Paths.get(rutaEntradaEncriptado));
            long tamFinal    = Files.size(Paths.get(rutaSalidaTxt));
            double ratio = 100.0 * (1.0 - ((double) tamFinal / (double) tamOriginal));

            OperacionLog.registrarOperacion(rutaEntradaEncriptado, rutaSalidaTxt, "LZSS", tamOriginal, tamFinal, ratio, "DESENCRIPTAR+DESCOMPRIMIR");

            System.out.println("Desencriptar+Descomprimir: " + rutaEntradaEncriptado + " -> " + rutaSalidaTxt);
            System.out.println("Listo: " + rutaSalidaTxt + " creado.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al desencriptarYDescomprimirArchivo: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // Valida si la password es correcta para un .ec SIN crear archivos.
// Intenta descifrar y parsear los tokens del .cmp en memoria.
    public static boolean validarPassword(String rutaEc, String password) {
        try {
            // 1) Leer .ec
            byte[] ecBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(rutaEc));
            // 2) Descifrar -> bytes .cmp
            byte[] cmpBytes = crypto.Decryptor.decrypt(ecBytes, password);
            // 3) Parseo "lightweight" del formato de tokens (líneas tipo "1 <char>" o "0 <dist> <len>")
            String cmpText = new String(cmpBytes, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = cmpText.split("\\R"); // \R = cualquier salto de línea

            int count = 0;
            for (String line : lines) {
                if (line.isBlank()) continue;
                // Literal: "1 " + char (al menos 3 caracteres por "1 " + 1 char)
                if (line.startsWith("1 ")) {
                    // ok incluso si el char es espacio o símbolo; con que haya algo luego del espacio
                    if (line.length() < 3) throw new IllegalArgumentException("Token literal inválido.");
                }
                // Referencia: "0 <dist> <len>"
                else if (line.startsWith("0 ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length != 3) throw new IllegalArgumentException("Token ref inválido.");
                    // dist y len deben ser enteros >= 1
                    Integer.parseInt(parts[1]);
                    Integer.parseInt(parts[2]);
                } else {
                    // Cualquier prefijo extraño => probablemente password incorrecta
                    throw new IllegalArgumentException("Prefijo desconocido: '" + line + "'");
                }
                if (++count >= 5) break; // validar unas cuantas líneas es suficiente
            }
            // Si pasó el parseo básico, la clave probablemente es correcta
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

}
