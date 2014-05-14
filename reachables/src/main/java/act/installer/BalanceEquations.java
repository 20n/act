package act.installer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import act.server.Logger;
import act.server.SQLInterface.MongoDB;
import act.server.Search.Counter;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

/**
 * Pretty simple balancing method but hacky:
 * 	- if solution found
 * 		- guaranteed to be correct (balanced)
 * 		- may involve adding common omitted atoms/cofactors
 * 	- else 
 * 		- solution may still exist 
 * 	- should replace with more robust balancer (use an ILP solver?)
 * @author paul
 *
 */
public class BalanceEquations {
	private static final boolean PRINT_DETAILS = false;
	static int numFailed;
	static int numSuccess;
	static int numBadMolecule;
	
	private static Map<Long, Counter<String>> moleculeCache = new HashMap<Long, Counter<String>>();
	
	private static Counter<String> getMolecule(Long id, MongoDB db) {
		Indigo indigo = new Indigo();
		IndigoInchi indigoInchi = new IndigoInchi(indigo);
		Counter<String> molecule = moleculeCache.get(id);
		if (molecule == null) {
			molecule = new Counter<String>();
			Chemical c = db.getChemicalFromChemicalUUID(id);
			String inchi = c.getInChI();
			IndigoObject io;
			try {
				io = indigoInchi.loadMolecule(inchi);
			} catch (Exception e) {
				return null;
			}
			
			for (IndigoObject atom : io.iterateAtoms()) {
				molecule.inc(atom.symbol());
			}
			molecule.inc("H", io.countImplicitHydrogens());
			moleculeCache.put(id,  molecule);
		}
		return molecule;
	}
	
	public static void balanceIfNot(Reaction reaction, MongoDB db) {
		if (!isBalanced(reaction, db)) {
			
		}
	}
	
	public static boolean isBalanced(Reaction reaction, MongoDB db) {
		Counter<String> imbalance = getImbalance(reaction, db);
		//System.out.println(imbalance);
		return imbalance != null && imbalance.getAbsTotal() == 0;
	}
	
	public static Counter<String> getImbalance(Reaction reaction, MongoDB db) {
		return getImbalance(reaction, db, false);
	}
	
	public static Counter<String> getImbalance(Reaction reaction, MongoDB db, boolean useCoeffOneIfNull) {
		Counter<String> imbalance = new Counter<String>();
		Long[] ss = reaction.getSubstrates();
		for (Long s : ss) {
			Counter<String> molecule = getMolecule(s, db);
			if (molecule == null) {
				return null;
			}
			molecule =  molecule.clone();
			Integer c = reaction.getSubstrateCoefficient(s);
			if (c == null) {
				if (useCoeffOneIfNull)
					c = 1;
				else
					return null;
			}
			if (c != 1) molecule.scale(c);
			imbalance.subBy(molecule);
		}
		Long[] ps = reaction.getProducts();
		for (Long p : ps) {
			Counter<String> molecule = getMolecule(p, db);
			if (molecule == null) {
				return null;
			}
			molecule =  molecule.clone();
			Integer c = reaction.getProductCoefficient(p);
			if (c == null) {
				if (useCoeffOneIfNull)
					c = 1;
				else
					return null;
			}
			if (c != 1) molecule.scale(c);
			imbalance.addBy(molecule);
		}
		return imbalance;
	}
	
