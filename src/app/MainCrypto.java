package app;

import archivos.FileCompressor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * MainCrypto: utilidad de consola y fachada para la UI.
 * - Métodos estáticos que usa la interfaz.
 * - "main" interactivo por si quieres correrlo solo.
 */
public class MainCrypto {

    // ===== Fachada para la UI =====
    public static void comprimirYEncriptarArchivo(String rutaEntrada, String rutaSalida, String password) throws IOException {
        FileCompressor.comprimirYEncriptarArchivo(rutaEntrada, rutaSalida, password);
    }
    public static void desencriptarYDescomprimirArchivo(String rutaEntrada, String rutaSalida, String password) throws IOException {
        FileCompressor.desencriptarYDescomprimirArchivo(rutaEntrada, rutaSalida, password);
    }

    // ===== Modo consola opcional =====
    public static void main(String[] args) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("=== Crypto ===");
            System.out.println("1) Comprimir + Encriptar");
            System.out.println("2) Desencriptar + Descomprimir");
            System.out.print("Opción: ");
            int op = Integer.parseInt(br.readLine().trim());

            System.out.print("Ruta de entrada: ");
            String in = br.readLine();
            System.out.print("Ruta de salida: ");
            String out = br.readLine();
            System.out.print("Contraseña: ");
            String pw = br.readLine();

            switch (op) {
                case 1 -> {
                    try {
                        comprimirYEncriptarArchivo(in, out, pw);
                        System.out.println("OK: " + in + " -> " + out);
                    } catch (IOException e) {
                        System.err.println("ERROR al comprimir+encriptar: " + e.getMessage());
                    }
                }
                case 2 -> {
                    try {
                        desencriptarYDescomprimirArchivo(in, out, pw);
                        System.out.println("OK: " + in + " -> " + out);
                    } catch (IOException e) {
                        System.err.println("ERROR al desencriptar+descomprimir: " + e.getMessage());
                    }
                }
                default -> System.out.println("Opción inválida.");
            }
        } catch (Exception e) {
            System.err.println("Fallo inesperado: " + e.getMessage());
        }
    }
}
