package act.shared;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.ActAdminServiceImpl;
import act.server.Logger;
import act.server.EnumPath.Enumerator;
import act.server.EnumPath.OperatorSet;
import act.server.EnumPath.OperatorSet.OpID;
import act.server.Molecules.CRO;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.RxnTx;
import act.server.SQLInterface.MongoDBPaths;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;

public class AugmentedReactionNetwork {
	SimplifiedReactionNetwork network;

	Set<String> cofactors;
	
	public AugmentedReactionNetwork(MongoDBPaths db, String nwNameInDB, HashMap<Integer, OperatorSet> opsByReactantNum, int numHops) {
		 
		List<Chemical> cofactorChems = db.getCofactorChemicals();
		this.cofactors = getInChis(cofactorChems);

		Logger.println(0, "Creating network of compounds with concrete reactions");
		this.network = new SimplifiedReactionNetwork(db, nwNameInDB, cofactorChems, true); //contains all default reactions

		Logger.println(0, "Reading all chemical inchi's and ensuring that in one round trip they remain.");
		int numNoInchi = 0;
		int numInchiFromSmiles = 0;
		int numNonCanonicalInchi = 0;
		Set<Long> ids = this.network.getChemicals();
		Indigo indigo = new Indigo(); IndigoInchi indigoInchi = new IndigoInchi(indigo);
		for (Long id : ids) {
			Chemical chem = this.network.getChemical(id);
			String inchi = chem.getInChI();
			//let indigo read inchi and load once to remove extra info kept in inchi string
			try {
				String inchiRoundtrip = indigoInchi.getInchi(indigoInchi.loadMolecule(inchi));
				if (!inchiRoundtrip.equals(inchi))
					numNonCanonicalInchi++;
			} catch (IndigoException e) {
				// sometimes indigo fails to load some molecules 
				// eg., Expanding node InChI=1S/C9H10NO4/c1-2-9(11)14-8-6-4-3-5-7(8)10(12)13/h3-6H,2H2,1H3,(H,12,13) in the network
				// Caused by: com.ggasoftware.indigo.IndigoException: element: can not calculate valence on N, charge 0, connectivity 4
				//System.err.format("(Inchi Passthrough) Indigo failed to load molecule\n\tMol: %s\n\tMsg: %s\n", inchi, e.getMessage());
				if (chem.getSmiles() != null) {
					//System.err.println("BIG PROBLEM: chem.smiles() != null and inchi load failed. Smiles was " + chem.getSmiles());
					// try {System.in.read();} catch (IOException e1) { }
					// If indigo thinks inchi is buggy, load it with smiles instead.
					inchi = indigoInchi.getInchi(indigo.loadMolecule(chem.getSmiles()));
					numInchiFromSmiles++;
				} else {
					numNoInchi++;
					continue;
				}
			}
		}
		System.out.println("unresolved: " + numNoInchi);
		System.out.println("bad inchi: " + numInchiFromSmiles);
		System.out.println("non canonical inchi in brenda: " + numNonCanonicalInchi);
		System.out.println("total: " + this.network.getChemicals().size());

		int count = 0; // we maintain a separate counter because e.g., there might be operators 1,2,3,5 with 4 missing, so we still need to iterate in order 1, 2, 3, 5, but stop when count = 5
		for (int numReactants = 1; ; numReactants++) {
			OperatorSet opSet = opsByReactantNum.get(numReactants);
			
			if (opSet == null)
				continue;
			
			System.out.format("Num operators of left_size(%d) = %d\n", numReactants, opSet.getAllCROs().keySet().size());
			try { System.err.println("Press any key to continue.."); System.in.read();} catch (IOException e1) { }
			

			/* Augment the network with k recursive instantiation of ops */
			List<String> worklist = new ArrayList<String>(this.network.getChemicalInchis().keySet());
			Set<String> flaggedAsExpanded = new HashSet<String>();
			for (int i=0; i<numHops; i++) // k-closure of srn 
			{
				Logger.println(0, "Expanding hop " + i);
				//debugging...
				if (numReactants > 1) { System.out.format("Skipping >1 arity operators\n"); }
				else
					worklist = applyROsToEachNode(worklist, flaggedAsExpanded, numReactants, i, opSet); // finish one worklist an return newly created chems as the new worklist
			}
			
			count++;
			if (count >= opsByReactantNum.size())
				break;
	}
		

		Logger.println(0, "Done expanding network.");
		
	}

