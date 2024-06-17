import cmd.CmdOptions;
import cmd.GetOptions;
import engine.racedetectionengine.grainRaceInfo.GrainRaceEngine;
import engine.racedetectionengine.shb.SHBEngine;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;

public class GrainRaceInfo {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        GrainRaceEngine engine = new GrainRaceEngine(options.parserType, options.path);
        engine.analyzeTrace(true, 0);
    } 
}
