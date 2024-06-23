import java.util.HashSet;

import cmd.CmdOptions;
import cmd.GetOptions;
import engine.racedetectionengine.syncpreserving.SyncPreservingRaceOfflineEngine;

public class GrainRaceMinLocalSyncP {
    public static void main(String[] args) {
        CmdOptions options = new GetOptions(args).parse();
        SyncPreservingRaceOfflineEngine engine2 = new SyncPreservingRaceOfflineEngine(options.parserType, options.path);
        engine2.analyzeTrace(true, 1);
        HashSet<Long> syncPRacyEvents = engine2.getRacyEvents();
        engine.racedetectionengine.grainRaceMinLocalSyncP.GrainRaceEngine engine3 = new engine.racedetectionengine.grainRaceMinLocalSyncP.GrainRaceEngine(options.parserType, options.path, true, true, 5);
        engine3.analyzeTrace(true, 0);
        HashSet<Long> grainRacyEvents = engine3.getRacyEvents();      
        grainRacyEvents.removeAll(syncPRacyEvents);
        System.out.println("New races: " + grainRacyEvents.stream().sorted().toList());
        System.out.println("New races num: " + grainRacyEvents.size());  

    } 
}
