package crypto;

import java.nio.charset.StandardCharsets;

public class Decryptor {

    // Descifra los bytes. Es el mismo proceso XOR porque XOR es reversible
    public static byte[] decrypt(byte[] data, String password) {
        byte[] key = password.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return out;
    }
}
