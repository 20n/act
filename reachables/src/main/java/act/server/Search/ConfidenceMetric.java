package act.server.Search;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import act.render.RenderReactions;

import act.server.Molecules.ERO;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;

public class ConfidenceMetric {
	private static Map<Long, Integer> eroClassSizes;
	public static Set<String> hardConstraints = new HashSet<String>();
	public static String org = "escherichia";
	
	/**
	 * We might know that some enzymes will work or will not work.
	 * This method is useful for manually assigning confidence in such cases.
	 */
	public static Map<Long, Integer> fixedInconfidence = new HashMap<Long, Integer>();
	public static void setInconfidence(Long reactionID, int inconfidence) {
		System.out.println("setInconfidence " + reactionID + " " + inconfidence);
		fixedInconfidence.put(reactionID, inconfidence);
	}
	
	public static int getEROClassSize(Long reactionID, MongoDB db) {
		if (eroClassSizes == null)
			return 1;
		if (eroClassSizes == null) {
			eroClassSizes = new HashMap<Long, Integer>();
			List<ERO> eros = db.getTopKERO(3000);
			for (ERO ero : eros) {
				if (ero.ID() == 0) continue;
				List<Integer> eroReactionIDs = db.getRxnsOfERO(ero.ID());
				for(Integer eroReactionID : eroReactionIDs) {
					eroClassSizes.put(eroReactionID.longValue(), eroReactionIDs.size());
				}
			}
		}
		if (reactionID < 0) reactionID = Reaction.reverseID(reactionID);
		if (!eroClassSizes.containsKey(reactionID)) {
			return 0;
		}
		
		return eroClassSizes.get(reactionID);
	}
	
	static String[] negativeExpressionPhrases = {"inclusion bodies", " folding "};
	private static boolean notContainingNegativePhrases(String n) {
		for (String s : negativeExpressionPhrases) {
			if (n.contains(s)) {
				return false;
			}
		}
		return true;
	}
	
	public static boolean isNativeEnzyme(Reaction reaction) {
		String s = reaction.getReactionName();
		return s.toLowerCase().contains(org);
	}
	
	public static int expressionCount(Reaction reaction) {
		int count = 0;

    System.err.println("act.server.Search.ConfidenceMetric: Abort.");
    System.err.println("       act.shared.Reaction format changes.");
    System.exit(-1);
    // D act.shared.Reaction data structure changed
    // D Cloning and Expression data is now embedded deeper
    // D
		// D for (Reaction.CloningExprData o : reaction.getCloningData()) {
		// D 	String n = o.notes.toLowerCase();
		// D 	if (n.contains(org))
		// D 		if (notContainingNegativePhrases(n))
		// D 			count += 1;
		// D 		else
		// D 			count -= 1;
		// D } 

		return count;
	}
	
	public static int expressionCount(Reaction reaction, Long nativeOrgID) {
		int count = 0;

    System.err.println("act.server.Search.ConfidenceMetric: Abort.");
    System.err.println("       act.shared.Reaction format changes.");
    System.exit(-1);
    // D act.shared.Reaction data structure changed
    // D Cloning and Expression data is now embedded deeper
    // D
		// D for (Reaction.CloningExprData o : reaction.getCloningData()) {
		// D 	if (o.organism.equals(nativeOrgID)) {
		// D 		String n = o.notes.toLowerCase();
		// D 		if (n.contains(org))
		// D 			if (notContainingNegativePhrases(n))
		// D 				count += 1;
		// D 			else
		// D 				count -= 1;
		// D 	}
		// D }

		return count;
	}
	
	public static int expressionScore(Reaction reaction) {
		int count = 0;
		if (isNativeEnzyme(reaction))  count += 5;
		count += expressionCount(reaction);
		return count;
	}
	
