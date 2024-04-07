package engine.prefixAftSet.race;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

import event.Lock;
import event.Thread;
import event.Variable;

public class State {
    public TreeMap<DependentInfo, Long> states = new TreeMap<>(new DependentInfoComparator());
    public int numOfThreads;
    public int raceCnt = 0;
    public boolean racy = false;
    public long timestamp;
    private long lifetime;
    public HashSet<Integer> racyLocs = new HashSet<>();

    public State(int numOfThreads, long lifetime) {
        states.put(new DependentInfo(), (long) 0);
        this.numOfThreads = numOfThreads;
        this.lifetime = lifetime;
    };

    public boolean update(PrefixEvent e) {
        TreeMap<DependentInfo, Long> newStates = new TreeMap<>(new DependentInfoComparator());
        boolean matched = false;
        
        timestamp++;


        for(Iterator<DependentInfo> iterator = states.keySet().iterator(); iterator.hasNext();){
            DependentInfo dep = iterator.next(); 
            long birth = states.get(dep);
            if(lifetime > 0 && birth > 0 && timestamp - birth >= lifetime) {
                continue;
            }
            
            if(!racy && dep.candVar != null && e.getType().isAccessType() && !dep.threads.contains(e.getThread())) {
                racy = (e.getVariable() == dep.candVar) && (e.getType().isWrite() || dep.isWriteCandidate);
            }
            
            if(mustIgnore(e, dep)) {
                ignore(e, dep);
            }
            else {
                // Two Choices:
                // I. Ignore event e:
                // [Optimisation] Only proactively ignore an event, if
                // 1. It is read/write and there is no primary var in the state
                // 2. It is an acquire event and there is already a primary var
                if((e.getType().isAccessType() && dep.candVar == null) || (e.getType().isAcquire() && dep.candVar != null)) {
                    DependentInfo depCopied = new DependentInfo(dep);
                    if(e.getType().isAccessType()) {
                        depCopied.candVar = e.getVariable();
                        depCopied.isWriteCandidate = e.getType().isWrite();
                    }
                    ignore(e, depCopied);
                    depCopied.hashString = depCopied.toString();
                    // Birth time can be improved.
                    if(depCopied.threads.size() != numOfThreads) {
                        if(newStates.containsKey(depCopied)) {
                            long birthDup = newStates.get(depCopied);
                            if(timestamp > birthDup) {
                                newStates.put(depCopied, timestamp);
                            }
                        }
                        else {
                            newStates.put(depCopied, timestamp);
                        }
                    }
                }

                // II. Keep event e:
                if(e.getType().isWrite()) {
                    dep.wtVars.remove(e.getVariable());
                }
                if(e.getType().isAcquire()) {
                    dep.openLocks.add(e.getLock());
                }
                if(e.getType().isRelease()) {
                    dep.openLocks.remove(e.getLock());
                }   
            }
            dep.hashString = dep.toString();
            if(dep.threads.size() != numOfThreads) {
                if(newStates.containsKey(dep)) {
                    long birthDup = newStates.get(dep);
                    if(birth > birthDup) {
                        newStates.put(dep, birth);
                    }
                }
                else {
                    newStates.put(dep, birth);
                }
            }
            iterator.remove();
        }
        states = newStates;
        if(racy) {
            raceCnt++;
            matched = true;
        }
        racy = false;
        return matched;
    }

    private boolean mustIgnore(PrefixEvent e, DependentInfo dep){
        return  (dep.threads.contains(e.getThread())) ||
                (e.getType().isAcquire() && dep.openLocks.contains(e.getLock())) ||
                (e.getType().isRead() && dep.wtVars.contains(e.getVariable())) ||
                (e.getType().isJoin() && dep.threads.contains(e.getTarget()));
	}

    private void ignore(PrefixEvent e, DependentInfo dep) {
        dep.threads.add(e.getThread());
        if(e.getType().isWrite()) {
            dep.wtVars.add(e.getVariable());
        }
        if(e.getType().isFork()) {
            dep.threads.add(e.getTarget());
        }
    }

    public void printMemory() {
    }
}

class DependentInfo {
    public HashSet<Thread> threads;
    public HashSet<Variable> wtVars;
    public HashSet<Lock> openLocks;

    public Variable candVar;
    public boolean isWriteCandidate;

    public String hashString;

    public DependentInfo() {
        threads = new HashSet<>();
        wtVars = new HashSet<>();
        openLocks = new HashSet<>();
        candVar = null;
        isWriteCandidate = false;
        hashString = toString();
    }

    public DependentInfo(DependentInfo other) {
        threads = new HashSet<>(other.threads);
        wtVars = new HashSet<>(other.wtVars);
        openLocks = new HashSet<>(other.openLocks);
        candVar = other.candVar;
        isWriteCandidate = other.isWriteCandidate;
    }

    public void addWriteCandidate(Variable var) {
        candVar = var;
        isWriteCandidate = false;
    }

    public boolean checkWriteCandidate(Variable var) {
        return candVar != null && var.getId() == candVar.getId();
    }

    public void addReadCandidate(Variable var) {
        candVar = var;
        isWriteCandidate = true;
    }

    public boolean checkReadCandidate(Variable var) {
        return candVar != null && var.getId() == candVar.getId() && !isWriteCandidate;
    }

    private <T> String setToString(HashSet<T> set) {
        return set.stream().map(x -> x.toString()).sorted().toList().toString();
    }

    public String toString() {
        return  setToString(threads) + setToString(wtVars) + setToString(openLocks) + 
                (candVar == null ? "null" : candVar.toString()) + (isWriteCandidate ? "W" : "R");
    }

}

class DependentInfoComparator implements Comparator<DependentInfo> {
    public int compare(DependentInfo o1, DependentInfo o2) {
        return o1.hashString.compareTo(o2.hashString);
    }
}
