package engine.racedetectionengine.grainRace;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

import engine.grain.varAnnotation.VarAnnotationEngine;
import engine.racedetectionengine.RaceDetectionEngine;
import parse.ParserType;

public class GrainRaceEngine extends RaceDetectionEngine<GrainRaceState, GrainRaceEvent>{
    public GrainRaceEngine(ParserType pType, String trace_folder) {
        super(pType);
        initializeReader(trace_folder);
        VarAnnotationEngine varAnnotationEngine = new VarAnnotationEngine(pType, trace_folder, stdParser);
        varAnnotationEngine.analyzeTrace();
        stdParser.totEvents = 0;
        try{
            stdParser.bufferedReader = new BufferedReader(new FileReader(trace_folder));
        }
        catch (FileNotFoundException ex) {
            System.out.println("Unable to open file '" + trace_folder + "'");
        }
        this.state = new GrainRaceState(threadSet, varAnnotationEngine.getLastReads());
        this.handlerEvent = new GrainRaceEvent();
    }

    @Override
    protected boolean skipEvent(GrainRaceEvent handlerEvent) {
        return false;
    }

    @Override
    protected void postHandleEvent(GrainRaceEvent handlerEvent) {
        state.printMemory();
    }

    @Override
    protected void postAnalysis() {
        state.finalCheck();
        System.out.println(state.racyEvents.stream().sorted().toList());
    }
}
