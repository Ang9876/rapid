package engine.pattern.PatternTrackConsumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Consumer;

import org.javatuples.Pair;

import engine.pattern.State;
import engine.pattern.PatternTrack.VectorClockState;
import event.Thread;
import event.Event;
import event.Lock;
import event.Variable;
import util.vectorclock.VectorClock;

public class Monitor implements Consumer<Event> {
    private HashMap<Thread, Integer> processToIndex = new HashMap<>();
    public HashMap<ArrayList<Pair<Thread, Integer>> , ArrayList<VectorClock>> currentState = new HashMap<>();
    public HashMap<Integer, HashSet<Integer>> pattern = new HashMap<>();

    private int numProcesses;
    private int k;

    private HashMap<Thread, VectorClock> processClock = new HashMap<>();

    public Monitor(HashSet<Thread> tSet, ArrayList<Integer> pattern) {
        numProcesses = tSet.size();
        Iterator<Thread> itThread = tSet.iterator();
        int index = 0;
        while(itThread.hasNext()) {
            Thread thr = itThread.next();
            processToIndex.put(thr, index);
            index++;
            processClock.put(thr, emptyClock());
        }
        currentState.put(new ArrayList<>(), new ArrayList<>());
        int cnt = 0;
        for(int p : pattern) {
            if(!this.pattern.containsKey(p)) {
                this.pattern.put(p, new HashSet<Integer>());
            }
            this.pattern.get(p).add(cnt++);
        }
        this.k = pattern.size();
    }

    public void accept(Event event) {
        updateVectorClock(event);

        extendWitness(event.getLocId(), event.getThread(), new VectorClock(getThreadClock(event.getThread())));
    }

    private void updateVectorClock(Event event){
        // if(event.getType().isSend()) HandleSend(event);
        // if(event.getType().isReceive()) HandleReceive(event);
    }


    public VectorClock emptyClock() {
        return new VectorClock(numProcesses);
    }

    public VectorClock getThreadClock(Thread t) {
        return processClock.get(t);
    }

    public int getThreadIndex(Thread thread) {
        return processToIndex.get(thread);
    }

    public boolean extendWitness(int locId, Thread thread, VectorClock vc) {
        if(pattern.containsKey(locId)) {
            HashMap<ArrayList<Pair<Thread, Integer>> , ArrayList<VectorClock>> newStates = new HashMap<>();
            for (int index : pattern.get(locId)) {
                for(ArrayList<Pair<Thread, Integer>> witness: currentState.keySet()) {
                    if(witnesses(index, vc, witness, currentState.get(witness))) {
                        if(witness.size() == k - 1) {
                            return true;
                        }
                        ArrayList<Pair<Thread, Integer>> extendedWitness = new ArrayList<>();
                        extendedWitness.addAll(witness);
                        extendedWitness.add(new Pair<Thread, Integer>(thread, index));
                        ArrayList<VectorClock> extendedTimeStamps = new ArrayList<>();
                        extendedTimeStamps.addAll(currentState.get(witness));
                        extendedTimeStamps.add(vc);
                        newStates.put(extendedWitness, extendedTimeStamps);
                    }
                }
            }
            currentState.putAll(newStates);
        }
        return false;
    }

    private boolean witnesses(int index, VectorClock vc, ArrayList<Pair<Thread, Integer>> witness, ArrayList<VectorClock> timestamps) {
        Iterator<Pair<Thread, Integer>> itWitness = witness.iterator();
        Iterator<VectorClock> itTimestamp = timestamps.iterator();
        while(itWitness.hasNext()) {
            Pair<Thread, Integer> event2 = itWitness.next();
            if(event2.getValue1() == index) {
                return false;
            }
            VectorClock timeStamp = itTimestamp.next();
            if(event2.getValue1() > index &&
                timeStamp.isLessThanOrEqual(vc)) {
                    return false;
                }
        }
        return true;
    }
}

