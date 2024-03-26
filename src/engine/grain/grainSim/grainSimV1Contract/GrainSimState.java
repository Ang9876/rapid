package engine.grain.grainSim.grainSimV1Contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import engine.grain.GrainEvent;
import engine.grain.GrainState;
import event.Thread;
import event.Variable;

public class GrainSimState extends GrainState {
    
    TreeSet<NondetState> nondetStates;

    public GrainSimState(HashSet<Thread> tSet) {
        threadSet = tSet;
        nondetStates = new TreeSet<>(new StateComparator());
        NondetState initState = new NondetState();
        nondetStates.add(initState);    
    }

    public boolean update(GrainEvent e) {
        TreeSet<NondetState> newStates = new TreeSet<>(new StateComparator());
        Iterator<NondetState> iter = nondetStates.iterator();
        while(iter.hasNext()){
            // boolean singleRead = false;
            // boolean longGrain = false;
            NondetState state = iter.next();
            // Every grain containing e2 must start from e2
            if(e.isE2 && !state.threads.isEmpty()) {
                continue;
            }
            // Single read optimization (incomplete)
            // if(e.getType().isRead() && state.threads.isEmpty()) {
            //     singleRead = true;
            // }

            // if(state.threads.isEmpty()) {
            //     state.inFrontier = state.afterSetThreads.contains(e.getThread());
            // }
            // if(state.afterSetThreads.contains(e.getThread()) != state.inFrontier) {
            //     iter.remove();
            //     continue;
            // }

            // update current grain
            state.threads.add(e.getThread());

            if(e.getType().isRead()) {
                if(state.guessedCompleteVars.contains(e.getVariable())) {
                    continue;
                }
                if(state.guessedIncompleteVars.contains(e.getVariable())) {
                    state.guessedIncompleteVars.remove(e.getVariable());
                }
                // See a read before any write => incomplete
                if(!state.wtVars.contains(e.getVariable())) {
                    state.rdVars.add(e.getVariable());
                }
            }
            if(e.getType().isWrite()) {
                if(state.guessedIncompleteVars.contains(e.getVariable())) {
                    continue;
                }
                if(state.guessedCompleteVars.contains(e.getVariable())) {
                    state.guessedCompleteVars.remove(e.getVariable());
                }
                // Double writes optimization (incomplete)
                // if(state.currentGrain.wtVars.contains(e.getVariable())) {
                //     iter.remove();
                //     continue;
                // }
                // Many writes optimization (incomplete)
                // if(state.currentGrain.wtVars.size() > 1) {
                //     iter.remove();
                //     continue;
                // } 
                state.wtVars.add(e.getVariable());
            }
            state.size++;
            
            // Stop current grain here
            // If before e1 or after e2, do not add current grain into checking.
            if(!witnessE1 || state.containsE2) {
                state.hashString = state.toString();
                if(!newStates.contains(state)) {
                    newStates.add(state);
                }
                continue;
            }

            // Make guesses on complete Vars
            HashSet<Variable> varsCand = new HashSet<>(state.wtVars);
            varsCand.removeAll(state.rdVars);
            ArrayList<Variable> varsCandidates = new ArrayList<>(varsCand);
            for(long i = 0; i < (1 << varsCandidates.size()); i++) {
                HashSet<Variable> completeVars = new HashSet<>();
                HashSet<Variable> incompleteVars = new HashSet<>();
                long j = i;
                for(int offset = 0; offset < varsCandidates.size(); offset++) {
                    if(j % 2 == 1) {
                        completeVars.add(varsCandidates.get(offset));
                    }
                    else {
                        incompleteVars.add(varsCandidates.get(offset));
                    }
                    j = j >> 1;
                }
                // Make a copy of the state but with a new empty current grain
                boolean wrongWitness = false;
                boolean dependent = false;
                if(state.afterSetThreads.isEmpty() || state.isDependent(completeVars)) {
                    dependent = true;
                    if(witnessE2 && state.isDependentNoE1(completeVars)) {
                        wrongWitness = true;
                    }
                }
                // if current grain contains e2 and it is dependent with a grain other than the grain containing e1, then ignore it.
                if(!witnessE2 || !wrongWitness) {
                    NondetState newState = new NondetState(state);
                    if(dependent) {
                        newState.addAfterSet(state, completeVars, incompleteVars, e.isE1);
                    }
                    newState.hashString = newState.toString();
                    if(witnessE2) {
                        newState.containsE2 = true;
                    }
                    if(!newStates.contains(newState)) {
                        newStates.add(newState);
                    }
                }
            }
            if(!e.isE1) {
                state.hashString = state.toString();
                if(!newStates.contains(state)) {
                    newStates.add(state);
                }
            }
        }
        nondetStates = newStates;
        if(!witnessE1) {
            nondetStates.add(new NondetState());
        }
        return isConcurrent();
    }

