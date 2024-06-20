package engine.racedetectionengine.grainRaceMinWeak;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import engine.racedetectionengine.State;
import engine.racedetectionengine.grain.Grain;
import engine.racedetectionengine.grain.GrainFrontier;
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

    public GrainRaceState(HashSet<Thread> tSet, HashMap<Variable, HashSet<Long>> lastReads) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new HashMap<>());    
        this.lastReads = lastReads;
    }

    public boolean update(GrainRaceEvent e) {
        // for(NondetState state: nondetStates.keySet()) {
        //     System.out.println(state.hashString);
        // } 
        boolean findRace = false;
        TreeMap<NondetState, HashMap<String, Candidate>> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            HashMap<String, Candidate> candidates = nondetStates.get(state);
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            boolean isCandidate =  e.getType().isAccessType() && isConflict(candidates.keySet(), e);
            boolean minimal = state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();

            if(!state.aftSet.isDependentWith(state.currentGrain)){
                for(String s: candidates.keySet()) {
                    // System.out.println("Find " + state.toString());
                    // System.out.println(candidates); 
                    racyEvents.addAll(candidates.get(s).e2Sets);
                    findRace = true;
                } 
            }
            
            if(!state.firstGrain.isEmpty) {
                boolean singleOrComplete = state.currentGrain.isSingleton || state.currentGrain.isComplete;
                boolean definiteEdge = state.currentGrain.isDefDependentWith(e) || state.aftSet.isDefDependentWith(e);
                boolean edgeContraction = state.firstGrain.isDependentWith(state.currentGrain) && definiteEdge;
                if(minimal || (singleOrComplete && !edgeContraction)) {
                    cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                }
                if(!minimal) {
                    extendCurrentGrain(state, e, newStates, candidates, edgeContraction);
                }
            
            }
            else {
                if(state.currentGrain.isSingleton || state.currentGrain.isComplete) {
                    cutCurrentGrain(state, e, newStates, candidates, isCandidate);
                }
                if(!minimal) {
                    extendCurrentGrain(state, e, newStates, candidates, false);
                }
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        newState.currentGrain.isSingleton = true;
        if(e.getType().isWrite()) {
            newState.currentGrain.firstWrite = e.getVariable().getId();
        }
        if(e.getType().isAcquire()) {
            newState.currentGrain.firstLock = e.getLock().getId();
        }
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new HashMap<>());
        }
        if(e.getType().isAccessType()) {
            newStates.get(newState).put(e.getName(), new Candidate());
        }
        nondetStates = newStates;
        // if(e.eventCount >= 1) {
        //     System.out.println(e.toStandardFormat());
        //     for(NondetState state: nondetStates.keySet()) {
        //         System.out.println(state);
        //     }
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
        states.entrySet().removeIf(state -> !state.getKey().firstGrain.isEmpty && state.getValue().isEmpty());
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

    private void extendCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, HashMap<String, Candidate>> states, HashMap<String, Candidate> candidates, boolean edgeContraction) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        NondetState newState = new NondetState(state, true, false);
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        newState.currentGrain.isSingleton = false || edgeContraction;
        if(e.getType().isRead() && e.getVariable().getId() == newState.currentGrain.firstWrite && lastReads.get(e.getVariable()).contains(e.eventCount)) {
            newState.currentGrain.isComplete = true;
            newState.currentGrain.firstWrite = -1;
        }
        else if(e.getType().isRelease() && e.getLock().getId() == newState.currentGrain.firstLock) {
            newState.currentGrain.isComplete = true;
            newState.currentGrain.firstLock = -1;
        }
        else {
            newState.currentGrain.isComplete = false;
        }

        
        if(isFirstGrain) {
            candidates.clear();
            if(e.getType().isAccessType()) {
                candidates.put(e.getName(), new Candidate());
            }
        }
        newState.hashString = newState.toString();
        // System.out.println(state.hashString);
        HashSet<String> newCands = new HashSet<>(candidates.keySet());
        // System.out.println("AddCon " + newState);
        // System.out.println(states);
        if(addToStates(states, newState, newCands)) {
            // System.out.println("Add" + newState);
            if(!states.containsKey(newState)) {
                states.put(newState, new HashMap<>());
            }
            HashMap<String, Candidate> cands = states.get(newState);
            for(String candName: newCands) {
                if(cands.containsKey(candName)) {
                    cands.get(candName).e2Sets.addAll(candidates.get(candName).e2Sets);
                }
                else {
                    cands.put(candName, new Candidate(candidates.get(candName)));
                }
            }
        }
        // System.out.println(states);
    }

    private void cutCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, HashMap<String, Candidate>> states, HashMap<String, Candidate> candidates, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.isEmpty;
        NondetState newState = isFirstGrain ? new NondetState(state, false, true) : new NondetState(state, false, false);
        if(!isFirstGrain && (state.firstGrain.isDependentWith(state.currentGrain) || state.aftSet.isDependentWith(state.currentGrain))) {
            newState.aftSet.updateGrain(state.currentGrain);
        }
        newState.currentGrain.updateGrain(e, lastReads, e.eventCount);
        newState.currentGrain.isSingleton = true;
        // if(e.getType().isWrite()) {
        //     newState.currentGrain.firstWrite = e.getVariable().getId();
        // }
        // if(e.getType().isAcquire()) {
        //     newState.currentGrain.firstLock = e.getLock().getId();
        // }
        newState.hashString = newState.toString();
        
        HashSet<String> newCands = new HashSet<>(candidates.keySet());
        if(addToStates(states, newState, newCands)) {
            if(!states.containsKey(newState)) {
                states.put(newState, new HashMap<>());
            }
            HashMap<String, Candidate> cands = states.get(newState);
            for(String candName: newCands) {
                if(!cands.containsKey(candName)) {
                    cands.put(candName, new Candidate());
                }
            }
            if(isCandidate) {
                for(String candName: cands.keySet()) {
                    if(isNameConflict(candName, e)) {
                        cands.get(candName).e2Sets.add(e.eventCount);
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
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            HashMap<String, Candidate> candidates = nondetStates.get(state); 
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            if(!state.firstGrain.threadsBitSet.isEmpty() && !state.currentGrain.threadsBitSet.isEmpty() && !state.aftSet.isDependentWith(state.currentGrain)) {
                // for(long c: nondetStates.get(state)) {
                //     if(!racyEvents.contains(c)) {
                //         System.out.println(c);
                //     }
                // }
                // System.out.println(state.hashString);
                // System.out.println("Find " + state.toString());
                // System.out.println(candidates);
                for(String s: candidates.keySet()) {
                    racyEvents.addAll(candidates.get(s).e2Sets);
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
    public String hashString;

    public NondetState() {
        firstGrain = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        currentGrain = new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy, boolean first) {
        firstGrain = first ? new GrainFrontier(state.currentGrain) : new GrainFrontier(state.firstGrain);
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain(GrainRaceState.numOfThreads, GrainRaceState.numOfVars, GrainRaceState.numOfLocks);
        aftSet = new GrainFrontier(state.aftSet);
        hashString = this.toString();
    }

    public boolean subsume(NondetState other) {
        return !this.firstGrain.isEmpty && this.firstGrain.subsume(other.firstGrain) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet);
    }

    public String toString() {
        return "FG" + firstGrain.toString() + "CG" + currentGrain.toString() + "Aft" + aftSet.toString();
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


