package batch;

import archivos.FileCompressor;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Procesa carpetas completas en 4 modos:
 *  - COMPRESS -> genera .cmp
 *  - COMPRESS_ENCRYPT -> genera .ec (requiere password)
 *  - DECOMPRESS -> lee .cmp y genera .txt
 *  - DECRYPT_DECOMPRESS -> lee .ec y genera .txt (requiere password)
 *
 * Opciones:
 *  - recursive: recorrer subcarpetas
 *  - overwrite: sobrescribir si el destino existe
 *  - includeExts/excludeExts: filtro por extensiones (solo para COMPRESS/COMPRESS_ENCRYPT)
 *  - dryRun: simular (no escribe nada)
 *
 * Nota: los logs por archivo los escribe FileCompressor (Fase 3).
 */
public class BatchProcessor {

    // API rápida
    public static void processFolderCompress(String inputDir, String outputDir, boolean recursive) {
        runBatch(new BatchConfig(inputDir, outputDir, Mode.COMPRESS).recursive(recursive));
    }
    public static void processFolderCompressEncrypt(String inputDir, String outputDir, String password, boolean recursive) {
        runBatch(new BatchConfig(inputDir, outputDir, Mode.COMPRESS_ENCRYPT).password(password).recursive(recursive));
    }
    public static void processFolderDecompress(String inputDir, String outputDir, boolean recursive) {
        runBatch(new BatchConfig(inputDir, outputDir, Mode.DECOMPRESS).recursive(recursive));
    }
    public static void processFolderDecryptDecompress(String inputDir, String outputDir, String password, boolean recursive) {
        runBatch(new BatchConfig(inputDir, outputDir, Mode.DECRYPT_DECOMPRESS).password(password).recursive(recursive));
    }

    // API completa
    public static void runBatch(BatchConfig cfg) {
        Objects.requireNonNull(cfg, "cfg no puede ser null");
        Path inBase  = Paths.get(cfg.inputDir);
        Path outBase = Paths.get(cfg.outputDir);

        if (!Files.isDirectory(inBase)) {
            System.err.println("La carpeta de entrada no existe o no es carpeta: " + inBase.toAbsolutePath());
            return;
        }
        try { Files.createDirectories(outBase); }
        catch (IOException e) {
            System.err.println("No se pudo crear la carpeta de salida: " + outBase + " -> " + e.getMessage());
            return;
        }

        Set<String> include = normalizeExts(cfg.includeExts);
        Set<String> exclude = normalizeExts(cfg.excludeExts);

        printHeader(cfg, inBase, outBase, include, exclude);
        Summary sum = new Summary();

        try {
            if (cfg.recursive) {
                Files.walkFileTree(inBase, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        processOne(file, inBase, outBase, cfg, include, exclude, sum);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                try (var stream = Files.list(inBase)) {
                    stream.filter(Files::isRegularFile)
                            .forEach(p -> processOne(p, inBase, outBase, cfg, include, exclude, sum));
                }
            }
        } catch (IOException e) {
            System.err.println("Error recorriendo carpeta: " + e.getMessage());
        }

        printFooter(sum);
    }

    private static void processOne(Path src, Path inBase, Path outBase,
                                   BatchConfig cfg, Set<String> include, Set<String> exclude, Summary sum) {
        sum.processed++;
        String nameLower = src.getFileName().toString().toLowerCase(Locale.ROOT);
        if (nameLower.endsWith(".log")) { sum.skip++; log("SKIP (log): " + src); return; }

        if (!shouldConsiderFile(src, cfg.mode, include, exclude)) {
            sum.skip++; log("SKIP (no aplica por modo/filtro): " + src); return;
        }

        Path rel = inBase.relativize(src);
        Path dst = outBase.resolve(rel);
        dst = switch (cfg.mode) {
            case COMPRESS            -> changeExtension(dst, ".cmp");
            case COMPRESS_ENCRYPT    -> changeExtension(dst, ".ec");
            case DECOMPRESS          -> changeExtension(dst, ".txt");
            case DECRYPT_DECOMPRESS  -> changeExtension(dst, ".txt");
        };

        try { Files.createDirectories(dst.getParent()); }
        catch (IOException e) { sum.fail++; log("FAIL (mkdirs): " + src + " -> " + e.getMessage()); return; }

        if (!cfg.overwrite && Files.exists(dst)) { sum.skip++; log("SKIP (existe): " + dst); return; }
        if (cfg.dryRun) { sum.ok++; log("[DRY] " + cfg.mode + ": " + src + " -> " + dst); return; }

        try {
            switch (cfg.mode) {
                case COMPRESS -> FileCompressor.comprimirArchivo(src.toString(), dst.toString());
                case COMPRESS_ENCRYPT -> FileCompressor.comprimirYEncriptarArchivo(src.toString(), dst.toString(), requirePassword(cfg));
                case DECOMPRESS -> FileCompressor.descomprimirArchivo(src.toString(), dst.toString());
                case DECRYPT_DECOMPRESS -> FileCompressor.desencriptarYDescomprimirArchivo(src.toString(), dst.toString(), requirePassword(cfg));
            }
            sum.ok++; log("OK: " + src + " -> " + dst);
        } catch (Exception ex) {
            sum.fail++; log("FAIL: " + src + " (" + ex.getMessage() + ")");
        }
    }

    private static boolean shouldConsiderFile(Path src, Mode mode, Set<String> include, Set<String> exclude) {
        String fname = src.getFileName().toString().toLowerCase(Locale.ROOT);
        if ((mode == Mode.COMPRESS || mode == Mode.COMPRESS_ENCRYPT) &&
                (fname.endsWith(".cmp") || fname.endsWith(".ec") || fname.endsWith(".log"))) return false;

        switch (mode) {
            case COMPRESS, COMPRESS_ENCRYPT -> {
                String ext = fileExt(fname);
                // Si no definieron include, por defecto tomamos .txt y .md para evitar binarios
                if (include.isEmpty() && !(ext.equals(".txt") || ext.equals(".md"))) return false;
                if (!include.isEmpty() && !include.contains(ext)) return false;
                if (!exclude.isEmpty() && exclude.contains(ext)) return false;
                return true;
            }
            case DECOMPRESS -> { return fname.endsWith(".cmp"); }
            case DECRYPT_DECOMPRESS -> { return fname.endsWith(".ec"); }
        }
        return false;
    }

    private static String fileExt(String fname) {
        int dot = fname.lastIndexOf('.');
        return (dot >= 0) ? fname.substring(dot) : "";
    }

    private static Set<String> normalizeExts(Collection<String> exts) {
        Set<String> out = new HashSet<>();
        if (exts == null) return out;
        for (String s : exts) {
            if (s == null || s.isBlank()) continue;
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.startsWith(".")) t = "." + t;
            out.add(t);
        }
        return out;
    }

