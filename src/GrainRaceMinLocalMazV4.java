import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainRaceMinLocalMazV4 {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        engine.racedetectionengine.grainRaceMinLocalMazV4.GrainRaceEngine engine3 = new engine.racedetectionengine.grainRaceMinLocalMazV4.GrainRaceEngine(options.parserType, options.path, options.singleThread, options.boundedSize != -1, options.boundedSize, options.window != -1, options.window, options.start, options.length);
        engine3.analyzeTrace(true, 0);
    } 
}
