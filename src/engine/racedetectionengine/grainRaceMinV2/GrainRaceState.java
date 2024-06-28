package engine.racedetectionengine.grainRaceMinV2;

import java.security.cert.TrustAnchor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import engine.racedetectionengine.State;
import engine.racedetectionengine.grain.Grain;
import engine.racedetectionengine.grain.GrainFrontier;
import event.Thread;
import event.Variable;
import util.Pair;
import util.Triplet;

public class GrainRaceState extends State {
    
    public static int numOfThreads;
    public static int numOfVars;
    public static int numOfLocks;
    HashSet<Thread> threadSet;
    HashMap<String, HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>>> nondetStates;
    HashMap<Variable, HashSet<Long>> lastReads;
    public HashSet<Pair<Long, Integer>> racyEvents = new HashSet<>();
    private boolean singleThread;
    private boolean boundedSize;
    private int size;
    private boolean window;
    private int win;

    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads, boolean singleThread, boolean boundedSize, int size) {
        threadSet = tSet;
        nondetStates = new HashMap<>();
        NondetState initState = new NondetState();
        nondetStates.put(initState.getSignature(), new HashMap<>());   
        nondetStates.get(initState.getSignature()).put(initState.hashString, new Pair<>(initState, new HashMap<>())); 
        this.lastReads = lastReads;
        this.singleThread = singleThread;
        this.boundedSize = boundedSize;
        this.size = size;
        System.out.println("Mode of incomplete optimization: " + 
                            (singleThread ? "SingleThread, " : "") + 
                            (boundedSize ? "Bounded Grain Size = " + size + ", " : "") +
                            (window ? "Window = " + win : ""));
    }

    public boolean update(GrainRaceEvent e) {
        boolean findRace = false;
        HashMap<String, HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>>> newStates = new HashMap<>(); 
        for(String sig: nondetStates.keySet()) {
            HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> nondet = nondetStates.get(sig);
            for(String stateSig: nondet.keySet()){
                Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>> trip = nondet.get(stateSig);
                NondetState state = trip.first;
                HashMap<String, HashSet<Pair<Long, Integer>>> candidates = trip.second;
                // System.out.println(state.hashString);
                // System.out.println(candidates);
                boolean isCandidate =  e.getType().isAccessType() && isConflict(candidates.keySet(), e);
                boolean minimal = state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();
                boolean singleThread = !this.singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId());
                boolean boundedSize = !this.boundedSize || state.size < size;

                if(!state.aftSet.isDependentWith(state.currentGrain)){
                    for(String e1: candidates.keySet()) {
                        int sizeBefore = racyEvents.size();
                        racyEvents.addAll(candidates.get(e1));
                        int sizeAfter = racyEvents.size();
                        if(sizeAfter > sizeBefore) {
                            System.out.println(racyEvents);
                        }
                        findRace = true;
                    }
                    
                }
                
                if(!state.firstGrain.isEmpty) {
                    boolean singleOrComplete = state.currentGrain.isSingleton || state.currentGrain.isComplete;
                    boolean definiteEdge = state.currentGrain.isDefDependentWith(e) || state.aftSet.isDefDependentWith(e);
                    boolean edgeContraction = state.aftSet.isDependentWith(state.currentGrain) && definiteEdge && boundedSize;
                    if(minimal || (singleOrComplete && !edgeContraction)) {
                        cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                    }
                    // TODO: IF CUT OPTION IS RULED OUT, SHOULD WE DISABLE SINGLETHREAD CHECKING.
                    if(!minimal && boundedSize && singleThread) {
                        extendCurrentGrain(state, e, newStates, candidates, edgeContraction);
                    }
                }
                else {
                    if(state.currentGrain.isSingleton || state.currentGrain.isComplete) {
                        cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                    }
                    if(!minimal && boundedSize && singleThread) {
                        extendCurrentGrain(state, e, newStates, candidates, false);
                    }
                }
            }
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
        String sig = newState.getSignature();
        if(!newStates.containsKey(sig)) {
            newStates.put(sig, new HashMap<>());
        }
        HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> nondet = newStates.get(sig); 
        if(!nondet.containsKey(newState.hashString)) {
            nondet.put(newState.hashString, new Pair<>(newState, new HashMap<>()));
        }
        if(e.getType().isAccessType()) {
            nondet.get(newState.hashString).second.put(e.getName(), new HashSet<Pair<Long, Integer>>());
        }
        nondetStates = newStates;
        // System.out.println(e.eventCount);
        // if(e.eventCount >= 1) {
        //     System.out.println(e.toStandardFormat());
        //     for(String sig1: nondetStates.keySet()) {
        //         for(String state: nondetStates.get(sig1).keySet()) {
        //             System.out.println(state);
        //         }
        //     }
        // }
        
        return findRace;
    }

    private boolean addToStates(HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> states, NondetState newState, Set<String> cands) {

        for(Iterator<HashMap.Entry<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>>> it = states.entrySet().iterator(); it.hasNext();) {
            HashMap.Entry<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> entry = it.next();
            Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>> pair = entry.getValue();
            NondetState state = pair.first;
            HashMap<String, HashSet<Pair<Long, Integer>>> candidates = pair.second;
            boolean in = false;
            for(String s: cands) {
                if(candidates.keySet().contains(s)) {
                    in = true;
                    break;
                }
            }
            if(!in) {
                continue;
            }
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
                if(candidates.isEmpty() && !state.firstGrain.isEmpty) {
                    it.remove();
                }
            }
        }
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

    private void extendCurrentGrain(NondetState state, GrainRaceEvent e, HashMap<String, HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>>> states, HashMap<String, HashSet<Pair<Long, Integer>>> candidates, boolean edgeContraction) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        NondetState newState = new NondetState(state, true, false);
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
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

        if(isFirstGrain) {
            candidates.clear();
            if(e.getType().isAccessType()) {
                candidates.put(e.getName(), new HashSet<Pair<Long, Integer>>());
            }
        }

        HashSet<String> newCands = new HashSet<>(candidates.keySet());
        // System.out.println("AddCon " + newState);
        // System.out.println(states);
        String sig = newState.getSignature();
        boolean fresh = false;
        if(!states.containsKey(sig)) {
            fresh = true;
            states.put(sig, new HashMap<>());
        } 
        HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> nondet = states.get(sig);
        if(fresh || addToStates(nondet, newState, newCands)) {
            newState.hashString = newState.toString();
            // System.out.println("Add" + newState);
            if(!nondet.containsKey(newState.hashString)) {
                nondet.put(newState.hashString, new Pair<>(newState, new HashMap<>()));
            }
            HashMap<String, HashSet<Pair<Long, Integer>>> cands = nondet.get(newState.hashString).second;
            for(String candName: newCands) {
                if(cands.containsKey(candName)) {
                    cands.get(candName).addAll(candidates.get(candName));
                }
                else {
                    cands.put(candName, new HashSet<>(candidates.get(candName)));
                }
            }
        }
        // System.out.println(states);
    }

    private void cutCurrentGrain(NondetState state, GrainRaceEvent e, HashMap<String, HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>>> states, HashMap<String, HashSet<Pair<Long, Integer>>> candidates, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        if(isFirstGrain && candidates.isEmpty()) {
            return;
        }
        NondetState newState = isFirstGrain ? new NondetState(state, false, true) : new NondetState(state, false, false);
        if(!isFirstGrain && (state.firstGrain.isDependentWith(state.currentGrain) || state.aftSet.isDependentWith(state.currentGrain))) {
            newState.aftSet.updateGrain(state.currentGrain);
        }
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
        
        HashSet<String> newCands = new HashSet<>(candidates.keySet());
        String sig = newState.getSignature();
        boolean fresh = false;
        if(!states.containsKey(sig)) {
            fresh = true;
            states.put(sig, new HashMap<>());
        } 
        HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> nondet = states.get(sig);
        if(fresh || addToStates(nondet, newState, newCands)) {
            newState.hashString = newState.toString();
            if(!nondet.containsKey(newState.hashString)) {
                nondet.put(newState.hashString, new Pair<>(newState, new HashMap<>()));
            }
            HashMap<String, HashSet<Pair<Long, Integer>>> cands = nondet.get(newState.hashString).second;
            for(String candName: newCands) {
                if(!cands.containsKey(candName)) {
                    cands.put(candName, new HashSet<Pair<Long, Integer>>());
                }
            }
            if(isCandidate) {
                for(String candName: cands.keySet()) {
                    if(isNameConflict(candName, e)) {
                        cands.get(candName).add(new Pair<>(e.eventCount, e.getLocId()));
                    }
                }
            }
        }
        // if(e.eventCount == 8) {
        //     System.out.println("Add Stop " + newState);
        //     System.out.println(states);
        // }
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
            HashMap<String, Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>>> nondet = nondetStates.get(sig);
            for(String stateSig: nondetStates.get(sig).keySet()) {
                Pair<NondetState, HashMap<String, HashSet<Pair<Long, Integer>>>> pair = nondet.get(stateSig); 
                NondetState state = pair.first;
                HashMap<String, HashSet<Pair<Long, Integer>>> candidates = pair.second; 
                if(!state.firstGrain.threadsBitSet.isEmpty() && !state.currentGrain.threadsBitSet.isEmpty() && !state.aftSet.isDependentWith(state.currentGrain)) {
                    for(String s: candidates.keySet()) {
                        int sizeBefore = racyEvents.size();
                        racyEvents.addAll(candidates.get(s));
                        int sizeAfter = racyEvents.size();
                        if(sizeAfter > sizeBefore) {
                            System.out.println(racyEvents);
                        }
                    }
                }
            }
        }
        return false;
    }

    public void printMemory() {
        System.out.println(size());
    }
}

class NondetState {
    public GrainFrontier firstGrain; 
    public Grain currentGrain;
    public GrainFrontier aftSet;
    public int size;
    public String hashString;

    public NondetState() {
        firstGrain = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentGrain = new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        size = 0;
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy, boolean first) {
        firstGrain = first ? new GrainFrontier(state.currentGrain) : new GrainFrontier(state.firstGrain);
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(state.aftSet);
        size = copy ? state.size : 0;
        hashString = this.toString();
    }

    public boolean subsume(NondetState other) {
        return !this.firstGrain.isEmpty && this.firstGrain.subsume(other.firstGrain) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.size <= other.size;
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
        return sb.toString();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        firstGrain.toString(sb);
        currentGrain.toString(sb);
        aftSet.toString(sb);
        sb.append(size);
        return sb.toString();
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}