package engine.racedetectionengine.grainRace;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import engine.racedetectionengine.State;
import event.Lock;
import event.Thread;
import event.Variable;

public class GrainRaceState extends State {
    
    HashSet<Thread> threadSet;
    TreeMap<NondetState, HashSet<Long>> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;
    public HashSet<Long> racyEvents = new HashSet<>();

    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new HashSet<>());    
        this.lastReads = lastReads;
    }

    public boolean update(GrainRaceEvent e) {
        boolean findRace = false;
        TreeMap<NondetState, HashSet<Long>> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            // System.out.println(state);
            HashSet<Long> e2Sets = nondetStates.get(state);
            boolean isFirstGrain = state.aftSet.threads.isEmpty();
            boolean candidate = e.getType().isAccessType() && (e.getVariable() == state.e1Var && e.getThread() != state.e1Thr && (e.getType().isWrite() || state.e1Write));
            boolean doEdgeContraction = !isFirstGrain && !candidate &&
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e)));
            if(state.candidate && !state.currentGrain.isDependentWith(state.aftSetNoE1)){
                // if current grain contains e2 and it is independent of all grains other than the grain containing e1
                // for(long c: nondetStates.get(state)) {
                //     if(!racyEvents.contains(c)) {
                //         System.out.println(c);
                //     }
                // }
                racyEvents.addAll(nondetStates.get(state));
                findRace = true;
            }
            // Stop current grain here  
            if(!doEdgeContraction && !state.currentGrain.threads.isEmpty() && state.e1Thr != null) {
                NondetState newState = new NondetState(state, false);
                if(state.aftSet.threads.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                    newState.aftSet.updateGrain(state.currentGrain);
                    if(!isFirstGrain) {
                        newState.aftSetNoE1.updateGrain(state.currentGrain);
                    }
                }
                newState.currentGrain.updateGrain(e, lastReads);
                if(candidate) {
                    newState.candidate = true;
                }
                newState.hashString = newState.toString();
                // System.out.println(newState.hashString);
                if(!newStates.containsKey(newState)) {
                    newStates.put(newState, new HashSet<>());
                }
                newStates.get(newState).addAll(e2Sets);
                if(candidate) {
                    newStates.get(newState).add(e.eventCount);
                }
                
            }
            boolean minimal = !state.currentGrain.threads.isEmpty() && state.currentGrain.incompleteWtVars.isEmpty() && state.currentGrain.incompleteAcqs.isEmpty();
            // update current grain
            if(doEdgeContraction || !minimal) {
                if(isFirstGrain) {
                    if(e.getType().isAccessType()) {
                        state.e1Thr = e.getThread();
                        state.e1Var = e.getVariable();
                        state.e1Write = e.getType().isWrite();
                    }
                    else {
                        state.e1Thr = null;
                        state.e1Var = null;
                        state.e1Write = false;
                    }
                }
                state.currentGrain.updateGrain(e, lastReads);
                NondetState newState = new NondetState(state, true);
                // System.out.println(newState.hashString);
                if(!newStates.containsKey(newState)) {
                    newStates.put(newState, new HashSet<>());
                }
                newStates.get(newState).addAll(e2Sets);
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads);
        if(e.getType().isAccessType()) {
            newState.e1Thr = e.getThread();
            newState.e1Var = e.getVariable();
            newState.e1Write = e.getType().isWrite();
        } 
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new HashSet<>());
        }
        nondetStates = newStates;
        return findRace;
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            if(state.candidate && !state.currentGrain.isDependentWith(state.aftSetNoE1)) {
                // for(long c: nondetStates.get(state)) {
                //     if(!racyEvents.contains(c)) {
                //         System.out.println(c);
                //     }
                // }
                racyEvents.addAll(nondetStates.get(state));
            }
        }
        return false;
    }

    public void printMemory() {
        // System.out.println(nondetStates.size());
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state.hashString);
        // }
    }
}