    private static Path changeExtension(Path path, String newExt) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = (dot >= 0) ? fileName.substring(0, dot) : fileName;
        return path.getParent().resolve(base + newExt);
    }

    private static void log(String s) { System.out.println(s); }

    private static void printHeader(BatchConfig cfg, Path inBase, Path outBase, Set<String> include, Set<String> exclude) {
        System.out.println("=== BATCH " + cfg.mode.label + " ===");
        System.out.println("Entrada   : " + inBase.toAbsolutePath());
        System.out.println("Salida    : " + outBase.toAbsolutePath());
        System.out.println("Recursivo : " + cfg.recursive + " | Overwrite: " + cfg.overwrite + " | Dry-run: " + cfg.dryRun);
        if (cfg.mode == Mode.COMPRESS_ENCRYPT || cfg.mode == Mode.DECRYPT_DECOMPRESS) System.out.println("Password  : [oculto]");
        if (!include.isEmpty()) System.out.println("Include   : " + include);
        if (!exclude.isEmpty()) System.out.println("Exclude   : " + exclude);
        System.out.println("----------------------------------------");
    }

    private static void printFooter(Summary sum) {
        System.out.println("----------------------------------------");
        System.out.println("Procesados: " + sum.processed);
        System.out.println("OK       : " + sum.ok);
        System.out.println("SKIP     : " + sum.skip);
        System.out.println("FAIL     : " + sum.fail);
        System.out.println("=== FIN BATCH ===");
    }

    private static String requirePassword(BatchConfig cfg) {
        if (cfg.password == null || cfg.password.isEmpty()) throw new IllegalArgumentException("Password requerido para el modo " + cfg.mode);
        return cfg.password;
    }

    public enum Mode {
        COMPRESS("COMPRESIÓN"),
        COMPRESS_ENCRYPT("COMPRESIÓN+ENCRIPTACIÓN"),
        DECOMPRESS("DESCOMPRESIÓN (.cmp)"),
        DECRYPT_DECOMPRESS("DESCIFRAR+DESCOMPRIMIR (.ec)");
        public final String label;
        Mode(String label) { this.label = label; }
    }

    public static class BatchConfig {
        public final String inputDir;
        public final String outputDir;
        public final Mode mode;
        public boolean recursive = true;
        public boolean overwrite = false;
        public boolean dryRun = false;
        public String password = null;
        public List<String> includeExts = new ArrayList<>();
        public List<String> excludeExts = new ArrayList<>();
        public BatchConfig(String inputDir, String outputDir, Mode mode) {
            this.inputDir = Objects.requireNonNull(inputDir);
            this.outputDir = Objects.requireNonNull(outputDir);
            this.mode = Objects.requireNonNull(mode);
        }
        public BatchConfig recursive(boolean v) { this.recursive = v; return this; }
        public BatchConfig overwrite(boolean v) { this.overwrite = v; return this; }
        public BatchConfig dryRun(boolean v)    { this.dryRun = v; return this; }
        public BatchConfig password(String p)   { this.password = p; return this; }
        public BatchConfig include(String... exts) { this.includeExts.addAll(Arrays.asList(exts)); return this; }
        public BatchConfig exclude(String... exts) { this.excludeExts.addAll(Arrays.asList(exts)); return this; }
    }

    private static class Summary { int processed=0, ok=0, skip=0, fail=0; }
}
