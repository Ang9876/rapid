package engine.grain;

import event.Event;

public class GrainEvent extends Event {

    public boolean isE1;
    public boolean isE2;
    public long eventCount;
    
    public boolean Handle(GrainState state) {
        if(isE1) {
            state.witnessE1 = true;
        } 
        if(isE2) {
            state.witnessE2 = true;
        }
        boolean res = state.update(this);
        if(state.afterE1) {
            state.afterE1 = false;
        }
        if(isE1) {
            state.afterE1 = true;
        }
        return res;
    } 
}