	private Set<String> getInChis(List<Chemical> chems) {
		Set<String> inchis = new HashSet<String>();
		for (Chemical c : chems) {
			inchis.add(c.getInChI());
		}
		return inchis;
	}
	
	private Long createNode(SimplifiedReactionNetwork srn, String inchi) {
		// need to double check if the node already exists, if so just return the old ID
		// if the node does not exist then cook up an ID -- to make sure it is distinct we use NEGATIVES
		Chemical chem = srn.getChemical(inchi);
		return chem == null ? 
				srn.getMinChemicalId() - 1 // create new min chem id 
				: chem.getUuid(); // return old id
	}
	
	private List<String> applyROsToEachNode(List<String> worklist, Set<String> alreadyExpanded, int numReactantsInEachOp, int depth_is, OperatorSet ops) {
		
		Map<String, Chemical> allInchis = this.network.getChemicalInchis();
		List<String> possiblePairings = null;
		if (numReactantsInEachOp > 1)
		{
			possiblePairings = new ArrayList<String>();
			possiblePairings.addAll(this.cofactors);
			possiblePairings.addAll(allInchis.keySet()); // add all other nodes in the system as possible reactants.
		}
		
		List<String> new_worklist = new ArrayList<String>(); // we shall all newly created nodes in this phase for further processing (in the next hop)
		int positiveEdgesAdded = 0;
		int totalEdgesAdded = 0;
		P<Integer, Integer> numEdgesAdded;
		
		// we need to copy the inchis into a new map, because we add to the inchi set (from within srn)
		float totalsize = worklist.size();
		float donesize = 0;
		for (String node_inchi : worklist) {
			List<ROApplication> applied = applyROsWithoutCROs(node_inchi, ops, numReactantsInEachOp, possiblePairings);
			
			Chemical src = encapsulateInChemical(node_inchi);
			this.network.addChemicalObject(src);
			for (ROApplication application : applied) {
				
				int roid = application.roid;
				List<Chemical> chems_list = new ArrayList<Chemical>();
				for (String s : application.products) {
					Chemical c = encapsulateInChemical(s);
					chems_list.add(c);
					// only add the compounds to the worklist, if it has not already been expanded
					if (!alreadyExpanded.contains(s) && // expanded in some previous iteration
							!worklist.contains(s)) // will be expanded in this depth iteration
						new_worklist.add(s);
				}
				
				// add edge from srcID -> nID
				this.network.addChemicalObjects(chems_list);
				tellUserWeExpanded(node_inchi, application, allInchis);
				
				// flag this chemical as alreadyExpanded
				alreadyExpanded.add(node_inchi);
				
				// this.network.addEdges(createReactionObj((long)croid, src, chems_list), ReactionType.CRO);
				numEdgesAdded = this.network.addEdges(createReactionObj((long)roid, src, chems_list), application.type, application.probability);
			
				totalEdgesAdded += numEdgesAdded.fst();
				positiveEdgesAdded += numEdgesAdded.snd();
			}
			donesize++;
			System.out.format("[Depth %d] Percentage expanded: %2f\n", depth_is, (100 * (donesize/totalsize)));
		}
		
		System.out.println("\n\n\n Done expansion at depth: " + depth_is);
		System.out.println("# chemical in incoming list was " + worklist.size());
		System.out.println("# new chemicals generated are " + new_worklist.size());
		System.out.println("# of edges between chemicals in db added: " + positiveEdgesAdded);
		System.out.println("Total Edges Added: " + totalEdgesAdded);
		System.out.println("Press any key to cont.");
		try { System.in.read(); } catch (IOException e) { e.printStackTrace(); }
		
		return new_worklist;
	}

