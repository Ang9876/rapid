import cmd.CmdOptions;
import cmd.GetOptions;

public class GrainSim {
    public static void main(String[] args) {		
		CmdOptions options = new GetOptions(args).parse();
		// engine.grain.grainSim.grainSimV1Var.GrainSimEngine engine1 = new engine.grain.grainSim.grainSimV1Var.GrainSimEngine(options.parserType, options.path, options.excludeList);
		// engine1.analyzeTrace();
		// engine.grain.grainSim.grainSimV1VarContract2.GrainSimEngine engine2 = new engine.grain.grainSim.grainSimV1VarContract2.GrainSimEngine(options.parserType, options.path, options.excludeList);
		// engine2.analyzeTrace();
		// engine.grain.grainSim.grainSimV1VarMinimal.GrainSimEngine engine3 = new engine.grain.grainSim.grainSimV1VarMinimal.GrainSimEngine(options.parserType, options.path, options.excludeList);
		// engine3.analyzeTrace();
		engine.grain.grainSim.grainSimV1VarContract2Mini.GrainSimEngine engine4 = new engine.grain.grainSim.grainSimV1VarContract2Mini.GrainSimEngine(options.parserType, options.path, options.excludeList);
		engine4.analyzeTrace();
	} 
}
