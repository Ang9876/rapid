package engine.racedetectionengine.grainRaceMinLocalSyncP;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

import engine.racedetectionengine.State;
import engine.racedetectionengine.grain.Grain;
import engine.racedetectionengine.grain.GrainFrontier;
import engine.racedetectionengine.grain.SyncPFrontier;
import event.Thread;
import event.Variable;
import util.Triplet;

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
        nondetStates.put(initState, new Candidate(0, 0));    
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
            boolean isCandidate =  e.getType().isAccessType() && isConflict(candidates, e);
            if(state.aftSet.threadsBitSet.nextClearBit(0) >= numOfThreads) {
                continue;
            }
            boolean minimal = state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();
            boolean singleThread = !this.singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId());
            boolean boundedSize = !this.boundedSize || candidates.size <= size;
            boolean tooOld = candidates.lifetime > 500;
            if(tooOld) {
                continue;
            }

            if(state.reorder == 0 && !state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()){
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    racyEvents.addAll(candidates.e2Sets.get(e1));
                    findRace = true;
                }
            }
            
            if(state.reorder == 1 && !state.aftSet.isDependentWith(state.currentGrain) && !state.firstGrain.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    racyEvents.addAll(candidates.e2Sets.get(e1));
                    findRace = true;
                }
            }

            if(!state.firstGrain.isEmpty) {
                boolean singleOrComplete = state.currentGrain.isSingleton || state.currentGrain.isComplete;
                boolean definiteEdge = state.currentGrain.isDefDependentWith(e) || state.aftSet.isDefDependentWith(e);
                boolean edgeContraction = state.aftSet.isDependentWith(state.currentGrain) && definiteEdge && boundedSize;
                // boolean edgeContraction = false;

                if(minimal || (singleOrComplete && !edgeContraction)) {
                    cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                }
                // TODO: IF CUT OPTION IS RULED OUT, SHOULD WE DISABLE SINGLETHREAD CHECKING.
                if(!minimal && boundedSize && singleThread) {
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
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        newState.reorder = -1;
        newState.currentGrain.isSingleton = true;

        NondetState newState2 = new NondetState(newState, true, false);
        newState2.firstFrontierInclude(e);
        newState2.hashString = newState2.toString();
        if(!newStates.containsKey(newState2)) {
            newStates.put(newState2, new Candidate(1, 1)); 
        }
        Candidate cands = newStates.get(newState2);
        cands.size = 1;
        cands.lifetime = 1; 

        newState.firstFrontier.ignore(e);
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new Candidate(1, 1)); 
        }
        Candidate cands1 = newStates.get(newState);
        if(e.getType().isAccessType()) {
            cands1.e2Sets.put(new Triplet<>(e.getThread().getId(), e.getVariable().getId(), e.getType().isWrite()), new HashSet<>());
        }
        cands1.size = 1;
        cands1.lifetime = 1;  

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

    private boolean isConflict(Candidate candidates, GrainRaceEvent e) {
        for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
            if(e1.second == e.getVariable().getId() && e1.first != e.getThread().getId() && (e1.third || e.getType().isWrite())) {
                return true;
            } 
        }
        return false;
    }

    private void extendCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, Candidate> states, Candidate candidates, boolean edgeContraction, boolean isCandidate) {
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
        

        
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);

        // System.out.println("AddCon " + newState);
        // System.out.println(states);
        newState.hashString = newState.toString();
        if(isFirstGrain) {
            boolean mustIgnore = newState.firstFrontier.mustIgnore(e);
            if(!mustIgnore) {
                NondetState newState2 = new NondetState(newState, true, false);
                newState2.firstFrontierInclude(e);
                newState2.reorder = -1;
                newState2.hashString = newState2.toString();
                if(!states.containsKey(newState2)) {
                    states.put(newState2, new Candidate(1, candidates.lifetime)); 
                }
                
                Candidate cands = states.get(newState2);
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    if(cands.e2Sets.containsKey(e1)) {
                        cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                    }
                    else {
                        cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                    }
                }
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
            }

            NondetState newState3 = new NondetState(newState, true, false);
            boolean isThreadIgnore = newState3.firstFrontier.threadMissed(e);
            newState3.firstFrontier.ignore(e);
            newState3.reorder = -1;
            newState3.hashString = newState3.toString();
            if(!states.containsKey(newState3)) {
                states.put(newState3, new Candidate(1, candidates.lifetime)); 
            }
            Candidate cands = states.get(newState3);
            for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                if(cands.e2Sets.containsKey(e1)) {
                    cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                }
                else {
                    cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                }
            } 
            if(e.getType().isAccessType() && !isThreadIgnore) {
                cands.e2Sets.put(new Triplet<>(e.getThread().getId(), e.getVariable().getId(), e.getType().isWrite()), new HashSet<>());
            }
            cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
            cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
        }   
        else {
            if(state.reorder == 0 && !newState.aftSet.isDependentWith(newState.currentGrain)) {
                if(!newState.firstFrontier.mustIgnore(e) && !newState.currentFrontier.mustIgnore(e)) {
                    NondetState newState2 = new NondetState(newState, true, false);
                    newState2.currentFrontierInclude(e);
                    newState2.reorder = 0;
                    newState2.hashString = newState2.toString();
                    if(!states.containsKey(newState2)) {
                        states.put(newState2, new Candidate(1, candidates.lifetime)); 
                    }
                    Candidate cands = states.get(newState2);
                    for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                        if(cands.e2Sets.containsKey(e1)) {
                            cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                        }
                        else {
                            cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                        }
                    }
                    cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                    cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
                }

                NondetState newState3 = new NondetState(newState, true, false);
                boolean isThreadIgnore = newState3.firstFrontier.threadMissed(e) || newState3.currentFrontier.threadMissed(e);
                newState3.currentFrontier.ignore(e);
                newState3.reorder = 0;
                newState3.hashString = newState3.toString();
                if(!states.containsKey(newState3)) {
                    states.put(newState3, new Candidate(1, candidates.lifetime)); 
                }
                Candidate cands = states.get(newState3);
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    if(cands.e2Sets.containsKey(e1)) {
                        cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                    }
                    else {
                        cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                    }
                }
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
                // cands.e2Sets.addAll(candidates.e2Sets);
                if(isCandidate && !isThreadIgnore) {
                    for(Triplet<Integer, Integer, Boolean> e1: cands.e2Sets.keySet()) {
                        if(e1.first != e.getThread().getId() && e1.second == e.getVariable().getId() && (e1.third || e.getType().isWrite())) {
                            cands.e2Sets.get(e1).add(e.eventCount);
                        }
                    }
                } 
            }
            else if(state.reorder == 1 && !newState.aftSet.isDependentWith(newState.currentGrain)) {
                if(!newState.firstFrontier.mustIgnore(e) && !newState.currentFrontier.mustIgnore(e)) {
                    NondetState newState2 = new NondetState(newState, true, false);
                    newState2.currentFrontierInclude(e);
                    newState2.reorder = 1;
                    newState2.hashString = newState2.toString();
                    if(!states.containsKey(newState2)) {
                        states.put(newState2, new Candidate(1, candidates.lifetime)); 
                    }
                    Candidate cands = states.get(newState2);
                    for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                        if(cands.e2Sets.containsKey(e1)) {
                            cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                        }
                        else {
                            cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                        }
                    }
                    cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                    cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
                }

                NondetState newState3 = new NondetState(newState, true, false);
                boolean isThreadIgnore = newState3.currentFrontier.threadMissed(e);
                newState3.currentFrontier.ignore(e);
                newState3.reorder = 1;
                newState3.hashString = newState3.toString();
                if(!states.containsKey(newState3)) {
                    states.put(newState3, new Candidate(1, candidates.lifetime)); 
                }
                Candidate cands = states.get(newState3);
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    if(cands.e2Sets.containsKey(e1)) {
                        cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                    }
                    else {
                        cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                    }
                }
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
                // cands.e2Sets.addAll(candidates.e2Sets);
                if(isCandidate && !isThreadIgnore) {
                    for(Triplet<Integer, Integer, Boolean> e1: cands.e2Sets.keySet()) {
                        if(e1.first != e.getThread().getId() && e1.second == e.getVariable().getId() && (e1.third || e.getType().isWrite())) {
                            cands.e2Sets.get(e1).add(e.eventCount);
                        }
                    }
                } 
            }
            else {
                if(newState.reorder == 1 || newState.reorder == 0) {
                    newState.currentFrontier.clear();
                }
                newState.reorder = 2;
                newState.hashString = newState.toString();
                if(!states.containsKey(newState)) {
                    states.put(newState, new Candidate(1, candidates.lifetime)); 
                }
                Candidate cands = states.get(newState);
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    if(cands.e2Sets.containsKey(e1)) {
                        cands.e2Sets.get(e1).addAll(candidates.e2Sets.get(e1));
                    }
                    else {
                        cands.e2Sets.put(e1, new HashSet<>(candidates.e2Sets.get(e1)));
                    }
                }
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1; 
            }
        }
        // System.out.println(states);
    }

    private void cutCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, Candidate> states, Candidate candidates, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        if(isFirstGrain && candidates.e2Sets.isEmpty()) {
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

        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);

        boolean depG12 = newState.aftSet.isDefDependentWith(e);
        boolean depG21 = depG12 || newState.firstGrain.isDefDependentWith(e);

        // G1G2
        if(!depG12) {
            boolean mustIgnore = newState.firstFrontier.mustIgnore(e);
            if(!mustIgnore) {
                NondetState newState2 = new NondetState(newState, true, false);
                newState2.currentFrontierInclude(e);
                newState2.reorder = 0;
                newState2.hashString = newState2.toString();
                if(!states.containsKey(newState2)) {
                    states.put(newState2, new Candidate(1, candidates.lifetime)); 
                }
                Candidate cands = states.get(newState2);
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    if(!cands.e2Sets.containsKey(e1)) {
                        cands.e2Sets.put(e1, new HashSet<>());
                    }
                }
                cands.size = 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            }

            
            NondetState newState3 = new NondetState(newState, true, false);
            boolean isThreadIgnore = newState3.firstFrontier.threadMissed(e);
            newState3.currentFrontier.ignore(e);
            newState3.reorder = 0;
            newState3.hashString = newState3.toString();
            
            if(!states.containsKey(newState3)) {
                states.put(newState3, new Candidate(1, candidates.lifetime)); 
            } 
            Candidate cands = states.get(newState3);
            for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                if(!cands.e2Sets.containsKey(e1)) {
                    cands.e2Sets.put(e1, new HashSet<>());
                }
            }
            cands.size = 1;
            cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            if(isCandidate && !isThreadIgnore) {
                for(Triplet<Integer, Integer, Boolean> e1: cands.e2Sets.keySet()) {
                    if(e1.first != e.getThread().getId() && e1.second == e.getVariable().getId() && (e1.third || e.getType().isWrite())) {
                        cands.e2Sets.get(e1).add(e.eventCount);
                    }
                }
            }
        }

        // G2G1
        if(!depG21) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.currentFrontierInclude(e);
            newState2.reorder = 1;
            newState2.hashString = newState2.toString();
            if(!states.containsKey(newState2)) {
                states.put(newState2, new Candidate(1, candidates.lifetime)); 
            }
            Candidate cands = states.get(newState2);
            cands.size = 1;
            cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            
            NondetState newState3 = new NondetState(newState, true, false);
            newState3.currentFrontier.ignore(e);
            newState2.reorder = 1;
            newState3.hashString = newState3.toString();
            if(!states.containsKey(newState3)) {
                states.put(newState3, new Candidate(1, candidates.lifetime)); 
            } 
            Candidate cands1 = states.get(newState3);
            for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                if(!cands.e2Sets.containsKey(e1)) {
                    cands.e2Sets.put(e1, new HashSet<>());
                }
            }
            cands1.size = 1;
            cands1.lifetime = (cands1.lifetime < candidates.lifetime + 1) ? cands1.lifetime : candidates.lifetime + 1;
            // cands.e2Sets.addAll(candidates.e2Sets);
            if(isCandidate) {
                for(Triplet<Integer, Integer, Boolean> e1: cands1.e2Sets.keySet()) {
                    if(e1.first != e.getThread().getId() && e1.second == e.getVariable().getId() && (e1.third || e.getType().isWrite())) {
                        cands1.e2Sets.get(e1).add(e.eventCount);
                    }
                }
            } 
        }

        // Not track SyncP Prefix
        if(depG12 && depG21) {
            newState.reorder = 2;
            newState.hashString = newState.toString();
            if(!states.containsKey(newState)) {
                states.put(newState, new Candidate(1, candidates.lifetime));
            }
            Candidate cands = states.get(newState);
            for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                if(!cands.e2Sets.containsKey(e1)) {
                    cands.e2Sets.put(e1, new HashSet<>());
                }
            }
            cands.size = 1;
            cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
        }
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            Candidate candidates = nondetStates.get(state); 
            if(state.reorder == 0 && !state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()){
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    racyEvents.addAll(candidates.e2Sets.get(e1));
                }
            } 
            if(state.reorder == 1 && !state.aftSet.isDependentWith(state.currentGrain) && !state.firstGrain.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
                for(Triplet<Integer, Integer, Boolean> e1: candidates.e2Sets.keySet()) {
                    racyEvents.addAll(candidates.e2Sets.get(e1));
                }
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
    public SyncPFrontier firstFrontier;
    public SyncPFrontier currentFrontier;
    public int reorder;
    public String hashString;

    public NondetState() {
        firstGrain = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentGrain = new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        firstFrontier = new SyncPFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentFrontier = new SyncPFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        reorder = -1;
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy, boolean first) {
        firstGrain = first ? new GrainFrontier(state.currentGrain) : new GrainFrontier(state.firstGrain);
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(state.aftSet);
        firstFrontier = new SyncPFrontier(state.firstFrontier);
        currentFrontier = copy ? new SyncPFrontier(state.currentFrontier) : new SyncPFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        reorder = copy ? state.reorder : -1;
        hashString = this.toString();
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
        StringBuffer sb = new StringBuffer();
        firstGrain.toString(sb);
        currentGrain.toString(sb);
        aftSet.toString(sb);
        firstFrontier.toString(sb);
        currentFrontier.toString(sb);
        sb.append(reorder);
        return sb.toString();
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}

class Candidate {
    public HashMap<Triplet<Integer, Integer, Boolean>, HashSet<Long>> e2Sets;
    public int size;
    public int lifetime;

    public Candidate(int size, int lifetime) {
        e2Sets = new HashMap<>();
        this.size = size;
        this.lifetime = lifetime;
    }

    public String toString() {
        return e2Sets.toString();
    }
}