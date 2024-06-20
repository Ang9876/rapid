package engine.racedetectionengine.grain;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;

import event.Event;
import event.Variable;

public class Grain {
    public BitSet threadsBitSet;
    public BitSet completeVarBitSet;
    public BitSet incompleteWtVarsBitSet;
    public BitSet incompleteRdVarsBitSet;
    public BitSet completeLocksBitSet;
    public BitSet incompleteAcqsBitSet;
    public BitSet incompleteRelsBitSet;

    public int firstWrite;
    public int firstLock;
    public boolean isSingleton;
    public boolean isComplete;
   
    public Grain(int numOfThreads, int numOfVars, int numOfLocks) {
        threadsBitSet = new BitSet(numOfThreads);
        completeVarBitSet = new BitSet(numOfVars);
        incompleteWtVarsBitSet = new BitSet(numOfVars);
        incompleteRdVarsBitSet = new BitSet(numOfVars);
        completeLocksBitSet = new BitSet(numOfLocks);
        incompleteAcqsBitSet = new BitSet(numOfLocks);
        incompleteRelsBitSet = new BitSet(numOfLocks); 

        firstWrite = -1;
        firstLock = -1;
        isSingleton = false;
        isComplete = false;
    }

    public Grain(Grain other) {
        threadsBitSet = (BitSet)other.threadsBitSet.clone();
        completeVarBitSet = (BitSet)other.completeVarBitSet.clone();
        incompleteWtVarsBitSet = (BitSet)other.incompleteWtVarsBitSet.clone();
        incompleteRdVarsBitSet = (BitSet)other.incompleteRdVarsBitSet.clone();
        completeLocksBitSet = (BitSet)other.completeLocksBitSet.clone();
        incompleteAcqsBitSet = (BitSet)other.incompleteAcqsBitSet.clone();
        incompleteRelsBitSet = (BitSet)other.incompleteRelsBitSet.clone();
        firstWrite = other.firstWrite;
        firstLock = other.firstLock;
        isSingleton = other.isSingleton;
        isComplete = other.isComplete;
    }

    public void updateGrain(Event e, HashMap<Variable, HashSet<Long>> lastReads, long eventCount) {
        threadsBitSet.set(e.getThread().getId());
        if(e.getType().isExtremeType()) {
            threadsBitSet.set(e.getTarget().getId());
        }

        if(e.getType().isRead()) {
            if(!incompleteWtVarsBitSet.get(e.getVariable().getId())) {
                incompleteRdVarsBitSet.set(e.getVariable().getId());
            }
            else if(lastReads.get(e.getVariable()).contains(eventCount)) {
                incompleteWtVarsBitSet.clear(e.getVariable().getId());
                completeVarBitSet.set(e.getVariable().getId());
            }
        }
        if(e.getType().isWrite()) {
            if(!lastReads.get(e.getVariable()).contains(eventCount)) {
                incompleteWtVarsBitSet.set(e.getVariable().getId());
            }
            else {
                completeVarBitSet.set(e.getVariable().getId());
            }
        }
        if(e.getType().isAcquire()) {
            incompleteAcqsBitSet.set(e.getLock().getId());
        }
        if(e.getType().isRelease()) {
            if(incompleteAcqsBitSet.get(e.getLock().getId())) {
                incompleteAcqsBitSet.clear(e.getLock().getId());
                if(!incompleteRelsBitSet.get(e.getLock().getId())) {
                    completeLocksBitSet.set(e.getLock().getId());
                }
            }
            else {
                incompleteRelsBitSet.set(e.getLock().getId());
            }
        }
    }

    public boolean isDefDependentWith(Event e) {
        boolean po = threadsBitSet.get(e.getThread().getId()) || (e.getType().isExtremeType() && threadsBitSet.get(e.getTarget().getId()));
        boolean rf = e.getType().isRead() && incompleteWtVarsBitSet.get(e.getVariable().getId());
        boolean ww = e.getType().isWrite() && (incompleteRdVarsBitSet.get(e.getVariable().getId()) || (incompleteWtVarsBitSet.get(e.getVariable().getId())));
        boolean lock = e.getType().isLockType() && (incompleteAcqsBitSet.get(e.getLock().getId()) || incompleteRelsBitSet.get(e.getLock().getId()));
        return po || rf || ww || lock;
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(Grain other) {
        BitSet compPlusIncompWr = (BitSet)other.completeVarBitSet.clone();
        compPlusIncompWr.or(other.incompleteWtVarsBitSet);
        BitSet rdPlusIncompWr = (BitSet)other.incompleteRdVarsBitSet.clone();
        rdPlusIncompWr.or(other.incompleteWtVarsBitSet);
        BitSet compPlusIncompLck = (BitSet)other.completeLocksBitSet.clone();
        compPlusIncompLck.or(other.incompleteAcqsBitSet);
        compPlusIncompLck.or(other.incompleteRelsBitSet);
        return  subsume(this.threadsBitSet, other.threadsBitSet) &&
                subsume(this.completeVarBitSet, compPlusIncompWr) &&
                subsume(this.incompleteRdVarsBitSet, rdPlusIncompWr) &&
                this.incompleteWtVarsBitSet.equals(other.incompleteWtVarsBitSet) &&
                subsume(this.completeLocksBitSet, compPlusIncompLck) &&
                this.incompleteAcqsBitSet.equals(other.incompleteAcqsBitSet) &&
                subsume(this.incompleteRelsBitSet, other.incompleteRelsBitSet) &&
                this.isComplete == other.isComplete &&
                this.isSingleton == other.isSingleton &&
                this.firstLock == other.firstLock &&
                this.firstWrite == other.firstWrite;
        // return this.toString().equals(other.toString());
    }

    public String toString() {
        return threadsBitSet.toString() + completeVarBitSet.toString() + incompleteWtVarsBitSet.toString() + incompleteRdVarsBitSet.toString() + completeLocksBitSet.toString() + incompleteAcqsBitSet.toString() + incompleteRelsBitSet.toString() + firstWrite + firstLock + isComplete + isSingleton;
    }  
}
