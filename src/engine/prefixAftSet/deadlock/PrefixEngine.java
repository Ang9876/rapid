package engine.prefixAftSet.deadlock;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

import engine.Engine;
import event.Thread;
import parse.ParserType;
import parse.rr.ParseRoadRunner;
import parse.std.ParseStandard;

public class PrefixEngine extends Engine<PrefixEvent> {
    protected long eventCount;
    protected long totalSkippedEvents;
    
    protected HashSet<Thread> threadSet;
    protected HashMap<Integer, String> idToLocationMap = null;
    protected HashMap<String, Integer> locationToIdMap = null;
    protected State state;

    protected ArrayList<Integer> pattern = new ArrayList<>();

    public boolean partition = false;
    long startTimeAnalysis = 0;
    long start;
    long end;

    public PrefixEngine(ParserType pType, String trace_folder, String patternFileName, double prob) {
        super(pType);
        try {
            Scanner myReader = new Scanner(new File(patternFileName));
            int cnt = 0;
            while (myReader.hasNextLine()) {
                cnt += 1;
                String loc = myReader.nextLine();
                if(cnt == 1) {
                    start = Integer.valueOf(loc);
                }
                else if (cnt == 2) {
                    end = Integer.valueOf(loc);
                    this.initializeReader(trace_folder);
                }
                else {
                    if(locationToIdMap != null){
                        pattern.add(locationToIdMap.get(loc));
                    }
                    else {
                        pattern.add(Integer.parseInt(loc));
                    }
                }
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        eventCount = 0;
        totalSkippedEvents = 0;
        handlerEvent = new PrefixEvent();
        state = new State(threadSet, pattern, prob);
    }

    protected boolean analyzeEvent(PrefixEvent handlerEvent, Long eventCount){
		boolean patternMatched = false;
		try{
			patternMatched = handlerEvent.Handle(state);
		}
		catch(OutOfMemoryError oome){
			System.err.println("Number of events = " + Long.toString(eventCount));
			state.printMemory();
            oome.printStackTrace();
		}
		return patternMatched;
	}

    public void analyzeTrace() {
		if (this.parserType.isRR()) {
			analyzeTraceRR();
		}
        if (this.parserType.isSTD()) {
			analyzeTraceSTD();
		}
		printCompletionStatus();
		// postAnalysis();
    }

    private void analyzeTraceRR() {
        boolean flag = false;
        startTimeAnalysis = System.currentTimeMillis();
        long stopTimeAnalysis = 0;
        while(rrParser.checkAndGetNext(handlerEvent)) {
            eventCount = eventCount + 1;
            if(eventCount >= start && eventCount <= end) {
                boolean matched = analyzeEvent(handlerEvent, eventCount);
                if (matched) {
                    stopTimeAnalysis = System.currentTimeMillis();
                    flag = true;
                    System.out.println("Pattern Matched on the first " + eventCount + " events");
                    break;
                }
                postHandleEvent(handlerEvent);
            }
            if(eventCount > end) {
                break;
            }
        }
        if(!flag) {
            stopTimeAnalysis = System.currentTimeMillis();
            System.out.println("Not matched");
        }
        long timeAnalysis = stopTimeAnalysis - startTimeAnalysis;
        System.out.println("Time for full analysis = " + timeAnalysis + " milliseconds");
    }

    private void analyzeTraceSTD() {
        boolean flag = false;
        while(stdParser.hasNext()){
			eventCount = eventCount + 1;
			stdParser.getNextEvent(handlerEvent);
            boolean matched = analyzeEvent(handlerEvent, eventCount);
            if (matched) {
                flag = true;
                System.out.println("Pattern Matched on the first " + eventCount + " events");
                break;
            }
            postHandleEvent(handlerEvent);
        }
        if(!flag) {
            System.out.println("Not matched");
        }
        state.printMemory();
    }

    protected void initializeReaderRV(String trace_folder) {

    }

	protected void initializeReaderCSV(String trace_file) {

    }

	protected void initializeReaderSTD(String trace_file) {
        stdParser = new ParseStandard(trace_file, true);
		threadSet = stdParser.getThreadSet();
    }

	protected void initializeReaderRR(String trace_file) {
        rrParser = new ParseRoadRunner(trace_file, true, start, end);
        threadSet = rrParser.getThreadSet();
        idToLocationMap = rrParser.idToLocationMap;
        locationToIdMap = rrParser.locationToIdMap;
    }

    protected boolean skipEvent(PrefixEvent handlerEvent) {
        // return !handlerEvent.getType().isAccessType();
        return false;
    }

	protected void postHandleEvent(PrefixEvent handlerEvent) {

    } 
}
