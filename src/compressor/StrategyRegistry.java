package compressor;

import java.util.ArrayList;
import java.util.List;

public final class StrategyRegistry {
    private static final List<CompressorStrategy> STRATS = new ArrayList<>();
    static {
        // Orden de prueba (puedes reordenar si tu EXTERNAL es mejor)
        STRATS.add(new LzssStrategy());
        STRATS.add(new DeflateStrategy());
        STRATS.add(new ExternalAdapter()); // <-- tu compresor “bueno”
    }
    private StrategyRegistry(){}

    public static List<CompressorStrategy> all(){ return STRATS; }
}
