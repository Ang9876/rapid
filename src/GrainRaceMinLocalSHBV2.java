import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinLocalSHBV2 {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMinLocalSHBV2.GrainRaceEngine engine3 = new engine.racedetectionengine.grainRaceMinLocalSHBV2.GrainRaceEngine(options.parserType, options.path, options.singleThread, options.boundedSize != -1, options.boundedSize, options.window != -1, options.window);
        engine3.analyzeTrace(true, 0);
    } 
}
