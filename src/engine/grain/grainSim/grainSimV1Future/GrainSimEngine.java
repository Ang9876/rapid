package engine.grain.grainSim.grainSimV1Future;

import engine.grain.GrainEngine;
import parse.ParserType;

public class GrainSimEngine extends GrainEngine<GrainSimState>{
    public GrainSimEngine(ParserType pType, String trace_folder, String eventsFileName) {
        super(pType, trace_folder, eventsFileName);
        state = new GrainSimState(threadSet, threadMap);
    }
}
