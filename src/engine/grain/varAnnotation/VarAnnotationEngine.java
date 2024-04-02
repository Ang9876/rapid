package engine.grain.varAnnotation;

import java.util.HashMap;

import engine.Engine;
import parse.ParserType;
import parse.std.ParseStandard;

public class VarAnnotationEngine extends Engine<VarAnnotationEvent> {

    VarAnnotationState state;

    public VarAnnotationEngine(ParserType pType, String trace_folder) {
        super(pType);
        initializeReaderSTD(trace_folder);
        state = new VarAnnotationState();
    }

    public void analyzeTrace() {
        if (this.parserType.isSTD()) {
			analyzeTraceSTD();
		}
		printCompletionStatus();
    }

    protected void analyzeTraceSTD() {
        while(stdParser.hasNext()){
			stdParser.getNextEvent(handlerEvent);
            handlerEvent.Handle(state);
        }
        state.finalCheck();
    }

    public HashMap<Integer, Integer> getLastReads() {
        return state.lastReads;
    }

    protected void initializeReaderRV(String trace_folder) {}

	protected void initializeReaderCSV(String trace_file) {}

	protected void initializeReaderSTD(String trace_file) {
        stdParser = new ParseStandard(trace_file, true);
    }

	protected void initializeReaderRR(String trace_file) {}

}
