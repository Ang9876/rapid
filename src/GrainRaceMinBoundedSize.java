import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinBoundedSize {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMin.GrainRaceEngine engine = new engine.racedetectionengine.grainRaceMin.GrainRaceEngine(options.parserType, options.path, false, true, 5);
        engine.analyzeTrace(true, 0);
    } 
}
