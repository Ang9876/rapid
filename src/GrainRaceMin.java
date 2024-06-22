import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMin {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMin.GrainRaceEngine engine = new engine.racedetectionengine.grainRaceMin.GrainRaceEngine(options.parserType, options.path, false, false, 0);
        engine.analyzeTrace(true, 0);
    } 
}
