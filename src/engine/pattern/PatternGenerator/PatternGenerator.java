package engine.pattern.PatternGenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import event.Event;
import event.Thread;
import parse.ParserType;
import parse.rr.ParseRoadRunner;
import parse.std.ParseStandard;

public abstract class PatternGenerator {
    protected int k;
    protected String sourceFile;
    protected String patternFile;
    protected int number;

    protected ParserType parserType;
	protected ParseStandard stdParser; // STD
    protected ParseRoadRunner rrParser; // RR
	protected Event handlerEvent = new Event();

    protected ArrayList<ArrayList<String>> patterns;
    protected HashSet<Thread> threadSet;
    protected HashMap<Integer, String> idToLocationMap = null;
    protected long numOfEvents;

    protected ArrayList<Long> multiThreadedStarts = new ArrayList<>();

    ArrayList<HashMap<Thread, HashSet<Integer>>> candidatesLists = new ArrayList<>();
    HashMap<Integer, Thread> indexToThread = new HashMap<>();
    HashMap<Thread, Integer> threadToIndex = new HashMap<>();

    public PatternGenerator(ParserType pType,  String sourceFile, String patternFile, int number) {
        this.sourceFile = sourceFile;
        this.patternFile = patternFile;
        this.number = number;
        patterns = new ArrayList<>();
        this.parserType = pType;
        initParser();
        for(int i = 0; i < number; i++) {
            candidatesLists.add(new HashMap<>());
        }
    }

    protected void initParser() {
        if(this.parserType.isRR()) {
            initRRParser();
        }
        if(this.parserType.isSTD()) {
            initSTDParser();
        }
    }

    protected void initRRParser() {
        rrParser = new ParseRoadRunner(sourceFile, true);
        numOfEvents = rrParser.tot;
        threadSet = rrParser.getThreadSet();
        idToLocationMap = rrParser.idToLocationMap;
        int index = 0;
        for(Thread thread: threadSet) {
            threadToIndex.put(thread, index);
            indexToThread.put(index, thread);
            index++;
        }
    }

    protected void initSTDParser() {
        stdParser = new ParseStandard(sourceFile, true);
		threadSet = stdParser.getThreadSet();
        numOfEvents = stdParser.tot;
    }

    protected void resetParser() {
        if(this.parserType.isRR()) {
            resetRRParser();
        }
        if(this.parserType.isSTD()) {
            resetSTDParser();
        }
    }

    protected void resetSTDParser() {
        stdParser.totEvents = 0;
        try{
            stdParser.bufferedReader = new BufferedReader(new FileReader(sourceFile));
        }
        catch (FileNotFoundException ex) {
            System.out.println("Unable to open file '" + sourceFile + "'");
        }
    }

    protected void resetRRParser() {
        rrParser.totEvents = 0;
        try{
            rrParser.bufferedReader = new BufferedReader(new FileReader(sourceFile));
        }
        catch (FileNotFoundException ex) {
            System.out.println("Unable to open file '" + sourceFile + "'");
        }
    }

    public void generatePatterns() {
        resetRRParser();
        long eventCount = 0;
        HashSet<Thread> threads = new HashSet<>();
        while(rrParser.checkAndGetNext(handlerEvent)) {
            eventCount += 1;
            threads.add(handlerEvent.getThread());
            if(eventCount % 5000 == 0) {
                if(threads.size() > 1) {
                    multiThreadedStarts.add(eventCount - 5000);
                }
                threads.clear();
            }
        }
        if(threads.size() > 1) {
            multiThreadedStarts.add(eventCount < 5000 ? 0 : eventCount - 5000);
        }
        System.out.println(eventCount);
        System.out.println(multiThreadedStarts.size());
        resetRRParser();
        ArrayList<Long> starts = new ArrayList<>();
        for(int i = 0; i < number; i++) {
            Random generator = new Random();
            starts.add(multiThreadedStarts.get(generator.nextInt(multiThreadedStarts.size())));
        }
        
        long cnt = 0;
        while(rrParser.checkAndGetNext(handlerEvent)) {
            cnt++;
            for(int i = 0; i < number; i++) {
                long start = starts.get(i);
                long interval = 5000;
                if(cnt >= start && cnt < start + interval) {
                    String loc = idToLocation(handlerEvent.getLocId());
                    if(loc.length() > 0 && !loc.equals("null")) {
                        if(!candidatesLists.get(i).containsKey(handlerEvent.getThread())) {
                            candidatesLists.get(i).put(handlerEvent.getThread(), new HashSet<>());
                        }
                        candidatesLists.get(i).get(handlerEvent.getThread()).add(handlerEvent.getLocId());
                    }
                }      
            }    
        }

        for(int i = 0; i < number; i++) {
            System.out.println("start " + i);
            this.k = i >= (int)(number / 2) ? 3 : 5;
            resetParser();
            ArrayList<String> pattern = new ArrayList<>();
            pattern.add(((Long)starts.get(i)).toString());
            pattern.add(((Long)(starts.get(i) + 5000)).toString());
            this.generatePattern(pattern, candidatesLists.get(i)); 
            // while(!this.generatePattern(pattern, candidatesLists.get(i))) {
            //     resetRRParser();
            // }
            patterns.add(pattern);
            writeToFile(i);
        }
    }

    protected String idToLocation(int locId) {
        if(idToLocationMap != null) {
            return idToLocationMap.get(locId);
        }
        else {
            return String.valueOf(locId);
        }
    }

    protected abstract boolean generatePattern(ArrayList<String> pattern, HashMap<Thread, HashSet<Integer>> candidates);

    private void writeToFile(int i) {
        try {
            File myObj = new File(patternFile + i);
            System.out.println(patternFile + i);
            myObj.createNewFile();
            FileWriter myWriter = new FileWriter(patternFile + i);
            for(String loc: patterns.get(i)) {
                myWriter.write(loc + "\n");
            }
            myWriter.close();
            System.out.println("Finish Writing");
          } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
          }
    }

}
