package engine.grain;

import event.Event;

public class GrainConcurrentEvent extends Event {

    public boolean isE1;
    public boolean isE2;
    
    public boolean Handle(GrainConcurrentState state) {
        if(isE1) {
            state.witnessE1 = true;
        }
        if(isE2) {
            state.witnessE2 = true;
        }
        state.generateStates(this);
        return state.isConcurrent();
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


