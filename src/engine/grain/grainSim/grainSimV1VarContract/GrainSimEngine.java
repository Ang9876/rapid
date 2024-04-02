package engine.grain.grainSim.grainSimV1VarContract;

import engine.grain.GrainEngine;
import engine.grain.varAnnotation.VarAnnotationEngine;
import parse.ParserType;

public class GrainSimEngine extends GrainEngine<GrainSimState>{
    public GrainSimEngine(ParserType pType, String trace_folder, String eventsFileName) {
        super(pType, trace_folder, eventsFileName);
        if(pType.isSTD()) {
            VarAnnotationEngine varAnnotationEngine = new VarAnnotationEngine(pType, trace_folder);
            state = new GrainSimState(threadSet, varAnnotationEngine.getLastReads());
        }
        else {
            throw new IllegalArgumentException("Illegal trace type. Should be STD.");
        }
    }
}
