import cmd.CmdOptions;
import cmd.GetOptions;
import engine.grain.grainSim.grainSimV1Contract.GrainSimEngine;

public class GrainSim {
    public static void main(String[] args) {		
		CmdOptions options = new GetOptions(args).parse();
		GrainSimEngine engine = new GrainSimEngine(options.parserType, options.path, options.excludeList);
		engine.analyzeTrace();
	} 
}