    private boolean isConcurrent() {
        if(!witnessE2) {
            return false;
        }
        for(NondetState state: nondetStates) {
            // Contains e2 and all guessings are correct
            if(state.containsE2 && state.guessedCompleteVars.isEmpty() && state.guessedIncompleteVars.isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    public boolean finalCheck() {
        for(NondetState state: nondetStates) {
            if(state.containsE2 && state.guessedIncompleteVars.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void printMemory() {
        System.out.println(nondetStates.size());
        // for(NondetState state: nondetStates) {
        //     System.out.println(state.hashString);
        // }
    }
}

class NondetState {
    // current grain
    public HashSet<Thread> threads;
    public HashSet<Variable> wtVars;
    public HashSet<Variable> rdVars;
    public int size;
    boolean containsE2;
    public HashSet<Variable> singleWtVars;
    // after set
    public HashSet<Thread> afterSetThreads;
    public HashSet<Variable> afterSetRdVars;
    public HashSet<Variable> afterSetWtVars;
    public HashSet<Thread> afterSetThreadsNoE1;
    public HashSet<Variable> afterSetRdVarsNoE1;
    public HashSet<Variable> afterSetWtVarsNoE1;
    public HashSet<Variable> guessedCompleteVars;
    public HashSet<Variable> guessedIncompleteVars;
    public String hashString;

    public NondetState() {
        threads = new HashSet<>();
        wtVars = new HashSet<>();
        rdVars = new HashSet<>();
        size = 0;
        containsE2 = false;
        singleWtVars = new HashSet<>();
        afterSetThreads = new HashSet<>();
        afterSetRdVars = new HashSet<>();
        afterSetWtVars = new HashSet<>();
        afterSetThreadsNoE1 = new HashSet<>();
        afterSetRdVarsNoE1 = new HashSet<>();
        afterSetWtVarsNoE1 = new HashSet<>();
        guessedCompleteVars = new HashSet<>();
        guessedIncompleteVars = new HashSet<>();
        hashString = toString();
    }

    public NondetState(NondetState state) {
        this.threads = new HashSet<>();
        this.wtVars = new HashSet<>();
        this.rdVars = new HashSet<>();
        this.size = state.size;
        singleWtVars = new HashSet<>();
        afterSetThreads = new HashSet<>(state.afterSetThreads);
        afterSetRdVars = new HashSet<>(state.afterSetRdVars);
        afterSetWtVars = new HashSet<>(state.afterSetWtVars);
        afterSetThreadsNoE1 = new HashSet<>(state.afterSetThreadsNoE1);
        afterSetRdVarsNoE1 = new HashSet<>(state.afterSetRdVarsNoE1);
        afterSetWtVarsNoE1 = new HashSet<>(state.afterSetWtVarsNoE1);
        guessedCompleteVars = new HashSet<>(state.guessedCompleteVars);
        guessedIncompleteVars = new HashSet<>(state.guessedIncompleteVars);
    }

    boolean isDependent(HashSet<Variable> completeVars) {
        for(Thread t: this.threads) {
            if(afterSetThreads.contains(t)) {
                return true;
            }
        }
        for(Variable v: this.wtVars) {
            if(!completeVars.contains(v) && (afterSetWtVars.contains(v) || afterSetRdVars.contains(v))) {
                return true;
            }
        }
        for(Variable v: this.rdVars) {
            if(afterSetWtVars.contains(v)) {
                return true;
            }
        }
        return false;
    }

    boolean isDependentNoE1(HashSet<Variable> completeVars) {
        for(Thread t: this.threads) {
            if(afterSetThreadsNoE1.contains(t)) {
                return true;
            }
        }
        for(Variable v: this.wtVars) {
            if(!completeVars.contains(v) && (afterSetWtVarsNoE1.contains(v) || afterSetRdVarsNoE1.contains(v))) {
                return true;
            }
        }
        for(Variable v: this.rdVars) {
            if(afterSetWtVarsNoE1.contains(v)) {
                return true;
            }
        }
        return false;
    }

    void addAfterSet(NondetState state, HashSet<Variable> completeVars, HashSet<Variable> incompleteVars, boolean isE1) {
        afterSetThreads.addAll(state.threads);
        afterSetRdVars.addAll(state.rdVars);
        HashSet<Variable> wtvars = new HashSet<>(state.wtVars);
        wtvars.removeAll(completeVars);
        afterSetWtVars.addAll(wtvars);
        afterSetRdVars.removeAll(afterSetWtVars);
        if(!isE1) {
            afterSetThreadsNoE1.addAll(state.threads);
            afterSetRdVarsNoE1.addAll(state.rdVars);
            afterSetWtVarsNoE1.addAll(wtvars); 
            afterSetRdVarsNoE1.removeAll(afterSetWtVarsNoE1);
        }
        guessedCompleteVars.addAll(completeVars);
        guessedIncompleteVars.addAll(incompleteVars);
    }

    private <T> String setToString(HashSet<T> set) {
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return  setToString(threads) + setToString(wtVars) + setToString(rdVars) + 
                setToString(afterSetThreads) + setToString(afterSetRdVars) + setToString(afterSetWtVars) +
                setToString(afterSetThreadsNoE1) + setToString(afterSetRdVarsNoE1) + setToString(afterSetWtVarsNoE1) +  
                setToString(guessedCompleteVars) + setToString(guessedIncompleteVars);
    }

    public void print() {
        System.out.println("State: ");
        System.out.println("AfterSets:");
        System.out.print("GuessedComplete: ");
        for(Variable v: guessedCompleteVars) {
            System.out.print(v + " ");
        }
        System.out.println();
        System.out.print("GuessedIncomplete: ");
        for(Variable v: guessedIncompleteVars) {
            System.out.print(v + " ");
        }
        System.out.println();
        System.out.println("ContainsE2: " + containsE2);
    }
}

class StateComparator implements Comparator<NondetState> {
    public int compare(NondetState s1, NondetState s2) {
        return s1.hashString.compareTo(s2.hashString);
    }
}