	public static P<Counter<Long>, Counter<Long>> quickBalance(Reaction reaction, MongoDB db, 
			Map<Long, Counter<String>> extraChemicalPossibilities, Counter<String> initImbalance) {
		Map<Long, Counter<String>> reactantOptions = new HashMap<Long, Counter<String>>();
		Map<Long, Counter<String>> productOptions = new HashMap<Long, Counter<String>>();
		Counter<Long> reactantChoices = new Counter<Long>();
		Counter<Long> productChoices = new Counter<Long>();
		
		Counter<String> currImbalance = new Counter<String>();
		
		if (initImbalance == null) 
			initImbalance = new Counter<String>();
		
		Long[] ss = reaction.getSubstrates();
		for (Long s : ss) {
			Counter<String> molecule = getMolecule(s, db);
			if (molecule == null) {
				if (extraChemicalPossibilities == null)
					numBadMolecule++;
				return null;
			}
			if (reactantOptions.get(s) == null) {
				reactantOptions.put(s, molecule);
				reactantChoices.inc(s);
				currImbalance.subBy(molecule);
				initImbalance.subBy(molecule);
			}
			//System.out.println("S " + molecule);
		}
		Long[] ps = reaction.getProducts();
		for (Long p : ps) {
			Counter<String> molecule = getMolecule(p, db);
			if (molecule == null) {
				if (extraChemicalPossibilities == null)
					numBadMolecule++;
				return null;
			}
			if (productOptions.get(p) == null) {
				productOptions.put(p, molecule);
				productChoices.inc(p);
				currImbalance.addBy(molecule);
				initImbalance.addBy(molecule);
			}
			//System.out.println("P " + molecule);
		}
		if (extraChemicalPossibilities != null) {
			for (Long id : extraChemicalPossibilities.keySet()) {
				reactantOptions.put(id, extraChemicalPossibilities.get(id));
				productOptions.put(id, extraChemicalPossibilities.get(id));
			}
		}
		
		boolean failure = false;
		boolean success = false;
		while (!failure && !success) {
			//System.out.println("curr imbalance");
			//System.out.println(currImbalance);
			success = true;
			//pick element to balance
			String element = null;
			int minImbalance = 1000;
			for (String e : currImbalance.keySet()) {
				int imbalance = currImbalance.get(e);
				if (imbalance == 0) continue;
				//pick molecule that's closest to imbalance to add
				if (Math.abs(imbalance) < minImbalance) {
					element = e;
					minImbalance = Math.abs(imbalance);
				}
				success = false;
			}
			if (element != null) {
				Map<Long, Counter<String>> options = productOptions;
				Counter<Long> choices = productChoices;
				int imbalance = currImbalance.get(element);
				if (imbalance > 0) {
					options = reactantOptions;
					choices = reactantChoices;
				}
				Long bestID = null;
				int bestDiff = 10000;
				int bestMolSize = 10000;
				for (Long id : options.keySet()) {
					Integer amount = options.get(id).get(element);
					if (imbalance > 0) amount = -amount;
					if (amount == 0) continue;
					int diff = Math.abs(amount + imbalance);
					if (PRINT_DETAILS)
						System.out.println(diff + " " + imbalance + " " + amount);
					//if (diff > Math.abs(imbalance)) continue; //not helping element balance
					Counter<String> improved;
					//System.out.println(options.get(id));
					if (imbalance > 0)
						improved = options.get(id).sub(currImbalance);
					else
						improved = options.get(id).add(currImbalance);
					diff = improved.getAbsTotal();
					if (PRINT_DETAILS) {
						System.out.println("total diff " + diff);
						System.out.println(options.get(id));
					}
					int molSize = options.get(id).getAbsTotal();
					if (diff < bestDiff || (diff == bestDiff && molSize < bestMolSize)) {
						bestID = id;
						bestDiff = diff;
						bestMolSize = molSize;
					}
				}
				if (PRINT_DETAILS) {
					System.out.println("currImbalance " + currImbalance);
					System.out.println("bestDiff" + bestDiff);
				}
				if (bestID == null) {
					failure = true;
					if (extraChemicalPossibilities != null) {
						if (PRINT_DETAILS) {
							System.out.println("No element on one side: " + element + " ");
							printFormula(reactantOptions, productOptions);
						}
					}
					
					/*
					for (Long product : productOptions.keySet()) {
						System.out.println("p: " + productOptions.get(product));
					}
					for (Long reactant : reactantOptions.keySet()) {
						System.out.println("r: " + reactantOptions.get(reactant));
					}
					System.out.println(currImbalance);
					*/
					break;
				}
				choices.inc(bestID);
				if (imbalance > 0)
					currImbalance.subBy(options.get(bestID));
				else
					currImbalance.addBy(options.get(bestID));
				if (choices.get(bestID) > 20) {
					failure = true;
					if (extraChemicalPossibilities != null && PRINT_DETAILS) {
						System.out.print("Giving up ");
						printFormula(reactantOptions, productOptions);
						System.out.println(initImbalance);
					}
					/*
					System.out.println(reactantChoices);
					System.out.println(productChoices);
					System.out.println(currImbalance);
					printFormula(reactantOptions, productOptions);*/

					break;
				}
			}
		}
		if (!failure) {
			if(!verifyReduced(reactantChoices, productChoices)) {
				System.err.println("not most reduced but repaired");
				System.out.println(reactantChoices);
				System.out.println(productChoices);
			} 


				/*
			boolean print = false;
			System.out.println("success");
			for (Long id : reactantChoices.keySet()) {
				if (reactantChoices.get(id) > 1) {
					print = true;
				}
			}
			if (print) {
				System.out.println(reactantChoices);
				System.out.println(productChoices);

				for (Long reactant : reactantOptions.keySet()) {
					System.out.println("r: " + reactantOptions.get(reactant));
				}
				for (Long product : productOptions.keySet()) {
					System.out.println("p: " + productOptions.get(product));
				}
			}
				 */
			return new P<Counter<Long>, Counter<Long>>(reactantChoices, productChoices);
			
		}
		return null;
	}

