package crypto;

public final class Decryptor {
    private Decryptor(){}

    public static byte[] decrypt(byte[] data, String password){
        return Encryptor.encrypt(data, password); // XOR simétrico
    }
}
