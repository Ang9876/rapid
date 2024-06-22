import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinWeak {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMinWeak.GrainRaceEngine engine = new engine.racedetectionengine.grainRaceMinWeak.GrainRaceEngine(options.parserType, options.path);
        engine.analyzeTrace(true, 0);
    } 
}
