import cmd.CmdOptions;
import cmd.GetOptions;
import engine.grain.GrainConcurrentEngine;

public class GrainConcurrency {
   
    public static void main(String[] args) {		
		CmdOptions options = new GetOptions(args).parse();
		GrainConcurrentEngine engine = new GrainConcurrentEngine(options.parserType, options.path, options.excludeList);
		engine.analyzeTrace();
	} 
}
