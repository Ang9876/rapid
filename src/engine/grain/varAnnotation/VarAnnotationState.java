package engine.grain.varAnnotation;

import java.util.HashMap;
import java.util.HashSet;

import event.Variable;

public class VarAnnotationState {
    
    public HashMap<Variable, HashSet<Long>> lastReads = new HashMap<>();
    public HashMap<Variable, Long> activeVarMap = new HashMap<>();

    public void pushVar(VarAnnotationEvent e) {
        Variable var = e.getVariable();
        if(activeVarMap.containsKey(var)) {
            long lastRead = activeVarMap.get(var);
            if(!lastReads.containsKey(var)) {
                lastReads.put(var, new HashSet<>());
            }
            lastReads.get(var).add(lastRead);
        }
        activeVarMap.put(var, e.eventCounter);
    }

    public void updateVar(VarAnnotationEvent e) {
        Variable var = e.getVariable();
        activeVarMap.put(var, e.eventCounter);
    }

    public void finalCheck() {
        for(Variable var: activeVarMap.keySet()) {
            long lastRead = activeVarMap.get(var);
            if(!lastReads.containsKey(var)) {
                lastReads.put(var, new HashSet<>());
            }
            lastReads.get(var).add(lastRead);
        }
    }


    public void prlongMemory() {}
}



