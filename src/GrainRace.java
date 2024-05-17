import cmd.CmdOptions;
import cmd.GetOptions;
import engine.racedetectionengine.grainRaceMin.GrainRaceEngine;
import engine.racedetectionengine.shb.SHBEngine;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;

public class GrainRace {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        SHBEngine engine1 = new SHBEngine(options.parserType, options.path);
		// engine1.analyzeTrace(options.multipleRace, 1);
        // SyncPreservingRaceOfflineEngine engine2 = new SyncPreservingRaceOfflineEngine(options.parserType, options.path);
        // engine2.analyzeTrace(true, 1);
        GrainRaceEngine engine3 = new GrainRaceEngine(options.parserType, options.path);
        engine3.analyzeTrace(true, 0);
    } 
}
