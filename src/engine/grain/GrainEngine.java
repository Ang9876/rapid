package engine.grain;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Scanner;

import engine.Engine;
import event.Thread;
import parse.ParserType;
import parse.std.ParseStandard;

public class GrainEngine<S extends GrainState> extends Engine<GrainEvent> {

    protected long eventCount;
    protected long totalSkippedEvents;
    
    protected HashSet<Thread> threadSet;
    protected HashMap<String, Thread> threadMap;
    protected S state;

    protected long startTimeAnalysis = 0;
    protected long e1Index, e2Index;

    public GrainEngine(ParserType pType, String trace_folder, String eventsFileName) {
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
        handlerEvent = new GrainEvent();
        initializeReader(trace_folder);
    }

    public void analyzeTrace() {
        if (this.parserType.isSTD()) {
			analyzeTraceSTD();
		}
		printCompletionStatus();
    }

    protected void analyzeTraceSTD() {
        boolean flag = false;
        eventCount = 0;
        while(stdParser.hasNext()){
			eventCount = eventCount + 1;
            handlerEvent.eventCount = eventCount;
            if(eventCount == e1Index) {
                handlerEvent.isE1 = true;
            }
            else {
                handlerEvent.isE1 = false;
            }
            if(eventCount == e2Index) {
                handlerEvent.isE2 = true;
            }
            else {
                handlerEvent.isE2 = false;
            }
			stdParser.getNextEvent(handlerEvent);
            boolean matched = handlerEvent.Handle(state);
            state.printMemory();
            if (matched) {
                flag = true;
                break;
            }
            postHandleEvent(handlerEvent);
        }
        if(flag || state.finalCheck()) {
            System.out.println("YES");
        }
        else {
            System.out.println("NO");
        }
        state.printMemory();
    }

    protected void analyzeTraceRR() {}

    protected void initializeReaderRV(String trace_folder) {}

	protected void initializeReaderCSV(String trace_file) {}

	protected void initializeReaderSTD(String trace_file) {
        stdParser = new ParseStandard(trace_file, true);
		threadSet = stdParser.getThreadSet();
        threadMap = stdParser.getThreadMap();
    }

	protected void initializeReaderRR(String trace_file) {}

    protected boolean skipEvent(GrainEvent handlerEvent) {
        return false;
    }

	protected void postHandleEvent(GrainEvent handlerEvent) {
        handlerEvent.isE1 = false;
        handlerEvent.isE2 = false;
    }
}
