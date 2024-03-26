package engine.grain.grainSim.grainSimV1Mini;

import engine.grain.GrainEngine;
import parse.ParserType;

public class GrainSimEngine extends GrainEngine<GrainSimState>{
    public GrainSimEngine(ParserType pType, String trace_folder, String eventsFileName) {
        super(pType, trace_folder, eventsFileName);
        state = new GrainSimState(threadSet);
    }
}
