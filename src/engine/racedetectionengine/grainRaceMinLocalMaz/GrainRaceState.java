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
        boolean singleThread = true;
        TreeMap<NondetState, HashMap<String, Candidate>> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            HashMap<String, Candidate> candidates = nondetStates.get(state);
            // System.out.println(state.hashString);
            // System.out.println(candidates);
            boolean isCandidate =  e.getType().isAccessType() && !state.isDependentWith(e, true) && isConflict(candidates.keySet(), e);
            // System.out.println(e.toStandardFormat());
            // System.out.println(isCandidate);
            if(state.aftSet.threadsBitSet.nextSetBit(0) >= numOfThreads) {
                continue;
            }
            // boolean doEdgeContraction = !isFirstGrain &&
            //                             ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e, false)) ||
            //                              (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e, false)));
            boolean minimal = !state.currentGrain.threadsBitSet.isEmpty() && state.currentGrain.incompleteWtVarsBitSet.isEmpty() && state.currentGrain.incompleteAcqsBitSet.isEmpty();

            if(!state.currentGrain.threadsBitSet.isEmpty() && !state.currentGrain.isDependentWith(state.aftSet)){
                // System.out.println("Race " + state.toString());
                // System.out.println(candidates);
                for(String s: candidates.keySet()) {
                    racyEvents.addAll(candidates.get(s).e2Sets);
                    findRace = true;
                } 
            }
            
            if(!state.aftSet.threadsBitSet.isEmpty()) {
                boolean definiteEdge = state.aftSet.isDefiniteDependentWith(e);
                boolean noEdge = !definiteEdge && !state.aftSet.isPendingDependentWith(e, lastReads);
                boolean definiteEdgeCurrent = state.currentGrain.isDefiniteDependentWith(e);
                if(state.currentGrain.threadsBitSet.isEmpty()) {
                    if(definiteEdge) {
                        NondetState newState = joinFrontier(state, e, newStates);
                        addCutState(newState, newStates, candidates, e, isCandidate);
                    }
                    else if(!noEdge) {
                        extendCurrentGrain(state, e, newStates, candidates, isCandidate);
                    }
                    else {
                        if(isCandidate && !state.firstFrontier.isDependentWith(e)) {
                            // System.out.println("Find " + state.toString());
                            // System.out.println(candidates);
                            racyEvents.add(e.eventCount);
                            findRace = true;
                        }
                    }
                }
                else {
                    if(!isCandidate && !minimal && definiteEdgeCurrent && (!singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId())) && (state.size <= 5)) {
                        extendCurrentGrain(state, e, newStates, candidates, isCandidate);
                    }
                    else {
                        NondetState newState1 = cutCurrentGrain(state, e, newStates, candidates);
                        // System.out.println("AddCut " + newState1);
                        addCutState(newState1, newStates, candidates, e, isCandidate);
                        if(!minimal && (!singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId())) && (state.size <= 5)) {
                            extendCurrentGrain(state, e, newStates, candidates, isCandidate);
                        }
                    }
                }
                
            }
            else {
                if(state.e1Thread != -1) {
                    NondetState newState1 = cutCurrentGrain(state, e, newStates, candidates);
                    addCutState(newState1, newStates, candidates, e, isCandidate);
                }
                
                if(!minimal && (!singleThread || state.currentGrain.threadsBitSet.get(e.getThread().getId())) && state.size <= 5) {
                    extendCurrentGrain(state, e, newStates, candidates, isCandidate);
                }
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, lastReads);
        newState.size = 1;
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new HashMap<>());
        }
        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
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
    private void extendCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, HashMap<String, Candidate>> states, HashMap<String, Candidate> candidates, boolean isCandidate) {
        boolean isFirstGrain = state.firstGrain.threadsBitSet.isEmpty();
        boolean addToCand = false;
        NondetState newState = new NondetState(state, true, false);
        newState.currentGrain.updateGrain(e, lastReads);
        newState.size += 1;
        if(isFirstGrain && newState.e1Thread != -1 && (newState.firstFrontier.isDependentWith(e) || e.getThread().getId() == newState.e1Thread || (e.getType().isExtremeType() && e.getTarget().getId() == newState.e1Thread) || (e.getType().isAccessType() && e.getVariable().getId() == newState.e1Var && (e.getType().isWrite() || newState.e1Write)))) {
            newState.firstFrontier.update(e);
        }
        if(!isFirstGrain){
            if (state.firstFrontier.isDependentWith(e) || state.currentFrontier.isDependentWith(e) || e.getThread().getId() == newState.e1Thread || (e.getType().isExtremeType() && e.getTarget().getId() == newState.e1Thread) || (e.getType().isAccessType() && e.getVariable().getId() == newState.e1Var && (e.getType().isWrite() || newState.e1Write))) {
                state.currentFrontier.update(e);
            }
            else if(isCandidate) {
                addToCand = true;
            }
        }
        newState.hashString = newState.toString();
        HashSet<String> newCands = new HashSet<>(candidates.keySet());
        // System.out.println("AddCon " + newState);
        if(addToStates(states, newState, newCands)) {
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
            if(addToCand) {
                for(String candName: cands.keySet()) {
                    if(isNameConflict(candName, e)) {
                        cands.get(candName).e2Sets.add(e.eventCount);
                    }
                } 
            }
        }
        if(newState.e1Thread == -1 && e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true, false);
            newState2.e1Thread = e.getThread().getId();
            newState2.e1Var = e.getVariable().getId();
            newState2.e1Write = e.getType().isWrite();
            newState2.hashString = newState2.toString();
            assert(candidates.size() == 0);
            HashSet<String> newCands2 = new HashSet<>();
            newCands2.add(e.getName());
            if(addToStates(states, newState2, newCands2)) {
                if(!states.containsKey(newState2)) {
                    states.put(newState2, new HashMap<>());
                }

                HashMap<String, Candidate> cands = states.get(newState2);
                if(!cands.containsKey(e.getName())) {
                    cands.put(e.getName(), new Candidate());
                }
            }
        }

    }

    private NondetState cutCurrentGrain(NondetState state, GrainRaceEvent e, TreeMap<NondetState, HashMap<String, Candidate>> states, HashMap<String, Candidate> candidates) {
        boolean isFirstGrain = state.firstGrain.threadsBitSet.isEmpty();
        // System.out.println("Check");
        // System.out.println(state);
        NondetState newState = isFirstGrain ? new NondetState(state, false, true) : new NondetState(state, false, false);
        // System.out.println(newState);
        if(!isFirstGrain && (state.firstGrain.isDependentWith(state.currentGrain) || state.aftSet.isDependentWith(state.currentGrain))) {
            newState.aftSet.updateGrain(state.currentGrain);
        }
        newState.currentGrain.updateGrain(e, lastReads);
        newState.size += 1;
        if(newState.e1Thread != -1 && (newState.firstFrontier.isDependentWith(e) || e.getThread().getId() == newState.e1Thread || (e.getType().isExtremeType() && e.getTarget().getId() == newState.e1Thread) || (e.getType().isAccessType() && e.getVariable().getId() == newState.e1Var && (e.getType().isWrite() || newState.e1Write)))) {
            newState.currentFrontier.update(e);
        } 
        newState.hashString = newState.toString();
        return newState;
    }

    private NondetState joinFrontier(NondetState state, GrainRaceEvent e, TreeMap<NondetState, HashMap<String, Candidate>> states) {
        NondetState newState = new NondetState(state, true, false);
        newState.aftSet.updateGrain(e, lastReads); 
        newState.hashString = newState.toString();
        return newState;
    }

    private void addCutState(NondetState state, TreeMap<NondetState, HashMap<String, Candidate>> states, HashMap<String, Candidate> candidates, GrainRaceEvent e, boolean isCandidate) {
        HashSet<String> newCands = new HashSet<>(candidates.keySet());
        if(addToStates(states, state, newCands)) {
            if(!states.containsKey(state)) {
                states.put(state, new HashMap<>());
            }
            HashMap<String, Candidate> cands = states.get(state);
            for(String candName: newCands) {
                if(!cands.containsKey(candName)) {
                    cands.put(candName, new Candidate());
                }
            }
            if(isCandidate && !state.firstFrontier.isDependentWith(e)) {
                for(String candName: cands.keySet()) {
                    if(isNameConflict(candName, e)) {
                        cands.get(candName).e2Sets.add(e.eventCount);
                    }
                }
            }
        }
    }


    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            HashMap<String, Candidate> candidates = nondetStates.get(state); 
            if(!state.firstGrain.threadsBitSet.isEmpty() && !state.currentGrain.isDependentWith(state.aftSet)) {
                // System.out.println("Find:" + state.toString());
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
    public Grain firstGrain; 
    public Grain currentGrain;
    public Grain aftSet;

    public int e1Thread;
    public int e1Var;
    public boolean e1Write;
    public Frontier firstFrontier;
    public Frontier currentFrontier;
    public int size;
    public String hashString;

    public NondetState() {
        firstGrain = new Grain();
        currentGrain = new Grain();
        aftSet = new Grain();
        e1Thread = -1;
        e1Var = -1;
        e1Write = false;
        size = 0;
        firstFrontier = new Frontier();
        currentFrontier = new Frontier();
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy, boolean first) {
        firstGrain = first ? new Grain(state.currentGrain) : new Grain(state.firstGrain);
        currentGrain = copy ? new Grain(state.currentGrain) : new Grain();
        aftSet = new Grain(state.aftSet);
        e1Thread = state.e1Thread;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        size = copy ? state.size : 0;
        firstFrontier = new Frontier(state.firstFrontier);
        currentFrontier = copy ? new Frontier(state.currentFrontier) : new Frontier();
        hashString = this.toString();
    }

    public boolean isDependentWith(GrainRaceEvent e, boolean noE1) {
        Grain frontier = aftSet;

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
        return !this.firstGrain.threadsBitSet.isEmpty() && !other.firstGrain.threadsBitSet.isEmpty() && this.firstGrain.subsume(other.firstGrain) && this.currentGrain.subsume(other.currentGrain) && this.aftSet.subsume(other.aftSet) && this.e1Thread == other.e1Thread && this.e1Var == other.e1Var && (this.e1Write || !other.e1Write) && this.firstFrontier.subsume(other.firstFrontier) && this.currentFrontier.subsume(other.currentFrontier) && this.size <= other.size;
    }

    public String toString() {
        return "FG" + firstGrain.toString() + "CG" + currentGrain.toString() + "AF" + aftSet.toString() + e1Thread + e1Var + e1Write + "FF" + firstFrontier.toString() + "CF" + currentFrontier.toString() + "Size" + size;
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

    public boolean isDefiniteDependentWith(GrainRaceEvent e) {
        if(threadsBitSet.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && threadsBitSet.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isRead() && incompleteWtVarsBitSet.get(e.getVariable().getId())) {
            return true;
        }
        if(e.getType().isWrite() && incompleteRdVarsBitSet.get(e.getVariable().getId())) {
            return true;
        }
        if(e.getType().isAcquire() && incompleteRelsBitSet.get(e.getLock().getId())) {
            return true;
        }
        if(e.getType().isRelease() && incompleteAcqsBitSet.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public boolean isPendingDependentWith(GrainRaceEvent e, HashMap<Variable, HashSet<Long>> lastReads) {
        // assume isDefiniteDependentWith(e) == false
        if((e.getType().isWrite() && !lastReads.get(e.getVariable()).contains(e.eventCount) && completeVarBitSet.get(e.getVariable().getId())) || (e.getType().isAcquire() && completeLocksBitSet.get(e.getLock().getId()))) {
            return true;
        }
        return false;
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