	public static int inconfidence(Reaction reaction, MongoDB db) {
		Long id = new Long(reaction.getUUID());
		if (fixedInconfidence.containsKey(id)) {
			return fixedInconfidence.get(id);
		}
		double prob = 1;
		
		//ero?
		int classSize = getEROClassSize((long) reaction.getUUID(), db);
		if (classSize == 0) {
			prob = prob * 0.7;
			if (hardConstraints.contains("ERO"))
				return 1000;
		} else {
			prob = prob * (1 - 0.1/(classSize/2.0));
		}
		
		//directionality
		if (reaction.getUUID() < 0) {
			Double energy = reaction.getEstimatedEnergy();
			int reversible = reaction.isReversible();
			if (hardConstraints.contains("reversibility") && reversible != 1) return 1000;
			if (reversible == 1 || 
					(energy != null && (20 > Math.abs(energy) || energy < 0)))
				prob = prob * 1;
			else if (reversible == 0 && energy == null)
				prob = prob * 0.5;
			else if (energy == null)
				prob = prob * 0.3;
			else 
				prob = prob * 2/Math.abs(energy);
		}
		
		//valid?
		if (!isBalanced(reaction)) {
			if (hardConstraints.contains("balance"))
				return 1000;
			boolean invalidChemical = false;
			for (Long chemicalID : reaction.getSubstrates()) {
				Chemical chemical = db.getChemicalFromChemicalUUID(chemicalID);
				if (chemical.getSmiles() == null && !chemical.isCofactor()) {
					invalidChemical = true;
				}
			}
			if (invalidChemical) {
				prob = prob * 0.1;
				if (hardConstraints.contains("invalid chemical"))
					return 1000;
			} else {
				prob = prob * 0.85; //not balanced
			}
		}
		
		//expression
		int expressionCount = expressionScore(reaction);
		if (expressionCount <= 0) {
			expressionCount = 0;
			if (hardConstraints.contains("expression"))
				return 1000;
		}
		prob = prob * (1 - (1.0/(2 + expressionCount * 2)));
		
		return (int) (1000 - 1000 * prob);
	}

	public static boolean isBalanced(Reaction reaction) {
		return !reaction.getSubstratesWCoefficients().isEmpty();
	}
	
	/**
	 * Given the hard constraints, return a set of reaction ids that can be used.
	 * @param db
	 * @return
	 */
	public static Set<Long> getLegalReactionIDs(MongoDB db) {
		List<Long> reactionIDs = db.getAllReactionUUIDs();
		Set<Long> restrictedSet = new HashSet<Long>();
		for (Long id : reactionIDs) {
			Reaction reaction = db.getReactionFromUUID(id);
			if (1000 > ConfidenceMetric.inconfidence(reaction, db)) {
				restrictedSet.add(id);
			}
			reaction.reverse();
			if (1000 > ConfidenceMetric.inconfidence(reaction, db)) {
				restrictedSet.add(Reaction.reverseID(id));
			}
		}
		return restrictedSet;
	}
	
	public static void visualizeExamples(MongoDB db) {
		String dirname = "Vis_Confidence_Examples";
		File dir = new File(dirname);
		dir.mkdir();
		
		List<Long> allReactionIDs = db.getAllReactionUUIDs();
		Random random = new Random();
		for (int i = 0; i < 50; i++) {
			Integer r = random.nextInt(allReactionIDs.size());
			Long id = r.longValue();
			Reaction reaction = db.getReactionFromUUID(id);
			Double energy = reaction.getEstimatedEnergy();
			boolean reversed = reaction.getUUID() < 0;
			String reactionInfo = "id: " + id + ", cost: " + inconfidence(reaction, db)
					+ ", expression score: " + ConfidenceMetric.expressionScore(reaction)
					+ (energy != null ? //&& reversed && reaction.isReversible() != 1 ? 
							", est. deltaG0: " + energy.intValue()
							: "")
							+ ", direction: " + (reversed ? "reversed" : "forward")
							+ ", ero class size: " + ConfidenceMetric.getEROClassSize(id, db)
							+ ", desc: " + reaction.getReactionName();
			System.out.println(id);
			RenderReactions.renderByRxnID(id, dirname + "/r" + id + ".png", reactionInfo, db);
		}
	}
	
	public static void main(String[] args) {
		MongoDB db = new MongoDB();
		getEROClassSize(-1L, db);
		System.out.println(eroClassSizes.keySet().size());
		//visualizeExamples(db);
		/*
		List<Long> reactionIDs = db.getAllReactionUUIDs();
		int total = 0;
		for (Long i : reactionIDs) {
			int inconfidence = inconfidence(db.getReactionFromUUID(-(i + 1)), db);
			System.out.println(i + " " + inconfidence);
			total += inconfidence;
		}
		System.out.println(total/reactionIDs.size());*/
	}
}
