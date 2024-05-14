package engine.racedetectionengine.grainRaceBack;

import engine.racedetectionengine.RaceDetectionEvent;

public class GrainRaceEvent extends RaceDetectionEvent<GrainRaceState> {

    public long eventCount;

    public GrainRaceEvent() {
    }

    @Override
    public boolean Handle(GrainRaceState state, int verbosity) {
        eventCount -= 1;
        return state.update(this);
    }

    @Override
    public void printRaceInfoLockType(GrainRaceState state, int verbosity) {}

    @Override
    public void printRaceInfoAccessType(GrainRaceState state, int verbosity) {}

    @Override
    public void printRaceInfoExtremeType(GrainRaceState state, int verbosity) {}

    @Override
    public void printRaceInfoTransactionType(GrainRaceState state, int verbosity) {}

    @Override
    public boolean HandleSubAcquire(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubRelease(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubRead(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubWrite(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubFork(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubJoin(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubBegin(GrainRaceState state, int verbosity) {
        return false;
    }

    @Override
    public boolean HandleSubEnd(GrainRaceState state, int verbosity) {
        return false;
    }
    
}
