package log;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OperacionLog {

    private static final String LOG_FILE = "operaciones.log";

    public static void registrarOperacion(
            String archivoEntrada,
            String archivoSalida,
            String algoritmo,
            long tamOriginal,
            long tamFinal,
            double porcentajeCompresion,
            String modoOperacion
    ) {
        // timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // línea de log (una sola línea por operación)
        String linea = String.format(
                "[%s] modo=%s | alg=%s | in=%s (%d bytes) | out=%s (%d bytes) | ratio=%.2f%%\n",
                timestamp,
                modoOperacion,
                algoritmo,
                archivoEntrada,
                tamOriginal,
                archivoSalida,
                tamFinal,
                porcentajeCompresion
        );

        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(linea);
        } catch (IOException e) {
            System.err.println("No se pudo escribir en el log: " + e.getMessage());
        }
    }
}
