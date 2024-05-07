import cmd.CmdOptions;
import cmd.GetOptions;
import engine.racedetectionengine.grainRaceBack.GrainRaceEngine;
import engine.racedetectionengine.shb.SHBEngine;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;

public class GrainRace {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        SHBEngine engine1 = new SHBEngine(options.parserType, options.path);
		engine1.analyzeTrace(options.multipleRace, 1);
        // SyncPreservingRaceOfflineEngine engine2 = new SyncPreservingRaceOfflineEngine(options.parserType, options.path);
        // engine2.analyzeTrace(true, 1);
        String revPath = options.path.split("\\.")[0] + "_rev.std";
        // engine.racedetectionengine.grainRaceBack.GrainRaceEngine engine = new engine.racedetectionengine.grainRaceBack.GrainRaceEngine(options.parserType, revPath);
        // engine.analyzeTrace(true, 0);
        engine.racedetectionengine.grainRaceBackOp.GrainRaceEngine engine3 = new engine.racedetectionengine.grainRaceBackOp.GrainRaceEngine(options.parserType, revPath);
        engine3.analyzeTrace(true, 0);
        // engine.racedetectionengine.grainRaceBackMaz.GrainRaceEngine engine4 = new engine.racedetectionengine.grainRaceBackMaz.GrainRaceEngine(options.parserType, revPath);
        // engine4.analyzeTrace(true, 0);
    } 
}
