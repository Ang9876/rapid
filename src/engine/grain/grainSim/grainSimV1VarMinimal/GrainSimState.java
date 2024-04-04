package engine.grain.grainSim.grainSimV1VarMinimal;

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
        nondetStates = new TreeSet<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.add(initState);    
        this.lastReads = lastReads;
    }

    public boolean update(GrainEvent e) {
        TreeSet<NondetState> newStates = new TreeSet<>(new StateComparator());
        Iterator<NondetState> iter = nondetStates.iterator();
        while(iter.hasNext()){
            // boolean longGrain = false;
            NondetState state = iter.next();
            // Every grain containing e2 must start from e2
            if(e.isE2 && !state.currentGrain.threads.isEmpty()) {
                continue;
            }

            // update current grain
            state.currentGrain.threads.add(e.getThread());
            if(e.getType().isExtremeType()) {
                state.currentGrain.threads.add(e.getTarget());
            }

            if(e.getType().isRead()) {
                if(!state.currentGrain.incompleteWtVars.contains(e.getVariable())) {
                    state.currentGrain.incompleteRdVars.add(e.getVariable());
                }
                else if(lastReads.get(e.getVariable()).contains(e.eventCount)) {
                    state.currentGrain.incompleteWtVars.remove(e.getVariable());
                    state.currentGrain.completeVars.add(e.getVariable());
                }
            }
            if(e.getType().isWrite()) {
                if(!lastReads.get(e.getVariable()).contains(e.eventCount)) {
                    state.currentGrain.incompleteWtVars.add(e.getVariable());
                }
                else {
                    state.currentGrain.completeVars.add(e.getVariable());
                }
            }
            if(e.getType().isAcquire()) {
                state.currentGrain.incompleteLocks.add(e.getLock());
            }
            if(e.getType().isRelease()) {
                if(state.currentGrain.incompleteLocks.contains(e.getLock())) {
                    state.currentGrain.incompleteLocks.remove(e.getLock());
                    state.currentGrain.completeLocks.add(e.getLock());
                }
                else {
                    state.currentGrain.incompleteLocks.add(e.getLock());
                }
            }

            boolean minimal = true;
            Iterator<Grain> grainIter = state.subCurrentGrains.iterator();
            while(grainIter.hasNext()) {
                boolean edge = false;
                Grain g = grainIter.next();
                if(g.threads.contains(e.getThread())) {
                    edge = true;
                }
                if(e.getType().isRead()) {
                    if(g.incompleteWtVars.contains(e.getVariable())) {
                        edge = true;
                    }
                    if(!lastReads.get(e.getVariable()).contains(e.eventCount)) {
                        if(g.completeVars.contains(e.getVariable())) {
                            edge = true;
                        }
                    }
                }
                if(e.getType().isWrite()) {
                    if(g.incompleteWtVars.contains(e.getVariable()) || g.incompleteRdVars.contains(e.getVariable())) {
                        edge = true;
                    }
                    if(!lastReads.get(e.getVariable()).contains(e.eventCount)) {
                        if(g.completeVars.contains(e.getVariable())) {
                            edge = true;
                        }
                    }
                }
                if(e.getType().isAcquire()) {
                    if(g.incompleteLocks.contains(e.getLock()) || g.completeLocks.contains(e.getLock())) {
                        edge = true;
                    }
                }
                if(e.getType().isRelease()) {
                    if(g.incompleteLocks.contains(e.getLock())) {
                        edge = true;
                    }
                }
                if(!edge) {
                    minimal = false;
                }
                
            }
            // System.out.println(state.currentGrain);
            state.subCurrentGrains.add(new Grain(state.currentGrain));
            // Do not stop current grain here
            // If before e1, do not add current grain into checking.
            if(!witnessE1 || !minimal) {
                state.hashString = state.toString();
                newStates.add(state);
                continue;
            }

            // Stop current grain here
            if(witnessE2){
                if(state.currentGrain.isDependentWith(state.aftSetNoE1)) {
                    // if current grain contains e2 and it is dependent with a grain other than the grain containing e1, then ignore it.
                    state.hashString = state.toString();
                    newStates.add(state);
                    continue;
                }
                else {
                    // if current grain contains e2 and it is independent of all grains other than the grain containing e1, then return true. 
                    return true;
                }
            }

            // Make a copy of the state but with a new empty current grain
            NondetState newState = new NondetState(state);
            if(state.aftSet.threads.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                newState.aftSet.updateGrain(state.currentGrain);
                if(!e.isE1) {
                    newState.aftSetNoE1.updateGrain(state.currentGrain);
                }
            }
            newState.hashString = newState.toString();
            newStates.add(newState);
            if(!e.isE1) {
                state.hashString = state.toString();
                newStates.add(state);
            }
        }
        nondetStates = newStates;
        if(!witnessE1) {
            nondetStates.add(new NondetState());
        }
        return false;
    }

    @Override
    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        return false;
    }

    public void printMemory() {
        System.out.println(nondetStates.size());
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
    public TreeSet<Grain> subCurrentGrains;

    public NondetState() {
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain(); 
        subCurrentGrains = new TreeSet<>(new GrainComparator());
        hashString = this.toString();
    }

    public NondetState(NondetState state) {
        currentGrain = new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        subCurrentGrains = new TreeSet<>(new GrainComparator());
        hashString = this.toString();
    }

    private <T> String setToString(TreeSet<T> set) {
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return currentGrain.toString() + aftSet.toString() + aftSetNoE1.toString() + setToString(subCurrentGrains);
    }
}

class Grain {
    public HashSet<Thread> threads;
    public HashSet<Variable> completeVars;
    public HashSet<Variable> incompleteWtVars;
    public HashSet<Variable> incompleteRdVars;
    public HashSet<Lock> completeLocks;
    public HashSet<Lock> incompleteLocks;
    public String hashString;
   
    public Grain() {
        threads = new HashSet<>();
        completeVars = new HashSet<>();
        incompleteWtVars = new HashSet<>();
        incompleteRdVars = new HashSet<>();
        completeLocks = new HashSet<>();
        incompleteLocks = new HashSet<>();
        hashString = this.toString();
    }

    public Grain(Grain other) {
        threads = new HashSet<>(other.threads);
        completeVars = new HashSet<>(other.completeVars);
        incompleteWtVars = new HashSet<>(other.incompleteWtVars);
        incompleteRdVars = new HashSet<>(other.incompleteRdVars);
        completeLocks = new HashSet<>(other.completeLocks);
        incompleteLocks = new HashSet<>(other.incompleteLocks);
        hashString = this.toString();
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

class GrainComparator implements Comparator<Grain> {
    public int compare(Grain g1, Grain g2) {
        return g1.hashString.compareTo(g2.hashString);
    }
}