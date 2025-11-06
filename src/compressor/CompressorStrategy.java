package compressor;

public interface CompressorStrategy {
    AlgorithmId id();
    byte[] compress(byte[] input) throws Exception;
    byte[] decompress(byte[] payload) throws Exception;
}
