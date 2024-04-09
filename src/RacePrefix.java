import cmd.CmdOptions;
import cmd.GetOptions;
import engine.prefixAftSet.race.PrefixEngine;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;


public class RacePrefix {
    public static void main(String[] args) {		
        CmdOptions options = new GetOptions(args).parse();

        System.out.println("SyncPreserving:");
        long startTimeAnalysis = 0;
        startTimeAnalysis = System.currentTimeMillis(); 
        SyncPreservingRaceOfflineEngine engine = new SyncPreservingRaceOfflineEngine(options.parserType, options.path);
		engine.analyzeTrace(options.multipleRace, options.verbosity);
        long stopTimeAnalysis = System.currentTimeMillis();
        long timeAnalysis = stopTimeAnalysis - startTimeAnalysis;
        System.out.println("Time for analysis = " + timeAnalysis + " milliseconds");
        
        System.out.println();

        System.out.println("Strong Reads-from Prefix / Conflict Preserving:");
        startTimeAnalysis = 0;
        startTimeAnalysis = System.currentTimeMillis(); 
        PrefixEngine prefixEngine = new PrefixEngine(options.parserType, options.path);
        prefixEngine.analyzeTrace();
        stopTimeAnalysis = System.currentTimeMillis();
        timeAnalysis = stopTimeAnalysis - startTimeAnalysis;
        System.out.println("Time for analysis = " + timeAnalysis + " milliseconds");
        
	}
}
