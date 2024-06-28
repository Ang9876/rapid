import java.util.HashSet;

import cmd.CmdOptions;
import cmd.GetOptions;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;

public class GrainRaceMinLocalSHB {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        SyncPreservingRaceOfflineEngine engine2 = new SyncPreservingRaceOfflineEngine(options.parserType, options.path);
        engine2.analyzeTrace(true, 1);
        HashSet<Long> syncPRacyEvents = engine2.getRacyEvents();
        engine.racedetectionengine.grainRaceMinLocalSHB.GrainRaceEngine engine3 = new engine.racedetectionengine.grainRaceMinLocalSHB.GrainRaceEngine(options.parserType, options.path, options.singleThread, options.boundedSize != -1, options.boundedSize, options.window != -1, options.window);
        engine3.analyzeTrace(true, 0);
        HashSet<Long> grainRacyEvents = (HashSet<Long>)engine3.getRacyEvents().clone();       
        grainRacyEvents.removeAll(syncPRacyEvents);
        System.out.println("New races: " + grainRacyEvents.stream().sorted().toList());
        System.out.println("New races num: " + grainRacyEvents.size());  
        HashSet<Long> grainRacyEvents1 = engine3.getRacyEvents();    
        syncPRacyEvents.removeAll(grainRacyEvents1);
        System.out.println("Missed races: " + syncPRacyEvents.stream().sorted().toList());
        System.out.println("Missed races num: " + syncPRacyEvents.size());   
    } 
}
