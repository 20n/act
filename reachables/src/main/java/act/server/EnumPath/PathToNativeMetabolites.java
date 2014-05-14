package act.server.EnumPath;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.server.EnumPath.ReachableChems.Color;
import act.server.Molecules.CRO;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Path;

public class PathToNativeMetabolites {
	EnumeratedGraph graph;
	Enumerator enumerator;

	int maxNewChems;
	int topKOps;
	String outFnamePrefix = "enum-rslt";
	String outDir = "enum-log";
	
	public PathToNativeMetabolites(Chemical start, List<Chemical> endingChem, int numOpsToUse, int maxNewChems, MongoDB db) {
		this.maxNewChems = maxNewChems;
		this.topKOps = numOpsToUse;
		
		OperatorSet ops = new OperatorSet(db, topKOps, true /* filter duplicates */);
		
		List<Chemical> startingChem = new ArrayList<Chemical>();
		startingChem.add(start);
		
		this.graph = new EnumeratedGraph(startingChem, ops, endingChem);
		this.enumerator = new Enumerator(graph);
	}
	
	public List<Path> getPaths(int howmany) {
		boolean haveNodesToExpand = true;
		System.out.println("Stats:\t" + this.graph.statistics().headers());
		while (this.graph.getNumPaths() < howmany) {
			System.out.println("Stats:\t" + this.graph.statistics());
			haveNodesToExpand = this.enumerator.expandOneNode();
			if (this.graph.size() > maxNewChems || !haveNodesToExpand)
				break;
		}
		List<Path> paths = this.graph.getEndToEndPaths();
		logResultsOfEnumeration(paths, haveNodesToExpand);
		return paths;
	}

	private void logResultsOfEnumeration(List<Path> paths, boolean haveNodesToExpand) {
		
		new File(this.outDir).mkdir(); // mkdir if it does not already exist...
		
		String allChemsFname = this.outDir + "/" + this.outFnamePrefix + ".chems.txt";
		String graphFname = this.outDir + "/" + this.outFnamePrefix + ".gv";
		String logFname = this.outDir + "/" + this.outFnamePrefix + ".log.txt";
		
			
		try {
			BufferedWriter logFile = new BufferedWriter(new FileWriter(logFname, false)); // open for overwrite
			
			if (paths.size() == 0) {
				logFile.write("Did not find a path in " + maxNewChems + "\n");
				if (!haveNodesToExpand) 
					logFile.write("Expanded all nodes; but still path not found.\n");
			} else {
				logFile.write("Found Paths!\n");
				logFile.write("Paths = " + paths + "\n");
			}
			
			BufferedWriter allChemsFile = new BufferedWriter(new FileWriter(allChemsFname, false)); // open for overwrite
			
			HashMap<Color, List<String>> allChems = this.graph.smilesInGraph();
			for (Color c : allChems.keySet()) {
				for (String cc : allChems.get(c)) {
					int id = this.graph.instantiatedChemsInG.get(cc).id;
					allChemsFile.write(id + "\t" + c + "\t" + cc + "\n");
				}
			}
			
			this.graph.toDOT(graphFname);
			
			logFile.close();
			allChemsFile.close();
		} catch (IOException e) {
			System.err.println("Failed to log enumeration results...");
		}
	}
}