class NondetState {
    public Thread e1Thr;
    public Variable e1Var;
    public boolean e1Write;
    public Grain currentGrain;
    public Grain aftSet;
    public Grain aftSetNoE1;
    public boolean candidate;
    public String hashString;

    public NondetState() {
        e1Thr = null;
        e1Var = null;
        e1Write = false;
        candidate = false;
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
        e1Thr = state.e1Thr;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        candidate = copy ? state.candidate : false;
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        hashString = this.toString();
    }

    public boolean isDependentWith(GrainRaceEvent e) {
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
        if(e.getType().isAcquire() && (aftSet.incompleteAcqs.contains(e.getLock()) || aftSet.incompleteRels.contains(e.getLock()))) {
            return true;
        }
        if(e.getType().isRelease() && (aftSet.incompleteAcqs.contains(e.getLock()) || aftSet.completeLocks.contains(e.getLock()) || aftSet.incompleteRels.contains(e.getLock()) )) {
            return true;
        }
        return false;
    }

    public String toString() {
        return (e1Thr != null ? e1Thr.toString() : "NULL") + (e1Var != null ? e1Var.toString() : "NULL") + (e1Write ? "W" : "R") + currentGrain.toString() + aftSet.toString() + aftSetNoE1.toString() + candidate;
    }
}

class Grain {
    public HashSet<Thread> threads;
    public HashSet<Variable> completeVars;
    public HashSet<Variable> incompleteWtVars;
    public HashSet<Variable> incompleteRdVars;
    public HashSet<Lock> completeLocks;
    public HashSet<Lock> incompleteAcqs;
    public HashSet<Lock> incompleteRels; 
   
    public Grain() {
        threads = new HashSet<>();
        completeVars = new HashSet<>();
        incompleteWtVars = new HashSet<>();
        incompleteRdVars = new HashSet<>();
        completeLocks = new HashSet<>();
        incompleteAcqs = new HashSet<>();
        incompleteRels = new HashSet<>();
    }

    public Grain(Grain other) {
        threads = new HashSet<>(other.threads);
        completeVars = new HashSet<>(other.completeVars);
        incompleteWtVars = new HashSet<>(other.incompleteWtVars);
        incompleteRdVars = new HashSet<>(other.incompleteRdVars);
        completeLocks = new HashSet<>(other.completeLocks);
        incompleteAcqs = new HashSet<>(other.incompleteAcqs);
        incompleteRels = new HashSet<>(other.incompleteRels);
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
        for(Lock l: other.incompleteAcqs) {
            if(incompleteAcqs.contains(l) || incompleteRels.contains(l) || completeLocks.contains(l)) {
                return true;
            }
        }
        for(Lock l: other.incompleteRels) {
            if(incompleteAcqs.contains(l) || incompleteRels.contains(l) || completeLocks.contains(l)) {
                return true;
            }
        }
        for(Lock l: other.completeLocks) {
            if(incompleteAcqs.contains(l) || incompleteRels.contains(l)) {
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
        incompleteAcqs.addAll(other.incompleteAcqs); 
        incompleteRels.addAll(other.incompleteRels);
        completeLocks.removeAll(incompleteAcqs);
        completeLocks.removeAll(incompleteRels);
    }

    public void updateGrain(GrainRaceEvent e, HashMap<Variable, HashSet<Long>> lastReads) {
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
            incompleteAcqs.add(e.getLock());
        }
        if(e.getType().isRelease()) {
            if(incompleteAcqs.contains(e.getLock())) {
                incompleteAcqs.remove(e.getLock());
                if(!incompleteRels.contains(e.getLock())) {
                    completeLocks.add(e.getLock());
                }
            }
            else {
                incompleteRels.add(e.getLock());
            }
        }
    }

    private <T> String setToString(HashSet<T> set) {
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return  setToString(threads) + setToString(incompleteWtVars) + setToString(incompleteRdVars) + setToString(completeLocks) + setToString(incompleteAcqs) + setToString(incompleteRels); 
    } 
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}