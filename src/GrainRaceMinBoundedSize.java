import cmd.CmdOptions;
import cmd.GetOptions;
import engine.racedetectionengine.grainRaceMinLocalSHB.GrainRaceEngine;
import engine.racedetectionengine.shb.SHBEngine;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;

public class GrainRaceMinBoundedSize {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        SHBEngine engine1 = new SHBEngine(options.parserType, options.path);
		engine1.analyzeTrace(options.multipleRace, 1);
        SyncPreservingRaceOfflineEngine engine2 = new SyncPreservingRaceOfflineEngine(options.parserType, options.path);
        engine2.analyzeTrace(true, 1);
        // engine.racedetectionengine.grainRaceMin.GrainRaceEngine engine3 = new engine.racedetectionengine.grainRaceMin.GrainRaceEngine(options.parserType, options.path);
        // engine3.analyzeTrace(true, 0);
        // engine.racedetectionengine.grainRaceMinLocalSHB.GrainRaceEngine engine4 = new engine.racedetectionengine.grainRaceMinLocalSHB.GrainRaceEngine(options.parserType, options.path);
        // engine4.analyzeTrace(true, 0);
        // engine.racedetectionengine.grainRaceMinLocalSyncP.GrainRaceEngine engine5 = new engine.racedetectionengine.grainRaceMinLocalSyncP.GrainRaceEngine(options.parserType, options.path);
        // engine5.analyzeTrace(true, 0);
        engine.racedetectionengine.grainRaceMinBoundedSize.GrainRaceEngine engine5 = new engine.racedetectionengine.grainRaceMinBoundedSize.GrainRaceEngine(options.parserType, options.path);
        engine5.analyzeTrace(true, 0);
        // engine.racedetectionengine.grainRaceMinNoOp.GrainRaceEngine engine6 = new engine.racedetectionengine.grainRaceMinNoOp.GrainRaceEngine(options.parserType, options.path);
        // engine6.analyzeTrace(true, 0);
    } 
}
