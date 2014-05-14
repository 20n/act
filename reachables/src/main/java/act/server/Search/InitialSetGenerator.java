package act.server.Search;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;

/**
 * Generates sets of commonly used initial compounds
 *
 */
public class InitialSetGenerator {
	
	/**
	 * Everything marked as native or cofactor.
	 * @return
	 */
	public static Set<Long> natives(MongoDB db) {
		return db.getNativeIDs();
	}
	
	/**
	 * Everything reachable with reactions applied in Ecoli 
	 * starting from natives and cofactors.
	 * @return
	 */
	public static Set<Long> ecoli(MongoDB db) {
		Set<Long> ecoliReactions = db.getReactionsBySpecies(562L);
		ecoliReactions.addAll(Reaction.reverseAllIDs(ecoliReactions));
		System.out.println("num ecoli reactions " + ecoliReactions.size());
		ecoliReactions.retainAll(ConfidenceMetric.getLegalReactionIDs(db));
		
		PathBFS bfs = new PathBFS(db, natives(db));
		bfs.setRestrictedReactions(ecoliReactions);
		bfs.initTree();
		ReactionsHypergraph<Long, Long> g = bfs.getGraph();
		System.out.println("num ecoli applied reactions " + g.getNumReactions());
		
		Set<Long> natives = natives(db);
		natives.addAll(g.getChemicals());
		return natives;
	}
	
	/**
	 * All chemicals involved in reactions observed in Ecoli
	 * starting from natives and cofactors.
	 * @return
	 */
	public static Set<Long> ecoliAll(MongoDB db) {
		Set<Long> result = new HashSet<Long>();
		Set<Long> ecoliReactions = db.getReactionsBySpecies(562L);
		for (Long e : ecoliReactions) {
			Reaction reaction = db.getReactionFromUUID(e);
			result.addAll(Arrays.asList(reaction.getSubstrates()));
			result.addAll(Arrays.asList(reaction.getProducts()));
		}
		return result;
	}
	
	public static Set<String> getKeggIDsFromFile(String filename, MongoDB db) {
		Map<String, Long> keggID_ActID = db.getKeggID_ActID(true);
		Pattern pattern = Pattern.compile("(G|C)\\d{5}");
		Set<String> foundKeggIDs = new HashSet<String>();
		Set<String> excludeKeggIDs = new HashSet<String>(); 
		
		try{
			BufferedReader br = new BufferedReader(
					new InputStreamReader(
							new DataInputStream(new FileInputStream(filename))));
			String s;
			while ((s = br.readLine()) != null) {
				Matcher matcher = pattern.matcher(s);
				while (matcher.find()) {
					String keggID = matcher.group();
					if (s.contains("#E0E0E0")) { //exclude grey ones in ecoli map
						excludeKeggIDs.add(keggID);
					}
					foundKeggIDs.add(keggID);			
				}
			}
			foundKeggIDs.removeAll(excludeKeggIDs);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return foundKeggIDs;
	}
	
	public static Set<Long> getChemicalIDsOfKeggChemicalsFromFile(String filename, MongoDB db) {
		Set<String> unknownKeggIDs = new HashSet<String>();
		return getChemicalIDsOfKeggChemicalsFromFile(filename, db, unknownKeggIDs);
	}
	
	public static Set<Long> getChemicalIDsOfKeggChemicalsFromFile(String filename, MongoDB db, Set<String> unknownKeggIDs) {
		Map<String, Long> keggID_ActID = db.getKeggID_ActID(true);
		Set<Long> result = new HashSet<Long>();
		Set<String> foundKeggIDs = getKeggIDsFromFile(filename, db);
		for (String k : foundKeggIDs) {
			if (keggID_ActID.containsKey(k))
				result.add(keggID_ActID.get(k));
			else
				unknownKeggIDs.add(k);
		}
		
		System.out.println(result.size() + " out of " + foundKeggIDs.size());
		return result;
	}
	
	
	/**
	 * For HTML visualization
	 * @throws IOException 
	 */
	public static void toHTML(MongoDB db, Set<Long> chemicalIDs, String filename) throws IOException {
		String propertyList[] = {
				
		};
		
		BufferedWriter htmlFile = new BufferedWriter(new FileWriter(filename, false)); 
		htmlFile.write("<html><head></head>\n<body>\n");
		
		htmlFile.write("<table border=\"1\">");
		htmlFile.write("<tr>");
		htmlFile.write("<th>id</th>");
		htmlFile.write("<th>structure</th>");
		htmlFile.write("<th>name</th>");
		htmlFile.write("</tr>\n");
		
		for (Long c : chemicalIDs) {
			Chemical chemical = db.getChemicalFromChemicalUUID(c);
			htmlFile.write("<tr><td>" + c + "</td>" + 
					"<td><embed src=\""+ c +"\" type=\"image/svg+xml\" /></td>" + 
					"<td>" + db.getShortestName(c) + "</td></tr>\n");
		}
		htmlFile.write("</table>\n");
		htmlFile.write("</body></html>");
		htmlFile.close();
	}

}
