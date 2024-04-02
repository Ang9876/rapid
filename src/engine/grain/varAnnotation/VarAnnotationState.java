package engine.grain.varAnnotation;

import java.util.HashMap;

import event.Lock;
import event.Variable;
import util.Pair;

public class VarAnnotationState {
    
    public HashMap<Integer, Integer> lastReads = new HashMap<>();
    public HashMap<Variable, Pair<Integer, Integer>> activeVarMap = new HashMap<>();
    public HashMap<Lock, Pair<Integer, Integer>> activeLockMap = new HashMap<>();

    public void pushVar(VarAnnotationEvent e) {
        Variable var = e.getVariable();
        if(activeVarMap.containsKey(var)) {
            Pair<Integer, Integer> rwPair = activeVarMap.get(var);
            lastReads.put(rwPair.first, rwPair.second);
        }
        activeVarMap.put(var, new Pair<>(e.eventCounter, e.eventCounter));
    }

    public void pushLock(VarAnnotationEvent e) {
        Lock lock = e.getLock();
        if(activeLockMap.containsKey(lock)) {
            Pair<Integer, Integer> rwPair = activeLockMap.get(lock);
            lastReads.put(rwPair.first, rwPair.second);
        }
        activeLockMap.put(lock, new Pair<>(e.eventCounter, e.eventCounter));
    }

    public void updateVar(VarAnnotationEvent e) {
        Variable var = e.getVariable();
        Pair<Integer, Integer> rwPair = activeVarMap.get(var);
        rwPair.second = e.eventCounter;
    }

    public void updateLock(VarAnnotationEvent e) {
        Lock lock = e.getLock();
        Pair<Integer, Integer> rwPair = activeLockMap.get(lock);
        rwPair.second = e.eventCounter;
    }

    public void finalCheck() {
        for(Variable var: activeVarMap.keySet()) {
            Pair<Integer, Integer> rwPair = activeVarMap.get(var);
            lastReads.put(rwPair.first, rwPair.second);
        }

        for(Lock lock: activeLockMap.keySet()) {
            Pair<Integer, Integer> rwPair = activeLockMap.get(lock);
            lastReads.put(rwPair.first, rwPair.second);
        }
    }


    public void printMemory() {}
}



