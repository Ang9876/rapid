package engine.racedetectionengine.grain;

import java.util.BitSet;

import event.Event;

public class SHBFrontier {
    public BitSet threads;
    public BitSet locks;
    public BitSet lastWtVars;
    
    public SHBFrontier(int numOfThreads, int numOfVars, int numOfLocks) {
        threads = new BitSet(numOfThreads);
        lastWtVars = new BitSet(numOfVars); 
        locks = new BitSet(numOfLocks);
    }

    public SHBFrontier(SHBFrontier other) {
        threads = (BitSet)other.threads.clone();
        lastWtVars = (BitSet)other.lastWtVars.clone();
        locks = (BitSet)other.locks.clone();
    }


    public boolean isPODependentWithE1(Event e, int e1Thread) {
        return e1Thread == e.getThread().getId() || 
                (e.getType().isExtremeType() && e1Thread == e.getTarget().getId());
    }

    public boolean isRFDependentWithE1(Event e, int e1Var, boolean e1LastWrite) {
        return e1LastWrite && e.getType().isRead() && e.getVariable().getId() == e1Var;
    }

    public boolean isRFDependentWith(Event e) {
        return e.getType().isRead() && lastWtVars.get(e.getVariable().getId());
    }

    public boolean isPOLckDependentWith(Event e) {
        if(threads.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && threads.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isLockType() && locks.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public boolean isDependentWith(Event e, int e1Thread, int e1Var, boolean e1LastWrite) {
        return isPODependentWithE1(e, e1Thread) || isRFDependentWithE1(e, e1Var, e1LastWrite) || isRFDependentWith(e) || isPOLckDependentWith(e);
    }

    public boolean isSHBSandwiched(Event e) {
        return isRFDependentWith(e) || isPOLckDependentWith(e);
    }

    public void update(Event e) {
        threads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            threads.set(e.getTarget().getId());
        }
        if(e.getType().isWrite()) {
            lastWtVars.set(e.getVariable().getId());
        }
        if(e.getType().isLockType()) {
            locks.set(e.getLock().getId());
        }
    }

    public void removeWt(int v) {
        lastWtVars.clear(v);
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(SHBFrontier other) {
        return !this.threads.isEmpty() && !other.threads.isEmpty() && subsume(this.threads, other.threads) && subsume(this.lastWtVars, other.lastWtVars) && subsume(this.locks, other.locks);
    }

    public void toString(StringBuffer sb) {
        sb.append(threads);
        sb.append(lastWtVars);
        sb.append(locks);
    }
    
}
