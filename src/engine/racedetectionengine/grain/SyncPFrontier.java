package engine.racedetectionengine.grain;

import java.util.BitSet;

import event.Event;

public class SyncPFrontier {
   public BitSet missedThreads;
    public BitSet missedLastWtVars;
    public BitSet blockedLocks;
    public BitSet missedLastLocks;
    public BitSet includedThreads;
    public BitSet includedLocks;
    
    public SyncPFrontier(int numOfThreads, int numOfVars, int numOfLocks) {
        missedThreads = new BitSet(numOfThreads);
        missedLastWtVars = new BitSet(numOfVars); 
        blockedLocks = new BitSet(numOfLocks);
        missedLastLocks = new BitSet(numOfLocks);
        includedThreads = new BitSet(numOfThreads);
        includedLocks = new BitSet(numOfLocks);
    }

    public SyncPFrontier(SyncPFrontier other) {
        missedThreads = (BitSet)other.missedThreads.clone();
        missedLastWtVars = (BitSet)other.missedLastWtVars.clone();
        blockedLocks = (BitSet)other.blockedLocks.clone();
        missedLastLocks = (BitSet)other.missedLastLocks.clone();
        includedThreads = (BitSet)other.includedThreads.clone();
        includedLocks = (BitSet)other.includedLocks.clone();
    }

    public void clear() {
        missedLastLocks.clear();
        missedLastWtVars.clear();
        blockedLocks.clear();
        missedLastLocks.clear();
        includedThreads.clear();
        includedLocks.clear();
    }

    public boolean mustIgnore(Event e) {
        if(missedThreads.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && missedThreads.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isRead() && missedLastWtVars.get(e.getVariable().getId())) {
            return true;
        }
        if(e.getType().isLockType() && blockedLocks.get(e.getLock().getId())) {
            return true;
        }
        if(e.getType().isRelease() && missedLastLocks.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public void ignore(Event e) {
        missedThreads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            missedThreads.set(e.getTarget().getId());
        }
        if(e.getType().isWrite()) {
            missedLastWtVars.set(e.getVariable().getId());
        }
        if(e.getType().isRelease() && !missedLastLocks.get(e.getLock().getId())) {
            blockedLocks.set(e.getLock().getId());
        }
        if(e.getType().isAcquire()) {
            missedLastLocks.set(e.getLock().getId());
        }
    }

    public boolean threadMissed(Event e) {
        return missedThreads.get(e.getThread().getId());
    }
    // private boolean subsume(BitSet b1, BitSet b2) {
    //     BitSet b1Clone = (BitSet)b1.clone();
    //     b1Clone.andNot(b2);
    //     return b1Clone.isEmpty();
    // }

    // public boolean subsume(Frontier other) {
    //     return !this.threads.isEmpty() && !other.threads.isEmpty() && subsume(this.threads, other.threads) && subsume(this.lastWtVars, other.lastWtVars) && subsume(this.locks, other.locks);
    // }

    public void toString(StringBuffer sb) {
        sb.append(missedThreads);
        sb.append(missedLastWtVars);
        sb.append(blockedLocks);
        sb.append(missedLastLocks);
        sb.append(includedThreads);
        sb.append(includedLocks);
    } 
}
