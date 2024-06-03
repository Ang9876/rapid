package engine.racedetectionengine.grainRaceMin;

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
            boolean doEdgeContraction = !isFirstGrain && !isCandidate &&
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
                    if(isCandidate) {
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
                if(isFirstGrain) {
                    assert(candidates.size() <= 1);
                    if(!candidates.isEmpty()) {
                        candidates.clear();
                    }
                    if(e.getType().isAccessType()) {
                        candidates.put(e.getName(), new Candidate());
                    }
                }
                state.currentGrain.updateGrain(e, lastReads);
                if((!state.lastDependent || !state.currentGrain.isDependentWith(state.aftSetNoE1))) {
                    NondetState newState = new NondetState(state, true);
                    // System.out.println(newState.hashString);
                    HashSet<String> newCands = new HashSet<>(candidates.keySet());
                    // System.out.println("AddCon " + newState);
                    if(addToStates(newStates, newState, newCands)) {
                        if(!newStates.containsKey(newState)) {
                            newStates.put(newState, new HashMap<>());
                        }
                        HashMap<String, Candidate> cands = newStates.get(newState);
                        for(String candName: newCands) {
                            if(cands.containsKey(candName)) {
                                cands.get(candName).e2Sets.addAll(candidates.get(candName).e2Sets);
                            }
                            else {
                                cands.put(candName, new Candidate(candidates.get(candName)));
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
            newStates.get(newState).put(e.getName(), new Candidate());
        }
        nondetStates = newStates;
        // System.out.println(e.toStandardFormat());
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state);
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
        states.entrySet().removeIf(state -> state.getValue().isEmpty());
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
            // System.out.println(state.hashString);
            HashMap<String, Candidate> candidates = nondetStates.get(state); 
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
    public String hashString;

    public NondetState() {
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        lastDependent = false;
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
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

    public boolean subsume(NondetState other) {
        return this.aftSetNoE1.subsume(other.aftSetNoE1) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet);
    }

    public String toString() {
        return currentGrain.toString() + aftSet.toString() + aftSetNoE1.toString() + lastDependent;
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