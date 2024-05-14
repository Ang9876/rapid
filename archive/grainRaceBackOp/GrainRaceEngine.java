package engine.racedetectionengine.grainRaceBackOp;

import engine.racedetectionengine.RaceDetectionEngine;
import parse.ParserType;

public class GrainRaceEngine extends RaceDetectionEngine<GrainRaceState, GrainRaceEvent>{
    public GrainRaceEngine(ParserType pType, String trace_folder) {
        super(pType);
        initializeReader(trace_folder);
        this.state = new GrainRaceState(threadSet);
        this.handlerEvent = new GrainRaceEvent();
        this.handlerEvent.eventCount = stdParser.tot;
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
