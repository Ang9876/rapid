package engine.racedetectionengine.grainRaceMinLocalMazV4;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import engine.racedetectionengine.State;
import engine.racedetectionengine.grain.Grain;
import engine.racedetectionengine.grain.GrainFrontier;
import engine.racedetectionengine.grain.MazFrontier;
import event.Lock;
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

                if(state.inorder && !state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()){
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
                if(!state.inorder && !state.aftSet.isDependentWith(state.currentGrain) && !state.firstGrain.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
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
    
        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
            newState2.inorder = true;
            newState2.backier = null;
            newState2.hashString = newState2.toString();
            String sig1 = newState2.getSignature();
            boolean fresh = false;
            if(!newStates.containsKey(sig1)) {
                fresh = true;
                newStates.put(sig1, new HashMap<>());
            }
            HashMap<String, Pair<NondetState, Candidate>>nondet = newStates.get(sig1); 
            if(fresh || addToStates(nondet, newState2)) {
                if(!nondet.containsKey(newState2.hashString)) {
                    nondet.put(newState2.hashString, new Pair<>(newState2, new Candidate(1, 1)));
                }
                Candidate cands = nondet.get(newState2.hashString).second;
                cands.size = 1;
                cands.lifetime = 1;
            }

            NondetState newState3 = new NondetState(newState, true, false);
            newState3.e1Thread = e.getThread().getId();
            newState3.e1Var = e.getVariable().getId();
            newState3.e1Write = e.getType().isWrite();
            newState3.inorder = false;
            newState3.hashString = newState3.toString();
            // System.out.println("AddCon " + newState2);
            sig1 = newState3.getSignature();
            fresh = false;
            if(!newStates.containsKey(sig1)) {
                fresh = true;
                newStates.put(sig1, new HashMap<>());
            } 
            nondet = newStates.get(sig1); 
            if(fresh || addToStates(nondet, newState3)) {
                if(!nondet.containsKey(newState3.hashString)) {
                    nondet.put(newState3.hashString, new Pair<>(newState3, new Candidate(1, 1)));
                }
                Candidate cands = nondet.get(newState3.hashString).second;
                cands.size = 1;
                cands.lifetime = 1;
            }
        }
        newState.backier.update(e);
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
        
        nondetStates = newStates;
        
        // System.out.println(e.eventCount);
        // if(e.eventCount >= 1) {
        //     System.out.println(e.toStandardFormat());
        //     for(String sig1: nondetStates.keySet()) {
        //         for(String stateSig: nondetStates.get(sig1).keySet()) {
        //             System.out.println(nondetStates.get(sig1).get(stateSig).first);
        //             System.out.println(nondetStates.get(sig1).get(stateSig).second);
        //         }
        //     }
        // }
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
        if(isFirstGrain && state.e1Thread != -1 && state.inorder) {
            if(newState.firstFrontier.isDependentWith(e) || newState.isDependentWithE1(e)) {
                newState.firstFrontier.update(e);
            }
        }
        boolean addToCand = false;
        if(!isFirstGrain){
            if(isCandidate && !newState.firstFrontier.isDependentWith(e) && !newState.currentFrontier.isDependentWith(e)) {
                addToCand = true;
            }
            if(newState.inorder) {
                if((newState.firstFrontier.isDependentWith(e) || newState.currentFrontier.isDependentWith(e)) || newState.isDependentWithE1(e)) {
                    newState.currentFrontier.update(e);
                }
            }
            else if(newState.e2Thread == -1 && addToCand) {
                NondetState newState2 = new NondetState(newState, true, false);
                newState2.e2Thread = e.getThread().getId();
                newState2.e2Var = e.getVariable().getId();
                newState2.e2Write = e.getType().isWrite();
                newState2.currentGrain.updateGrain(e, lastReads, e.eventCount);
                newState2.hashString = newState2.toString();
                String sig1 = newState2.getSignature();
                boolean fresh = false;
                if(!states.containsKey(sig1)) {
                    fresh = true;
                    states.put(sig1, new HashMap<>());
                } 
                HashMap<String, Pair<NondetState, Candidate>> nondet = states.get(sig1); 
                if(fresh || addToStates(nondet, newState2)) {
                    if(!nondet.containsKey(newState2.hashString)) {
                        nondet.put(newState2.hashString, new Pair<>(newState2, new Candidate(candidates.size + 1, candidates.lifetime + 1)));
                    }
                    Candidate cands = nondet.get(newState2.hashString).second;
                    cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                    cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
                    cands.e2Sets.add(e.eventCount);
                    cands.e2LocSets.add(e.getLocId());
                }
            }
            else if(newState.e2Thread != -1) {
                if(newState.isDependentWithE2(e) || newState.currentFrontier.isDependentWith(e)) {
                    if(newState.firstFrontier.isDependentWith(e) || newState.isDependentWithE1(e)) {
                        return;
                    }
                    newState.currentFrontier.update(e);
                }
            }
        }
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);

        // System.out.println("AddCon " + newState);
        // System.out.println(states);
        

        if(state.e1Thread == -1 && e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
            newState2.inorder = true;
            newState2.backier = null;
            newState2.hashString = newState2.toString();
            // System.out.println("AddCon " + newState2);
            String sig1 = newState2.getSignature();
            boolean fresh = false;
            if(!states.containsKey(sig1)) {
                fresh = true;
                states.put(sig1, new HashMap<>());
            } 
            HashMap<String, Pair<NondetState, Candidate>> nondet = states.get(sig1); 
            if(fresh || addToStates(nondet, newState2)) {
                if(!nondet.containsKey(newState2.hashString)) {
                    nondet.put(newState2.hashString, new Pair<>(newState2, new Candidate(candidates.size + 1, candidates.lifetime + 1)));
                }
                Candidate cands = nondet.get(newState2.hashString).second;
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            }

            NondetState newState3 = new NondetState(newState, true, false);
            newState3.e1Thread = e.getThread().getId();
            newState3.e1Var = e.getVariable().getId();
            newState3.e1Write = e.getType().isWrite();
            newState3.inorder = false;
            newState3.firstFrontier = newState3.backier.getSummary(e);
            newState3.backier = null;
            newState3.hashString = newState3.toString();
            // System.out.println("AddCon " + newState2);
            sig1 = newState3.getSignature();
            fresh = false;
            if(!states.containsKey(sig1)) {
                fresh = true;
                states.put(sig1, new HashMap<>());
            } 
            nondet = states.get(sig1); 
            if(fresh || addToStates(nondet, newState3)) {
                if(!nondet.containsKey(newState3.hashString)) {
                    nondet.put(newState3.hashString, new Pair<>(newState3, new Candidate(candidates.size + 1, candidates.lifetime + 1)));
                }
                Candidate cands = nondet.get(newState3.hashString).second;
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
            }
        }

        if(state.e1Thread == -1) {
            state.backier.update(e);
        }
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
            cands.e2LocSets.addAll(candidates.e2LocSets);
            if(addToCand && newState.inorder) {
                cands.e2Sets.add(e.eventCount);
                cands.e2LocSets.add(e.getLocId());
            }
        }
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

        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        boolean addToCand = false;
        if(isCandidate && !state.firstFrontier.isDependentWith(e)){
            addToCand = true;
        }
        if(!newState.inorder && addToCand) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e2Thread = e.getThread().getId();
            newState2.e2Var = e.getVariable().getId();
            newState2.e2Write = e.getType().isWrite();
            newState2.backier = null;
            newState2.hashString = newState2.toString();
            String sig1 = newState2.getSignature();
            boolean fresh = false;
            if(!states.containsKey(sig1)) {
                fresh = true;
                states.put(sig1, new HashMap<>());
            } 
            HashMap<String, Pair<NondetState, Candidate>> nondet = states.get(sig1); 
            if(fresh || addToStates(nondet, newState2)) {
                if(!nondet.containsKey(newState2.hashString)) {
                    nondet.put(newState2.hashString, new Pair<>(newState2, new Candidate(candidates.size + 1, candidates.lifetime + 1)));
                }
                Candidate cands = nondet.get(newState2.hashString).second;
                cands.size = (cands.size < candidates.size + 1) ? cands.size : candidates.size + 1;
                cands.lifetime = (cands.lifetime < candidates.lifetime + 1) ? cands.lifetime : candidates.lifetime + 1;
                cands.e2Sets.add(e.eventCount);
                cands.e2LocSets.add(e.getLocId());
            }
        }
        if(newState.inorder && (newState.firstFrontier.isDependentWith(e) || newState.isDependentWithE1(e))) {
            newState.currentFrontier.update(e);
        }
        newState.backier = null;
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
            if(newState.inorder && addToCand) {
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
                if(state.inorder && !state.aftSet.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
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
                if(!state.inorder && !state.aftSet.isDependentWith(state.currentGrain) && !state.firstGrain.isDependentWith(state.currentGrain) && !candidates.e2Sets.isEmpty()) {
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

class MazBackier {
    public HashMap<Integer, MazFrontier> threadBackier = new HashMap<>();
    public HashMap<Integer, HashMap<Integer, MazFrontier>> readBackier = new HashMap<>();
    public HashMap<Integer, MazFrontier> writeBackier = new HashMap<>();
    public HashMap<Integer, MazFrontier> lockBackier = new HashMap<>();

    public void updateExtreme(int thread, int target) {
        if(threadBackier.containsKey(target)) {
            threadBackier.get(thread).union(threadBackier.get(target));
        }
    }

    public void updateRead(int thread, int var) {
        MazFrontier frontier = threadBackier.get(thread);
        frontier.rdVars.set(var);
        if(writeBackier.containsKey(var)) {
            frontier.union(writeBackier.get(var));
        }
        if(!readBackier.containsKey(thread)) {
            readBackier.put(thread, new HashMap<>());
        }
        readBackier.get(thread).put(var, new MazFrontier(frontier));
    }

    public void updateWrite(int thread, int var) {
        MazFrontier frontier = threadBackier.get(thread);
        frontier.wtVars.set(var);
        if(writeBackier.containsKey(var)) {
            frontier.union(writeBackier.get(var));
        }
        for(int t: readBackier.keySet()) {
            if(readBackier.get(t).containsKey(var)) {
                frontier.union(readBackier.get(t).get(var));
            }
        }
        writeBackier.put(var, new MazFrontier(frontier));
    }

    public void updateLock(int thread, int lock) {
        MazFrontier frontier = threadBackier.get(thread);
        frontier.locks.set(lock);
        if(lockBackier.containsKey(lock)) {
            frontier.union(lockBackier.get(lock));
        }
        lockBackier.put(lock, new MazFrontier(frontier));
    }

    public void update(GrainRaceEvent e) {
        int thread = e.getThread().getId();
        if(!threadBackier.containsKey(thread)) {
            threadBackier.put(thread, new MazFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks));
            threadBackier.get(thread).threads.set(thread);
        }
        if(e.getType().isExtremeType()) {
            updateExtreme(e.getThread().getId(), e.getTarget().getId());
        }
        if(e.getType().isRead()) {
            updateRead(e.getThread().getId(), e.getVariable().getId());
        }
        if(e.getType().isWrite()) {
            updateWrite(e.getThread().getId(), e.getVariable().getId());
        }
        if(e.getType().isLockType()) {
            updateLock(e.getThread().getId(), e.getLock().getId());
        }
    }

    public MazFrontier getSummary(GrainRaceEvent e) {
        int thread = e.getThread().getId();
        int var = e.getVariable().getId();
        MazFrontier frontier;
        if(!threadBackier.containsKey(thread)) {
            frontier = new MazFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        }
        else {
            frontier = new MazFrontier(threadBackier.get(thread));
        }
         
        if(writeBackier.containsKey(var)) {
            frontier.union(writeBackier.get(var));
        }
        if(e.getType().isWrite()) {
            for(int t: readBackier.keySet()) {
                if(readBackier.get(t).containsKey(var)) {
                    frontier.union(readBackier.get(t).get(var));
                }
            }
        }
        return frontier;
    }

    public boolean subsume(MazBackier other) {
        return threadBackier.equals(other.threadBackier) && readBackier.equals(other.readBackier) && writeBackier.equals(other.writeBackier) && lockBackier.equals(lockBackier);
    }

    public void toString(StringBuffer sb) {
        sb.append(threadBackier);
        sb.append(readBackier);
        sb.append(writeBackier);
        sb.append(lockBackier);
    }

    // public String toString() {
    //     StringBuffer sb = new StringBuffer();
    //     sb.append(threadBackier);
    //     sb.append(readBackier);
    //     sb.append(writeBackier);
    //     sb.append(lockBackier);
    //     return sb.toString();
    // }
}

class NondetState {
    public GrainFrontier firstGrain; 
    public Grain currentGrain;
    public GrainFrontier aftSet;

    public int e1Thread;
    public int e1Var;
    public boolean e1Write;
    public int e2Thread;
    public int e2Var;
    public boolean e2Write;
    public boolean inorder;
    public MazFrontier firstFrontier;
    public MazFrontier currentFrontier;
    public MazBackier backier;
    public String hashString;

    public NondetState() {
        firstGrain = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentGrain = new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        e1Thread = -1;
        e1Var = -1;
        e2Thread = -1;
        e2Var = -1;
        e2Write = false;
        inorder = false;
        e1Write = false;
        firstFrontier = new MazFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentFrontier = new MazFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        backier = new MazBackier();
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy, boolean first) {
        firstGrain = first ? new GrainFrontier(state.currentGrain) : new GrainFrontier(state.firstGrain);
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(state.aftSet);
        e1Thread = state.e1Thread;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        e2Thread = copy ? state.e2Thread : -1;
        e2Var = copy ? state.e2Var : -1;
        e2Write = copy ? state.e2Write : false;
        inorder = state.inorder;
        firstFrontier = new MazFrontier(state.firstFrontier);
        currentFrontier = copy ? new MazFrontier(state.currentFrontier) : new MazFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        backier = copy ? state.backier : null;
        hashString = this.toString();
    }
    
    public boolean isDependentWithE1(GrainRaceEvent e) {
        return  e.getThread().getId() == e1Thread || 
                (e.getType().isExtremeType() && e.getTarget().getId() == e1Thread) ||
                (e.getType().isAccessType() && e.getVariable().getId() == e1Var && (e1Write || e.getType().isWrite()));
    }

    public boolean isDependentWithE2(GrainRaceEvent e) {
        return  e.getThread().getId() == e2Thread || 
                (e.getType().isExtremeType() && e.getTarget().getId() == e2Thread) ||
                (e.getType().isAccessType() && e.getVariable().getId() == e2Var && (e2Write || e.getType().isWrite()));
    }

    public boolean subsume(NondetState other) {
        return !this.firstGrain.isEmpty && this.e1Thread == other.e1Thread && this.e1Var == other.e1Var && this.inorder == other.inorder && (this.e1Write || !other.e1Write) && (this.e2Write || !other.e2Write) && this.firstGrain.subsume(other.firstGrain) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.firstFrontier.subsume(other.firstFrontier) && this.currentFrontier.subsume(other.currentFrontier) && ((this.backier == null && other.backier == null) || (this.backier != null && other.backier != null && this.backier.subsume(other.backier)));
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
        sb.append(e2Thread);
        sb.append(e2Var);
        sb.append(inorder);
        return sb.toString();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        firstGrain.toString(sb);
        currentGrain.toString(sb);
        aftSet.toString(sb);
        firstFrontier.toString(sb);
        currentFrontier.toString(sb);
        if(backier != null) 
            backier.toString(sb);
        else
            sb.append("NULL");
        sb.append(inorder);
        sb.append(e1Thread);
        sb.append(e1Var);
        sb.append(e1Write);
        sb.append(e2Thread);
        sb.append(e2Var);
        sb.append(e2Write);
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

    public String toString() {
        return e2Sets.toString();
    }
}