package batch;

import archivos.FileCompressor;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class BatchProcessor {

    public enum Mode { COMPRESS, COMPRESS_ENCRYPT, DECOMPRESS, DECRYPT_DECOMPRESS }

    public static final class BatchConfig {
        public final Path inputDir;
        public final Path outputDir;
        public final Mode mode;

        public boolean recursive = true;
        public boolean overwrite = true;
        public boolean dryRun    = false;

        public String password = "";
        public final Set<String> includeExts = new HashSet<>();
        public final Set<String> excludeExts = new HashSet<>();

        public BatchConfig(String inDir, String outDir, Mode mode) {
            this.inputDir  = Paths.get(inDir);
            this.outputDir = Paths.get(outDir);
            this.mode = Objects.requireNonNull(mode);
        }
        public BatchConfig recursive(boolean v) { this.recursive = v; return this; }
        public BatchConfig overwrite(boolean v) { this.overwrite = v; return this; }
        public BatchConfig dryRun(boolean v)    { this.dryRun = v; return this; }
        public BatchConfig password(String p)   { this.password = p; return this; }
        public BatchConfig include(String... e) { this.includeExts.addAll(toLower(e)); return this; }
        public BatchConfig exclude(String... e) { this.excludeExts.addAll(toLower(e)); return this; }
        private static Collection<String> toLower(String... a){
            ArrayList<String> r = new ArrayList<>();
            for (String s : a) if (s!=null) r.add(s.toLowerCase(Locale.ROOT));
            return r;
        }
    }

    private BatchProcessor(){}

    public static void runBatch(BatchConfig cfg) throws IOException {
        if (!Files.isDirectory(cfg.inputDir)) throw new IOException("Directorio de entrada inválido: " + cfg.inputDir);
        Files.createDirectories(cfg.outputDir);

        Summary sum = new Summary();
        Files.walkFileTree(cfg.inputDir, cfg.recursive ? EnumSet.noneOf(FileVisitOption.class) : EnumSet.noneOf(FileVisitOption.class),
                cfg.recursive ? Integer.MAX_VALUE : 1,
                new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        try {
                            processOne(file, cfg);
                            sum.ok++;
                        } catch (Skip s) {
                            System.out.println("[SKIP] " + file + " - " + s.reason);
                            sum.skip++;
                        } catch (Exception e) {
                            System.out.println("[FAIL] " + file + " - " + e.getMessage());
                            sum.fail++;
                        }
                        sum.processed++;
                        return FileVisitResult.CONTINUE;
                    }
                });

        System.out.printf("%nProcesados: %d | OK: %d | SKIP: %d | FAIL: %d%n",
                sum.processed, sum.ok, sum.skip, sum.fail);
    }

    // ---------- Lógica por archivo ----------
    private static void processOne(Path src, BatchConfig cfg) throws Exception {
        if (Files.isDirectory(src)) throw new Skip("es carpeta");

        // Filtros include/exclude por extensión (si se configuraron)
        if (!shouldConsiderByExt(src, cfg)) throw new Skip("filtrado por Include/Exclude");

        Path rel = cfg.inputDir.relativize(src);
        Path dstBase = cfg.outputDir.resolve(rel).normalize();
        Files.createDirectories(dstBase.getParent());

        switch (cfg.mode) {
            case COMPRESS -> {
                // Evitar recomprimir contenedores
                String n = src.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(".cmp") || n.endsWith(".ec")) throw new Skip("ya es contenedor (.cmp/.ec)");
                Path out = replaceExt(dstBase, ".cmp");
                if (!cfg.overwrite && Files.exists(out)) throw new Skip("existe y overwrite=false");
                if (cfg.dryRun) { System.out.println("[DRY] " + src + " -> " + out); return; }
                FileCompressor.compressFile(src, out);
                System.out.println("[OK] COMPRESS " + src + " -> " + out);
            }
            case COMPRESS_ENCRYPT -> {
                String n = src.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(".cmp") || n.endsWith(".ec")) throw new Skip("ya es contenedor (.cmp/.ec)");
                Path out = replaceExt(dstBase, ".ec");
                if (!cfg.overwrite && Files.exists(out)) throw new Skip("existe y overwrite=false");
                if (cfg.dryRun) { System.out.println("[DRY] " + src + " -> " + out); return; }
                FileCompressor.compressEncrypt(src, out, cfg.password);
                System.out.println("[OK] COMPRESS+ENCRYPT " + src + " -> " + out);
            }
            case DECOMPRESS -> {
                // Ahora no dependemos de la extensión: leemos el contenedor
                Sfe1Info info = probeSfe1(src);
                if (!info.isSfe1) throw new Skip("no es contenedor SFE1");
                if (info.encrypted) throw new Skip("está encriptado; usa DECRYPT_DECOMPRESS");
                Path out = replaceExt(dstBase, ".txt");
                if (!cfg.overwrite && Files.exists(out)) throw new Skip("existe y overwrite=false");
                if (cfg.dryRun) { System.out.println("[DRY] " + src + " -> " + out); return; }
                FileCompressor.decompressFile(src, out);
                System.out.println("[OK] DECOMPRESS " + src + " -> " + out);
            }
            case DECRYPT_DECOMPRESS -> {
                Sfe1Info info = probeSfe1(src);
                if (!info.isSfe1) throw new Skip("no es contenedor SFE1");
                if (!info.encrypted) throw new Skip("no está encriptado; usa DECOMPRESS");
                Path out = replaceExt(dstBase, ".txt");
                if (!cfg.overwrite && Files.exists(out)) throw new Skip("existe y overwrite=false");
                if (cfg.dryRun) { System.out.println("[DRY] " + src + " -> " + out); return; }
                FileCompressor.decryptDecompress(src, out, cfg.password);
                System.out.println("[OK] DECRYPT+DECOMPRESS " + src + " -> " + out);
            }
        }
    }

    // ---------- Helpers ----------
    private static boolean shouldConsiderByExt(Path src, BatchConfig cfg) {
        String ext = extOf(src).orElse("");

        // Include/Exclude vacíos => no filtran nada
        if (!cfg.includeExts.isEmpty() && !cfg.includeExts.contains(ext)) return false;
        if (cfg.excludeExts.contains(ext)) return false;

        return true;
    }

    private static Optional<String> extOf(Path p){
        String n = p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return (i>=0 && i<n.length()-1) ? Optional.of(n.substring(i+1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    private static Path replaceExt(Path base, String newExt){
        String n = base.getFileName().toString();
        int i = n.lastIndexOf('.');
        String b = (i>=0) ? n.substring(0,i) : n;
        return base.getParent()==null ? Paths.get(b+newExt) : base.getParent().resolve(b+newExt);
    }

    // Lee MAGIC y FLAGS rápidamente para decidir si es SFE1 y si está encriptado.
    private record Sfe1Info(boolean isSfe1, boolean encrypted) {}
    private static Sfe1Info probeSfe1(Path p) {
        try {
            byte[] h = Files.readAllBytes(p);
            if (h.length < 5) return new Sfe1Info(false, false);
            if (h[0]=='S' && h[1]=='F' && h[2]=='E' && h[3]=='1') {
                boolean enc = (h[4] & 0b0000_0010) != 0;
                return new Sfe1Info(true, enc);
            }
            return new Sfe1Info(false, false);
        } catch (Exception e) {
            return new Sfe1Info(false, false);
        }
    }

    private static class Skip extends Exception {
        final String reason;
        Skip(String r){ this.reason = r; }
    }
    private static class Summary { int processed=0, ok=0, skip=0, fail=0; }
}
