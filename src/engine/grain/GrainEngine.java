package engine.grain;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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
    String trace_folder;

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
        this.trace_folder = trace_folder;
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
        long sizeAcc = 0;
        FileWriter fw;
        // try{
        //      fw = new FileWriter(trace_folder.replace("std", "out"), true);
        //      fw.append(e1Index + " " + e2Index);
        
        
        while(stdParser.hasNext()){
			eventCount = eventCount + 1;
            handlerEvent.eventCount = eventCount;
            if(eventCount == e1Index) {
                // System.out.println("find");
                handlerEvent.isE1 = true;
            }
            else {
                
                handlerEvent.isE1 = false;
            }
            if(eventCount == e2Index) {
                // System.out.println("find");
                handlerEvent.isE2 = true;
            }
            else {
                handlerEvent.isE2 = false;
            }
			stdParser.getNextEvent(handlerEvent);
            boolean matched = handlerEvent.Handle(state);
            sizeAcc += state.size();
            // if(eventCount % 500 == 0) {
            //     System.out.println(sizeAcc / 500);
            //     sizeAcc = 0;
            // }
            state.printMemory();
            if (matched) {
                flag = true;
                break;
            }
            postHandleEvent(handlerEvent);
        }
        if(flag || state.finalCheck()) {
            System.out.print("YES");
        }
        else {
            System.out.print("NO");
        }
        //     if(flag || state.finalCheck()) {
        //         fw.append("YES\n");
        //     }
        //     else {
        //         fw.append("NO\n");
        //     }
        //     fw.close();
        // } catch (Exception e) {}
        // state.printMemory();
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
