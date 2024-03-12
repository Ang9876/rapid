package engine.grain.grainConcurrent;

import engine.grain.GrainEngine;
import parse.ParserType;

public class GrainConcurrentEngine extends GrainEngine<GrainConcurrentState>{
    public GrainConcurrentEngine(ParserType pType, String trace_folder, String eventsFileName) {
        super(pType, trace_folder, eventsFileName);
        state = new GrainConcurrentState(threadSet);
    }
}
