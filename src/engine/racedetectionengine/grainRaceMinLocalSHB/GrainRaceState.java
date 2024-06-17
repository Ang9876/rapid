package engine.racedetectionengine.grainRaceMinLocalSHB;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import engine.racedetectionengine.State;
import event.Thread;
import event.Variable;

public class GrainRaceState extends State {
    
    public static int numOfThreads;
    public static int numOfVars;
    public static int numOfLocks;
    HashSet<Thread> threadSet;
    TreeMap<NondetState, Candidate> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;
    public HashSet<Long> racyEvents = new HashSet<>();

    GrainRaceEvent ev;
    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new Candidate());    
        this.lastReads = lastReads;
    }

    public boolean update(GrainRaceEvent e) {
        ev = e;
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state.hashString);
        // } 
        boolean findRace = false;
        TreeMap<NondetState, Candidate> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            Candidate candidates = nondetStates.get(state);
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            boolean isFirstGrain = state.aftSet.threadsBitSet.isEmpty();
            boolean isCandidate =  e.getType().isAccessType() && !state.isDependentWith(e, true) && isConflict(state, e);
            // System.out.println(e.toStandardFormat());
            // System.out.println(isCandidate);
            if(state.aftSetNoE1.threadsBitSet.nextSetBit(0) >= numOfThreads) {
                continue;
            }
            boolean doEdgeContraction = !isFirstGrain && !isCandidate && 
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e, false)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e, false)));
            boolean minimal = !state.currentGrain.threadsBitSet.isEmpty() && state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();

            if(!state.currentGrain.isDependentWith(state.aftSetNoE1) && !candidates.e2Sets.isEmpty()){
                // System.out.println("Race" + candidates.get(s).e2Sets);
                racyEvents.addAll(candidates.e2Sets);
                findRace = true;
            } 

            // Stop current grain here  
            if(!doEdgeContraction && !(isFirstGrain && state.e1Thread == -1)) {
                NondetState newState = new NondetState(state, false);
                if(state.aftSet.threadsBitSet.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                    newState.aftSet.updateGrain(state.currentGrain);
                    if(!isFirstGrain) {
                        newState.aftSetNoE1.updateGrain(state.currentGrain);
                    }
                }
                newState.lastDependent = !isFirstGrain && state.currentGrain.isDependentWith(state.aftSet) && !minimal; 
                if(e.getType().isWrite() && e.getVariable().getId() == newState.e1Var) {
                    newState.e1LastWrite = false;
                }
                newState.currentGrain.updateGrain(e, lastReads);
                boolean addToCand = false;
                if(isCandidate && !state.firstFrontier.isSHBSandwiched(e)){
                    addToCand = true;
                }
                if(newState.firstFrontier.isDependentWith(e, state.e1Thread, state.e1Var, state.e1LastWrite)) {
                    newState.currentFrontier.update(e);
                }
                else if(e.getType().isWrite()) {
                    newState.firstFrontier.removeWt(e.getVariable().getId());
                    newState.currentFrontier.removeWt(e.getVariable().getId());
                }
                
                newState.hashString = newState.toString();
                // System.out.println("Addstop " + newState);
                if(addToStates(newStates, newState)) {
                    if(!newStates.containsKey(newState)) {
                        newStates.put(newState, new Candidate());
                    }
                    Candidate cands = newStates.get(newState);
                    // cands.e2Sets.addAll(candidates.e2Sets);
                    if(addToCand) {
                        cands.e2Sets.add(e.eventCount);
                    }
                }
            }
            
            // update current grain
            if(doEdgeContraction || !minimal) {
                boolean addToCand = false;
                if(isFirstGrain && state.e1Thread != -1) {
                    if(state.firstFrontier.isDependentWith(e, state.e1Thread, state.e1Var, state.e1LastWrite)) {
                        state.firstFrontier.update(e);
                   }
                   else if(e.getType().isWrite()) {
                       state.firstFrontier.removeWt(e.getVariable().getId());
                       state.currentFrontier.removeWt(e.getVariable().getId());
                   }
                }
                if(isCandidate && !isFirstGrain && !state.firstFrontier.isSHBSandwiched(e) && !state.currentFrontier.isSHBSandwiched(e)) {
                    addToCand = true;
                }
                if(!isFirstGrain) {
                    if((state.firstFrontier.isDependentWith(e, state.e1Thread, state.e1Var, state.e1LastWrite) || state.currentFrontier.isSHBSandwiched(e))) {
                        state.currentFrontier.update(e);
                    }
                    else if(e.getType().isWrite()) {
                        state.firstFrontier.removeWt(e.getVariable().getId());
                        state.currentFrontier.removeWt(e.getVariable().getId());
                    }
                }
                if(e.getType().isWrite() && e.getVariable().getId() == state.e1Var) {
                    state.e1LastWrite = false;
                }
                state.currentGrain.updateGrain(e, lastReads);
                
                if((!state.lastDependent || !state.currentGrain.isDependentWith(state.aftSetNoE1))) {
                    NondetState newState = new NondetState(state, true);
                    // System.out.println(newState.hashString);
                    // System.out.println("AddCon " + newState);
                    // System.out.println(addToStates(newStates, newState, newCands));
                    if(addToStates(newStates, newState)) {
                        if(!newStates.containsKey(newState)) {
                            newStates.put(newState, new Candidate());
                        }
                        Candidate cands = newStates.get(newState);
                        cands.e2Sets.addAll(candidates.e2Sets);
                        if(addToCand) {
                            cands.e2Sets.add(e.eventCount);
                        }
                    }

                    
                    if(state.e1Thread == -1 && e.getType().isAccessType()) {
                        NondetState newState2 = new NondetState(newState, true);
                        newState2.e1Thread = e.getThread().getId();
                        newState2.e1Var = e.getVariable().getId();
                        newState2.e1Write = e.getType().isWrite();
                        newState2.e1LastWrite = newState2.e1Write;
                        newState2.hashString = newState2.toString();
                        // System.out.println("AddCon " + newState2);
                        if(addToStates(newStates, newState2)) {
                            if(!newStates.containsKey(newState2)) {
                                newStates.put(newState2, new Candidate());
                            }
                        }
                    }
                }
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads);
        newState.hashString = newState.toString();
        if(addToStates(newStates, newState)) {
            if(!newStates.containsKey(newState)) {
                newStates.put(newState, new Candidate());
            }
        }
        
        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
            newState2.e1LastWrite = newState2.e1Write;
            newState2.hashString = newState2.toString();
            if(addToStates(newStates, newState2)) {
                if(!newStates.containsKey(newState2)) {
                    newStates.put(newState2, new Candidate());
                }
            }
        }

        nondetStates = newStates;
        // System.out.println(e.toStandardFormat());
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state);
        //     System.out.println(nondetStates.get(state));
        // }
        return findRace;
    }

    private boolean addToStates(TreeMap<NondetState, Candidate> states, NondetState newState) {
        TreeSet<NondetState> subsumedStates = new TreeSet<>(new StateComparator());
        boolean add = true;
        for(NondetState state: states.keySet()) {
            if(add && state.subsume(newState)) {
                add = false;
            }
            if(newState.subsume(state)) {
                subsumedStates.add(state);
            }
        }
        for(NondetState state: subsumedStates) {
            states.remove(state);
        }
        return add;
    }

    private boolean isConflict(NondetState state, GrainRaceEvent e) {
        return state.e1Var == e.getVariable().getId() && state.e1Thread != e.getThread().getId() && (state.e1Write || e.getType().isWrite());
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            Candidate candidates = nondetStates.get(state); 
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            // System.out.println(!state.currentGrain.isDependentWith(state.aftSetNoE1)); 
            if(!state.currentGrain.isDependentWith(state.aftSetNoE1) && !candidates.e2Sets.isEmpty()) {
                racyEvents.addAll(candidates.e2Sets);
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
    
    public Grain currentGrain;
    public Grain aftSet;
    public Grain aftSetNoE1;
    public boolean lastDependent;
    public int e1Thread;
    public int e1Var;
    public boolean e1Write;
    public boolean e1LastWrite;
    public Frontier firstFrontier;
    public Frontier currentFrontier;
    public String hashString;

    public NondetState() {
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        lastDependent = false;
        e1Thread = -1;
        e1Var = -1;
        e1Write = false;
        e1LastWrite = false;
        firstFrontier = new Frontier();
        currentFrontier = new Frontier();
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
        lastDependent = copy ? state.lastDependent : false;
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        e1Thread = state.e1Thread;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        e1LastWrite = state.e1LastWrite;
        firstFrontier = new Frontier(state.firstFrontier);
        currentFrontier = copy ? new Frontier(state.currentFrontier) : new Frontier();
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

    public boolean subsume(NondetState other) {
        return this.aftSetNoE1.subsume(other.aftSetNoE1) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.e1Thread == other.e1Thread && this.e1Var == other.e1Var && (this.e1Write || !other.e1Write) && (!this.e1LastWrite || other.e1LastWrite) && this.firstFrontier.subsume(other.firstFrontier) && this.currentFrontier.subsume(other.currentFrontier);
    }

    public String toString() {
        return "CG" + currentGrain.toString() + "AF" + aftSet.toString() + "AFN" + aftSetNoE1.toString() + lastDependent + e1Thread + e1Var + e1Write + e1LastWrite + "FF" + firstFrontier.toString() + "CF" + currentFrontier.toString();
    }
}

class Candidate {
    public HashSet<Long> e2Sets;

    public Candidate() {
        e2Sets = new HashSet<>();
    }

    public Candidate(Candidate other) {
        e2Sets = new HashSet<>(other.e2Sets);
    }

    public String toString() {
        return e2Sets.toString();
    }
}

class Grain {
    public BitSet threadsBitSet;
    public BitSet completeVarBitSet;
    public BitSet incompleteWtVarsBitSet;
    public BitSet incompleteRdVarsBitSet;
    public BitSet completeLocksBitSet;
    public BitSet incompleteAcqsBitSet;
    public BitSet incompleteRelsBitSet;
   
    public Grain() {
        threadsBitSet = new BitSet(GrainRaceState.numOfThreads);
        completeVarBitSet = new BitSet(GrainRaceState.numOfVars);
        incompleteWtVarsBitSet = new BitSet(GrainRaceState.numOfVars);
        incompleteRdVarsBitSet = new BitSet(GrainRaceState.numOfVars);
        completeLocksBitSet = new BitSet(GrainRaceState.numOfLocks);
        incompleteAcqsBitSet = new BitSet(GrainRaceState.numOfLocks);
        incompleteRelsBitSet = new BitSet(GrainRaceState.numOfLocks); 
    }

    public Grain(Grain other) {
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
        if(incompleteAcqsBitSet.intersects(other.incompleteAcqsBitSet) || incompleteRelsBitSet.intersects(other.incompleteAcqsBitSet) || completeLocksBitSet.intersects(other.incompleteAcqsBitSet)) {
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
        threadsBitSet.or(other.threadsBitSet);
        completeVarBitSet.or(other.completeVarBitSet);
        incompleteWtVarsBitSet.or(other.incompleteWtVarsBitSet);
        incompleteRdVarsBitSet.or(other.incompleteRdVarsBitSet);
        completeVarBitSet.andNot(incompleteWtVarsBitSet);
        completeVarBitSet.andNot(incompleteRdVarsBitSet);
        completeLocksBitSet.or(other.completeLocksBitSet);
        incompleteAcqsBitSet.or(other.incompleteAcqsBitSet);
        incompleteRelsBitSet.or(other.incompleteRelsBitSet);
        completeLocksBitSet.andNot(incompleteAcqsBitSet);
        completeLocksBitSet.andNot(incompleteRelsBitSet);
    }

    public void updateGrain(GrainRaceEvent e, HashMap<Variable, HashSet<Long>> lastReads) {
        // threads.add(e.getThread());
        threadsBitSet.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            threadsBitSet.set(e.getTarget().getId());
        }

        if(e.getType().isRead()) {
            if(!incompleteWtVarsBitSet.get(e.getVariable().getId())) {
                incompleteRdVarsBitSet.set(e.getVariable().getId());
            }
            else if(lastReads.get(e.getVariable()).contains(e.eventCount)) {
                incompleteWtVarsBitSet.clear(e.getVariable().getId());
                completeVarBitSet.set(e.getVariable().getId());
            }
        }
        if(e.getType().isWrite()) {
            if(!lastReads.get(e.getVariable()).contains(e.eventCount)) {
                incompleteWtVarsBitSet.set(e.getVariable().getId());
            }
            else {
                completeVarBitSet.set(e.getVariable().getId());
            }
        }
        if(e.getType().isAcquire()) {
            incompleteAcqsBitSet.set(e.getLock().getId());
        }
        if(e.getType().isRelease()) {
            if(incompleteAcqsBitSet.get(e.getLock().getId())) {
                incompleteAcqsBitSet.clear(e.getLock().getId());
                if(!incompleteRelsBitSet.get(e.getLock().getId())) {
                    completeLocksBitSet.set(e.getLock().getId());
                }
            }
            else {
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
        BitSet occured = (BitSet)other.completeVarBitSet.clone();
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

    public String toString() {
        return threadsBitSet.toString() + completeVarBitSet.toString() + incompleteWtVarsBitSet.toString() + incompleteRdVarsBitSet.toString() + completeLocksBitSet.toString() + incompleteAcqsBitSet.toString() + incompleteRelsBitSet.toString();
    } 
}

class Frontier {
    public BitSet threads;
    public BitSet locks;
    public BitSet lastWtVars;
    
    public Frontier() {
        threads = new BitSet(GrainRaceState.numOfThreads);
        lastWtVars = new BitSet(GrainRaceState.numOfVars); 
        locks = new BitSet(GrainRaceState.numOfLocks);
    }

    public Frontier(Frontier other) {
        threads = (BitSet)other.threads.clone();
        lastWtVars = (BitSet)other.lastWtVars.clone();
        locks = (BitSet)other.locks.clone();
    }


    public boolean isPODependentWithE1(GrainRaceEvent e, int e1Thread) {
        return e1Thread == e.getThread().getId() || 
                (e.getType().isExtremeType() && e1Thread == e.getTarget().getId());
    }

    public boolean isRFDependentWithE1(GrainRaceEvent e, int e1Var, boolean e1LastWrite) {
        return e1LastWrite && e.getType().isRead() && e.getVariable().getId() == e1Var;
    }

    public boolean isRFDependentWith(GrainRaceEvent e) {
        return e.getType().isRead() && lastWtVars.get(e.getVariable().getId());
    }

    public boolean isPOLckDependentWith(GrainRaceEvent e) {
        if(threads.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && threads.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isLockType() && locks.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public boolean isDependentWith(GrainRaceEvent e, int e1Thread, int e1Var, boolean e1LastWrite) {
        return isPODependentWithE1(e, e1Thread) || isRFDependentWithE1(e, e1Var, e1LastWrite) || isRFDependentWith(e) || isPOLckDependentWith(e);
    }

    public boolean isSHBSandwiched(GrainRaceEvent e) {
        return isRFDependentWith(e) || isPOLckDependentWith(e);
    }

    public void update(GrainRaceEvent e) {
        threads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            threads.set(e.getTarget().getId());
        }
        if(e.getType().isWrite()) {
            lastWtVars.set(e.getVariable().getId());
        }
        if(e.getType().isLockType()) {
            locks.set(e.getLock().getId());
        }
    }

    public void removeWt(int v) {
        lastWtVars.clear(v);
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(Frontier other) {
        return !this.threads.isEmpty() && !other.threads.isEmpty() && subsume(this.threads, other.threads) && subsume(this.lastWtVars, other.lastWtVars) && subsume(this.locks, other.locks);
    }

    public String toString() {
        return threads.toString() + lastWtVars.toString() + locks.toString();
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}