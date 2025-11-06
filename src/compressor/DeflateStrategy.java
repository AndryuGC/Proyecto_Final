package compressor;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class DeflateStrategy implements CompressorStrategy {
    @Override public AlgorithmId id(){ return AlgorithmId.DEFLATE; }

    @Override public byte[] compress(byte[] input) throws Exception {
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION, true); // raw
        def.setInput(input); def.finish();
        byte[] buf = new byte[4096];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (!def.finished()) {
            int n = def.deflate(buf);
            if (n == 0 && def.needsInput()) break;
            baos.write(buf, 0, n);
        }
        def.end();
        return baos.toByteArray();
    }

    @Override public byte[] decompress(byte[] input) throws Exception {
        Inflater inf = new Inflater(true); // raw
        inf.setInput(input);
        byte[] buf = new byte[4096];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && inf.needsInput()) break;
                baos.write(buf, 0, n);
            }
        } finally { inf.end(); }
        return baos.toByteArray();
    }
}
