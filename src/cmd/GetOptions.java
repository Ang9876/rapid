package cmd;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import parse.ParserType;

public class GetOptions {

	private static final Logger log = Logger.getLogger(GetOptions.class.getName());
	private String[] args = null;
	private Options options = new Options();

	public GetOptions(String[] args) {
		this.args = args;
		options.addOption("h", "help", false, "generate this message");
		options.addOption("f", "format", true, "format of the trace. Possible choices include rv, csv, rr, std (Default : csv) ");
		options.addOption("s", "single", false, "force the algorithm to terminate after the first race is detected");
		options.addOption("p", "path", true, "the path to the trace file/folder (Required)");
		options.addOption("v", "verbosity", true, "for setting verbosity: Allowed levels = 0, 1, 2 (Default : 0)");
        options.addOption("m", "excluded-methods", true, "path to file that lists methods to be excluded");
		options.addOption("prob", "probability", true, "probability of ignoring an event in prefix");
		options.addOption("st", "singleThreaded", false, "use singleThreaded grain optimization");
		options.addOption("bs", "boundedSize", true, "bound the size of each chosen grain");
		options.addOption("win", "window", true, "window size in dynamic analysis");
		options.addOption("stt", "start", true, "start position");
		options.addOption("len", "length", true, "processed length");
	}

	public CmdOptions parse() {
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		CmdOptions cmdOpt = new CmdOptions();;

		try {
			cmd = parser.parse(options, args);
			if (cmd.hasOption("h"))
				help();

			if (cmd.hasOption("f")) {
				cmdOpt.parserType = ParserType.getType(cmd.getOptionValue("f")) ;   
			} 

			if (cmd.hasOption("s")) {
				cmdOpt.multipleRace = false;  
			}

			if (cmd.hasOption("v")) {
				try{
					cmdOpt.verbosity = Integer.parseInt(cmd.getOptionValue("v"));
					if(cmdOpt.verbosity < 0 || cmdOpt.verbosity > 3){
						log.log(Level.INFO, "Invalid verbosity level : " + cmdOpt.verbosity);
					}
				}
				catch (NumberFormatException nfe){
					log.log(Level.INFO, "Invalid verbosity option : " + cmd.getOptionValue("v"));
				}
			}

			if (cmd.hasOption("p")) {
				cmdOpt.path = cmd.getOptionValue("p") ;   
			}
			else {
				log.log(Level.INFO, "MIssing path to file/folder");
				help();
			}
			
            if (cmd.hasOption("m")) {
                cmdOpt.excludeList = cmd.getOptionValue("m") ;   
            }

			if (cmd.hasOption("prob")) {
				cmdOpt.prob = Double.parseDouble(cmd.getOptionValue("prob"));
			}

			if (cmd.hasOption("st")) {
				cmdOpt.singleThread = true;
			}

			if (cmd.hasOption("bs")) {
				cmdOpt.boundedSize = Integer.parseInt(cmd.getOptionValue("bs"));
			}

			if (cmd.hasOption("win")) {
				cmdOpt.window = Integer.parseInt(cmd.getOptionValue("win"));
			}

			if (cmd.hasOption("start")) {
				cmdOpt.start = Long.parseLong(cmd.getOptionValue("start"));
			}

			if (cmd.hasOption("length")) {
				cmdOpt.length = Long.parseLong(cmd.getOptionValue("length"));
			}

		} catch (ParseException e) {
			help();
		}

		return cmdOpt;
	}

	private void help() {
		HelpFormatter formater = new HelpFormatter();
		formater.printHelp("RAPID", options);
		System.exit(0);
	}

	public static void main(String[] args) {
		new GetOptions(args).parse();
	}
}
