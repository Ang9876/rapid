package engine.racedetectionengine.grainRaceMinLocalSyncP;

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
import it.unimi.dsi.fastutil.Hash;
import util.Pair;
import util.Triplet;

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
            boolean isCandidate =  e.getType().isAccessType() && isConflict(state, e);
            // System.out.println(e.toStandardFormat());
            // System.out.println(isCandidate);
            if(state.aftSetNoE1.threadsBitSet.nextSetBit(0) >= numOfThreads) {
                continue;
            }
            boolean doEdgeContraction = !isFirstGrain &&
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e, false)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e, false)));
            boolean minimal = !state.currentGrain.threadsBitSet.isEmpty() && state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();

            if(state.reorder == 0 && !state.currentGrain.isDependentWith(state.aftSetNoE1) && !candidates.e2Sets.isEmpty()){
                racyEvents.addAll(candidates.e2Sets);
                findRace = true;
            } 
            if(state.reorder == 1 && !state.currentGrain.isDependentWith(state.aftSet) && !candidates.e2Sets.isEmpty()) {
                racyEvents.addAll(candidates.e2Sets);
                findRace = true;
            }

            boolean ctxSwitch = isFirstGrain && !state.currentGrain.threadsBitSet.isEmpty() && !state.currentGrain.threadsBitSet.get(e.getThread().getId());

            // Stop current grain here  
            if((!doEdgeContraction && !(isFirstGrain && state.enabledE1s.isEmpty()))) {
                NondetState newState = new NondetState(state, false);
                if(state.aftSet.threadsBitSet.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                    newState.aftSet.updateGrain(state.currentGrain);
                    if(!isFirstGrain) {
                        newState.aftSetNoE1.updateGrain(state.currentGrain);
                    }
                }
                newState.lastDependent = !isFirstGrain && state.currentGrain.isDependentWith(state.aftSet) && !minimal; 
                newState.currentGrain.updateGrain(e, lastReads);
                
                boolean depG12 = newState.isDependentWith(e, true);
                boolean depG21 = newState.isDependentWith(e, false);

                // G1G2
                if(!depG12) {
                    boolean mustIgnore = newState.firstFrontier.mustIgnore(e);
                    if(!mustIgnore) {
                        NondetState newState2 = new NondetState(newState, true);
                        newState2.currentFrontierInclude(e);
                        newState2.reorder = 0;
                        newState2.hashString = newState2.toString();
                        if(!newStates.containsKey(newState2)) {
                            newStates.put(newState2, new Candidate()); 
                        }
                    }

                    NondetState newState3 = new NondetState(newState, true);
                    boolean isThreadIgnore = newState3.firstFrontier.threadMissed(e);
                    newState3.currentFrontier.ignore(e);
                    newState3.reorder = 0;
                    newState3.hashString = newState3.toString();
                    if(!newStates.containsKey(newState3)) {
                        newStates.put(newState3, new Candidate()); 
                    } 
                    Candidate cands = newStates.get(newState3);
                    // cands.e2Sets.addAll(candidates.e2Sets);
                    if(isCandidate && !isThreadIgnore) {
                        cands.e2Sets.add(e.eventCount);
                    }
                }

                // G2G1
                if(!depG21) {
                    NondetState newState2 = new NondetState(newState, true);
                    newState2.currentFrontierInclude(e);
                    newState2.reorder = 1;
                    newState2.hashString = newState2.toString();
                    if(!newStates.containsKey(newState2)) {
                        newStates.put(newState2, new Candidate()); 
                    }
                    
                    NondetState newState3 = new NondetState(newState, true);
                    newState3.currentFrontier.ignore(e);
                    newState2.reorder = 1;
                    newState3.hashString = newState3.toString();
                    if(!newStates.containsKey(newState3)) {
                        newStates.put(newState3, new Candidate()); 
                    } 
                    Candidate cands = newStates.get(newState3);
                    // cands.e2Sets.addAll(candidates.e2Sets);
                    if(isCandidate) {
                        cands.e2Sets.add(e.eventCount);
                    } 
                }

                // Not track SyncP Prefix
                if(depG12 && depG21) {
                    newState.reorder = 2;
                    newState.hashString = newState.toString();
                    if(!newStates.containsKey(newState)) {
                        newStates.put(newState, new Candidate());
                    }
                }
            }
            
            // update current grain
            if((doEdgeContraction || !minimal) && ((!state.lastDependent || !state.currentGrain.isDependentWith(state.aftSetNoE1)))) {
                state.currentGrain.updateGrain(e, lastReads);
                NondetState newState = new NondetState(state, true);
                if(isFirstGrain) {
                    boolean mustIgnore = newState.firstFrontier.mustIgnore(e);
                    if(!mustIgnore) {
                        NondetState newState2 = new NondetState(newState, true);
                        newState2.firstFrontierInclude(e);
                        newState2.reorder = -1;
                        newState2.hashString = newState2.toString();
                        if(!newStates.containsKey(newState2)) {
                            newStates.put(newState2, new Candidate()); 
                        }
                    }

                    NondetState newState3 = new NondetState(newState, true);
                    boolean isThreadIgnore = newState3.firstFrontier.threadMissed(e);
                    newState3.firstFrontier.ignore(e);
                    newState3.reorder = -1;
                    if(e.getType().isAccessType() && !isThreadIgnore) {
                        newState3.enabledE1s.add(new Triplet<>(e.getThread().getId(), e.getVariable().getId(), e.getType().isWrite()));
                    }
                    newState3.hashString = newState3.toString();
                    if(!newStates.containsKey(newState3)) {
                        newStates.put(newState3, new Candidate()); 
                    } 
                }   
                else {
                    if(state.reorder == 0 && !newState.aftSetNoE1.isDependentWith(newState.currentGrain)) {
                        if(!newState.firstFrontier.mustIgnore(e) && !newState.currentFrontier.mustIgnore(e)) {
                            NondetState newState2 = new NondetState(newState, true);
                            newState2.currentFrontierInclude(e);
                            newState2.reorder = 0;
                            newState2.hashString = newState2.toString();
                            if(!newStates.containsKey(newState2)) {
                                newStates.put(newState2, new Candidate()); 
                            }
                        }
    
                        NondetState newState3 = new NondetState(newState, true);
                        boolean isThreadIgnore = newState3.firstFrontier.threadMissed(e) || newState3.currentFrontier.threadMissed(e);
                        newState3.currentFrontier.ignore(e);
                        newState3.reorder = 0;
                        newState3.hashString = newState3.toString();
                        if(!newStates.containsKey(newState3)) {
                            newStates.put(newState3, new Candidate()); 
                        } 
                        Candidate cands = newStates.get(newState3);
                        cands.e2Sets.addAll(candidates.e2Sets);
                        if(isCandidate && !isThreadIgnore) {
                            cands.e2Sets.add(e.eventCount);
                        } 
                    }
                    else if(state.reorder == 1 && !newState.aftSet.isDependentWith(newState.currentGrain)) {
                        if(!newState.firstFrontier.mustIgnore(e) && !newState.currentFrontier.mustIgnore(e)) {
                            NondetState newState2 = new NondetState(newState, true);
                            newState2.currentFrontierInclude(e);
                            newState2.reorder = 1;
                            newState2.hashString = newState2.toString();
                            if(!newStates.containsKey(newState2)) {
                                newStates.put(newState2, new Candidate()); 
                            }
                        }
    
                        NondetState newState3 = new NondetState(newState, true);
                        boolean isThreadIgnore = newState3.currentFrontier.threadMissed(e);
                        newState3.currentFrontier.ignore(e);
                        newState3.reorder = 1;
                        newState3.hashString = newState3.toString();
                        if(!newStates.containsKey(newState3)) {
                            newStates.put(newState3, new Candidate()); 
                        } 
                        Candidate cands = newStates.get(newState3);
                        cands.e2Sets.addAll(candidates.e2Sets);
                        if(isCandidate && !isThreadIgnore) {
                            cands.e2Sets.add(e.eventCount);
                        } 
                    }
                    else {
                        if(newState.reorder == 1 || newState.reorder == 0) {
                            newState.currentFrontier.clear();
                        }
                        newState.reorder = 2;
                        newState.hashString = newState.toString();
                        if(!newStates.containsKey(newState)) {
                            newStates.put(newState, new Candidate());
                        }
                    }
                }
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads);
        NondetState newState2 = new NondetState(newState, true);
        newState2.firstFrontierInclude(e);
        newState2.reorder = -1;
        newState2.hashString = newState2.toString();
        if(!newStates.containsKey(newState2)) {
            newStates.put(newState2, new Candidate()); 
        }

        newState.firstFrontier.ignore(e);
        newState.reorder = -1;
        if(e.getType().isAccessType()) {
            newState.enabledE1s.add(new Triplet<>(e.getThread().getId(), e.getVariable().getId(), e.getType().isWrite()));
        }
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new Candidate()); 
        } 

        nondetStates = newStates;
        // System.out.println(e.toStandardFormat());
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state);
        //     System.out.println(nondetStates.get(state));
        // }
        return findRace;
    }

    // private boolean addToStates(TreeMap<NondetState, Candidate> states, NondetState newState) {
    //     TreeSet<NondetState> subsumedStates = new TreeSet<>(new StateComparator());
    //     boolean add = true;
    //     for(NondetState state: states.keySet()) {
    //         if(add && state.subsume(newState)) {
    //             add = false;
    //         }
    //         if(newState.subsume(state)) {
    //             subsumedStates.add(state);
    //         }
    //     }
    //     for(NondetState state: subsumedStates) {
    //         states.remove(state);
    //     }
    //     return add;
    // }

    private boolean isConflict(NondetState state, GrainRaceEvent e) {
        for(Triplet<Integer, Integer, Boolean> e1: state.enabledE1s) {
            if(e1.second == e.getVariable().getId() && e1.first != e.getThread().getId() && (e1.third || e.getType().isWrite())) {
                return true;
            } 
        }
        return false;
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
            if(state.reorder == 0 && !state.currentGrain.isDependentWith(state.aftSetNoE1) && !candidates.e2Sets.isEmpty()){
                // System.out.println("Find: " + state);
                racyEvents.addAll(candidates.e2Sets);
            } 
            if(state.reorder == 1 && !state.currentGrain.isDependentWith(state.aftSet) && !candidates.e2Sets.isEmpty()) {
                // System.out.println("Find: " + state);
                racyEvents.addAll(candidates.e2Sets);
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
    
    public Grain currentGrain;
    public Grain aftSet;
    public Grain aftSetNoE1;
    public boolean lastDependent;
    public Frontier firstFrontier;
    public Frontier currentFrontier;
    public HashSet<Triplet<Integer, Integer, Boolean>> enabledE1s;
    public int reorder;
    public String hashString;

    public NondetState() {
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        lastDependent = false;
        firstFrontier = new Frontier();
        currentFrontier = new Frontier();
        enabledE1s = new HashSet<>();
        reorder = -1;
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
        lastDependent = copy ? state.lastDependent : false;
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain();
        aftSet = new Grain(state.aftSet);
        aftSetNoE1 = new Grain(state.aftSetNoE1);
        firstFrontier = new Frontier(state.firstFrontier);
        currentFrontier = copy ? new Frontier(state.currentFrontier) : new Frontier();
        enabledE1s = new HashSet<>(state.enabledE1s);
        reorder = copy ? state.reorder : -1;
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

    // public boolean subsume(NondetState other) {
    //     return this.aftSetNoE1.subsume(other.aftSetNoE1) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet);
    // }

    public void currentFrontierInclude(GrainRaceEvent e) {
        currentFrontier.includedThreads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            currentFrontier.includedThreads.set(e.getTarget().getId());
        }
        if(e.getType().isWrite()) {
            currentFrontier.missedLastWtVars.clear(e.getVariable().getId());
            firstFrontier.missedLastWtVars.clear(e.getVariable().getId());
        }
        if(e.getType().isAcquire()) {
            currentFrontier.missedLastLocks.clear(e.getLock().getId());
            firstFrontier.missedLastLocks.clear(e.getLock().getId());
        }
    }

    public void firstFrontierInclude(GrainRaceEvent e) {
        firstFrontier.includedThreads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            firstFrontier.includedThreads.set(e.getTarget().getId());
        }
        if(e.getType().isWrite()) {
            firstFrontier.missedLastWtVars.clear(e.getVariable().getId());
        }
        if(e.getType().isAcquire()) {
            firstFrontier.missedLastLocks.clear(e.getLock().getId());
        }
    }

    public String toString() {
        return "CG" + currentGrain.toString() + "AF" + aftSet.toString() + "AFN" + aftSetNoE1.toString() + lastDependent + "FF" + firstFrontier.toString() + "CF" + currentFrontier.toString() + "E1" + enabledE1s.toString() + reorder;
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
    public BitSet missedThreads;
    public BitSet missedLastWtVars;
    public BitSet blockedLocks;
    public BitSet missedLastLocks;
    public BitSet includedThreads;
    public BitSet includedLocks;
    
    public Frontier() {
        missedThreads = new BitSet(GrainRaceState.numOfThreads);
        missedLastWtVars = new BitSet(GrainRaceState.numOfVars); 
        blockedLocks = new BitSet(GrainRaceState.numOfLocks);
        missedLastLocks = new BitSet(GrainRaceState.numOfLocks);
        includedThreads = new BitSet(GrainRaceState.numOfThreads);
        includedLocks = new BitSet(GrainRaceState.numOfLocks);
    }

    public Frontier(Frontier other) {
        missedThreads = (BitSet)other.missedThreads.clone();
        missedLastWtVars = (BitSet)other.missedLastWtVars.clone();
        blockedLocks = (BitSet)other.blockedLocks.clone();
        missedLastLocks = (BitSet)other.missedLastLocks.clone();
        includedThreads = (BitSet)other.includedThreads.clone();
        includedLocks = (BitSet)other.includedLocks.clone();
    }

    public void clear() {
        missedLastLocks.clear();
        missedLastWtVars.clear();
        blockedLocks.clear();
        missedLastLocks.clear();
        includedThreads.clear();
        includedLocks.clear();
    }

    public boolean mustIgnore(GrainRaceEvent e) {
        if(missedThreads.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && missedThreads.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isRead() && missedLastWtVars.get(e.getVariable().getId())) {
            return true;
        }
        if(e.getType().isLockType() && blockedLocks.get(e.getLock().getId())) {
            return true;
        }
        if(e.getType().isRelease() && missedLastLocks.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public void ignore(GrainRaceEvent e) {
        missedThreads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            missedThreads.set(e.getTarget().getId());
        }
        if(e.getType().isWrite()) {
            missedLastWtVars.set(e.getVariable().getId());
        }
        if(e.getType().isRelease() && !missedLastLocks.get(e.getLock().getId())) {
            blockedLocks.set(e.getLock().getId());
        }
        if(e.getType().isAcquire()) {
            missedLastLocks.set(e.getLock().getId());
        }
    }

    public boolean threadMissed(GrainRaceEvent e) {
        return missedThreads.get(e.getThread().getId());
    }
    // private boolean subsume(BitSet b1, BitSet b2) {
    //     BitSet b1Clone = (BitSet)b1.clone();
    //     b1Clone.andNot(b2);
    //     return b1Clone.isEmpty();
    // }

    // public boolean subsume(Frontier other) {
    //     return !this.threads.isEmpty() && !other.threads.isEmpty() && subsume(this.threads, other.threads) && subsume(this.lastWtVars, other.lastWtVars) && subsume(this.locks, other.locks);
    // }

    public String toString() {
        return missedThreads.toString() + missedLastWtVars.toString() + blockedLocks.toString() + missedLastLocks.toString() + includedThreads.toString() + includedLocks.toString();
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}