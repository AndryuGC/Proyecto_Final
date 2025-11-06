package compressor;

/** Identificador binario que se graba como primer byte del payload comprimido. */
public enum AlgorithmId {
    LZSS((byte)0x4C),   // 'L'
    DEFLATE((byte)0x44),// 'D'
    EXTERNAL((byte)0x45); // 'E'

    public final byte marker;
    AlgorithmId(byte m){ this.marker = m; }

    public static AlgorithmId fromMarker(byte m){
        for (AlgorithmId a: values()) if (a.marker == m) return a;
        return null;
    }
}
