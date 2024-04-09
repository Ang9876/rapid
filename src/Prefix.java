import cmd.CmdOptions;
import cmd.GetOptions;
import engine.pattern.PatternTrack.VectorClockEngine;
import engine.prefix.pattern.PrefixEngine;

public class Prefix {
    public static void main(String[] args) {		
		CmdOptions options = new GetOptions(args).parse();
		String patternFile = options.excludeList;
		VectorClockEngine engine = new VectorClockEngine(options.parserType, options.path, patternFile);
		System.out.println("Mazurkiewicz trace:");
		engine.analyzeTrace();

		PrefixEngine prefixEngine = new PrefixEngine(options.parserType, options.path, patternFile, options.prob);
		System.out.println("Strong Reads-from Prefix:");
		prefixEngine.analyzeTrace();
	}
}