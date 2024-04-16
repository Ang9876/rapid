package engine.grain.grainSim.grainSimV1VarContract1;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

import engine.grain.GrainEngine;
import engine.grain.varAnnotation.VarAnnotationEngine;
import parse.ParserType;

public class GrainSimEngine extends GrainEngine<GrainSimState>{
    public GrainSimEngine(ParserType pType, String trace_folder, String eventsFileName) {
        super(pType, trace_folder, eventsFileName);
        if(pType.isSTD()) {
            VarAnnotationEngine varAnnotationEngine = new VarAnnotationEngine(pType, trace_folder, stdParser);
            varAnnotationEngine.analyzeTrace();
            state = new GrainSimState(threadSet, varAnnotationEngine.getLastReads());
            stdParser.totEvents = 0;
            try{
                stdParser.bufferedReader = new BufferedReader(new FileReader(trace_folder));
            }
            catch (FileNotFoundException ex) {
                System.out.println("Unable to open file '" + trace_folder + "'");
            }
        }
        else {
            throw new IllegalArgumentException("Illegal trace type. Should be STD.");
        }
    }
}
