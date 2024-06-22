package engine.racedetectionengine.grainRaceMinLocalSHB;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import engine.racedetectionengine.State;
import engine.racedetectionengine.grain.Grain;
import engine.racedetectionengine.grain.GrainFrontier;
import engine.racedetectionengine.grain.SHBFrontier;
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
    private boolean singleThread;
    private boolean boundedSize;
    private int size;

    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads, boolean singleThread, boolean boundedSize, int size) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new Candidate());    
        this.lastReads = lastReads;
        this.singleThread = singleThread;
        this.boundedSize = boundedSize;
        this.size = size;
    }

    public boolean update(GrainRaceEvent e) {
        boolean findRace = false;
        TreeMap<NondetState, Candidate> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            Candidate candidates = nondetStates.get(state);
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            boolean isCandidate =  e.getType().isAccessType() && isConflict(state, e);
            boolean minimal = state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();
            boolean singleThread = !this.singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId());
            boolean boundedSize = !this.boundedSize || state.size <= size;

            if(!state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()){
                racyEvents.addAll(candidates.e2Sets);
                findRace = true;
            } 

            if(!state.firstGrain.isEmpty) {
                boolean singleOrComplete = state.currentGrain.isSingleton || state.currentGrain.isComplete;
                boolean definiteEdge = state.currentGrain.isDefDependentWith(e) || state.aftSet.isDefDependentWith(e);
                boolean edgeContraction = state.firstGrain.isDependentWith(state.currentGrain) && definiteEdge;
                if(minimal || (singleOrComplete && !edgeContraction)) {
                    cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                }
                // TODO: IF CUT OPTION IS RULED OUT, SHOULD WE DISABLE SINGLETHREAD CHECKING.
                if(!minimal && (boundedSize || edgeContraction) && singleThread) {
                    extendCurrentGrain(state, e, newStates, candidates, edgeContraction, isCandidate);
                }
            }
            else {
                if(state.currentGrain.isSingleton || state.currentGrain.isComplete) {
                    cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                }
                if(!minimal && boundedSize && singleThread) {
                    extendCurrentGrain(state, e, newStates, candidates, false, isCandidate);
                }
            }

            // // Stop current grain here  
            // if(!doEdgeContraction && !(isFirstGrain && state.e1Thread == -1)) {
            //     NondetState newState = new NondetState(state, false);
            //     if(state.aftSet.threadsBitSet.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
            //         newState.aftSet.updateGrain(state.currentGrain);
            //         if(!isFirstGrain) {
            //             newState.aftSetNoE1.updateGrain(state.currentGrain);
            //         }
            //     }
            //     newState.lastDependent = !isFirstGrain && state.currentGrain.isDependentWith(state.aftSet) && !minimal; 
            //     if(e.getType().isWrite() && e.getVariable().getId() == newState.e1Var) {
            //         newState.e1LastWrite = false;
            //     }
            //     newState.currentGrain.updateGrain(e, lastReads);
            //     boolean addToCand = false;
            //     if(isCandidate && !state.firstFrontier.isSHBSandwiched(e)){
            //         addToCand = true;
            //     }
            //     if(newState.firstFrontier.isDependentWith(e, state.e1Thread, state.e1Var, state.e1LastWrite)) {
            //         newState.currentFrontier.update(e);
            //     }
            //     else if(e.getType().isWrite()) {
            //         newState.firstFrontier.removeWt(e.getVariable().getId());
            //         newState.currentFrontier.removeWt(e.getVariable().getId());
            //     }
                
            //     newState.hashString = newState.toString();
            //     // System.out.println("Addstop " + newState);
            //     if(addToStates(newStates, newState)) {
            //         if(!newStates.containsKey(newState)) {
            //             newStates.put(newState, new Candidate());
            //         }
            //         Candidate cands = newStates.get(newState);
            //         // cands.e2Sets.addAll(candidates.e2Sets);
            //         if(addToCand) {
            //             cands.e2Sets.add(e.eventCount);
            //         }
            //     }
            // }
            
            // // update current grain
            // if(doEdgeContraction || !minimal) {
            //     boolean addToCand = false;
            //     if(isFirstGrain && state.e1Thread != -1) {
            //         if(state.firstFrontier.isDependentWith(e, state.e1Thread, state.e1Var, state.e1LastWrite)) {
            //             state.firstFrontier.update(e);
            //        }
            //        else if(e.getType().isWrite()) {
            //            state.firstFrontier.removeWt(e.getVariable().getId());
            //            state.currentFrontier.removeWt(e.getVariable().getId());
            //        }
            //     }
            //     if(isCandidate && !isFirstGrain && !state.firstFrontier.isSHBSandwiched(e) && !state.currentFrontier.isSHBSandwiched(e)) {
            //         addToCand = true;
            //     }
            //     if(!isFirstGrain) {
            //         if((state.firstFrontier.isDependentWith(e, state.e1Thread, state.e1Var, state.e1LastWrite) || state.currentFrontier.isSHBSandwiched(e))) {
            //             state.currentFrontier.update(e);
            //         }
            //         else if(e.getType().isWrite()) {
            //             state.firstFrontier.removeWt(e.getVariable().getId());
            //             state.currentFrontier.removeWt(e.getVariable().getId());
            //         }
            //     }
            //     if(e.getType().isWrite() && e.getVariable().getId() == state.e1Var) {
            //         state.e1LastWrite = false;
            //     }
            //     state.currentGrain.updateGrain(e, lastReads);
                
            //     if((!state.lastDependent || !state.currentGrain.isDependentWith(state.aftSetNoE1))) {
            //         NondetState newState = new NondetState(state, true);
            //         // System.out.println(newState.hashString);
            //         // System.out.println("AddCon " + newState);
            //         // System.out.println(addToStates(newStates, newState, newCands));
            //         if(addToStates(newStates, newState)) {
            //             if(!newStates.containsKey(newState)) {
            //                 newStates.put(newState, new Candidate());
            //             }
            //             Candidate cands = newStates.get(newState);
            //             cands.e2Sets.addAll(candidates.e2Sets);
            //             if(addToCand) {
            //                 cands.e2Sets.add(e.eventCount);
            //             }
            //         }

                    
            //         if(state.e1Thread == -1 && e.getType().isAccessType()) {
            //             NondetState newState2 = new NondetState(newState, true);
            //             newState2.e1Thread = e.getThread().getId();
            //             newState2.e1Var = e.getVariable().getId();
            //             newState2.e1Write = e.getType().isWrite();
            //             newState2.e1LastWrite = newState2.e1Write;
            //             newState2.hashString = newState2.toString();
            //             // System.out.println("AddCon " + newState2);
            //             if(addToStates(newStates, newState2)) {
            //                 if(!newStates.containsKey(newState2)) {
            //                     newStates.put(newState2, new Candidate());
            //                 }
            //             }
            //         }
            //     }
            // }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        if(this.boundedSize) {
            newState.size += 1;
        }
        newState.currentGrain.isSingleton = true;
        if(e.getType().isWrite()) {
            newState.currentGrain.firstWrite = e.getVariable().getId();
        }
        if(e.getType().isAcquire()) {
            newState.currentGrain.firstLock = e.getLock().getId();
        }
        newState.hashString = newState.toString();
        if(addToStates(newStates, newState)) {
            if(!newStates.containsKey(newState)) {
                newStates.put(newState, new Candidate());
            }
        }
        
        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
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
        // if(e.eventCount <= 8){
        //     System.out.println(e.toStandardFormat());
        //     for(NondetState state: nondetStates.keySet()) {
        //         System.out.println(state);
        //         System.out.println(nondetStates.get(state));
        //     }
        // }
        
        return findRace;
    }

    private boolean addToStates(TreeMap<NondetState, Candidate> states, NondetState newState) {
        TreeSet<NondetState> subsumedStates = new TreeSet<>(new StateComparator());
        boolean add = true;
        for(NondetState state: states.keySet()) {
            if(add && state.subsume(newState)) {
                // add = false;
                break;
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

    private void extendCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, Candidate> states, Candidate candidates, boolean edgeContraction, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        NondetState newState = new NondetState(state, true, false);
        if(this.boundedSize) {
            newState.size += 1;
        }
        newState.currentGrain.isSingleton = false || edgeContraction;
        if(newState.currentGrain.firstWrite != -1) {
            if(e.getType().isRead() && e.getVariable().getId() == newState.currentGrain.firstWrite && lastReads.get(e.getVariable()).contains(e.eventCount)) {
                newState.currentGrain.isComplete = true;
                newState.currentGrain.firstWrite = -1;
            }
            else{
                newState.currentGrain.isComplete = false;
            }
        }
        else if(newState.currentGrain.firstLock != -1) {
            if(e.getType().isRelease() && e.getLock().getId() == newState.currentGrain.firstLock) {
                newState.currentGrain.isComplete = true;
                newState.currentGrain.firstLock = -1;
            }
            else {
                newState.currentGrain.isComplete = false;
            }
        }
        else if(newState.currentGrain.firstWrite == -1 && newState.currentGrain.firstLock == -1) {
            if(e.getType().isRead() && state.currentGrain.incompleteWtVarsBitSet.get(e.getVariable().getId()) && lastReads.get(e.getVariable()).contains(e.eventCount)) {
                newState.currentGrain.isComplete = true;
            }
            else if(e.getType().isRelease() && state.currentGrain.incompleteAcqsBitSet.get(e.getLock().getId())) {
                newState.currentGrain.isComplete = true;
            } 
            else {
                newState.currentGrain.isComplete = false;
            }
        }
        

        // FirstGrain
        if(isFirstGrain && state.e1Thread != -1) {
            if(newState.firstFrontier.isDependentWith(e, newState.e1Thread, newState.e1Var, newState.e1LastWrite)) {
                newState.firstFrontier.update(e);
           }
           else if(e.getType().isWrite()) {
                newState.firstFrontier.removeWt(e.getVariable().getId());
                newState.currentFrontier.removeWt(e.getVariable().getId());
           }
        }
        boolean addToCand = false;
        if(!isFirstGrain){
            if(isCandidate && !newState.firstFrontier.isSHBSandwiched(e) && !newState.currentFrontier.isSHBSandwiched(e)) {
                addToCand = true;
            }
            if((newState.firstFrontier.isDependentWith(e, newState.e1Thread, newState.e1Var, newState.e1LastWrite) || newState.currentFrontier.isSHBSandwiched(e))) {
                newState.currentFrontier.update(e);
            }
            else if(e.getType().isWrite()) {
                newState.firstFrontier.removeWt(e.getVariable().getId());
                newState.currentFrontier.removeWt(e.getVariable().getId());
            }
        }
        if(e.getType().isWrite() && e.getVariable().getId() == state.e1Var) {
            newState.e1LastWrite = false;
        }
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);

        // System.out.println("AddCon " + newState);
        // System.out.println(states);
        if(addToStates(states, newState)) {
            newState.hashString = newState.toString();
            // System.out.println("Add" + newState);
            if(!states.containsKey(newState)) {
                states.put(newState, new Candidate());
            }
            Candidate cands = states.get(newState);
            cands.e2Sets.addAll(candidates.e2Sets);
            if(addToCand) {
                cands.e2Sets.add(e.eventCount);
            }
        }

        if(state.e1Thread == -1 && e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
            newState2.e1LastWrite = newState2.e1Write;
            newState2.hashString = newState2.toString();
            // System.out.println("AddCon " + newState2);
            if(addToStates(states, newState2)) {
                if(!states.containsKey(newState2)) {
                    states.put(newState2, new Candidate());
                }
            }
        }
        // System.out.println(states);
    }

    private void cutCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, Candidate> states, Candidate candidates, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        if(isFirstGrain && state.e1Thread == -1) {
            return;
        }
        NondetState newState = isFirstGrain ? new NondetState(state, false, true) : new NondetState(state, false, false);
        if(!isFirstGrain && (state.firstGrain.isDependentWith(state.currentGrain) || state.aftSet.isDependentWith(state.currentGrain))) {
            newState.aftSet.updateGrain(state.currentGrain);
        }
        if(this.boundedSize) {
            newState.size += 1;
        }
        newState.currentGrain.isSingleton = true;
        if(e.getType().isWrite()) {
            newState.currentGrain.firstWrite = e.getVariable().getId();
        }
        if(e.getType().isAcquire()) {
            newState.currentGrain.firstLock = e.getLock().getId();
        }

        if(e.getType().isWrite() && e.getVariable().getId() == newState.e1Var) {
            newState.e1LastWrite = false;
        }
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
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

        if(addToStates(states, newState)) {
            newState.hashString = newState.toString();
            if(!states.containsKey(newState)) {
                states.put(newState, new Candidate());
            }
            Candidate cands = states.get(newState);
            if(addToCand) {
                cands.e2Sets.add(e.eventCount);
            }
        }
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            Candidate candidates = nondetStates.get(state); 
            
            
            // System.out.println(!state.currentGrain.isDependentWith(state.aftSetNoE1)); 
            if(!state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
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
    public GrainFrontier firstGrain; 
    public Grain currentGrain;
    public GrainFrontier aftSet;
    public int e1Thread;
    public int e1Var;
    public boolean e1Write;
    public boolean e1LastWrite;
    public SHBFrontier firstFrontier;
    public SHBFrontier currentFrontier;
    public int size;
    public String hashString;

    public NondetState() {
        firstGrain = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentGrain = new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        e1Thread = -1;
        e1Var = -1;
        e1Write = false;
        e1LastWrite = false;
        firstFrontier = new SHBFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentFrontier = new SHBFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        size = 0;
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy, boolean first) {
        firstGrain = first ? new GrainFrontier(state.currentGrain) : new GrainFrontier(state.firstGrain);
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(state.aftSet);
        e1Thread = state.e1Thread;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        e1LastWrite = state.e1LastWrite;
        firstFrontier = new SHBFrontier(state.firstFrontier);
        currentFrontier = copy ? new SHBFrontier(state.currentFrontier) : new SHBFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        size = copy ? state.size : 0;
        hashString = this.toString();
    }

    public boolean subsume(NondetState other) {
        return !this.firstGrain.isEmpty && this.e1Thread == other.e1Thread && this.e1Var == other.e1Var && (this.e1Write || !other.e1Write) && (!this.e1LastWrite || other.e1LastWrite) && this.firstGrain.subsume(other.firstGrain) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.firstFrontier.subsume(other.firstFrontier) && this.currentFrontier.subsume(other.currentFrontier) && this.size <= other.size;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        firstGrain.toString(sb);
        currentGrain.toString(sb);
        aftSet.toString(sb);
        firstFrontier.toString(sb);
        currentFrontier.toString(sb);
        sb.append(e1Thread);
        sb.append(e1Var);
        sb.append(e1Write);
        sb.append(e1LastWrite);
        sb.append(size);
        return sb.toString();
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
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
