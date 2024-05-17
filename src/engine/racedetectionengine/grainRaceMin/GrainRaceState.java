package engine.racedetectionengine.grainRaceMin;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import engine.grain.GrainEvent;
import engine.racedetectionengine.State;
import event.Lock;
import event.Thread;
import event.Variable;

public class GrainRaceState extends State {
    
    public static int numOfThreads;
    public static int numOfVars;
    public static int numOfLocks;
    HashSet<Thread> threadSet;
    TreeMap<NondetState, HashSet<Long>> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;
    public HashSet<Long> racyEvents = new HashSet<>();

    GrainRaceEvent ev;
    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new HashSet<>());    
        this.lastReads = lastReads;
    }

    public boolean update(GrainRaceEvent e) {
        ev = e;
        boolean findRace = false;
        TreeMap<NondetState, HashSet<Long>> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            // System.out.println(state);
            HashSet<Long> e2Sets = nondetStates.get(state);
            boolean isFirstGrain = state.aftSet.threadsBitSet.isEmpty();
            boolean candidate = e.getType().isAccessType() && (e.getVariable() == state.e1Var && e.getThread() != state.e1Thr && (e.getType().isWrite() || state.e1Write)) && !state.isDependentWith(e, true);
            boolean doEdgeContraction = !isFirstGrain && !candidate &&
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e, false)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e, false)));
            boolean minimal = !state.currentGrain.threadsBitSet.isEmpty() && state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();


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
            if(!doEdgeContraction && !state.currentGrain.threadsBitSet.isEmpty() && state.e1Thr != null) {
                NondetState newState = new NondetState(state, false);
                if(state.aftSet.threadsBitSet.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                    newState.aftSet.updateGrain(state.currentGrain);
                    if(!isFirstGrain) {
                        newState.aftSetNoE1.updateGrain(state.currentGrain);
                    }
                }
                newState.lastDependent = !isFirstGrain && state.currentGrain.isDependentWith(state.aftSet) && !minimal; 
                newState.currentGrain.updateGrain(e, lastReads);
                if(candidate) {
                    newState.candidate = true;
                }
                newState.hashString = newState.toString();
                // System.out.println(newState.hashString);
                if(addToStates(newStates, newState)) {
                    if(!newStates.containsKey(newState)) {
                        newStates.put(newState, new HashSet<>());
                    }
                    newStates.get(newState).addAll(e2Sets);
                    if(candidate) {
                        newStates.get(newState).add(e.eventCount);
                    }
                }
            }
            
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
                if((!state.lastDependent || !state.currentGrain.isDependentWith(state.aftSetNoE1))) {
                    NondetState newState = new NondetState(state, true);
                    // System.out.println(newState.hashString);
                    if(addToStates(newStates, newState)) {
                        if(!newStates.containsKey(newState)) {
                            newStates.put(newState, new HashSet<>());
                        }
                        newStates.get(newState).addAll(e2Sets);
                    }
                }
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

    private boolean addToStates(TreeMap<NondetState, HashSet<Long>> states, NondetState newState) {
        for(NondetState state: states.keySet()) {
            if(newState.e1Var == state.e1Var && newState.e1Thr == state.e1Thr && newState.e1Write == state.e1Write && state.aftSetNoE1.subsume(newState.aftSetNoE1) && state.currentGrain.subsume(newState.currentGrain) && state.aftSet.subsume(newState.aftSet)) {
                return false;
            }
        }

        return true;
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            // System.out.println(state.hashString);
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
        System.out.println(nondetStates.size());
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
    public boolean lastDependent;
    public String hashString;

    public NondetState() {
        e1Thr = null;
        e1Var = null;
        e1Write = false;
        candidate = false;
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        lastDependent = false;
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
        e1Thr = state.e1Thr;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        candidate = copy ? state.candidate : false;
        lastDependent = copy ? state.lastDependent : false;
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        hashString = this.toString();
    }

    public boolean isDependentWith(GrainRaceEvent e, boolean noE1) {
        Grain frontier = noE1 ? aftSetNoE1 : aftSet;

        if(frontier.threadsBitSet.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && frontier.threadsBitSet.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isRead() && (frontier.incompleteWtVarsBitSet.get(e.getVariable().getId()) || frontier.completeVarBitSet.get(e.getVariable().getId()))) {
            return true;
        }
        if(e.getType().isWrite() && (frontier.incompleteWtVarsBitSet.get(e.getVariable().getId()) || frontier.incompleteRdVarsBitSet.get(e.getVariable().getId()))) {
            return true;
        }
        if(e.getType().isAcquire() && (frontier.incompleteAcqsBitSet.get(e.getLock().getId()) || frontier.incompleteRelsBitSet.get(e.getLock().getId()))) {
            return true;
        }
        if(e.getType().isRelease() && (frontier.incompleteAcqsBitSet.get(e.getLock().getId()) || frontier.completeLocksBitSet.get(e.getLock().getId()) || frontier.incompleteRelsBitSet.get(e.getLock().getId()) )) {
            return true;
        }
        return false;
    }

    public String toString() {
        return (e1Thr != null ? e1Thr.toString() : "NULL") + (e1Var != null ? e1Var.toString() : "NULL") + (e1Write ? "W" : "R") + currentGrain.toString() + aftSet.toString() + aftSetNoE1.toString() + candidate;
    }
}

