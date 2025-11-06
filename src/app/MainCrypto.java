package app;

import archivos.FileCompressor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

public class MainCrypto {

    public static void main(String[] args) {
        String entradaTexto = "mensaje.txt";
        String salidaCifrada = "mensaje.ec";
        String salidaRecuperada = "mensaje_recuperado.txt";
        String password = "clave123";

        // 1) Comprimir + Encriptar
        FileCompressor.comprimirYEncriptarArchivo(entradaTexto, salidaCifrada, password);

        // 2) Desencriptar + Descomprimir
        FileCompressor.desencriptarYDescomprimirArchivo(salidaCifrada, salidaRecuperada, password);

        // 3) Verificación automática
        Path p1 = Paths.get(entradaTexto);
        Path p2 = Paths.get(salidaRecuperada);

        try {
            System.out.println("\n=== VERIFICACIÓN FASE 4 ===");
            System.out.printf("Original:   %s (%d bytes)\n", p1, Files.size(p1));
            System.out.printf("Recuperado: %s (%d bytes)\n", p2, Files.size(p2));

            String h1 = sha256Hex(Files.readAllBytes(p1));
            String h2 = sha256Hex(Files.readAllBytes(p2));
            System.out.println("SHA-256 original:   " + h1);
            System.out.println("SHA-256 recuperado: " + h2);

            boolean igualesBytes = Arrays.equals(Files.readAllBytes(p1), Files.readAllBytes(p2));
            if (igualesBytes) {
                System.out.println("OK -> Coincide (byte a byte)");
                return;
            }

            // Si difiere byte-a-byte, probamos una comparación normalizada de texto
            boolean igualesTexto = equalsNormalizedText(p1, p2);
            if (igualesTexto) {
                System.out.println("OK -> Coincide (texto normalizado: solo cambiaron saltos de línea)");
            } else {
                System.out.println("NO COINCIDE");
                reportFirstDiff(p1, p2);
            }

        } catch (IOException e) {
            System.err.println("Error verificando archivos: " + e.getMessage());
        }
    }

    // ===== Utilidades de verificación =====

    private static boolean equalsNormalizedText(Path p1, Path p2) throws IOException {
        String a = Files.readString(p1, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String b = Files.readString(p2, StandardCharsets.UTF_8).replace("\r\n", "\n");
        return a.equals(b);
    }

    private static void reportFirstDiff(Path p1, Path p2) throws IOException {
        byte[] a = Files.readAllBytes(p1);
        byte[] b = Files.readAllBytes(p2);

        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                System.out.printf("Primera diferencia en byte %d: original=0x%02X, recuperado=0x%02X\n", i, a[i], b[i]);
                printContext(p1, p2, i);
                return;
            }
        }
        if (a.length != b.length) {
            System.out.printf("Difieren en longitud: original=%d bytes, recuperado=%d bytes\n", a.length, b.length);
        } else {
            System.out.println("No se halló diferencia (posible distinto encoding/lectura).");
        }
    }

    private static void printContext(Path p1, Path p2, int index) throws IOException {
        String a = Files.readString(p1, StandardCharsets.UTF_8);
        String b = Files.readString(p2, StandardCharsets.UTF_8);

        // Posición segura en texto (si el archivo no es texto, esto puede no ser útil)
        int start = Math.max(0, index - 10);
        int endA = Math.min(a.length(), index + 10);
        int endB = Math.min(b.length(), index + 10);

        String sa = safeSlice(a, start, endA);
        String sb = safeSlice(b, start, endB);

        System.out.println("--- Contexto alrededor de la diferencia (texto) ---");
        System.out.println("Original  : \"" + visualize(sa) + "\"");
        System.out.println("Recuperado: \"" + visualize(sb) + "\"");
    }

    private static String safeSlice(String s, int from, int to) {
        if (from >= s.length()) return "";
        if (to <= from) return "";
        to = Math.min(to, s.length());
        return s.substring(from, to);
    }

    private static String visualize(String s) {
        // Muestra saltos de línea y tabs para que se vean
        return s.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(data);
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte x : dig) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "(no-sha256: " + e.getMessage() + ")";
        }
    }
}
