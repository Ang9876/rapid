package engine.grain;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Scanner;

import engine.Engine;
import event.Thread;
import parse.ParserType;
import parse.std.ParseStandard;

public class GrainConcurrentEngine extends Engine<GrainConcurrentEvent> {

    protected long eventCount;
    protected long totalSkippedEvents;
    
    protected HashSet<Thread> threadSet;
    protected GrainConcurrentState state;

    protected long startTimeAnalysis = 0;
    protected long e1Index, e2Index;

    public GrainConcurrentEngine(ParserType pType, String trace_folder, String eventsFileName) {
        super(pType);
        try {
            Scanner myReader = new Scanner(new File(eventsFileName));
            e1Index = myReader.nextLong();
            e2Index = myReader.nextLong();
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        } catch (NoSuchElementException e) {
            System.out.println("Please specify two events");
            e.printStackTrace();
        }
        eventCount = 0;
        totalSkippedEvents = 0;
        handlerEvent = new GrainConcurrentEvent();
        initializeReader(trace_folder);
        state = new GrainConcurrentState(threadSet);
    }

    public void analyzeTrace() {
        if (this.parserType.isSTD()) {
			analyzeTraceSTD();
		}
		printCompletionStatus();
    }

    private void analyzeTraceSTD() {
        boolean flag = false;
        eventCount = 0;
        while(stdParser.hasNext()){
			eventCount = eventCount + 1;
            if(eventCount == e1Index) {
                handlerEvent.isE1 = true;
            }
            if(eventCount == e2Index) {
                handlerEvent.isE2 = true;
            }
			stdParser.getNextEvent(handlerEvent);
            boolean matched = handlerEvent.Handle(state);
            if (matched) {
                flag = true;
                break;
            }
            postHandleEvent(handlerEvent);
        }
        if(flag || state.finalCheck()) {
            System.out.println("e1 and e2 are concurrent");
        }
        else {
            System.out.println("e1 and e2 are ordered");
        }
        state.printMemory();
    }

    protected void analyzeTraceRR() {}

    protected void initializeReaderRV(String trace_folder) {}

	protected void initializeReaderCSV(String trace_file) {}

	protected void initializeReaderSTD(String trace_file) {
        stdParser = new ParseStandard(trace_file, true);
		threadSet = stdParser.getThreadSet();
    }

	protected void initializeReaderRR(String trace_file) {}

    protected boolean skipEvent(GrainConcurrentEvent handlerEvent) {
        return false;
    }

	protected void postHandleEvent(GrainConcurrentEvent handlerEvent) {
        handlerEvent.isE1 = false;
        handlerEvent.isE2 = false;
    }
}
