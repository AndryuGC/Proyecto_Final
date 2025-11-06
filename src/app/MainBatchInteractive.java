package app;

import batch.BatchProcessor;
import batch.BatchProcessor.BatchConfig;
import batch.BatchProcessor.Mode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;

public class MainBatchInteractive {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Procesamiento por Carpeta (Interactivo) ===");

        // 1) Modo
        System.out.println("Elige modo:");
        System.out.println("  1) COMPRESION (.txt -> .cmp)");
        System.out.println("  2) COMPRESION+ENCRIPTACION (.txt -> .ec)");
        System.out.println("  3) DESCOMPRESION (.cmp -> .txt)");
        System.out.println("  4) DESCIFRAR+DESCOMPRIMIR (.ec -> .txt)");
        System.out.print("Opción [1-4]: ");
        int opt = readInt(sc, 1, 4);

        Mode mode = switch (opt) {
            case 1 -> Mode.COMPRESS;
            case 2 -> Mode.COMPRESS_ENCRYPT;
            case 3 -> Mode.DECOMPRESS;
            case 4 -> Mode.DECRYPT_DECOMPRESS;
            default -> throw new IllegalStateException("Opción inválida");
        };

        // 2) Rutas
        System.out.print("Carpeta de ENTRADA: ");
        String inputDir = sc.nextLine().trim();
        System.out.print("Carpeta de SALIDA : ");
        String outputDir = sc.nextLine().trim();

        if (!Files.isDirectory(Path.of(inputDir))) {
            System.err.println("La carpeta de entrada no existe o no es carpeta.");
            return;
        }

        // 3) Recursivo / Overwrite / Dry-run
        boolean recursive = askYesNo(sc, "¿Recursivo? (s/n) [s]: ", true);
        boolean overwrite = askYesNo(sc, "¿Sobrescribir si existe? (s/n) [n]: ", false);
        boolean dryRun    = askYesNo(sc, "¿Simular (dry-run)? (s/n) [n]: ", false);

        // 4) Password si el modo lo requiere
        String password = null;
        if (mode == Mode.COMPRESS_ENCRYPT || mode == Mode.DECRYPT_DECOMPRESS) {
            System.out.print("Password: ");
            password = sc.nextLine();
            if (password.isBlank()) {
                System.err.println("Password requerido para este modo.");
                return;
            }
        }

        // 5) Filtros include/exclude (solo para modos que leen texto)
        String includeCsv = "";
        String excludeCsv = "";
        if (mode == Mode.COMPRESS || mode == Mode.COMPRESS_ENCRYPT) {
            System.out.print("Extensiones a INCLUIR (csv, ej: .txt,.md) [recomendado .txt,.md]: ");
            includeCsv = sc.nextLine().trim();
            if (includeCsv.isBlank()) includeCsv = ".txt,.md";
            System.out.print("Extensiones a EXCLUIR (csv) [opcional]: ");
            excludeCsv = sc.nextLine().trim();
        }

        // 6) Correr
        BatchConfig cfg = new BatchConfig(inputDir, outputDir, mode)
                .recursive(recursive)
                .overwrite(overwrite)
                .dryRun(dryRun);
        if (password != null) cfg.password(password);
        if (!includeCsv.isBlank()) cfg.include(splitCsv(includeCsv));
        if (!excludeCsv.isBlank()) cfg.exclude(splitCsv(excludeCsv));

        System.out.println("\nEjecutando...");
        BatchProcessor.runBatch(cfg);
        System.out.println("\nListo. Revisa 'operaciones.log' y las carpetas generadas.");
    }

    private static int readInt(Scanner sc, int min, int max) {
        while (true) {
            String s = sc.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) return v;
            } catch (Exception ignored) {}
            System.out.print("Número inválido, intenta otra vez: ");
        }
    }

    private static boolean askYesNo(Scanner sc, String prompt, boolean defaultYes) {
        System.out.print(prompt);
        String s = sc.nextLine().trim().toLowerCase(Locale.ROOT);
        if (s.isBlank()) return defaultYes;
        return s.startsWith("s") || s.startsWith("y");
    }

    private static String[] splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(x -> !x.isBlank())
                .toArray(String[]::new);
    }
}
