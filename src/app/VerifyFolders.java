package app;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class VerifyFolders {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: VerifyFolders <carpeta_original> <carpeta_recuperada>");
            System.out.println("Ejemplo: VerifyFolders Entrada RecuperadosEC");
            System.exit(2);
        }
        Path dirA = Paths.get(args[0]);
        Path dirB = Paths.get(args[1]);

        if (!Files.isDirectory(dirA) || !Files.isDirectory(dirB)) {
            System.err.println("Ambas rutas deben ser carpetas existentes.");
            System.exit(2);
        }

        try {
            Result r = compareTrees(dirA, dirB);
            System.out.println("\n==== RESUMEN ====");
            System.out.println("Iguales     : " + r.equalsCount);
            System.out.println("Distintos   : " + r.diffCount);
            System.out.println("Solo en A   : " + r.onlyInACount);
            System.out.println("Solo en B   : " + r.onlyInBCount);

            if (r.diffCount == 0 && r.onlyInACount == 0 && r.onlyInBCount == 0) {
                System.out.println("OK -> Carpetas equivalentes (byte a byte).");
                System.exit(0);
            } else {
                System.out.println("NO COINCIDEN");
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }
    }

    private static class Result {
        int equalsCount = 0;
        int diffCount = 0;
        int onlyInACount = 0;
        int onlyInBCount = 0;
    }

    private static Result compareTrees(Path dirA, Path dirB) throws Exception {
        Map<String, Path> filesA = listFiles(dirA);
        Map<String, Path> filesB = listFiles(dirB);

        Result res = new Result();

        // Archivos solo en A
        for (String rel : diff(filesA.keySet(), filesB.keySet())) {
            System.out.println("Solo en A: " + rel);
            res.onlyInACount++;
        }
        // Solo en B
        for (String rel : diff(filesB.keySet(), filesA.keySet())) {
            System.out.println("Solo en B: " + rel);
            res.onlyInBCount++;
        }

        // Comparar comunes
        for (String rel : intersect(filesA.keySet(), filesB.keySet())) {
            Path a = filesA.get(rel);
            Path b = filesB.get(rel);

            long sizeA = Files.size(a);
            long sizeB = Files.size(b);
            if (sizeA != sizeB) {
                System.out.println("Tamaño distinto: " + rel + " (A=" + sizeA + " B=" + sizeB + ")");
                res.diffCount++;
                continue;
            }
            String hA = sha256(a);
            String hB = sha256(b);
            if (!hA.equals(hB)) {
                System.out.println("Contenido distinto: " + rel);
                System.out.println("  SHA-256 A: " + hA);
                System.out.println("  SHA-256 B: " + hB);
                res.diffCount++;
            } else {
                res.equalsCount++;
            }
        }
        return res;
    }

    private static Map<String, Path> listFiles(Path base) throws IOException {
        try (var stream = Files.walk(base)) {
            return stream.filter(Files::isRegularFile)
                    .collect(Collectors.toMap(
                            p -> base.relativize(p).toString().replace('\\','/'),
                            p -> p
                    ));
        }
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>();
        for (String x : a) if (!b.contains(x)) out.add(x);
        return out;
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>();
        for (String x : a) if (b.contains(x)) out.add(x);
        return out;
    }

    private static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] data = Files.readAllBytes(p);
        byte[] dig = md.digest(data);
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte x : dig) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
