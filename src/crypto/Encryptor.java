package crypto;

import java.nio.charset.StandardCharsets;

public final class Encryptor {
    private Encryptor(){}

    public static byte[] encrypt(byte[] data, String password){
        byte[] out = new byte[data.length];
        long s = seed(password);
        for (int i=0;i<data.length;i++){
            s = lcg(s);
            out[i] = (byte) (data[i] ^ (byte)(s & 0xFF));
        }
        return out;
    }

    static long seed(String pw){
        byte[] k = pw.getBytes(StandardCharsets.UTF_8);
        long s = 1469598103934665603L; // FNV offset
        for (byte b: k){ s ^= (b & 0xFF); s *= 1099511628211L; }
        return s;
    }
    static long lcg(long x){ return (6364136223846793005L * x + 1442695040888963407L); }
}

