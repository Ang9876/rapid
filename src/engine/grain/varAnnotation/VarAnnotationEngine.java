package engine.grain.varAnnotation;

import java.util.HashMap;
import java.util.HashSet;

import engine.Engine;
import event.Variable;
import parse.ParserType;
import parse.std.ParseStandard;

public class VarAnnotationEngine extends Engine<VarAnnotationEvent> {

    VarAnnotationState state;

    public VarAnnotationEngine(ParserType pType, String trace_folder, ParseStandard stdParser) {
        super(pType);
        this.stdParser = stdParser;
        state = new VarAnnotationState();
        handlerEvent = new VarAnnotationEvent();
    }

    public void analyzeTrace() {
        if (this.parserType.isSTD()) {
			analyzeTraceSTD();
		}
		printCompletionStatus();
    }

    protected void analyzeTraceSTD() {
        long eventCount = 0;
        while(stdParser.hasNext()){
			stdParser.getNextEvent(handlerEvent);
            eventCount += 1;
            handlerEvent.eventCounter = eventCount;
            handlerEvent.Handle(state);
        }
        state.finalCheck();
    }

    public HashMap<Variable, HashSet<Long>> getLastReads() {
        return state.lastReads;
    }

    protected void initializeReaderRV(String trace_folder) {}

	protected void initializeReaderCSV(String trace_file) {}

	protected void initializeReaderSTD(String trace_file) {}

	protected void initializeReaderRR(String trace_file) {}

}
