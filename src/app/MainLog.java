package app;

import archivos.FileCompressor;

public class MainLog {

    public static void main(String[] args) {
        // rutas de prueba
        String original = "mensajeLog.txt";
        String comprimido = "mensaje.cmp";
        String recuperado = "mensaje_out.txt";

        try {
            // 1. Comprimir
            System.out.println("Comprimir: " + original + " -> " + comprimido);
            FileCompressor.comprimirArchivo(original, comprimido);
            System.out.println("Listo: " + comprimido + " creado.");

            // 2. Descomprimir
            System.out.println("Descomprimir: " + comprimido + " -> " + recuperado);
            FileCompressor.descomprimirArchivo(comprimido, recuperado);
            System.out.println("Listo: " + recuperado + " creado.");

            // 3. Aviso final
            System.out.println("Revisa el archivo operaciones.log para ver el registro.");

        } catch (Exception e) {
            System.out.println("Error en fase 3: " + e.getMessage());
        }
    }
}
