import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinNoOp {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMinNoOp.GrainRaceEngine engine = new engine.racedetectionengine.grainRaceMinNoOp.GrainRaceEngine(options.parserType, options.path);
        engine.analyzeTrace(true, 0);
    } 
}
