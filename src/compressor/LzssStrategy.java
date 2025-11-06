package compressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class LzssStrategy implements CompressorStrategy {
    private static final int WINDOW = 4096, LOOK = 18, MINLEN = 4;

    @Override public AlgorithmId id(){ return AlgorithmId.LZSS; }

    @Override public byte[] compress(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        while (pos < in.length) {
            int bestDist = 0, bestLen = 0;
            int start = Math.max(0, pos - WINDOW);
            for (int j = start; j < pos; j++) {
                int len = 0;
                while (len < LOOK && pos + len < in.length && in[j + len] == in[pos + len]) len++;
                if (len > bestLen) { bestLen = len; bestDist = pos - j; if (bestLen == LOOK) break; }
            }
            if (bestLen >= MINLEN) {
                out.write(0);
                out.write((bestDist >>> 8) & 0xFF);
                out.write(bestDist & 0xFF);
                out.write(bestLen);
                pos += bestLen;
            } else {
                out.write(1);
                out.write(in[pos] & 0xFF);
                pos++;
            }
        }
        return out.toByteArray();
    }

    @Override public byte[] decompress(byte[] in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < in.length) {
            int flag = in[i++] & 0xFF;
            if (flag == 1) {
                if (i >= in.length) throw new IOException("LZSS literal fuera de rango");
                out.write(in[i++] & 0xFF);
            } else if (flag == 0) {
                if (i + 2 >= in.length) throw new IOException("LZSS referencia truncada");
                int dist = ((in[i++] & 0xFF) << 8) | (in[i++] & 0xFF);
                int len  = in[i++] & 0xFF;
                int start = out.size() - dist;
                if (start < 0) throw new IOException("Distancia inválida en LZSS");
                byte[] buf = out.toByteArray();
                for (int k = 0; k < len; k++) out.write(buf[start + k]);
            } else {
                throw new IOException("Flag LZSS inválido: " + flag);
            }
        }
        return out.toByteArray();
    }
}
