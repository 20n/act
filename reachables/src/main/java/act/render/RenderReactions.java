package act.render;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;

public class RenderReactions {
	
	private static void renderBySmiles(P<List<String>, List<String>> rxn, String filename, String comments) {
		Indigo indigo = new Indigo();
		String rxnStr = SMILES.convertToSMILESRxn(rxn);
		SMILES.renderReaction(indigo.loadReaction(rxnStr), filename, comments, indigo);
	}
	
	public static void renderByRxnID(Long id, String filename, String comments, MongoDB db) {
		Reaction r = db.getReactionFromUUID(id);
		System.out.println(r);
		if (r == null) {
			ArrayList<String> empty = new ArrayList<String>();
			P<List<String>, List<String>> rxn = new P<List<String>, List<String>>(empty,empty);
			renderBySmiles(rxn, filename, "Reaction not found in db; id is " + id);
			return;
		}
		
		List<String> substrates = new ArrayList<String>();
		for(Long s : r.getSubstrates()) {
			Chemical c = db.getChemicalFromChemicalUUID(s);
			String smiles = c.getSmiles();
			if(smiles == null) smiles = "";
			substrates.add(smiles);
		}
		List<String> products = new ArrayList<String>();
		for(Long p : r.getProducts()) {
			Chemical c = db.getChemicalFromChemicalUUID(p);
			String smiles = c.getSmiles();
			if(smiles == null) smiles = "";
			products.add(smiles);
		}
		if(comments==null) {
			comments = r.getReactionName();
		}
		P<List<String>, List<String>> rxn = new P<List<String>, List<String>>(substrates,products);
		renderBySmiles(rxn, filename, comments) ;
		System.out.println("done rendering reaction");
	}
	
	public static void renderRxnsInCRO(Integer croID, String dir, String name, int limit, MongoDBPaths db) {
		List<Integer> rxns = db.getRxnsOfCRO(croID);
		int skip = rxns.size()/limit;
		if(skip==0) skip = 1;
		int count = 0;
		for(Integer r : rxns) {
			if(count % skip != 0) {
				count++;
				continue;
			}
			Reaction reaction = db.getReactionFromUUID(r.longValue());
			new File(dir).mkdir();
			renderByRxnID(r.longValue(),dir+"/"+name+"_rxn_"+r+".png",reaction.toString(), db);
			limit--;
			if(limit <= 0) return;
			count++;
			
		}
	}
	
	public static void renderRxnsInERO(Integer eroID, String dir, String name, int limit, MongoDBPaths db) {
		List<Integer> rxns = db.getRxnsOfERO(eroID);
		int skip = rxns.size()/limit;
		int count = 0;
		for(Integer r : rxns) {
			if(count % skip != 0) {
				count++;
				continue;
			}
			Reaction reaction = db.getReactionFromUUID(r.longValue());
			new File(dir).mkdir();
			renderByRxnID(r.longValue(),dir+"/" + name+ "_rxn_"+r + ".png",reaction.toString(), db);
			limit--;
			if(limit <= 0) return;
			count++;
			
		}
	}
}
