package engine.grain.varAnnotation;

import event.Event;

public class VarAnnotationEvent extends Event {
    public long eventCounter;

    public void Handle(VarAnnotationState state) {
        if(this.type.isRead()) {
            state.updateVar(this);
        }
        else if(this.type.isWrite()) {
            state.pushVar(this);;
        }
    }
}
