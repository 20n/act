package act.installer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import act.client.CommandLineRun;
import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;


public class KeggParser {
	private static Map<String, Long> keggID_ActID = new HashMap<String, Long>();
	
	public static void parseReactions(String filename, MongoDB db) {
		try{
			BufferedReader br = new BufferedReader(
					new InputStreamReader(
							new DataInputStream(new FileInputStream(filename))));
			  
			String strLine;
			int numGood = 0, numNew = 0, i = 0;
			while ((strLine = br.readLine()) != null) {
				String[] splitted = strLine.split("\\s+");
				boolean bad = false, productSide = false;

				List<Long> reactantIDs = new ArrayList<Long>();
				List<Long> productIDs = new ArrayList<Long>();
				for (String s : splitted) {
					if (s.charAt(0) == 'C' || s.charAt(0) == 'G') { //is it a chemical or glycan?
						if (!keggID_ActID.containsKey(s)) 
							bad = true;
						else if (!productSide)
							reactantIDs.add(keggID_ActID.get(s));
						else
							productIDs.add(keggID_ActID.get(s));
					} else if (s.contains("=")) {
						productSide = true;
					}
				}
				
				if (!bad) {
					numGood++;
					if (reactantIDs.isEmpty() || productIDs.isEmpty()) {
						System.out.println(strLine);
						
					} else {
						List<Long> temp = db.getRxnsWith(reactantIDs.get(0), productIDs.get(0));
						for (Long p : productIDs) {
							for (Long r : reactantIDs) {
								if (p == productIDs.get(0) && r == reactantIDs.get(0)) continue;
								temp.retainAll(db.getRxnsWith(r, p));
								if (temp.isEmpty()) break;
							}
							if (temp.isEmpty()) break;
						}
						if (temp.isEmpty()) {
							numNew++;
						}
					}
				}
				if (i % 1000 == 0) System.out.println("done " + i);
				i++;
			}
			
			System.out.println("Num Reactions: " + numGood + " New: " + numNew);
		} catch (Exception e){//Catch exception if any
			e.printStackTrace();
		}
	}
	
	public static void parseCompoundInchis(String filename, MongoDB db) {
		try{
			BufferedReader br = new BufferedReader(
					new InputStreamReader(
							new DataInputStream(new FileInputStream(filename))));
			FileWriter fstream = new FileWriter("keggCompoundsNotFound.txt");
			BufferedWriter notFound = new BufferedWriter(fstream);
			  
			String strLine;
			int i = 0;
			int numFound = 0;
			Indigo indigo = new Indigo();
			IndigoInchi indigoInchi = new IndigoInchi(indigo);
			while ((strLine = br.readLine()) != null) {
				String[] splitted = strLine.split("\\s+");
				String keggId = splitted[0];
				String inchi = splitted[1];
				inchi = CommandLineRun.consistentInChI(inchi);
				String inchiKey = indigoInchi.getInchiKey(inchi);
				Chemical c = db.getChemicalFromInChIKey(inchiKey);
				i++;
				if (c == null) {
					notFound.write(strLine + "\n");
					continue;
				}
				
				keggID_ActID.put(keggId, c.getUuid());
				numFound++;
				DBObject existing = (DBObject) c.getRef(Chemical.REFS.KEGG);
				if (existing != null) {
					BasicDBList ids = (BasicDBList) existing.get("id");
					if (!ids.contains(keggId))
						ids.add(keggId);
				} else {
					DBObject entry = new BasicDBObject();
					BasicDBList ids = new BasicDBList();
					ids.add(keggId);
					entry.put("id", ids);
					c.putRef(Chemical.REFS.KEGG, entry);
				}
				db.updateActChemical(c, c.getUuid());
				if (i % 1000 == 0) System.out.println("Done " + i);
			}
			br.close();
			notFound.close();
			System.out.println("num found " + numFound);
		} catch (Exception e){//Catch exception if any
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		MongoDB db = new MongoDB();
		//buildKeggID_ActID(db);
		//getChemicalIDsOfKeggChemicalsFromFile("data/keggEco01100.xml", db);
		//parseCompoundInchis("data/keggCompound.inchi", db);
		//parseReactions("data/keggReactionsList.txt", db);
	}
}
