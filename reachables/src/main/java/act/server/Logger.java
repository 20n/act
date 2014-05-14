package act.server;

import java.io.PrintStream;

public class Logger {
	static private int maxImportanceToBeShown; // 0 -> showstopper; 10 -> informational; etc.. 100 -> extra junk
	static private PrintStream stream;
	
	static {
		Logger.stream = System.out;
		// default: print everthing including extra junk...
		// use setMaxImpToShow to change the verbosity level
		Logger.maxImportanceToBeShown = Integer.MAX_VALUE; 
	}
	
	public static int getMaxImpToShow() {
		return Logger.maxImportanceToBeShown;
	}
	
	public static void setMaxImpToShow(int maxImportanceToBeShown) {
		Logger.maxImportanceToBeShown = maxImportanceToBeShown;
	}
	
	public static void print(int zeroMeansVeryImportantInfIsJunk, String msg) { 
		if (zeroMeansVeryImportantInfIsJunk <= Logger.maxImportanceToBeShown)
			stream.print(msg); 
	}
	
	public static void println(int zeroMeansVeryImportantInfIsJunk, String ln) { 
		if (zeroMeansVeryImportantInfIsJunk <= Logger.maxImportanceToBeShown)
			stream.println(ln); 
	}

	public static void printf(int zeroMeansVeryImportantInfIsJunk, String format, Object ... args) {
		if (zeroMeansVeryImportantInfIsJunk <= Logger.maxImportanceToBeShown)
			stream.printf(format, args); 
	}
}
