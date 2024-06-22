import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinBoundedSingle {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMin.GrainRaceEngine engine5 = new engine.racedetectionengine.grainRaceMin.GrainRaceEngine(options.parserType, options.path, true, true, 5);
        engine5.analyzeTrace(true, 0);
    } 
}
