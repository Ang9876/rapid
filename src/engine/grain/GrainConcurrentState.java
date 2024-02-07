package engine.grain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import engine.pattern.State;
import event.Thread;
import event.Variable;

public class GrainConcurrentState extends State {
    
    ArrayList<NondetState> nondetStates;

    public GrainConcurrentState(HashSet<Thread> tSet) {
        nondetStates = new ArrayList<>();
        NondetState initState = new NondetState();
        nondetStates.add(initState);        
    }

    

    public void printMemory() {
    }
}

class Grain {
    HashSet<Thread> threads = new HashSet<>();
    HashSet<Variable> wtVars = new HashSet<>();
    HashSet<Variable> rdVars = new HashSet<>();
}

class NondetState {
    // current grain
    Grain currentGrain;

    // after set
    HashSet<Grain> afterSet;
    // HashMap<Variable, Grain> grainBuffer;
    HashSet<Variable> completeVars;

    public NondetState() {
        currentGrain = new Grain();
        afterSet = new HashSet<>();
        completeVars = new HashSet<>();
        // grainBuffer = new HashMap<>();
    }

    public NondetState(HashSet<Grain> afterSetCpy, HashSet<Variable> completeVarsCpy) {
        currentGrain = new Grain();
        afterSet = new HashSet<>(afterSetCpy);
        completeVars = new HashSet<>(completeVarsCpy);
        // grainBuffer = new HashMap<>(grainBufferCpy);
    }
}

