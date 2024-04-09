package engine.prefixAftSet.race;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
    protected State state;
    protected String sourceFile;

    HashSet<Long> racyevents = new HashSet<>();

    public boolean partition = false;
    long startTimeAnalysis = 0;

    public PrefixEngine(ParserType pType, String trace_folder) {
        super(pType);
        sourceFile = trace_folder;
        eventCount = 0;
        totalSkippedEvents = 0;
        this.initializeReader(trace_folder);
        handlerEvent = new PrefixEvent();
        eventCount = 0;
        long lifetime = 10000;
		try {
            Scanner myReader = new Scanner(new File(trace_folder.replace("std", "ssp")));
            while (myReader.hasNextLine()) {
				String str = myReader.nextLine();
				if(str.contains("lifetime")) {
					lifetime = Long.parseLong(str.substring(11, str.length()));
				}
            }
            myReader.close();
        } catch (FileNotFoundException e) {
        }
        state = new State(threadSet.size(), lifetime);
    }

    protected void analyzeEvent(PrefixEvent handlerEvent, Long eventCount){
		
	}

    public void analyzeTrace() {
		if (this.parserType.isRR()) {
			analyzeTraceRR();
		}
        if (this.parserType.isSTD()) {
			analyzeTraceSTD();
		}
		printCompletionStatus();
    }

    private void analyzeTraceRR() {
        boolean flag = false;
        startTimeAnalysis = System.currentTimeMillis();
        long stopTimeAnalysis = 0;
        while(rrParser.checkAndGetNext(handlerEvent)) {
            eventCount = eventCount + 1;
            analyzeEvent(handlerEvent, eventCount);
            postHandleEvent(handlerEvent);
        }
        if(!flag) {
            stopTimeAnalysis = System.currentTimeMillis();
            System.out.println("Not matched");
        }
        long timeAnalysis = stopTimeAnalysis - startTimeAnalysis;
        System.out.println("Time for full analysis = " + timeAnalysis + " milliseconds");
    }

    private void analyzeTraceSTD() {
        while(stdParser.hasNext()){
            eventCount = eventCount + 1;
            stdParser.getNextEvent(handlerEvent);
            try{
                if(handlerEvent.Handle(state)) {
                    racyevents.add(eventCount);
                }
            }
            catch(OutOfMemoryError oome){
                System.err.println("Number of events = " + Long.toString(eventCount));
                state.printMemory();
                oome.printStackTrace();
            }
            // System.out.println(eventCount + " " + state.raceCnt + " " + state.states.size());
            // state.printMemory();
            postHandleEvent(handlerEvent);
        }
        System.out.println("Number of 'racy' events found = " + racyevents.size());
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
        rrParser = new ParseRoadRunner(trace_file, true);
        threadSet = rrParser.getThreadSet();
    }

    protected void resetSTDParser() {
        stdParser.totEvents = 0;
        try{
            stdParser.bufferedReader = new BufferedReader(new FileReader(sourceFile));
        }
        catch (FileNotFoundException ex) {
            System.out.println("Unable to open file '" + sourceFile + "'");
        }
    }

    protected boolean skipEvent(PrefixEvent handlerEvent) {
        // return !handlerEvent.getType().isAccessType();
        return false;
    }

	protected void postHandleEvent(PrefixEvent handlerEvent) {

    } 
}
