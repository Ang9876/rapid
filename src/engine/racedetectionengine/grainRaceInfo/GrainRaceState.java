package engine.racedetectionengine.grainRaceInfo;

import java.util.HashSet;

import engine.racedetectionengine.State;
import event.Thread;
import event.Lock;
import event.Variable;

public class GrainRaceState extends State {
    
    public int numOfThreads = 0;
    public int numOfVars = 0;
    public int numOfLocks = 0;
    private HashSet<Thread> occurredThreads = new HashSet<>();
    private HashSet<Variable> occurredVariables = new HashSet<>();
    private HashSet<Lock> occurredLocks = new HashSet<>(); 
    private Thread lastThread = null;
    private boolean ctxSwitch = false;

    public void update(GrainRaceEvent e) {
        if(!occurredThreads.contains(e.getThread())) {
            numOfThreads += 1;
            occurredThreads.add(e.getThread());
        }
        if(e.getType().isAccessType() && !occurredVariables.contains(e.getVariable())) {
            numOfVars += 1;
            occurredVariables.add(e.getVariable());
        }
        if(e.getType().isLockType() && !occurredLocks.contains(e.getLock())) {
            numOfLocks += 1;
            occurredLocks.add(e.getLock());
        }
        if(lastThread != null && e.getThread().getId() != lastThread.getId()) {
            ctxSwitch = true;   
        }
        else {
            ctxSwitch = false;
        }
        lastThread = e.getThread();
    }

    public void printMemory() {
        System.out.println(numOfThreads + " " + numOfVars + " " + numOfLocks + " " + ctxSwitch);
    }
}