	private static void printFormula(
			Map<Long, Counter<String>> reactantOptions,
			Map<Long, Counter<String>> productOptions) {
		for (Long reactant : reactantOptions.keySet()) {
			Counter<String> reactantCounter = reactantOptions.get(reactant);
			for (String elem : reactantCounter.keySet()) {
				System.out.print(elem + reactantCounter.get(elem));
			}
			System.out.print(" + ");
		}
		System.out.print(" -> ");
		for (Long product : productOptions.keySet()) {
			Counter<String> productCounter = productOptions.get(product);
			for (String elem : productCounter.keySet()) {
				System.out.print(elem + productCounter.get(elem));
			}
			System.out.print(" + ");
		}
		System.out.println();
	}
	
	public static boolean verifyReduced(Counter<Long> reactantCoeff, Counter<Long> productCoeff) {
		List<BigInteger> coefficients = new ArrayList<BigInteger>();
		for (Long a : reactantCoeff.keySet()) 
			coefficients.add(BigInteger.valueOf(reactantCoeff.get(a)));
		for (Long b : productCoeff.keySet()) 
			coefficients.add(BigInteger.valueOf(productCoeff.get(b)));
		BigInteger one = BigInteger.valueOf(1);
		BigInteger gcd = coefficients.get(0);
		for (BigInteger a : coefficients) {
			gcd = gcd.gcd(a);
		}
		
		if (gcd.intValue() != 1) {
			System.out.println(gcd.intValue());
			for (Long r : reactantCoeff.keySet()) {
				reactantCoeff.put(r, reactantCoeff.get(r)/gcd.intValue());
			}
			for (Long p : productCoeff.keySet()) {
				productCoeff.put(p, productCoeff.get(p)/gcd.intValue());
			}
		}
		
		return one.equals(gcd);
	}
	
	/**
	 * Balances and updates all reactions in between lowID and highID (excluding those).
	 * Balances all if they are null.
	 * 
	 * @param db
	 * @param rebalance - whether to rebalance a reaction that is already balanced
	 */
	public static void balanceAll(MongoDB db, boolean rebalance, Long lowID, Long highID) {
		numSuccess = 0;
		numFailed = 0;
		numBadMolecule = 0;
		Logger.setMaxImpToShow(-1);
		int timedout = 0;
		
		Map<Long, Counter<String>> extras = new HashMap<Long, Counter<String>>();
		Counter<String> molH = new Counter<String>();
		molH.inc("H");
		extras.put(14107L, molH);
		Counter<String> molPO4 = new Counter<String>();
		molPO4.inc("P", 1);
		molPO4.inc("O", 4);
		extras.put(14042L, molPO4);
		Counter<String> molSO4 = new Counter<String>();
		molSO4.inc("S", 1);
		molSO4.inc("O", 4);
		extras.put(14025L, molSO4);
		Counter<String> molCO2 = new Counter<String>();
		molCO2.inc("C", 1);
		molCO2.inc("O", 2);
		extras.put(13985L, molCO2);
		Counter<String> molH2O = new Counter<String>();
		molH2O.inc("H", 2);
		molH2O.inc("O", 1);
		extras.put(28248L, molH2O);
		Counter<String> molO2 = new Counter<String>();
		molO2.inc("O", 2);
		extras.put(14095L, molO2);
		
		
		List<Long> reactionIDs = db.getAllReactionUUIDs();
		Collections.sort(reactionIDs);
		for (Long rid : reactionIDs) {
			if (lowID != null && rid < lowID) continue;
			if (highID != null && rid > highID) continue;
			//if (rid >= 41853L) continue;
			
			//if (!rid.equals(new Long(10))) continue;
			Reaction reaction = db.getReactionFromUUID(rid);

			//if (!reaction.getReactionName().contains("R00068")) continue;
			//System.out.println("Check " + rid);
			if (!rebalance && isBalanced(reaction, db)) continue;
			//System.out.println("Rebalance " + rid);
			Counter<String> initImbalance = new Counter<String>();
			P<Counter<Long>, Counter<Long>> result = quickBalance(reaction, db, null, initImbalance);
			//System.out.println(initImbalance);
			if (result == null) {
				//try rebalancing with extras
				Map<Long, Counter<String>> pickedExtras = new HashMap<Long, Counter<String>>();
				
				if (initImbalance.keySet().contains("H")) 
					pickedExtras.put(14107L, extras.get(14107L));
				
				if (initImbalance.keySet().contains("P")) 
					pickedExtras.put(14042L, extras.get(14042L));
				//else if (initImbalance.keySet().contains("S")) 
					//pickedExtras.put(14025L, extras.get(14025L));
				else if (initImbalance.keySet().contains("C"))
					pickedExtras.put(13985L, extras.get(13985L));
				else if (initImbalance.keySet().contains("O")) {
					if (initImbalance.keySet().contains("H"))
						pickedExtras.put(28248L, extras.get(28248L));
					else
						pickedExtras.put(14095L, extras.get(14095L));
				}
				
				result = quickBalance(reaction, db, pickedExtras, new Counter<String>());
				if (result == null) {
					//System.out.println(reaction.getReactionName());
				}
				//System.out.println("Success after adding" + reaction.getUUID() + " " + result.fst());
			}
			// First clear out any old coefficients
			for (Long s: reaction.getSubstratesWCoefficients()) {
				reaction.setSubstrateCoefficient(s, null);
			}
			for (Long p: reaction.getProductsWCoefficients()) {
				reaction.setProductCoefficient(p, null);
			}
			
			if (result != null) {
				Counter<Long> substrates = result.fst();
				Counter<Long> products = result.snd();
				for (Long s: substrates.keySet()) {
					if (substrates.get(s) == 0) continue;
					reaction.setSubstrateCoefficient(s, substrates.get(s));
				}
				for (Long p: products.keySet()) {
					if (products.get(p) == 0) continue;
					reaction.setProductCoefficient(p, products.get(p));
				}	
				numSuccess++;
			} else {
				numFailed++;
				System.out.println("Failed reaction id: " + rid);
			}
			
			db.updateStoichiometry(reaction);
			if ((numSuccess + numFailed) % 1000 == 0) 
				System.out.println("Successfully completed " + numSuccess);
		}
		
		System.out.println("failed to balance: " + numFailed);
		System.out.println("timedout: " + timedout);
		System.out.println("skipped due to bad molecule: " + numBadMolecule);
		System.out.println("success: " + numSuccess);	
	}
	
