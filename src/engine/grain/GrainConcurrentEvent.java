package engine.grain;

import event.Event;

public class GrainConcurrentEvent extends Event {
    
    public boolean Handle(GrainConcurrentState state) {
        return true;
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


