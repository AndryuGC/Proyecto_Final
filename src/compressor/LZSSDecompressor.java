package compressor;

import java.util.List;

public class LZSSDecompressor {

    public static String descomprimir(List<Token> tokens) {
        StringBuilder out = new StringBuilder();

        for (Token t : tokens) {
            if (t.isLiteral()) {
                out.append(t.getCh());
            } else {
                int dist = t.getDistance();
                int len  = t.getLength();
                int start = out.length() - dist;
                for (int k = 0; k < len; k++) {
                    out.append(out.charAt(start + k));
                }
            }
        }
        return out.toString();
    }
}
