package app;

import batch.BatchProcessor;
import batch.BatchProcessor.BatchConfig;
import batch.BatchProcessor.Mode;

public class MainBatch {

    public static void main(String[] args) {
        String entradaTxt = "Entrada";
        String salidaCmp  = "SalidaCMP";
        String salidaEc   = "SalidaEC";
        String recTxt1    = "RecuperadosCMP";
        String recTxt2    = "RecuperadosEC";
        String password   = "clave123";

        // 1) Comprimir solo .txt y .md
        BatchProcessor.runBatch(
                new BatchConfig(entradaTxt, salidaCmp, Mode.COMPRESS)
                        .recursive(true).overwrite(false).dryRun(false)
                        .include(".txt", ".md").exclude(".log")
        );

        // 2) Comprimir + Encriptar (todo lo de texto)
        BatchProcessor.runBatch(
                new BatchConfig(entradaTxt, salidaEc, Mode.COMPRESS_ENCRYPT)
                        .password(password)
                        .recursive(true).overwrite(false).dryRun(false)
                        .include(".txt", ".md").exclude(".log")
        );

        // 3) Descomprimir .cmp
        BatchProcessor.runBatch(
                new BatchConfig(salidaCmp, recTxt1, Mode.DECOMPRESS)
                        .recursive(true).overwrite(true)
        );

        // 4) Descifrar + Descomprimir .ec
        BatchProcessor.runBatch(
                new BatchConfig(salidaEc, recTxt2, Mode.DECRYPT_DECOMPRESS)
                        .password(password)
                        .recursive(true).overwrite(true)
        );

        System.out.println("\nListo. Revisa 'operaciones.log' y las carpetas generadas.");
    }
}
