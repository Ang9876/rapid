package engine.grain;

import engine.grain.grainConcurrent.GrainConcurrentState;
import event.Event;

public class GrainEvent extends Event {

    public boolean isE1;
    public boolean isE2;
    
    public boolean Handle(GrainState state) {
        if(isE1) {
            state.witnessE1 = true;
        }
        if(isE2) {
            state.witnessE2 = true;
        }
        return state.update(this);
    }

    public boolean HandleSubAcquire(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubRelease(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubRead(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubWrite(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubFork(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubJoin(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubBegin(GrainConcurrentState state) {
        return false;
    }

	public boolean HandleSubEnd(GrainConcurrentState state) {
        return false;
    }
}


