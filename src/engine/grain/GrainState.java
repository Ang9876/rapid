package engine.grain;

import java.util.HashSet;

import event.Thread;
import engine.pattern.State;

public abstract class GrainState extends State {
    
    protected HashSet<Thread> threadSet;

    public boolean witnessE1;
    public boolean witnessE2;

    public abstract boolean update(GrainEvent e);
    
    public abstract boolean finalCheck();

    public void printMemory() {}
}
