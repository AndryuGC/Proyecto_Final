package compressor;

import java.util.ArrayList;
import java.util.List;

public class LZSSCompressor {

    private static final int WINDOW_SIZE = 4096;
    private static final int LOOKAHEAD_SIZE = 18; // típico LZSS

    public static List<Token> comprimir(String input) {
        List<Token> out = new ArrayList<>();
        char[] s = input.toCharArray();
        int i = 0;

        while (i < s.length) {
            int bestLen = 0;
            int bestDist = 0;

            int windowStart = Math.max(0, i - WINDOW_SIZE);

            for (int j = windowStart; j < i; j++) {
                int len = 0;
                while (i + len < s.length &&
                        len < LOOKAHEAD_SIZE &&
                        s[j + len] == s[i + len]) {
                    len++;
                }
                if (len > bestLen) {
                    bestLen = len;
                    bestDist = i - j;
                    if (bestLen == LOOKAHEAD_SIZE) break;
                }
            }

            if (bestLen >= 3) {
                out.add(Token.ref(bestDist, bestLen));
                i += bestLen;
            } else {
                out.add(Token.literal(s[i]));
                i += 1;
            }
        }
        return out;
    }
}