class Grain {
    // public HashSet<Thread> threads;
    // public HashSet<Variable> completeVars;
    // public HashSet<Variable> incompleteWtVars;
    // public HashSet<Variable> incompleteRdVars;
    // public HashSet<Lock> completeLocks;
    // public HashSet<Lock> incompleteAcqs;
    // public HashSet<Lock> incompleteRels; 
    public BitSet threadsBitSet;
    public BitSet completeVarBitSet;
    public BitSet incompleteWtVarsBitSet;
    public BitSet incompleteRdVarsBitSet;
    public BitSet completeLocksBitSet;
    public BitSet incompleteAcqsBitSet;
    public BitSet incompleteRelsBitSet;
   
    public Grain() {
        // threads = new HashSet<>();
        // completeVars = new HashSet<>();
        // incompleteWtVars = new HashSet<>();
        // incompleteRdVars = new HashSet<>();
        // completeLocks = new HashSet<>();
        // incompleteAcqs = new HashSet<>();
        // incompleteRels = new HashSet<>();
        threadsBitSet = new BitSet(GrainRaceState.numOfThreads);
        completeVarBitSet = new BitSet(GrainRaceState.numOfVars);
        incompleteWtVarsBitSet = new BitSet(GrainRaceState.numOfVars);
        incompleteRdVarsBitSet = new BitSet(GrainRaceState.numOfVars);
        completeLocksBitSet = new BitSet(GrainRaceState.numOfLocks);
        incompleteAcqsBitSet = new BitSet(GrainRaceState.numOfLocks);
        incompleteRelsBitSet = new BitSet(GrainRaceState.numOfLocks); 
    }

    public Grain(Grain other) {
        // threads = new HashSet<>(other.threads);
        // completeVars = new HashSet<>(other.completeVars);
        // incompleteWtVars = new HashSet<>(other.incompleteWtVars);
        // incompleteRdVars = new HashSet<>(other.incompleteRdVars);
        // completeLocks = new HashSet<>(other.completeLocks);
        // incompleteAcqs = new HashSet<>(other.incompleteAcqs);
        // incompleteRels = new HashSet<>(other.incompleteRels);
        threadsBitSet = (BitSet)other.threadsBitSet.clone();
        completeVarBitSet = (BitSet)other.completeVarBitSet.clone();
        incompleteWtVarsBitSet = (BitSet)other.incompleteWtVarsBitSet.clone();
        incompleteRdVarsBitSet = (BitSet)other.incompleteRdVarsBitSet.clone();
        completeLocksBitSet = (BitSet)other.completeLocksBitSet.clone();
        incompleteAcqsBitSet = (BitSet)other.incompleteAcqsBitSet.clone();
        incompleteRelsBitSet = (BitSet)other.incompleteRelsBitSet.clone();
    }

    public boolean isDependentWith(Grain other) {
        if(threadsBitSet.intersects(other.threadsBitSet)) {
            return true;
        }
        if(incompleteRdVarsBitSet.intersects(other.incompleteWtVarsBitSet) || incompleteWtVarsBitSet.intersects(other.incompleteWtVarsBitSet) || completeVarBitSet.intersects(other.incompleteWtVarsBitSet)) {
            return true;
        }
        if(incompleteWtVarsBitSet.intersects(other.incompleteRdVarsBitSet) || completeVarBitSet.intersects(other.incompleteRdVarsBitSet)) {
            return true;
        }
        if(incompleteWtVarsBitSet.intersects(other.completeVarBitSet) || incompleteRdVarsBitSet.intersects(other.completeVarBitSet)) {
            return true;
        }
        if(incompleteAcqsBitSet.intersects(other.incompleteAcqsBitSet) || incompleteRelsBitSet.intersects(other.incompleteRelsBitSet) || completeLocksBitSet.intersects(other.completeLocksBitSet)) {
            return true;
        }
        if(incompleteAcqsBitSet.intersects(other.incompleteRelsBitSet) || incompleteRelsBitSet.intersects(other.incompleteRelsBitSet) || completeLocksBitSet.intersects(other.incompleteRelsBitSet)) {
            return true;
        }
        if(incompleteAcqsBitSet.intersects(other.completeLocksBitSet) || incompleteRelsBitSet.intersects(other.completeLocksBitSet)) {
            return true;
        }
        return false;
    }

