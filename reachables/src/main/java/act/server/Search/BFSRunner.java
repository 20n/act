package act.server.Search;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.VariousSearchers;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.ReactionDetailed;

public class BFSRunner {
	
	/**
	 * The first argument are options.
	 *  f for file input
	 *  r for including reverse reactions
	 *  
	 * 
	 * The second argument is the target name or InChI 
	 * or a file of targets if the options contain 'f'.
	 * 
	 * The output is paths (list of reactions) for each target in text form.
	 * 
	 */
	public static void main(String[] args) {
		MongoDB db = new MongoDB();
		HashSet<Long> metabolites = VariousSearchers.chemicalsToIDs(db.getNativeMetaboliteChems());
		//temporarily adding ero expanded
		/*try {
			FileInputStream fstream = new FileInputStream("tempIDs.txt");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			while ((strLine = br.readLine()) != null)   {
			//	metabolites.add(Long.parseLong(strLine));
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
		
		PathBFS pathFinder = new PathBFS(db, metabolites);
		String options = args[0];
		Set<String> targetStrings = new HashSet<String>();
		Map<Long, String> targets = new HashMap<Long, String>();
		if (options.contains("f")) {
			try {
				FileInputStream fstream = new FileInputStream(args[1]);
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				String strLine;
				while ((strLine = br.readLine()) != null)   {
					targetStrings.add(strLine);
				}
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			targetStrings.add(args[1]);
		}
		
		
		
		for (String targetString : targetStrings) {
			if (targetString.startsWith("InChI=")) {
				Chemical chem = db.getChemicalFromInChI(targetString);	
				if (chem != null) {
					targets.put(chem.getUuid(), targetString);
				}
			} else if (targetString.startsWith("id=")) {
				String idString = targetString.substring(3);
				Long id = Long.parseLong(idString);
				targets.put(id, targetString);
			} else {
				long id = db.getChemicalIDFromName(targetString);
				if (id > -1) {
					targets.put(id, targetString);
				} else {
					System.out.println("Cannot find " + targetString);
				}
			}
		}
		
		if (options.contains("r")) {
			pathFinder.setReverse(true);
		}
		
		printPaths(db, pathFinder, targets);
	}

	public static void printPaths(MongoDB db, PathBFS pathFinder,
			Map<Long, String> targets) {
		pathFinder.initTree();
		for (Long target : targets.keySet()) {
			if (target == null) return;
			
			Chemical chem = db.getChemicalFromChemicalUUID(target);
			
			System.out.println("\n\nTarget: " + chem.getShortestName() + " " + chem.getInChI() +
					", id: " + chem.getUuid() + ", requested: " + targets.get(target));
			List<List<ReactionDetailed>> paths = pathFinder.getPaths(chem);
			int pathNum = 0;
			for (List<ReactionDetailed> path : paths) {
				if (pathNum > 5) break;
				int step = 0;
				System.out.println("Path " + pathNum + ": ");
				pathNum++;
				//List<ReactionDetailed> path = paths.get(0);
				for(ReactionDetailed reaction : path) {
					step++;
					System.out.println("\nStep " + step + "\n" + reaction);
					Long[] substrateIDs = reaction.getSubstrates();
					for (int i = 0; i < substrateIDs.length; i++) {
						System.out.println(
								substrateIDs[i] + ", " +
										db.getShortestName(substrateIDs[i]) +
										", " + db.getChemicalFromChemicalUUID(substrateIDs[i]).getSmiles() +
										", " + db.getChemicalFromChemicalUUID(substrateIDs[i]).getInChI()
								);
					}
					Long[] productIDs = reaction.getProducts();
					for (int i = 0; i < productIDs.length; i++) {
						System.out.println(
								productIDs[i] + ", " +
										db.getShortestName(productIDs[i]) +
										", " + db.getChemicalFromChemicalUUID(productIDs[i]).getSmiles() +
										", " + db.getChemicalFromChemicalUUID(productIDs[i]).getInChI()
						);
					}
				}
				
				System.out.println();
			} 
			
			if (paths.size() == 0) {
				System.out.println("No paths found.");
			}
		}
	}
}
