package engine.racedetectionengine.grainRaceMinLocalMaz;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import engine.racedetectionengine.State;
import event.Thread;
import event.Variable;

public class GrainRaceState extends State {
    
    public static int numOfThreads;
    public static int numOfVars;
    public static int numOfLocks;
    HashSet<Thread> threadSet;
    TreeMap<NondetState, HashMap<String, Candidate>> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;
    public HashSet<Long> racyEvents = new HashSet<>();

    GrainRaceEvent ev;
    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new HashMap<>());    
        this.lastReads = lastReads;
    }

    public boolean update(GrainRaceEvent e) {
        ev = e;
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state.hashString);
        // } 
        boolean findRace = false;
        TreeMap<NondetState, HashMap<String, Candidate>> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            HashMap<String, Candidate> candidates = nondetStates.get(state);
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            boolean isFirstGrain = state.aftSet.threadsBitSet.isEmpty();
            boolean isCandidate =  e.getType().isAccessType() && !state.isDependentWith(e, true) && isConflict(candidates.keySet(), e);
            // System.out.println(e.toStandardFormat());
            // System.out.println(isCandidate);
            if(state.aftSetNoE1.threadsBitSet.nextSetBit(0) >= numOfThreads) {
                continue;
            }
            boolean doEdgeContraction = !isFirstGrain &&
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e, false)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e, false)));
            boolean minimal = !state.currentGrain.threadsBitSet.isEmpty() && state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();

            if(!state.currentGrain.isDependentWith(state.aftSetNoE1)){
                // if current grain contains e2 and it is independent of all grains other than the grain containing e1
                // for(long c: nondetStates.get(state)) {
                //     if(!racyEvents.contains(c)) {
                //         System.out.println(c);
                //     }
                // }
                for(String s: candidates.keySet()) {
                    if(candidates.get(s).candidate) {
                        // System.out.println("Race" + candidates.get(s).e2Sets);
                        racyEvents.addAll(candidates.get(s).e2Sets);
                        findRace = true;
                    }
                } 
            }

            // Stop current grain here  
            if(!doEdgeContraction && !(isFirstGrain && candidates.isEmpty())) {
                NondetState newState = new NondetState(state, false);
                if(state.aftSet.threadsBitSet.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                    newState.aftSet.updateGrain(state.currentGrain);
                    if(!isFirstGrain) {
                        newState.aftSetNoE1.updateGrain(state.currentGrain);
                    }
                }
                newState.lastDependent = !isFirstGrain && state.currentGrain.isDependentWith(state.aftSet) && !minimal; 
                newState.currentGrain.updateGrain(e, lastReads);
                if(newState.firstFrontier.isDependentWith(e)) {
                    newState.currentFrontier.update(e);
                }
                
                newState.hashString = newState.toString();
                // System.out.println("Addstop " + newState);
                HashSet<String> newCands = new HashSet<>(candidates.keySet());
                if(addToStates(newStates, newState, newCands)) {
                    if(!newStates.containsKey(newState)) {
                        newStates.put(newState, new HashMap<>());
                    }
                    HashMap<String, Candidate> cands = newStates.get(newState);
                    for(String candName: newCands) {
                        if(!cands.containsKey(candName)) {
                            cands.put(candName, new Candidate());
                        }
                    }
                    // System.out.println(isCandidate);
                    if(isCandidate && !state.firstFrontierNoE1.isDependentWith(e)) {
                        for(String candName: cands.keySet()) {
                            if(isNameConflict(candName, e)) {
                                cands.get(candName).candidate = true;
                                cands.get(candName).e2Sets.add(e.eventCount);
                            }
                        }
                    }
                    // System.out.println(newStates.get(newState));
                }
            }
            
            // update current grain
            if(doEdgeContraction || !minimal) {
                boolean addToCand = false;
                
                if(isFirstGrain && !state.firstFrontier.threads.isEmpty() && state.firstFrontier.isDependentWith(e)) {
                     state.firstFrontier.update(e);
                     state.firstFrontierNoE1.update(e);
                }
                if(isCandidate && !isFirstGrain && !state.firstFrontierNoE1.isDependentWith(e) && !state.currentFrontier.isDependentWith(e)) {
                    addToCand = true;
                }
                if(!isFirstGrain && (state.firstFrontier.isDependentWith(e) || state.currentFrontier.isDependentWith(e))) {
                    state.currentFrontier.update(e);
                }
                state.currentGrain.updateGrain(e, lastReads);
                
                if((!state.lastDependent || !state.currentGrain.isDependentWith(state.aftSetNoE1))) {
                    NondetState newState = new NondetState(state, true);
                    // System.out.println(newState.hashString);
                    HashSet<String> newCands = new HashSet<>(candidates.keySet());
                    // System.out.println("AddCon " + newState);
                    // System.out.println(addToStates(newStates, newState, newCands));
                    if(addToStates(newStates, newState, newCands)) {
                        if(!newStates.containsKey(newState)) {
                            // System.out.println("put " + newState);
                            newStates.put(newState, new HashMap<>());
                        }
                        // System.out.println(candidates);
                        // System.out.println(newCands);
                        HashMap<String, Candidate> cands = newStates.get(newState);
                        for(String candName: newCands) {
                            if(cands.containsKey(candName)) {
                                cands.get(candName).e2Sets.addAll(candidates.get(candName).e2Sets);
                            }
                            else {
                                cands.put(candName, new Candidate(candidates.get(candName)));
                            }
                        }
                        if(addToCand) {
                            for(String candName: cands.keySet()) {
                                if(isNameConflict(candName, e)) {
                                    cands.get(candName).candidate = true;
                                    cands.get(candName).e2Sets.add(e.eventCount);
                                }
                            }
                        }
                    }

                    
                    if(state.firstFrontier.threads.isEmpty() && e.getType().isAccessType()) {
                        NondetState newState2 = new NondetState(newState, true);
                        newState2.firstFrontier.update(e);
                        newState2.hashString = newState2.toString();
                        // System.out.println("AddCon " + newState2);
                        assert(candidates.size() == 0);
                        HashSet<String> newCands2 = new HashSet<>();
                        newCands2.add(e.getName());
                        if(addToStates(newStates, newState2, newCands2)) {
                            if(!newStates.containsKey(newState2)) {
                                newStates.put(newState2, new HashMap<>());
                            }

                            HashMap<String, Candidate> cands = newStates.get(newState2);
                            if(!cands.containsKey(e.getName())) {
                                cands.put(e.getName(), new Candidate());
                            }
                        }
                    }
                }
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads);
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new HashMap<>());
        }
        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true);
            newState2.firstFrontier.update(e);
            newState2.hashString = newState2.toString();

            HashSet<String> newCands = new HashSet<>();
            newCands.add(e.getName());
            if(addToStates(newStates, newState2, newCands)) {
                if(!newStates.containsKey(newState2)) {
                    newStates.put(newState2, new HashMap<>());
                }

                HashMap<String, Candidate> cands = newStates.get(newState2);
                if(!cands.containsKey(e.getName())) {
                    cands.put(e.getName(), new Candidate());
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

    private boolean addToStates(TreeMap<NondetState, HashMap<String, Candidate>> states, NondetState newState, Set<String> cands) {
        for(NondetState state: states.keySet()) {
            HashMap<String, Candidate> candidates = states.get(state);
            if(state.subsume(newState)) {
                cands.removeAll(candidates.keySet());
                if(cands.isEmpty()) {
                    return false;
                }
            }
            if(newState.subsume(state)) {
                for(String s: cands) {
                    candidates.remove(s);
                }
            }
        }
        states.entrySet().removeIf(state -> !state.getKey().firstFrontier.threads.isEmpty() && state.getValue().isEmpty());
        return true;
    }

    private boolean isConflict(Set<String> states, GrainRaceEvent e) {
        for(String name: states) {
            if(isNameConflict(name, e)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNameConflict(String name, GrainRaceEvent e) {
        String[] res = name.split(";");
        String thread = res[2];
        String var = res[0];
        boolean isWrite = res[1].equals("W");
        if(e.getVariable().getName().equals(var) && !e.getThread().getName().equals(thread) && (e.getType().isWrite() || isWrite)) {
            return true;
        }
        return false;
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            HashMap<String, Candidate> candidates = nondetStates.get(state); 
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            if(!state.currentGrain.isDependentWith(state.aftSetNoE1)) {
                // for(long c: nondetStates.get(state)) {
                //     if(!racyEvents.contains(c)) {
                //         System.out.println(c);
                //     }
                // }
                for(String s: candidates.keySet()) {
                    if(candidates.get(s).candidate) {
                        racyEvents.addAll(candidates.get(s).e2Sets);
                    }
                }
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

    public Frontier firstFrontier;
    public Frontier firstFrontierNoE1;
    public Frontier currentFrontier;

    public String hashString;

    public NondetState() {
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        lastDependent = false;
        firstFrontier = new Frontier();
        firstFrontierNoE1 = new Frontier();
        currentFrontier = new Frontier();
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
        lastDependent = copy ? state.lastDependent : false;
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        firstFrontier = new Frontier(state.firstFrontier);
        firstFrontierNoE1 = new Frontier(state.firstFrontierNoE1);
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
        return this.aftSetNoE1.subsume(other.aftSetNoE1) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.firstFrontier.subsume(other.firstFrontier) && this.firstFrontierNoE1.subsume(other.firstFrontierNoE1) && this.currentFrontier.subsume(other.currentFrontier);
    }

    public String toString() {
        return "CG" + currentGrain.toString() + "AF" + aftSet.toString() + "AFN" + aftSetNoE1.toString() + lastDependent + "FF" + firstFrontier.toString() + "FFN" + firstFrontierNoE1.toString() + "CF" + currentFrontier.toString();
    }
}

class Candidate {
    public boolean candidate;
    public HashSet<Long> e2Sets;

    public Candidate() {
        candidate = false;
        e2Sets = new HashSet<>();
    }

    public Candidate(Candidate other) {
        candidate = false;
        e2Sets = new HashSet<>(other.e2Sets);
    }

    public String toString() {
        return (candidate ? "T" : "F") + e2Sets;
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
    public BitSet rdVars;
    public BitSet wtVars;
    public BitSet locks;

    public Frontier() {
        threads = new BitSet(GrainRaceState.numOfThreads);
        rdVars = new BitSet(GrainRaceState.numOfVars);
        wtVars = new BitSet(GrainRaceState.numOfVars); 
        locks = new BitSet(GrainRaceState.numOfLocks);
    }

    public Frontier(Frontier other) {
        threads = (BitSet)other.threads.clone();
        rdVars = (BitSet)other.rdVars.clone();
        wtVars = (BitSet)other.wtVars.clone();
        locks = (BitSet)other.locks.clone();
    }

    public boolean isDependentWith(GrainRaceEvent e) {
        if(threads.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && threads.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isRead() && wtVars.get(e.getVariable().getId())) {
            return true;
        }
        if(e.getType().isWrite() && (wtVars.get(e.getVariable().getId()) || rdVars.get(e.getVariable().getId()))) {
            return true;
        }
        if(e.getType().isLockType() && locks.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public void update(GrainRaceEvent e) {
        threads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            threads.set(e.getTarget().getId());
        }
        if(e.getType().isRead()) {
            rdVars.set(e.getVariable().getId());
        }
        if(e.getType().isWrite()) {
            wtVars.set(e.getVariable().getId());
        }
        if(e.getType().isLockType()) {
            locks.set(e.getLock().getId());
        }
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(Frontier other) {
        return !this.threads.isEmpty() && !other.threads.isEmpty() && subsume(this.threads, other.threads) && subsume(this.wtVars, other.wtVars) && subsume(this.rdVars, other.rdVars) && subsume(this.locks, other.locks);
    }

    public String toString() {
        return threads.toString() + wtVars.toString() + rdVars.toString() + locks.toString();
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}