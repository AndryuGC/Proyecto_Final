package app;

import batch.BatchProcessor;
import batch.BatchProcessor.BatchConfig;
import batch.BatchProcessor.Mode;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainBatchInteractive {
    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Directorio de entrada: ");
        String inDir = br.readLine();

        System.out.print("Directorio de salida: ");
        String outDir = br.readLine();

        System.out.print("Modo [1=COMPRESS, 2=COMPRESS_ENCRYPT, 3=DECOMPRESS, 4=DECRYPT_DECOMPRESS]: ");
        int m = Integer.parseInt(br.readLine().trim());
        Mode mode = switch (m) {
            case 1 -> Mode.COMPRESS;
            case 2 -> Mode.COMPRESS_ENCRYPT;
            case 3 -> Mode.DECOMPRESS;
            case 4 -> Mode.DECRYPT_DECOMPRESS;
            default -> throw new IllegalArgumentException("Modo inválido");
        };

        String pw = "";
        if (mode == Mode.COMPRESS_ENCRYPT || mode == Mode.DECRYPT_DECOMPRESS) {
            System.out.print("Contraseña: ");
            pw = br.readLine();
        }

        BatchConfig cfg = new BatchConfig(inDir, outDir, mode)
                .recursive(true).overwrite(true).dryRun(false).password(pw);

        BatchProcessor.runBatch(cfg);
        System.out.println("Listo.");
    }
}