	private void tellUserWeExpanded(String node_inchi,
			ROApplication application, Map<String, Chemical> allInchis) {
		String id = allInchis.get(node_inchi) != null ? "UUID " + allInchis.get(node_inchi).getUuid() : "NewChem " + node_inchi.substring(0,20);
		Logger.println(0, id + " expanded using " + application.type + "ID " + application.roid);
		
	}

	private Chemical encapsulateInChemical(String inchi) {
		Long nID = createNode(this.network, inchi);
		Chemical chem = new Chemical(nID);
		chem.setInchi(inchi);
		return chem;
	}
	
	private Reaction createReactionObj(Long uuid, Chemical src, List<Chemical> dst) {
		Long[] substrates = new Long[1];
		substrates[0] = src.getUuid();
		Long[] products = new Long[dst.size()];
		for (int i = 0; i < dst.size(); i++)
			products[i] = dst.get(i).getUuid();
		String ecnum = "";
		String prettyname = "no name yet..."; 
		Long[] orgIDs = {};
		return new Reaction(uuid, substrates, products, ecnum, prettyname, orgIDs);
		
		/*
		if (srcID>0 && nID>0) {
			IndigoObject srcMol = indigoInchi.loadMolecule(node_inchi);
			IndigoObject dstMol = indigoInchi.loadMolecule(inchi);
			System.out.format("Adding a new edge %d -> %d\n", srcID, nID);
			if (!dstMol.canonicalSmiles().equals(srcMol.canonicalSmiles())) {
				positiveEdgesAdded+=2;
				// System.err.format("Press any key to continue;");
				// try { System.in.read(); } catch (IOException e) { e.printStackTrace(); }
			}
		} else
			System.out.format("Adding a new edge %d -> %d\n", srcID, nID);	
		*/
		
	}
	
	private List<ROApplication> applyROsWithoutCROs(String inchi, OperatorSet ops, int arity, List<String> others) {
		Indigo indigo = new Indigo();
		IndigoInchi indigoInchi = new IndigoInchi(indigo);
		List<ROApplication> newChems = new ArrayList<ROApplication>();
		
		if (arity == 1) {
			// expanding single reactant operators is trivial
			for (CRO cro : ops.getAllCROs().values())
				expandAndAddPrimary(inchi, null, cro, ops.getAllEROsFor(cro), newChems, false, indigo, indigoInchi);
			return newChems;
		}
		
		AllEnum<String> allPairs = new AllEnum<String>(others, arity-1);
		for (CRO cro : ops.getAllCROs().values()) {
			for (List<String> supporters : allPairs)
				expandAndAddPrimary(inchi, supporters, cro, ops.getAllEROsFor(cro), newChems, false, indigo, indigoInchi);
		}
		return newChems;
	}
	
	public static void expandAndAddPrimaryWithCROs(String inchi, List<String> coReactants,
			CRO cro, List<ERO> validatingEROs, List<ROApplication> newChems,
			Indigo indigo, IndigoInchi indigoInchi) {
		expandAndAddPrimary(inchi, coReactants, cro, validatingEROs, newChems, 
				true, // by default also send back CROs
				indigo, indigoInchi);
		
	}

