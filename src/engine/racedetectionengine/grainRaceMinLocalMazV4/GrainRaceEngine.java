package engine.racedetectionengine.grainRaceMinLocalMazV4;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

import engine.grain.varAnnotation.VarAnnotationEngine;
import engine.racedetectionengine.RaceDetectionEngine;
import parse.ParserType;

public class GrainRaceEngine extends RaceDetectionEngine<GrainRaceState, GrainRaceEvent>{

    private long start;
    private long length;

    public GrainRaceEngine(ParserType pType, String trace_folder, boolean singleThread, boolean boundedSize, int size, boolean window, int win, long start, long length) {
        super(pType);
        this.start = start;
        this.length = length;
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
        this.state = new GrainRaceState(threadSet, varAnnotationEngine.getLastReads(), singleThread, boundedSize, size, window, win);
        for(String t: stdParser.getThreadMap().keySet()) {
            System.out.println(t + " " + stdParser.getThreadMap().get(t).getId());
        }
        for(String t: stdParser.getVariableMap().keySet()) {
            System.out.println(t + " " + stdParser.getVariableMap().get(t).getId());
        }
        for(String t: stdParser.getLockMap().keySet()) {
            System.out.println(t + " " + stdParser.getLockMap().get(t).getId());
        }
        GrainRaceState.numOfThreads = stdParser.getThreadMap().size();
        GrainRaceState.numOfVars = stdParser.getVariableMap().size();
        GrainRaceState.numOfLocks = stdParser.getLockMap().size();
        System.out.println("Num of Threads: " + GrainRaceState.numOfThreads);
        System.out.println("Num of Vars: " + GrainRaceState.numOfVars);
        System.out.println("Num of Locks: " + GrainRaceState.numOfLocks);
        this.handlerEvent = new GrainRaceEvent();
    }

    @Override
    public void analyzeTraceSTD(boolean multipleRace, int verbosity) {
		while (stdParser.hasNext()) {
			eventCount = eventCount + 1;
			if(eventCount < start || eventCount > start + length) {
                continue;
            }
			stdParser.getNextEvent(handlerEvent);
			if (skipEvent(handlerEvent)) {
				totalSkippedEvents = totalSkippedEvents + 1;
			} else {
				boolean raceDetected = analyzeEvent(handlerEvent, verbosity, eventCount);
				if (raceDetected) {
					raceCount++;
					if (!multipleRace) {
						break;
					}
				}
				postHandleEvent(handlerEvent);
			}
		}
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
        System.out.println(state.racyLocs.stream().sorted().toList()); 
        System.out.println("Number of races = " + state.racyEvents.size());
    }
}
