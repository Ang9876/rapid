package engine.grain.grainSim.grainSimV1VarContract2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import engine.grain.GrainEvent;
import engine.grain.GrainState;
import event.Lock;
import event.Thread;
import event.Variable;

public class GrainSimState extends GrainState {
    
    TreeSet<NondetState> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;

    public GrainSimState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads) {
        threadSet = tSet;
        witnessE1 = false;
        witnessE2 = false;
        afterE1 = false;
        nondetStates = new TreeSet<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.add(initState);    
        this.lastReads = lastReads;
    }

    public boolean update(GrainEvent e) {
        TreeSet<NondetState> newStates = new TreeSet<>(new StateComparator());
        Iterator<NondetState> iter = nondetStates.iterator();
        while(iter.hasNext()){
            NondetState state = iter.next();
            // System.out.println(state.hashString);
            boolean doEdgeContraction = witnessE1 && !e.isE1 && !afterE1 && !witnessE2 && 
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e)));


            // Stop current grain here  
            if(!doEdgeContraction && !state.currentGrain.threads.isEmpty()) {
                NondetState newState = new NondetState(state);
                if(witnessE1 && !e.isE1) {
                    if(state.aftSet.threads.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                        newState.aftSet.updateGrain(state.currentGrain);
                        if(!afterE1) {
                            newState.aftSetNoE1.updateGrain(state.currentGrain);
                        }
                    }
                }
                newState.currentGrain.updateGrain(e, lastReads);
                boolean isSim = true;
                if(witnessE2 && !e.isE2){
                    if(state.currentGrain.isDependentWith(state.aftSetNoE1)) {
                        isSim = false;
                    }
                    else {
                        // if current grain contains e2 and it is independent of all grains other than the grain containing e1
                        return true;
                    }
                }
                if(isSim) {
                    newState.hashString = newState.toString();
                    newStates.add(newState);
                }
            }
            
            // update current grain
            if(!e.isE2 && !afterE1) {
                state.currentGrain.updateGrain(e, lastReads);
                state.hashString = state.toString();
                newStates.add(state);
            }
        }
        nondetStates = newStates;
        return false;
    }

    @Override
    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates) {
            if(!state.currentGrain.isDependentWith(state.aftSetNoE1)) {
                return true;
            }
        }
        return false;
    }

    public void printMemory() {
        // System.out.println(nondetStates.size());
        // for(NondetState state: nondetStates) {
        //     System.out.println(state.hashString);
        // }
    }
}

class NondetState {
    // current grain
    public Grain currentGrain;
    public Grain aftSet;
    public Grain aftSetNoE1;
    public String hashString;

    public NondetState() {
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        hashString = this.toString();
    }

    public NondetState(NondetState state) {
        currentGrain = new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        hashString = this.toString();
    }

    public boolean isDependentWith(GrainEvent e) {
        if(aftSet.threads.contains(e.getThread())) {
            return true;
        }
        if(e.getType().isExtremeType() && aftSet.threads.contains(e.getTarget())) {
            return true;
        }
        if(e.getType().isRead() && (aftSet.incompleteWtVars.contains(e.getVariable()) || aftSet.completeVars.contains(e.getVariable()))) {
            return true;
        }
        if(e.getType().isWrite() && (aftSet.incompleteWtVars.contains(e.getVariable()) || aftSet.incompleteRdVars.contains(e.getVariable()))) {
            return true;
        }
        if(e.getType().isAcquire() && (aftSet.incompleteLocks.contains(e.getLock()))) {
            return true;
        }
        if(e.getType().isRelease() && (aftSet.incompleteLocks.contains(e.getLock()) || aftSet.completeLocks.contains(e.getLock()))) {
            return true;
        }
        return false;
    }

    public String toString() {
        return currentGrain.toString() + aftSet.toString() + aftSetNoE1.toString();
    }
}

class Grain {
    public HashSet<Thread> threads;
    public HashSet<Variable> completeVars;
    public HashSet<Variable> incompleteWtVars;
    public HashSet<Variable> incompleteRdVars;
    public HashSet<Lock> completeLocks;
    public HashSet<Lock> incompleteLocks;
   
    public Grain() {
        threads = new HashSet<>();
        completeVars = new HashSet<>();
        incompleteWtVars = new HashSet<>();
        incompleteRdVars = new HashSet<>();
        completeLocks = new HashSet<>();
        incompleteLocks = new HashSet<>();
    }

    public Grain(Grain other) {
        threads = new HashSet<>(other.threads);
        completeVars = new HashSet<>(other.completeVars);
        incompleteWtVars = new HashSet<>(other.incompleteWtVars);
        incompleteRdVars = new HashSet<>(other.incompleteRdVars);
        completeLocks = new HashSet<>(other.completeLocks);
        incompleteLocks = new HashSet<>(other.incompleteLocks);
    }

    public boolean isDependentWith(Grain other) {
        for(Thread t: other.threads) {
            if(threads.contains(t)) {
                return true;
            }
        }
        for(Variable v: other.incompleteWtVars) {
            if(incompleteRdVars.contains(v) || incompleteWtVars.contains(v) || completeVars.contains(v)) {
                return true;
            }
        }
        for(Variable v: other.incompleteRdVars) {
            if(incompleteWtVars.contains(v) || completeVars.contains(v)) {
                return true;
            }
        }
        for(Variable v: other.completeVars) {
            if(incompleteWtVars.contains(v) || incompleteRdVars.contains(v)) {
                return true;
            }
        }
        for(Lock l: other.incompleteLocks) {
            if(incompleteLocks.contains(l) || completeLocks.contains(l)) {
                return true;
            }
        }
        for(Lock l: other.completeLocks) {
            if(incompleteLocks.contains(l)) {
                return true;
            }
        }
        return false;
    }

    public void updateGrain(Grain other) {
        threads.addAll(other.threads);
        completeVars.addAll(other.completeVars);
        incompleteWtVars.addAll(other.incompleteWtVars);
        incompleteRdVars.addAll(other.incompleteRdVars);
        completeVars.removeAll(incompleteWtVars);
        completeVars.removeAll(incompleteRdVars);
        completeLocks.addAll(other.completeLocks);
        incompleteLocks.addAll(other.incompleteLocks); 
        completeLocks.removeAll(incompleteLocks);
    }

    public void updateGrain(GrainEvent e, HashMap<Variable, HashSet<Long>> lastReads) {
        threads.add(e.getThread());
        if(e.getType().isExtremeType()) {
            threads.add(e.getTarget());
        }

        if(e.getType().isRead()) {
            if(!incompleteWtVars.contains(e.getVariable())) {
                incompleteRdVars.add(e.getVariable());
            }
            else if(lastReads.get(e.getVariable()).contains(e.eventCount)) {
                incompleteWtVars.remove(e.getVariable());
                completeVars.add(e.getVariable());
            }
        }
        if(e.getType().isWrite()) {
            if(!lastReads.get(e.getVariable()).contains(e.eventCount)) {
                incompleteWtVars.add(e.getVariable());
            }
            else {
                completeVars.add(e.getVariable());
            }
        }
        if(e.getType().isAcquire()) {
            incompleteLocks.add(e.getLock());
        }
        if(e.getType().isRelease()) {
            if(incompleteLocks.contains(e.getLock())) {
                incompleteLocks.remove(e.getLock());
                completeLocks.add(e.getLock());
            }
            else {
                incompleteLocks.add(e.getLock());
            }
        }
    }

    private <T> String setToString(HashSet<T> set) {
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return  setToString(threads) + setToString(incompleteWtVars) + setToString(incompleteRdVars) + setToString(incompleteLocks); 
    } 
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}