    public void updateGrain(Grain other) {
        // threads.addAll(other.threads);
        threadsBitSet.or(other.threadsBitSet);
        // completeVars.addAll(other.completeVars);
        completeVarBitSet.or(other.completeVarBitSet);
        // incompleteWtVars.addAll(other.incompleteWtVars);
        incompleteWtVarsBitSet.or(other.incompleteWtVarsBitSet);
        // incompleteRdVars.addAll(other.incompleteRdVars);
        incompleteRdVarsBitSet.or(other.incompleteRdVarsBitSet);
        // completeVars.removeAll(incompleteWtVars);
        completeVarBitSet.andNot(incompleteWtVarsBitSet);
        // completeVars.removeAll(incompleteRdVars);
        completeVarBitSet.andNot(incompleteRdVarsBitSet);
        // completeLocks.addAll(other.completeLocks);
        completeLocksBitSet.or(other.completeLocksBitSet);
        // incompleteAcqs.addAll(other.incompleteAcqs); 
        incompleteAcqsBitSet.or(other.incompleteAcqsBitSet);
        // incompleteRels.addAll(other.incompleteRels);
        incompleteRelsBitSet.or(other.incompleteRelsBitSet);
        // completeLocks.removeAll(incompleteAcqs);
        completeLocksBitSet.andNot(incompleteAcqsBitSet);
        // completeLocks.removeAll(incompleteRels);
        completeLocksBitSet.andNot(incompleteRelsBitSet);
    }

    public void updateGrain(GrainRaceEvent e, HashMap<Variable, HashSet<Long>> lastReads) {
        // threads.add(e.getThread());
        threadsBitSet.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            // threads.add(e.getTarget());
            threadsBitSet.set(e.getTarget().getId());
        }

        if(e.getType().isRead()) {
            if(!incompleteWtVarsBitSet.get(e.getVariable().getId())) {
                // incompleteRdVars.add(e.getVariable());
                incompleteRdVarsBitSet.set(e.getVariable().getId());
            }
            else if(lastReads.get(e.getVariable()).contains(e.eventCount)) {
                // incompleteWtVars.remove(e.getVariable());
                incompleteWtVarsBitSet.clear(e.getVariable().getId());
                // completeVars.add(e.getVariable());
                completeVarBitSet.set(e.getVariable().getId());
            }
        }
        if(e.getType().isWrite()) {
            if(!lastReads.get(e.getVariable()).contains(e.eventCount)) {
                // incompleteWtVars.add(e.getVariable());
                incompleteWtVarsBitSet.set(e.getVariable().getId());
            }
            else {
                // completeVars.add(e.getVariable());
                completeVarBitSet.set(e.getVariable().getId());
            }
        }
        if(e.getType().isAcquire()) {
            // incompleteAcqs.add(e.getLock());
            incompleteAcqsBitSet.set(e.getLock().getId());
        }
        if(e.getType().isRelease()) {
            if(incompleteAcqsBitSet.get(e.getLock().getId())) {
                // incompleteAcqs.remove(e.getLock());
                incompleteAcqsBitSet.clear(e.getLock().getId());
                if(!incompleteRelsBitSet.get(e.getLock().getId())) {
                    // completeLocks.add(e.getLock());
                    completeLocksBitSet.set(e.getLock().getId());
                }
            }
            else {
                // incompleteRels.add(e.getLock());
                incompleteRelsBitSet.set(e.getLock().getId());
            }
        }
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(Grain other) {
        BitSet occured = (BitSet)other.completeVarBitSet;
        occured.or(other.incompleteRdVarsBitSet);
        occured.or(other.incompleteWtVarsBitSet);
        return  subsume(this.threadsBitSet, other.threadsBitSet) &&
                subsume(this.completeVarBitSet, occured) &&
                subsume(this.incompleteRdVarsBitSet, other.incompleteRdVarsBitSet) &&
                subsume(this.incompleteWtVarsBitSet, other.incompleteWtVarsBitSet) &&
                subsume(this.completeLocksBitSet, other.completeLocksBitSet) &&
                subsume(this.incompleteAcqsBitSet, other.incompleteAcqsBitSet) &&
                subsume(this.incompleteRelsBitSet, other.incompleteRelsBitSet);
    }

    // private <T> String setToString(HashSet<T> set) {
    //     return set.stream().map(x -> x.toString()).sorted().toList().toString();
    // }

    public String toString() {
        // return  setToString(threads) + setToString(completeVars) + setToString(incompleteWtVars) + setToString(incompleteRdVars) + setToString(completeLocks) + setToString(incompleteAcqs) + setToString(incompleteRels); 
        return threadsBitSet.toString() + completeVarBitSet.toString() + incompleteWtVarsBitSet.toString() + incompleteRdVarsBitSet.toString() + completeLocksBitSet.toString() + incompleteAcqsBitSet.toString() + incompleteRelsBitSet.toString();
    } 
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}