	public static void main(String[] args) {
		MongoDB db = new MongoDB();
		//System.out.println(getImbalance(db.getReactionFromUUID(48728L), db, true));
		balanceAll(db, true, null, 41852L);
		balanceAll(db, false, 41851L, null);
		/*
		Counter<String> initImbalance = new Counter<String>();
		Map<Long, Counter<String>> extras = new HashMap<Long, Counter<String>>();
		Counter<String> molH = new Counter<String>();
		molH.inc("H");
		extras.put(-1L, molH);
		Counter<String> molPO4 = new Counter<String>();
		molPO4.inc("P", 1);
		molPO4.inc("O", 4);
		extras.put(-2L, molPO4);
		Counter<String> molSO4 = new Counter<String>();
		molSO4.inc("S", 1);
		molSO4.inc("O", 4);
		extras.put(-3L, molSO4);
		Counter<String> molCO2 = new Counter<String>();
		molCO2.inc("C", 1);
		molCO2.inc("O", 2);
		extras.put(-4L, molCO2);
		Counter<String> molH2O = new Counter<String>();
		molH2O.inc("H", 2);
		molH2O.inc("O", 1);
		extras.put(-5L, molH2O);
		Counter<String> molO2 = new Counter<String>();
		molO2.inc("H", 2);
		molO2.inc("O", 1);
		extras.put(-6L, molO2);

		quickBalance(db.getReactionFromUUID(21637L), db, null, initImbalance);
		System.out.println(initImbalance);
		Map<Long, Counter<String>> pickedExtras = new HashMap<Long, Counter<String>>();
		
		if (initImbalance.keySet().contains("H"))
			pickedExtras.put(-1L, extras.get(-1L));
		
		if (initImbalance.keySet().contains("P")) 
			pickedExtras.put(-2L, extras.get(-2L));
		else if (initImbalance.keySet().contains("S")) 
			pickedExtras.put(-3L, extras.get(-3L));
		else if (initImbalance.keySet().contains("O"))
			pickedExtras.put(-6L, extras.get(-6L));
		System.out.println("Use heuristics");
		quickBalance(db.getReactionFromUUID(21637L), db, pickedExtras, initImbalance);
		*/
			
	}
}