	public static void expandAndAddPrimary(String inchi, List<String> coReactants, CRO cro, List<ERO> validatingEROs, List<ROApplication> newChems, boolean addCROs, Indigo indigo, IndigoInchi indigoInchi) {
		// expand to all possible chemicals that can result from this operator application...
		
		/* 
		 * Saurabh: apply(reactants...) should only be passed reactant lists that are in DotNotation 
		 *                because it in turn calls RxnTx.expandChemical2AllProducts which works over 
		 *                DotNotation(and really only over smiles not inchis)
		 */
		// Don't do the following:
		// *** List<String> reactants = new ArrayList<String>(); reactants.add(inchi);
		// *** if (coReactants != null) reactants.addAll(coReactants);
		// Instead do:
		List<String> reactants = ActAdminServiceImpl.getSubstrateForROAppl(inchi, indigo, indigoInchi);
		if (coReactants != null) 
			for (String co : coReactants) 
				reactants.addAll(ActAdminServiceImpl.getSubstrateForROAppl(co, indigo, indigoInchi));
		
		List<List<String>> cro_app;
		// check if cro application actually resulted in a transformation...
		// System.out.println("Checking CRO application croID:" + cro.ID());
		if ((cro_app = apply(reactants, cro, indigo, indigoInchi)) == null)
			return;
		
		boolean ero_applied = false;
		// cro applies, lets check if any ero applies...
		for (ERO ero : validatingEROs) {
			// System.out.println("CRO applies. Checking ERO application eroID:" + ero.ID());
			List<List<String>> app_on_different_parts_of_the_mol = apply(reactants, ero, indigo, indigoInchi);
			if (app_on_different_parts_of_the_mol == null)
				continue;
			double probability = computeProbabilityOfApp();
			for (List<String> app : app_on_different_parts_of_the_mol)
				newChems.add(new ROApplication(ero.ID(), probability, app, ReactionType.ERO));
		}
		
		if (addCROs && !ero_applied) {
			// cro applies but none of the EROs apply.. assign a very low probability to CRO application
			double probability = 0.01; // very low probability if only cro applied
			for (List<String> cro_a : cro_app)
				newChems.add(new ROApplication(cro.ID(), probability, cro_a, ReactionType.CRO));
		}
	}

	private static double computeProbabilityOfApp() {
		// TODO: compute the probability of node_inchi being applied.
		
		// recover all substrates/products in all reactions with ero eroid
		// compute similarity with any substrate/product (since we reverse operators too, the 
		
		return 0.8; // for debugging: give it a non-probability value so that the network prefers RO edges over concrete edges! 
		
	}
	
	private static List<List<String>> apply(List<String> reactants, RO ro, Indigo indigo, IndigoInchi indigoInchi) {
		return RxnTx.expandChemical2AllProducts(reactants, ro, indigo, indigoInchi);
	}

	public class AllEnum<T> implements Iterable<List<T>> {
		EnumIterator<List<T>> iterator;

		public AllEnum(List<T> elems, int numCols) {
			this.iterator = new EnumIterator<List<T>>(elems, numCols);
		}

		@Override
		public EnumIterator<List<T>> iterator() {
			return this.iterator;
		}
		
		public class EnumIterator<S extends List<T>> implements Iterator<List<T>> {
			List<T> rowElems;
			int cols;
			int maxRows;
			int[] indices;
			boolean hasNext;

			public EnumIterator(List<T> elems, int numCols) {
				this.cols = numCols;
				this.rowElems = elems;
				this.maxRows = elems.size();
				this.indices = new int[numCols];
				for (int i = 0; i<numCols; i++) this.indices[i] = 0;
				this.hasNext = (elems.size() > 0 && numCols > 0);					
			}

			@Override
			public boolean hasNext() {
				return this.hasNext;
			}

			@Override
			public List<T> next() {
				List<T> ret = new ArrayList<T>();
				for (int i = 0; i < this.cols; i++) 
					ret.add(this.rowElems.get(this.indices[i]));
				
				incrementIndices();

				return ret;
			}

			private void incrementIndices() {
				boolean updated = false;
				for (int i = this.cols - 1; i>=0; i--) {
					if (this.indices[i] < this.maxRows - 1) {
						this.indices[i]++; // move the rhs position up (and given that all to its right have been updated to 0, we are good, so break;
						updated = true;
						break;
					} else {
						this.indices[i] = 0; // reset the rhs digit position to 0
					}
				}
				if (!updated)
					this.hasNext = false;
			}

			@Override
			public void remove() {
				System.err.println("Calling remove on iterator!");
				System.exit(-1);
			}

		}

	}

}
