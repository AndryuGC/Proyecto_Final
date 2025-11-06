package app;

import batch.BatchProcessor;
import batch.BatchProcessor.BatchConfig;
import batch.BatchProcessor.Mode;

public class MainBatch {
    // Uso:
    // java app.MainBatch <modo> <inDir> <outDir> [password]
    // modo: COMPRESS | COMPRESS_ENCRYPT | DECOMPRESS | DECRYPT_DECOMPRESS
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: java app.MainBatch <modo> <inDir> <outDir> [password]");
            return;
        }
        Mode mode = Mode.valueOf(args[0]);
        String inDir = args[1];
        String outDir = args[2];
        String pw = (args.length >= 4) ? args[3] : "";

        BatchConfig cfg = new BatchConfig(inDir, outDir, mode)
                .recursive(true).overwrite(true).dryRun(false).password(pw);
        BatchProcessor.runBatch(cfg);
    }
}
