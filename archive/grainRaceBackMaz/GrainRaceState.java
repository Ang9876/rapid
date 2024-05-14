package engine.racedetectionengine.grainRaceBackMaz;

import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeMap;

import engine.racedetectionengine.State;
import event.Lock;
import event.Thread;
import event.Variable;

public class GrainRaceState extends State {
    
    HashSet<Thread> threadSet;
    TreeMap<NondetState, HashSet<Long>> nondetStates;
    public HashSet<Long> racyEvents = new HashSet<>();
    private HashSet<Variable> notLastReads = new HashSet<>();

    public GrainRaceState(HashSet<Thread> tSet) {
        threadSet = tSet;
        nondetStates = new TreeMap<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.put(initState, new HashSet<>());
    }

    public boolean update(GrainRaceEvent e) {
        boolean findRace = false;
        // System.out.println(e.toStandardFormat());
        TreeMap<NondetState, HashSet<Long>> newStates = new TreeMap<>(new StateComparator());
        for(NondetState state: nondetStates.keySet()){
            // System.out.println(state);
            if(state.e1Thr != null && !state.e1Write && !notLastReads.contains(state.e1Var)) {
                state.crossBlock = true;
            }

            HashSet<Long> e1Sets = nondetStates.get(state);
            if(!e1Sets.isEmpty()) {
                for(long e1c: racyEvents) {
                    e1Sets.remove(e1c);
                }
                if(e1Sets.isEmpty()) {
                    continue;
                }
            }
 
            boolean isFirstGrain = state.aftSet.threads.isEmpty();
            boolean candidate = e.getType().isAccessType() && state.e1Thr != null && e.getVariable().getName().equals(state.e1Var.getName()) && !e.getThread().getName().equals(state.e1Thr.getName()) && (e.getType().isWrite() || state.e1Write);
            
            boolean doEdgeContraction = !isFirstGrain && !candidate &&
                                        ((state.currentGrain.isDependentWith(state.aftSet) && state.isDependentWith(e, notLastReads, false)) ||
                                         (!state.currentGrain.isDependentWith(state.aftSet) && !state.isDependentWith(e, notLastReads, false)));


            if(state.candidate && !state.currentGrain.isDependentWith(state.aftSetNoE1)){
                // if current grain contains e2 and it is independent of all grains other than the grain containing e1
                for(long c: nondetStates.get(state)) {
                    if(!racyEvents.contains(c)) {
                        System.out.println(c);
                    }
                }
                System.out.println("YES");
                racyEvents.addAll(nondetStates.get(state));
                findRace = true;
            }
            // Stop current grain here  
            if(!doEdgeContraction && !state.currentGrain.threads.isEmpty() && state.e1Thr != null && !state.crossBlock) {
                NondetState newState = new NondetState(state, false);
                if(state.aftSet.threads.isEmpty() || state.currentGrain.isDependentWith(state.aftSet)) {
                    newState.aftSet.updateGrain(state.currentGrain);
                    if(!isFirstGrain) {
                        newState.aftSetNoE1.updateGrain(state.currentGrain);
                    }
                }
                newState.currentGrain.updateGrain(e, notLastReads);
                // update frontier for Maz
                if(candidate && !state.crossBlock && !newState.firstFrontierNoE1.isDependentWith(e) && !newState.currentFrontier.isDependentWith(e)) {
                    newState.candidate = true;
                }
                if(newState.firstFrontier.isDependentWith(e)) {
                    newState.currentFrontier.update(e);
                }

                newState.hashString = newState.toString();
                // System.out.println(newState.hashString);
                if(!newStates.containsKey(newState)) {
                    newStates.put(newState, new HashSet<>());
                }
                newStates.get(newState).addAll(e1Sets);
                // System.out.println("addStop: " + newState.toString());
            }
            boolean minimal = !state.currentGrain.threads.isEmpty() && state.currentGrain.incompleteRdVars.isEmpty() && state.currentGrain.incompleteRdVarsLast.isEmpty() && state.currentGrain.incompleteRels.isEmpty();
            // update current grain
            if((doEdgeContraction && state.e1Thr != null) || !minimal) {
                // set e as e1
                if(state.e1Thr == null && e.getType().isAccessType()) {
                    NondetState newState = new NondetState(state, true);
                    newState.currentGrain.updateGrain(e, notLastReads);
                    newState.e1Thr = e.getThread();
                    newState.e1Var = e.getVariable();
                    newState.e1Write = e.getType().isWrite();
                    newState.firstFrontier.update(e);
                    newState.hashString = newState.toString();
                    if(!newStates.containsKey(newState)) {
                        newStates.put(newState, new HashSet<>());
                    }
                    // newStates.get(newState).addAll(e1Sets); 
                    newStates.get(newState).add(e.eventCount + 1);
                    // System.out.println("add: " + newState.toString());
                }

                state.currentGrain.updateGrain(e, notLastReads);

                // update frontier for Maz
                if(isFirstGrain && state.e1Thr != null && state.firstFrontier.isDependentWith(e)) {
                    state.firstFrontier.update(e);
                    state.firstFrontierNoE1.update(e);
                }
                if(candidate && !state.crossBlock && !state.firstFrontierNoE1.isDependentWith(e) && !state.currentFrontier.isDependentWith(e)) {
                    state.candidate = true;
                }
                if(!isFirstGrain && (state.firstFrontier.isDependentWith(e) || state.currentFrontier.isDependentWith(e))) {
                    state.currentFrontier.update(e);
                }

                NondetState newState = new NondetState(state, true);
                if(!newStates.containsKey(newState)) {
                    newStates.put(newState, new HashSet<>());
                }
                newStates.get(newState).addAll(e1Sets);
                // System.out.println("addContinue: " + newState.toString());
            }
        }
        NondetState newState = new NondetState();
        newState.currentGrain.updateGrain(e, notLastReads);
        newState.hashString = newState.toString();
        if(!newStates.containsKey(newState)) {
            newStates.put(newState, new HashSet<>());
        }
        // System.out.println("add: " + newState.toString());

        if(e.getType().isAccessType()) {
            NondetState newState2 = new NondetState(newState, true);
            newState2.e1Thr = e.getThread();
            newState2.e1Var = e.getVariable();
            newState2.e1Write = e.getType().isWrite();
            newState2.firstFrontier.update(e);
            newState2.hashString = newState2.toString();
            if(!newStates.containsKey(newState2)) {
                newStates.put(newState2, new HashSet<>());
            }
            newStates.get(newState2).add(e.eventCount + 1); 
            // System.out.println("addNew: " + newState2.toString());
        }
        // System.out.println("All states");
        // System.out.println(newStates);
        // System.out.println("Finish");
        nondetStates = newStates;
        if(e.getType().isRead()) {
            notLastReads.add(e.getVariable());
        }
        else if(e.getType().isWrite()) {
            notLastReads.remove(e.getVariable());
        }
        return findRace;
    }

