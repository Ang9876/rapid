package engine.grain.grainSim.grainSimV1NoOp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import engine.grain.GrainEvent;
import engine.grain.GrainState;
import event.Thread;
import event.Variable;

public class GrainSimState extends GrainState {
    
    
    ArrayList<NondetState> nondetStates;

    public GrainSimState(HashSet<Thread> tSet) {
        threadSet = tSet;
        nondetStates = new ArrayList<>();
        NondetState initState = new NondetState();
        nondetStates.add(initState);    
    }

    public boolean update(GrainEvent e) {
        ArrayList<NondetState> newStates = new ArrayList<>();
        Iterator<NondetState> iter = nondetStates.iterator();
        while(iter.hasNext()){
            NondetState state = iter.next();
            // Every grain containing e2 must start from e2
            if(e.isE2 && !state.currentGrain.threads.isEmpty()) {
                iter.remove();
                continue;
            }
            // update current grain
            if(e.getType().isRead()) {
                if(state.guessedCompleteVars.contains(e.getVariable())) {
                    iter.remove();
                    continue;
                }
                if(state.guessedIncompleteVars.contains(e.getVariable())) {
                    state.guessedIncompleteVars.remove(e.getVariable());
                }
                state.currentGrain.rdVars.add(e.getVariable());
                // See a read before any write => incomplete
                if(!state.currentGrain.wtVars.contains(e.getVariable())) {
                    state.currentGrain.incompleteVars.add(e.getVariable());
                }
            }
            if(e.getType().isWrite()) {
                if(state.guessedIncompleteVars.contains(e.getVariable())) {
                    iter.remove();
                    continue;
                }
                if(state.guessedCompleteVars.contains(e.getVariable())) {
                    state.guessedCompleteVars.remove(e.getVariable());
                }
                state.currentGrain.wtVars.add(e.getVariable());
                // Add current write var into completeVars as a candidate
                if(!state.currentGrain.incompleteVars.contains(e.getVariable())) {
                    state.currentGrain.completeVars.add(e.getVariable());
                }
            }
            state.currentGrain.threads.add(e.getThread());
            
            // Stop current grain here
            // If before e1 or after e2, do not add current grain into checking.
            if(!witnessE1 || state.containsE2) {
                continue;
            }
            
            // Make guesses on complete Vars
            ArrayList<Variable> varsCandidates = new ArrayList<>(state.currentGrain.completeVars);
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
                // Make a copy of current grain
                Grain newGrain = new Grain(state.currentGrain, completeVars);
                if(e.isE1) {
                    newGrain.e1Grain = true;
                }
                // Make a copy of the state but with a new empty current grain
                NondetState newState = new NondetState(state);
                // Add current grain into state:
                // Update afterSet
                boolean flagDependent = false;
                if(newState.afterSet.isEmpty()) {
                    newState.afterSet.add(newGrain);
                }
                else {
                    for(Grain aftGrain: newState.afterSet) {
                        if(newGrain.isDependentWith(aftGrain)) {
                            newState.afterSet.add(newGrain);
                            if(witnessE2 && !aftGrain.e1Grain) {
                                flagDependent = true;
                            }
                            break;
                        }
                    }
                }
                // if current grain contains e2 and it is dependent with a grain other than the grain containing e1, then ignore it.
                if(!witnessE2 || !flagDependent) {
                    newState.guessedCompleteVars.addAll(completeVars);
                    newState.guessedIncompleteVars.addAll(incompleteVars);
                    if(witnessE2) {
                        newState.containsE2 = true;
                    }
                    newStates.add(newState);
                }
            }
        }
        if(e.isE1) {
            // All grains stop here
            nondetStates = newStates;
        }
        else {
            nondetStates.addAll(newStates);
        }
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
        //     state.print();
        // }
    }
}

class Grain {
    public HashSet<Thread> threads;
    public HashSet<Variable> wtVars;
    public HashSet<Variable> rdVars;
    public HashSet<Variable> incompleteVars;
    public HashSet<Variable> completeVars;
    public boolean e1Grain;

    public Grain() {
        threads = new HashSet<>();
        wtVars = new HashSet<>();
        rdVars = new HashSet<>();
        incompleteVars = new HashSet<>();
        completeVars = new HashSet<>();
    }

    public Grain(Grain grain, HashSet<Variable> completeVars) {
        this.threads = new HashSet<>(grain.threads);
        this.wtVars = new HashSet<>(grain.wtVars);
        this.rdVars = new HashSet<>(grain.rdVars);
        this.completeVars = new HashSet<>(completeVars);
    }

    boolean isDependentWith(Grain other) {
        for(Thread t: this.threads) {
            if(other.threads.contains(t)) {
                return true;
            }
        }
        for(Variable v: this.wtVars) {
            if((other.wtVars.contains(v) || other.rdVars.contains(v)) && (!this.completeVars.contains(v) || !other.completeVars.contains(v))) {
                return true;
            }
        }
        for(Variable v: this.rdVars) {
            if(other.wtVars.contains(v) && (!this.completeVars.contains(v) || !other.completeVars.contains(v))) {
                return true;
            }
        }
        return false;
    }

    public void print() {
        
        System.out.print("Threads: ");
        for(Thread t: threads) {
            System.out.print(t + " ");
        }
        System.out.println();
        System.out.print("Wt: ");
        for(Variable v: wtVars) {
            System.out.print(v + " ");
        }
        System.out.println();
        System.out.print("Rd: ");
        for(Variable v: rdVars) {
            System.out.print(v + " ");
        }
        System.out.println();
        System.out.print("Complete: ");
        for(Variable v: completeVars) {
            System.out.print(v + " ");
        }
        System.out.println();
    }

}

class NondetState {
    // current grain
    public Grain currentGrain;
    boolean containsE2;
    // after set
    public HashSet<Grain> afterSet;
    public HashSet<Variable> guessedCompleteVars;
    public HashSet<Variable> guessedIncompleteVars;

    public NondetState() {
        currentGrain = new Grain();
        containsE2 = false;
        afterSet = new HashSet<>();
        guessedCompleteVars = new HashSet<>();
        guessedIncompleteVars = new HashSet<>();
    }

    public NondetState(NondetState state) {
        currentGrain = new Grain();
        afterSet = new HashSet<>(state.afterSet);
        guessedCompleteVars = new HashSet<>(state.guessedCompleteVars);
        guessedIncompleteVars = new HashSet<>(state.guessedIncompleteVars);
    }

    public void print() {
        System.out.println("State: ");
        currentGrain.print();
        System.out.println("AfterSets:");
        for(Grain grain: afterSet) {
            grain.print();
        }
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
