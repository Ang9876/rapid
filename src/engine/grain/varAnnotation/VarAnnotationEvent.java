package engine.grain.varAnnotation;

import event.Event;

public class VarAnnotationEvent extends Event {
    public int eventCounter;

    public void Handle(VarAnnotationState state) {
        if(this.type.isAcquire()) {
            state.pushLock(this);
        }
        else if(this.type.isRelease()) {
            state.updateLock(this);
        }
        else if(this.type.isRead()) {
            state.updateVar(this);
        }
        else if(this.type.isWrite()) {
            state.pushVar(this);;
        }
    }
}
