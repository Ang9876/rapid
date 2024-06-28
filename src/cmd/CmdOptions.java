package cmd;

import parse.ParserType;

public class CmdOptions {
	
	public ParserType parserType;
	public boolean online;
	public boolean multipleRace;
	public String path;
	public int verbosity;
	public String excludeList;
	public double prob;
	public boolean singleThread;
	public int boundedSize;
	public int window;

	public CmdOptions() {
		this.parserType = ParserType.CSV;
		this.online = true;
		this.multipleRace = true;
		this.path = null;
		this.verbosity = 0;
		this.excludeList = null;
		this.prob = 1;
		singleThread = false;
		boundedSize = -1;
		window = -1;
	}
	
	public String toString(){
		String str = "";
		str += "parserType		" + " = " + this.parserType.toString() 	+ "\n";
		str += "online			" + " = " + this.online					+ "\n";
		str += "multipleRace	" + " = " + this.multipleRace			+ "\n";	
		str += "path			" + " = " + this.path					+ "\n";
		str += "verbosity		" + " = " + this.verbosity				+ "\n";
		str += "excludeList		" + " = " + this.excludeList			+ "\n";
		return str;
	}

}
