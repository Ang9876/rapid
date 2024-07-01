package engine.racedetectionengine.grain;

import java.util.BitSet;

import event.Event;

public class MazFrontier {
    public BitSet threads;
    public BitSet rdVars;
    public BitSet wtVars;
    public BitSet locks;

    public MazFrontier(int numOfThreads, int numOfVars, int numOfLocks) {
        threads = new BitSet(numOfThreads);
        rdVars = new BitSet(numOfVars);
        wtVars = new BitSet(numOfVars); 
        locks = new BitSet(numOfLocks);
    }

    public MazFrontier(MazFrontier other) {
        threads = (BitSet)other.threads.clone();
        rdVars = (BitSet)other.rdVars.clone();
        wtVars = (BitSet)other.wtVars.clone();
        locks = (BitSet)other.locks.clone();
    }

    public boolean isDependentWith(Event e) {
        if(threads.get(e.getThread().getId())) {
            return true;
        }
        if(e.getType().isExtremeType() && threads.get(e.getTarget().getId())) {
            return true;
        }
        if(e.getType().isRead() && wtVars.get(e.getVariable().getId())) {
            return true;
        }
        if(e.getType().isWrite() && (wtVars.get(e.getVariable().getId()) || rdVars.get(e.getVariable().getId()))) {
            return true;
        }
        if(e.getType().isLockType() && locks.get(e.getLock().getId())) {
            return true;
        }
        return false;
    }

    public void update(Event e) {
        threads.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            threads.set(e.getTarget().getId());
        }
        if(e.getType().isRead()) {
            rdVars.set(e.getVariable().getId());
        }
        if(e.getType().isWrite()) {
            wtVars.set(e.getVariable().getId());
        }
        if(e.getType().isLockType()) {
            locks.set(e.getLock().getId());
        }
    }

    public void union(MazFrontier other) {
        threads.or(other.threads);
        rdVars.or(other.rdVars);
        wtVars.or(other.wtVars);
        locks.or(other.locks);
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(MazFrontier other) {
        return !this.threads.isEmpty() && !other.threads.isEmpty() && subsume(this.threads, other.threads) && subsume(this.wtVars, other.wtVars) && subsume(this.rdVars, other.rdVars) && subsume(this.locks, other.locks);
    }

    public void toString(StringBuffer sb) {
        sb.append(threads);
        sb.append(wtVars);
        sb.append(rdVars);
        sb.append(locks);
    }

    public String toString(){
        StringBuffer sb = new StringBuffer();
        sb.append(threads);
        sb.append(wtVars);
        sb.append(rdVars);
        sb.append(locks);
        return sb.toString();
    }
}