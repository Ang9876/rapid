package engine.racedetectionengine.grainRaceMinLocalSHBV2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import engine.racedetectionengine.State;
import engine.racedetectionengine.grain.Grain;
import engine.racedetectionengine.grain.GrainFrontier;
import engine.racedetectionengine.grain.SHBFrontier;
import event.Thread;
import event.Variable;
import util.Pair;

public class GrainRaceState extends State {
    
    public static int numOfThreads;
    public static int numOfVars;
    public static int numOfLocks;
    HashSet<Thread> threadSet;
    HashMap<String, HashMap<String, Pair<NondetState, Candidate>>> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;
    public HashSet<Long> racyEvents = new HashSet<>();
    public HashSet<Integer> racyLocs = new HashSet<>();
    private boolean singleThread;
    private boolean boundedSize;
    private int size;
    private boolean window;
    private int win;

    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads, boolean singleThread, boolean boundedSize, int size, boolean window, int win) {
        threadSet = tSet;
        nondetStates = new HashMap<>();
        NondetState initState = new NondetState();
        nondetStates.put(initState.getSignature(), new HashMap<>());    
        nondetStates.get(initState.getSignature()).put(initState.hashString, new Pair<>(initState, new Candidate(0, 0)));
        this.lastReads = lastReads;
        this.singleThread = singleThread;
        this.boundedSize = boundedSize;
        this.size = size;
        this.window = window;
        this.win = win;
        System.out.println("Mode of incomplete optimization: " + 
                            (singleThread ? "SingleThread, " : "") + 
                            (boundedSize ? "Bounded Grain Size = " + size + ", " : "") +
                            (window ? "Window = " + win : ""));
    }

    public boolean update(GrainRaceEvent e) {
        boolean findRace = false;
        HashMap<String, HashMap<String, Pair<NondetState, Candidate>>> newStates = new HashMap<>(); 
        for(String sig: nondetStates.keySet()) {
            HashMap<String, Pair<NondetState, Candidate>> nondet = nondetStates.get(sig);
            for(String stateSig: nondet.keySet()){
                Pair<NondetState, Candidate> pair = nondet.get(stateSig);
                NondetState state = pair.first;
                Candidate candidates = pair.second;
                // System.out.println(state.hashString);
                // System.out.println(candidates);
                boolean isCandidate =  e.getType().isAccessType() && isConflict(state, e);
                if(state.aftSet.threadsBitSet.nextClearBit(0) >= numOfThreads) {
                    continue;
                }

                boolean minimal = state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();
                boolean singleThread = !this.singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId());
                boolean boundedSize = !this.boundedSize || candidates.size < size;
                boolean tooOld = window && candidates.lifetime > win;
                if(tooOld) {
                    continue;
                }

                if(!state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()){
                    for(Long e2: candidates.e2Sets) {
                        if(!racyEvents.contains(e2)) {
                            racyEvents.add(e2);
                            System.out.println("New Race: " + e2);
                        }
                    }
                    for(Integer e2: candidates.e2LocSets) {
                        if(!racyLocs.contains(e2)) {
                            racyLocs.add(e2);
                            System.out.println("New Race Loc: " + e2);
                        }
                    }
                    findRace = true;
                } 

                if(!state.firstGrain.isEmpty) {
                    boolean singleOrComplete = state.currentGrain.isSingleton || state.currentGrain.isComplete;
                    boolean definiteEdge = state.currentGrain.isDefDependentWith(e) || state.aftSet.isDefDependentWith(e);
                    boolean edgeContraction = state.aftSet.isDependentWith(state.currentGrain) && definiteEdge;
                    // boolean edgeContraction = false;

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
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        // if(this.boundedSize) {
        //     newState.size += 1;
        // }
        newState.currentGrain.isSingleton = true;
        if(e.getType().isWrite()) {
            newState.currentGrain.firstWrite = e.getVariable().getId();
        }
        if(e.getType().isAcquire()) {
            newState.currentGrain.firstLock = e.getLock().getId();
        }
        newState.hashString = newState.toString();
        String sig = newState.getSignature();
        boolean fresh = false;
        if(!newStates.containsKey(sig)) {
            fresh = true;
            newStates.put(sig, new HashMap<>());
        } 
        HashMap<String, Pair<NondetState, Candidate>> nondet = newStates.get(sig);
        if(fresh || addToStates(nondet, newState)) {
            if(!nondet.containsKey(newState.hashString)) {
                nondet.put(newState.hashString, new Pair<>(newState, new Candidate(1, 1)));
            }
            Candidate cands = nondet.get(newState.hashString).second;
            cands.size = 1;
            cands.lifetime = 1; 
        }
        
        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
            newState2.e1LastWrite = newState2.e1Write;
            newState2.hashString = newState2.toString();
            String sig1 = newState2.getSignature();
            fresh = false;
            if(!newStates.containsKey(sig1)) {
                fresh = true;
                newStates.put(sig1, new HashMap<>());
            }
            nondet = newStates.get(sig1); 
            if(fresh || addToStates(nondet, newState2)) {
                if(!nondet.containsKey(newState2.hashString)) {
                    nondet.put(newState2.hashString, new Pair<>(newState2, new Candidate(1, 1)));
                }
                Candidate cands = nondet.get(newState2.hashString).second;
                cands.size = 1;
                cands.lifetime = 1;
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
        // System.out.println(e.eventCount + " " + size());
        return findRace;
    }

    private boolean addToStates(HashMap<String, Pair<NondetState, Candidate>> states, NondetState newState) {
        if(states.containsKey(newState.hashString)) {
            return true;
        }
        for(Iterator<HashMap.Entry<String, Pair<NondetState, Candidate>>> it = states.entrySet().iterator(); it.hasNext();) {
            HashMap.Entry<String, Pair<NondetState, Candidate>> entry = it.next();
            Pair<NondetState, Candidate> pair = entry.getValue();
            NondetState state = pair.first;
            if(state.subsume(newState)) {
                return false;
            }
            if(newState.subsume(state)) {
                it.remove();
            }
        }
        return true;
    }

    private boolean isConflict(NondetState state, GrainRaceEvent e) {
        return state.e1Var == e.getVariable().getId() && state.e1Thread != e.getThread().getId() && (state.e1Write || e.getType().isWrite());
    }

    private void extendCurrentGrain(NondetState state, GrainRaceEvent e, HashMap<String, HashMap<String, Pair<NondetState, Candidate>>> states, Candidate candidates, boolean edgeContraction, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        NondetState newState = new NondetState(state, true, false);
        // if(this.boundedSize) {
        //     newState.size += 1;
        // }
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
        newState.hashString = newState.toString();
        String sig = newState.getSignature();
        boolean fresh = false;
        if(!states.containsKey(sig)) {
            fresh = true;
            states.put(sig, new HashMap<>());
        } 
        HashMap<String, Pair<NondetState, Candidate>> nondet = states.get(sig);
        if(fresh || addToStates(nondet, newState)) {
            // System.out.println("Add" + newState);
            if(!nondet.containsKey(newState.hashString)) {
                nondet.put(newState.hashString, new Pair<>(newState, new Candidate(candidates.size + 1, candidates.lifetime + 1)));
            }
            Candidate cands = nondet.get(newState.hashString).second;
            cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
            cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            cands.e2Sets.addAll(candidates.e2Sets);
            if(addToCand) {
                cands.e2Sets.add(e.eventCount);
                cands.e2LocSets.add(e.getLocId());
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
            String sig1 = newState2.getSignature();
            fresh = false;
            if(!states.containsKey(sig1)) {
                fresh = true;
                states.put(sig1, new HashMap<>());
            } 
            nondet = states.get(sig1); 
            if(fresh || addToStates(nondet, newState2)) {
                if(!nondet.containsKey(newState2.hashString)) {
                    nondet.put(newState2.hashString, new Pair<>(newState2, new Candidate(candidates.size + 1, candidates.lifetime + 1)));
                }
                Candidate cands = nondet.get(newState2.hashString).second;
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            }
        }
        // System.out.println(states);
    }

    private void cutCurrentGrain(NondetState state, GrainRaceEvent e, HashMap<String, HashMap<String, Pair<NondetState, Candidate>>> states, Candidate candidates, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        if(isFirstGrain && state.e1Thread == -1) {
            return;
        }
        NondetState newState = isFirstGrain ? new NondetState(state, false, true) : new NondetState(state, false, false);
        if(!isFirstGrain && (state.firstGrain.isDependentWith(state.currentGrain) || state.aftSet.isDependentWith(state.currentGrain))) {
            newState.aftSet.updateGrain(state.currentGrain);
        }
        // if(this.boundedSize) {
        //     newState.size += 1;
        // }
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

        newState.hashString = newState.toString();
        newState.hashString = newState.toString();
        String sig = newState.getSignature();
        boolean fresh = false;
        if(!states.containsKey(sig)) {
            fresh = true;
            states.put(sig, new HashMap<>());
        } 
        HashMap<String, Pair<NondetState, Candidate>> nondet = states.get(sig); 
        if(fresh || addToStates(nondet, newState)) {
            if(!nondet.containsKey(newState.hashString)) {
                nondet.put(newState.hashString, new Pair<>(newState, new Candidate(1, candidates.lifetime + 1)));
            }
            Candidate cands = nondet.get(newState.hashString).second;
            cands.size = 1;
            cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            if(addToCand) {
                cands.e2Sets.add(e.eventCount);
                cands.e2LocSets.add(e.getLocId());
            }
        }
    }

    public long size() {
        int size = 0;
        for(String sig: nondetStates.keySet()) {
            size += nondetStates.get(sig).size();
        }
        return size;
    }

    public boolean finalCheck() {
        for(String sig: nondetStates.keySet()) {
            HashMap<String, Pair<NondetState, Candidate>> nondet = nondetStates.get(sig);
            for(String stateSig: nondet.keySet()) {
                Pair<NondetState, Candidate> pair = nondet.get(stateSig);
                NondetState state = pair.first;
                Candidate candidates = pair.second; 
                if(!state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
                    for(Long e2: candidates.e2Sets) {
                        if(!racyEvents.contains(e2)) {
                            racyEvents.add(e2);
                            System.out.println("New Race: " + e2);
                        }
                    }
                    for(Integer e2: candidates.e2LocSets) {
                        if(!racyLocs.contains(e2)) {
                            racyLocs.add(e2);
                            System.out.println("New Race Loc: " + e2);
                        }
                    }
                }
            }
        }
        return false;
    }

    public void printMemory() {
        System.out.println(size());
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
        hashString = this.toString();
    }

    public boolean subsume(NondetState other) {
        return !this.firstGrain.isEmpty && this.e1Thread == other.e1Thread && this.e1Var == other.e1Var && (this.e1Write || !other.e1Write) && (!this.e1LastWrite || other.e1LastWrite) && this.firstGrain.subsume(other.firstGrain) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.firstFrontier.subsume(other.firstFrontier) && this.currentFrontier.subsume(other.currentFrontier);// && this.size <= other.size;
    }

    public String getSignature() {
        StringBuffer sb = new StringBuffer();
        sb.append(currentGrain.incompleteWtVarsBitSet);
        sb.append(currentGrain.incompleteAcqsBitSet);
        sb.append(currentGrain.isSingleton);
        sb.append(currentGrain.isComplete);
        sb.append(currentGrain.firstWrite);
        sb.append(currentGrain.firstLock);
        sb.append(firstGrain.isEmpty);
        sb.append(e1Thread);
        sb.append(e1Var);
        return sb.toString();
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
        return sb.toString();
    }
}

class Candidate {
    public HashSet<Long> e2Sets;
    public HashSet<Integer> e2LocSets;
    public int size;
    public int lifetime;

    public Candidate(int size, int lifetime) {
        e2Sets = new HashSet<>();
        e2LocSets = new HashSet<>();
        this.size = size;
        this.lifetime = lifetime;
    }
}
