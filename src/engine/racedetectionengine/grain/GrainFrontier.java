package engine.racedetectionengine.grain;

import java.util.BitSet;

import event.Event;

public class GrainFrontier {
    public BitSet threadsBitSet;
    public BitSet completeVarBitSet;
    public BitSet incompleteRdVarsBitSet; 
    public BitSet incompleteWtVarsBitSet;
    public BitSet completeLocksBitSet;
    public BitSet incompleteLocksBitSet;

    public boolean isEmpty;
   
    public GrainFrontier(int numOfThreads, int numOfVars, int numOfLocks) {
        threadsBitSet = new BitSet(numOfThreads);
        completeVarBitSet = new BitSet(numOfVars);
        incompleteWtVarsBitSet = new BitSet(numOfVars);
        incompleteRdVarsBitSet = new BitSet(numOfVars);
        completeLocksBitSet = new BitSet(numOfLocks);
        incompleteLocksBitSet = new BitSet(numOfLocks);
        isEmpty = true;
    }

    public GrainFrontier(GrainFrontier other) {
        threadsBitSet = (BitSet)other.threadsBitSet.clone();
        completeVarBitSet = (BitSet)other.completeVarBitSet.clone();
        incompleteWtVarsBitSet = (BitSet)other.incompleteWtVarsBitSet.clone();
        incompleteRdVarsBitSet = (BitSet)other.incompleteRdVarsBitSet.clone();
        completeLocksBitSet = (BitSet)other.completeLocksBitSet.clone();
        incompleteLocksBitSet = (BitSet)other.incompleteLocksBitSet.clone();
        isEmpty = other.isEmpty;
    }

    public GrainFrontier(Grain other) {
        threadsBitSet = (BitSet)other.threadsBitSet.clone();
        completeVarBitSet = (BitSet)other.completeVarBitSet.clone();
        incompleteWtVarsBitSet = (BitSet)other.incompleteWtVarsBitSet.clone();
        incompleteRdVarsBitSet = (BitSet)other.incompleteRdVarsBitSet.clone();
        completeLocksBitSet = (BitSet)other.completeLocksBitSet.clone();
        incompleteLocksBitSet = (BitSet)other.incompleteAcqsBitSet.clone();
        incompleteLocksBitSet.or(other.incompleteRelsBitSet);
        isEmpty = false;
    }

    public boolean isDependentWith(Grain other) {
        return  threadsBitSet.intersects(other.threadsBitSet) || 
                incompleteWtVarsBitSet.intersects(other.incompleteWtVarsBitSet) || 
                incompleteRdVarsBitSet.intersects(other.incompleteWtVarsBitSet) || 
                completeVarBitSet.intersects(other.incompleteWtVarsBitSet) ||
                incompleteWtVarsBitSet.intersects(other.incompleteRdVarsBitSet) || 
                completeVarBitSet.intersects(other.incompleteRdVarsBitSet) ||
                incompleteWtVarsBitSet.intersects(other.completeVarBitSet) || 
                incompleteRdVarsBitSet.intersects(other.completeVarBitSet) ||
                incompleteLocksBitSet.intersects(other.incompleteAcqsBitSet) || 
                completeLocksBitSet.intersects(other.incompleteAcqsBitSet) ||
                incompleteLocksBitSet.intersects(other.incompleteRelsBitSet) || 
                completeLocksBitSet.intersects(other.incompleteRelsBitSet) ||
                incompleteLocksBitSet.intersects(other.completeLocksBitSet);
    }

    public void updateGrain(Grain other) {
        threadsBitSet.or(other.threadsBitSet);
        completeVarBitSet.or(other.completeVarBitSet);
        incompleteWtVarsBitSet.or(other.incompleteWtVarsBitSet);
        incompleteRdVarsBitSet.or(other.incompleteRdVarsBitSet);
        incompleteRdVarsBitSet.andNot(incompleteWtVarsBitSet);
        completeVarBitSet.andNot(incompleteWtVarsBitSet);
        completeVarBitSet.andNot(incompleteRdVarsBitSet);
        completeLocksBitSet.or(other.completeLocksBitSet);
        incompleteLocksBitSet.or(other.incompleteAcqsBitSet);
        incompleteLocksBitSet.or(other.incompleteRelsBitSet);
        completeLocksBitSet.andNot(incompleteLocksBitSet);
    }

    public boolean isDefDependentWith(Event e) {
        boolean po = threadsBitSet.get(e.getThread().getId()) || (e.getType().isExtremeType() && threadsBitSet.get(e.getTarget().getId()));
        boolean rf = e.getType().isRead() && incompleteWtVarsBitSet.get(e.getVariable().getId());
        boolean ww = e.getType().isWrite() && (incompleteRdVarsBitSet.get(e.getVariable().getId()) || (incompleteWtVarsBitSet.get(e.getVariable().getId())));
        boolean lock = e.getType().isLockType() && (incompleteLocksBitSet.get(e.getLock().getId()));
        return po || rf || ww || lock;
    }

    private boolean subsume(BitSet b1, BitSet b2) {
        BitSet b1Clone = (BitSet)b1.clone();
        b1Clone.andNot(b2);
        return b1Clone.isEmpty();
    }

    public boolean subsume(GrainFrontier other) {
        BitSet compPlusIncompWr = (BitSet)other.completeVarBitSet.clone();
        compPlusIncompWr.or(other.incompleteWtVarsBitSet);
        BitSet rdPlusIncompWr = (BitSet)other.incompleteRdVarsBitSet.clone();
        rdPlusIncompWr.or(other.incompleteWtVarsBitSet);
        BitSet compPlusIncompLck = (BitSet)other.completeLocksBitSet.clone();
        compPlusIncompLck.or(other.incompleteLocksBitSet);
        return  subsume(this.threadsBitSet, other.threadsBitSet) &&
                subsume(this.completeVarBitSet, compPlusIncompWr) &&
                subsume(this.incompleteRdVarsBitSet, rdPlusIncompWr) &&
                subsume(this.incompleteWtVarsBitSet, other.incompleteWtVarsBitSet) &&
                subsume(this.completeLocksBitSet, compPlusIncompLck) &&
                subsume(this.incompleteLocksBitSet, other.incompleteLocksBitSet) && 
                this.isEmpty == other.isEmpty;
    }

    public void toString(StringBuffer sb) {
        sb.append(threadsBitSet);
        sb.append(completeVarBitSet);
        sb.append(incompleteWtVarsBitSet);
        sb.append(incompleteRdVarsBitSet);
        sb.append(completeLocksBitSet);
        sb.append(incompleteLocksBitSet);
        sb.append(isEmpty);
    }   
}