    public long size() {
        return nondetStates.size();
    }

    public boolean finalCheck() {
        for(NondetState state: nondetStates.keySet()) {
            // System.out.println(state.hashString);
            if(state.candidate && !state.currentGrain.isDependentWith(state.aftSetNoE1)) {
                // for(long c: nondetStates.get(state)) {
                //     if(!racyEvents.contains(c)) {
                //         System.out.println(c);
                //     }
                // }
                System.out.println("YES");
                racyEvents.addAll(nondetStates.get(state));
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
    public Thread e1Thr;
    public Variable e1Var;
    public boolean e1Write;
    public boolean crossBlock;
    public Grain currentGrain;
    public Grain aftSet;
    public Grain aftSetNoE1;
    public boolean candidate;

    public Frontier firstFrontier;
    public Frontier firstFrontierNoE1;
    public Frontier currentFrontier;

    public String hashString;

    public NondetState() {
        e1Thr = null;
        e1Var = null;
        e1Write = false;
        crossBlock = false;
        candidate = false;
        currentGrain = new Grain();
        aftSet = new Grain();
        aftSetNoE1 = new Grain();
        firstFrontier = new Frontier();
        firstFrontierNoE1 = new Frontier();
        currentFrontier = new Frontier();
        hashString = this.toString();
    }

    public NondetState(NondetState state, boolean copy) {
        e1Thr = state.e1Thr;
        e1Var = state.e1Var;
        e1Write = state.e1Write;
        crossBlock = copy ? state.crossBlock : false;
        candidate = copy ? state.candidate : false;
        currentGrain = copy ? new Grain(state.currentGrain, true) : new Grain();
        aftSet = new Grain(state.aftSet, false);
        aftSetNoE1 = new Grain(state.aftSetNoE1, false);
        firstFrontier = new Frontier(state.firstFrontier);
        firstFrontierNoE1 = new Frontier(state.firstFrontierNoE1);
        currentFrontier = copy ? new Frontier(state.currentFrontier) : new Frontier();
        hashString = this.toString();
    }

    public boolean isDependentWith(GrainRaceEvent e, HashSet<Variable> notLastReads, boolean noE1) {
        // e is in frontier iff any grain starting with e is dependent with the frontier
        Grain frontier = noE1 ? aftSetNoE1 : aftSet;
        if(frontier.threads.contains(e.getThread())) {
            return true;
        }
        if(e.getType().isExtremeType() && frontier.threads.contains(e.getTarget())) {
            return true;
        }
        if(e.getType().isRead() && (frontier.incompleteWtVars.contains(e.getVariable()) || (notLastReads.contains(e.getVariable()) && frontier.completeVars.contains(e.getVariable())))) {
            return true;
        }
        if(e.getType().isWrite() && (frontier.incompleteWtVars.contains(e.getVariable()) || frontier.incompleteRdVars.contains(e.getVariable()) || (notLastReads.contains(e.getVariable()) && frontier.completeVars.contains(e.getVariable())))) {
            return true;
        }
        if(e.getType().isAcquire() && (frontier.incompleteAcqs.contains(e.getLock()) || frontier.incompleteRels.contains(e.getLock()) || frontier.completeLocks.contains(e.getLock()))) {
            return true;
        }
        if(e.getType().isRelease() && (frontier.incompleteAcqs.contains(e.getLock()) || frontier.incompleteRels.contains(e.getLock()) )) {
            return true;
        }
        return false;
    }

    public String toString() {
        return (e1Thr != null ? e1Thr.toString() : "NULL") + (e1Var != null ? e1Var.toString() : "NULL") + (e1Write ? "W" : "R") + crossBlock + currentGrain.toString() + aftSet.toString() + aftSetNoE1.toString() + candidate + firstFrontier.toString() + firstFrontierNoE1.toString() + currentFrontier.toString();
    }
}

class Grain {
    public HashSet<Thread> threads;
    public HashSet<Variable> completeVars;
    public HashSet<Variable> incompleteWtVars;
    public HashSet<Variable> incompleteRdVarsLast;
    public HashSet<Variable> incompleteRdVars;
    public HashSet<Lock> completeLocks;
    public HashSet<Lock> incompleteAcqs;
    public HashSet<Lock> incompleteRels; 
   
    public Grain() {
        threads = new HashSet<>();
        completeVars = new HashSet<>();
        incompleteWtVars = new HashSet<>();
        incompleteRdVars = new HashSet<>();
        incompleteRdVarsLast = new HashSet<>();
        completeLocks = new HashSet<>();
        incompleteAcqs = new HashSet<>();
        incompleteRels = new HashSet<>();
    }

    public Grain(Grain other, boolean current) {
        threads = new HashSet<>(other.threads);
        completeVars = new HashSet<>(other.completeVars);
        incompleteWtVars = new HashSet<>(other.incompleteWtVars);
        incompleteRdVars = new HashSet<>(other.incompleteRdVars);
        incompleteRdVarsLast = current ? new HashSet<>(other.incompleteRdVarsLast) : null;
        completeLocks = new HashSet<>(other.completeLocks);
        incompleteAcqs = new HashSet<>(other.incompleteAcqs);
        incompleteRels = new HashSet<>(other.incompleteRels);
    }

    public boolean isDependentWith(Grain other) {
        for(Thread t: other.threads) {
            if(threads.contains(t)) {
                return true;
            }
        }
        for(Variable v: other.incompleteWtVars) {
            if(incompleteRdVars.contains(v) || incompleteRdVarsLast.contains(v) || incompleteWtVars.contains(v) || completeVars.contains(v)) {
                return true;
            }
        }
        for(Variable v: other.incompleteRdVars) {
            if(incompleteWtVars.contains(v) || completeVars.contains(v)) {
                return true;
            }
        }
        for(Variable v: other.completeVars) {
            if(incompleteWtVars.contains(v) || incompleteRdVars.contains(v) || incompleteRdVarsLast.contains(v)) {
                return true;
            }
        }
        for(Lock l: other.incompleteAcqs) {
            if(incompleteAcqs.contains(l) || incompleteRels.contains(l) || completeLocks.contains(l)) {
                return true;
            }
        }
        for(Lock l: other.incompleteRels) {
            if(incompleteAcqs.contains(l) || incompleteRels.contains(l) || completeLocks.contains(l)) {
                return true;
            }
        }
        for(Lock l: other.completeLocks) {
            if(incompleteAcqs.contains(l) || incompleteRels.contains(l)) {
                return true;
            }
        }
        return false;
    }

    public void updateGrain(Grain other) {
        threads.addAll(other.threads);
        completeVars.addAll(other.completeVars);
        incompleteWtVars.addAll(other.incompleteWtVars);
        incompleteRdVars.addAll(other.incompleteRdVars);
        incompleteRdVars.addAll(other.incompleteRdVarsLast);
        completeVars.removeAll(incompleteWtVars);
        // completeVars.removeAll(incompleteRdVars);
        completeLocks.addAll(other.completeLocks);
        incompleteAcqs.addAll(other.incompleteAcqs); 
        incompleteRels.addAll(other.incompleteRels);
        completeLocks.removeAll(incompleteAcqs);
        completeLocks.removeAll(incompleteRels);
    }

    public void updateGrain(GrainRaceEvent e, HashSet<Variable> notLastReads) {
        threads.add(e.getThread());
        if(e.getType().isExtremeType()) {
            threads.add(e.getTarget());
        }

        if(e.getType().isRead()) {
            if(notLastReads.contains(e.getVariable())) {
                incompleteRdVars.add(e.getVariable());
            }
            else {
                incompleteRdVarsLast.add(e.getVariable());
            }
        }
        if(e.getType().isWrite()) {
            if((incompleteRdVarsLast.contains(e.getVariable()) || !notLastReads.contains(e.getVariable())) && !incompleteWtVars.contains(e.getVariable())) {
                completeVars.add(e.getVariable());
                incompleteRdVarsLast.remove(e.getVariable());
            }
            else {
                incompleteWtVars.add(e.getVariable());
                incompleteRdVars.remove(e.getVariable());
                incompleteRdVarsLast.remove(e.getVariable());
            }
        }
        if(e.getType().isAcquire()) {
            if(incompleteRels.contains(e.getLock()) && !incompleteAcqs.contains(e.getLock())) {
                completeLocks.add(e.getLock());
                incompleteRels.remove(e.getLock());
            }
            else {
                incompleteRels.remove(e.getLock());
                incompleteAcqs.add(e.getLock());
            }
        }
        if(e.getType().isRelease()) {
            incompleteRels.add(e.getLock());
        }
    }

    private <T> String setToString(HashSet<T> set) {
        if(set == null) {
            return "null";
        }
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return  setToString(threads) + setToString(incompleteWtVars) + setToString(incompleteRdVars) + setToString(incompleteRdVarsLast) + setToString(completeVars) + setToString(completeLocks) + setToString(incompleteAcqs) + setToString(incompleteRels); 
    } 
}

class Frontier {
    public HashSet<Thread> threads;
    public HashSet<Variable> rdVars;
    public HashSet<Variable> wtVars;
    public HashSet<Lock> locks;

    public Frontier() {
        threads = new HashSet<>();
        rdVars = new HashSet<>();
        wtVars = new HashSet<>();
        locks = new HashSet<>();
    }

    public Frontier(Frontier other) {
        threads = new HashSet<>(other.threads);
        rdVars = new HashSet<>(other.rdVars);
        wtVars = new HashSet<>(other.wtVars);
        locks = new HashSet<>(other.locks);
    }

    public boolean isDependentWith(GrainRaceEvent e) {
        if(threads.contains(e.getThread())) {
            return true;
        }
        if(e.getType().isExtremeType() && threads.contains(e.getTarget())) {
            return true;
        }
        if(e.getType().isRead() && wtVars.contains(e.getVariable())) {
            return true;
        }
        if(e.getType().isWrite() && (wtVars.contains(e.getVariable()) || rdVars.contains(e.getVariable()))) {
            return true;
        }
        if(e.getType().isLockType() && locks.contains(e.getLock())) {
            return true;
        }
        return false;
    }

    public void update(GrainRaceEvent e) {
        threads.add(e.getThread());
        if(e.getType().isExtremeType()) {
            threads.add(e.getTarget());
        }
        if(e.getType().isRead()) {
            rdVars.add(e.getVariable());
        }
        if(e.getType().isWrite()) {
            wtVars.add(e.getVariable());
        }
        if(e.getType().isLockType()) {
            locks.add(e.getLock());
        }
    }

    private <T> String setToString(HashSet<T> set) {
        if(set == null) {
            return "null";
        }
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return setToString(threads) + setToString(rdVars) + setToString(wtVars) + setToString(locks);
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}