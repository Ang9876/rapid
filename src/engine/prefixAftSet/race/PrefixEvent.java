package engine.prefixAftSet.race;

import event.Event;

public class PrefixEvent extends Event {

    public boolean Handle(State state) {
		return state.update(this);
	}   
}
