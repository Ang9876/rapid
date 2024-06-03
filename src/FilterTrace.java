import engine.accesstimes.orderedvars.OrderedVarsEngine;
import event.Event;
import parse.ParserType;
import parse.rr.ParseRoadRunner;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;

public class FilterTrace {
    public static void main(String[] args) throws Exception {
        String traceDir = args[0];
        String saveLoc = args[1];
        System.out.println(traceDir + saveLoc);
        filter(traceDir, saveLoc);
//        filter("C:\\Research\\traces\\RaceInjector\\shb_missed\\treeset\\injectedTrace97.std",
//                "C:\\Research\\traces\\RaceInjector\\shb_missed\\treeset\\injectedTrace97_filter.std");
    }

    public static OrderedVarsEngine findOrderdVars(String traceDir){
        OrderedVarsEngine orderedVarsEngine = new OrderedVarsEngine(ParserType.RR, traceDir, 0);
        orderedVarsEngine.analyzeTrace(true, 0);
        return orderedVarsEngine;
    }

    public static void filter(String traceDir, String saveLocation) throws Exception{
        OrderedVarsEngine engine = findOrderdVars(traceDir);
        HashMap<String, HashSet<String>> varToThreadSet = engine.getVariableToThreadSet();
        HashSet<String> orderedVars = engine.getOrdredVars();
        // System.out.println(orderdVars);
        HashMap<String, HashSet<String>> lockToThreadSet = engine.getLockToThreadSet();

        ParseRoadRunner rrParser = new ParseRoadRunner(traceDir, true);
        Event e = new Event();
        File wFile = new File(saveLocation);
        BufferedWriter out = new BufferedWriter(new FileWriter(wFile));
        while(rrParser.checkAndGetNext(e)) {
            if(e.getType().isAccessType() || e.getType().isExtremeType() || e.getType().isLockType()) {
                boolean flag = true;
                if(!e.getType().isExtremeType()) {
                    String var = e.getType().isAccessType() ? e.getVariable().toString() : e.getLock().toString(); 
                    if(varToThreadSet.containsKey(var) && varToThreadSet.get(var).size() == 1){
                        flag = false;
                    }
                    // if(orderedVars.contains(var)) {
                    //     flag = false;
                    // }
                    if(lockToThreadSet.containsKey(var) && lockToThreadSet.get(var).size() == 1){
                        flag = false;
                    }
                }
                if(flag) {
                    // System.out.println(e.toStandardFormat());
                    out.write(e.toStandardFormat() + "\n");
                    out.flush();
                } 
            }
            
        }
        out.close();
    }
}
