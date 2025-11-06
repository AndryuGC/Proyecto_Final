package compressor;

/**
 * Adaptador para TU compresor “que sí comprime bien”.
 * Aquí solo llamas a las funciones de ese proyecto y devuelves bytes.
 */
public final class ExternalAdapter implements CompressorStrategy {

    @Override public AlgorithmId id(){ return AlgorithmId.EXTERNAL; }

    @Override public byte[] compress(byte[] input) throws Exception {
        // TODO: Reemplaza por llamadas reales a tu compresor del proyecto que funciona.
        // EJEMPLO de patrón (pseudo):
        // return YourGoodCompressor.compress(input);
        return input; // temporal: no hacer nada si aún no pegas tu compresor
    }

    @Override public byte[] decompress(byte[] payload) throws Exception {
        // TODO: Reemplaza por llamadas reales a tu descompresor.
        // return YourGoodCompressor.decompress(payload);
        return payload; // temporal
    }
}
