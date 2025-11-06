package crypto;

import java.nio.charset.StandardCharsets;

public class Encryptor {

    // Cifra los bytes usando XOR con la contraseña repetida
    public static byte[] encrypt(byte[] data, String password) {
        byte[] key = password.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return out;
    }
}
