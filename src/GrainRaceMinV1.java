import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinV1 {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMinV1.GrainRaceEngine engine = new engine.racedetectionengine.grainRaceMinV1.GrainRaceEngine(options.parserType, options.path, options.singleThread, options.boundedSize != -1, options.boundedSize);
        engine.analyzeTrace(true, 0);
    } 
}
