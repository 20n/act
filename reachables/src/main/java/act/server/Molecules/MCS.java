package act.server.Molecules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;

import act.server.Logger;
import act.shared.AAMFailException;
import act.shared.MalFormedReactionException;
import act.shared.helpers.P;

class OLDDistanceMCS {
	private Double distance;

	public OLDDistanceMCS(MCS mcsS, MCS mcsP) {
		Set<P<MolGraph, MolGraph>> smiles = SMILES.computePairingsMolGraphs(mcsS.getMolGraphs(), mcsP.getMolGraphs());
		
		Double dist = 0.0;
		for (P<MolGraph, MolGraph> s : smiles)
			dist += getDist(s.fst(), s.snd());
		this.distance = dist/smiles.size();
	}

	private Double getDist(MolGraph a, MolGraph b) {
		// MolSimilarity.similarity returns the probability of the molecules being similar
		// we need the distance which is 1.0 - similarity_prob
		return 1.0 - MolSimilarity.similarity(MolSimilarity.Type.CorrHeavyAtomsCount, new MorS(a), new MorS(b));
	}

	public Double getDist() {
		return distance;
	}
}

class DistanceMCS {
	private Double distance;

	public DistanceMCS(MCS mcsS, MCS mcsP) {
		
		Double dist = 0.0;
		for (MolGraph g : mcsS.getMolGraphs())
			dist += getDist(g);
		for (MolGraph g : mcsP.getMolGraphs())
			dist += getDist(g);
		
		this.distance = dist;
	}

	private Double getDist(MolGraph g) {
		Double dist = 0.0;
		for (Element atom : Element.values()) {
			dist += MolSimilarity.countAtoms(g, new Atom(atom));
		}
		return 100.0 * ( 1.0/ ( 1.0 + dist ) ); // between 100.00 (no atoms common), and 0.0 (infinite number of common atoms)
	}

	public Double getDist() {
		return distance;
	}
}

public class MCS {
	private List<MolGraph> mcs;

	public MCS(List<String> smilesA, List<String> smilesB) throws AAMFailException, MalFormedReactionException {
		this.mcs = getMaxPreserved(smilesA, smilesB);
	}

	public List<MolGraph> getMolGraphs() {
		return this.mcs;
	}

	public MCS(List<List<String>> smiles) throws AAMFailException, MalFormedReactionException {

		Indigo indigo = new Indigo();
		List<MolGraph> acc;
		
		if (smiles.size() == 1) {
			acc = new ArrayList<MolGraph>();
			for (String s : smiles.get(0)) acc.add(SMILES.ToGraph(indigo, s));
		} else {
			// we do a two stage algorithm; we first compute pairwise adjacent mcs'
			// then we get these very similar mcs between consecutive pairs that can be collapsed linearly...
			//
			// Rationale is that the input graphs G1..Gn are very different from the consecutive mcs g1..gn-1;
			// but the Gi's are similar to each other more and the gi's are similar to each other
			//
			// So if we computed a running mcs directly then we would be comparing a Gi to a gi; which is 
			// expected to be very easy for the AAM computation. OTOH, computing consecutive g1..gn-1 we are already close to our ans...
			List<List<MolGraph>> gis = new ArrayList<List<MolGraph>>();
			List<String> G0 = smiles.get(0);
			for (int i = 1; i<smiles.size(); i++) {
				List<String> G1 = smiles.get(i);
				List<MolGraph> g12 = getMaxPreserved(G0, G1);
				gis.add(g12);
			}
			
			for (List<MolGraph> gl : gis) {
				Logger.printf(0,"[MCS] Pairwise MCS list = %s\n", gl);
			}
			Logger.printf(0,"[MCS]\n");
			
			// now compute the accumulated mcs
			acc = gis.get(0);
			
			/* because we diffed against G0 consistently; we can simply take the MolGraph intersection
			 * (presuming; maybe too strongly? TODO) that the mols are correctly ordered; and their permutation mapping is identical.
			 */
			for (int i = 1; i<gis.size(); i++)
				acc = presumptuous_intersect(acc, gis.get(i));
			
			/* 
			 * [ below doesn't work because molgraphs mcs' cannot be converted back to smiles.. ]
			 * for (int i = 1; i<gis.size(); i++) {
			 *  	acc = getMaxPreserved(acc, gis.get(i), indigo);
			 * }
			 */
		}
		
		this.mcs = acc;
	}

	private List<MolGraph> presumptuous_intersect(List<MolGraph> accG, List<MolGraph> newG) {
		// (presuming; maybe too strongly? TODO) that the mols are correctly ordered; and their permutation mapping is identical.
		List<MolGraph> accNew = new ArrayList<MolGraph>();
		for (int i = 0; i<accG.size(); i++)
			accNew.add(accG.get(i).intersect(newG.get(i)));
		return accNew;
	}

	@SuppressWarnings("unused")
	private List<MolGraph> getMaxPreserved(List<MolGraph> A, List<MolGraph> B, Indigo indigo) throws AAMFailException, MalFormedReactionException {
		// doesn't work because molgraphs mcs' cannot be converted back to smiles..
		List<String> smilesA = new ArrayList<String>();
		List<String> smilesB = new ArrayList<String>();
		for (MolGraph a : A) smilesA.add(SMILES.FromGraphWithoutUnknownAtoms(indigo, a));
		for (MolGraph b : B) smilesB.add(SMILES.FromGraphWithoutUnknownAtoms(indigo, b));
		return getMaxPreserved(smilesA, smilesB);
	}

	private MolGraph getMaxPreserved(String A, String B) throws AAMFailException, MalFormedReactionException {
		if (A.equals(B)) // short path...
			return SMILES.ToGraph(new Indigo(), A);
		Logger.printf(5, "[MCS] Computing MCS between\n[MCS] --- %s\n[MCS] --- %s\n", A, B);
		// String pseudoRxn = A + ">>" + B;
		List<String> aList = new ArrayList<String>(); aList.add(A);
		List<String> bList = new ArrayList<String>(); bList.add(B);
		P<List<String>, List<String>> pseudoRxn = new P<List<String>, List<String>>(aList, bList);
		MolGraph maxPreserved = SMILES.GetMaxPreserved(pseudoRxn);
		Logger.printf(5, "[MCS] MCS computed: %s\n", maxPreserved);
		return maxPreserved;
	}

	private List<MolGraph> getMaxPreserved(List<String> smilesA, List<String> smilesB) throws AAMFailException, MalFormedReactionException {
		// Here, we want to do a pairwise MCS. So we need to find the right pairing first (i.e., the maximal weight matching)
		Set<P<String, String>> pairs = SMILES.computePairingsSmiles(smilesA, smilesB);
		
		// pretend that they are substrates and products and then do an AAM;
		// then for the guys that are assigned; we have a mcs...
		List<MolGraph> mcsL = new ArrayList<MolGraph>();
		for (P<String, String> pair : pairs) {
			// those that were not paired up are not in the mcs...
			if (pair.fst() == null || pair.snd() == null)
				continue;
			MolGraph subMol = getMaxPreserved(pair.fst(), pair.snd());
			mcsL.add(subMol);
		}
		return mcsL;
	}

	public MolGraph getMCS() {
		// if (true) {System.err.println("\n\n\nneed to collapse separate molecules into a single disjoint graph...\n\n\n"); System.exit(-1);}
		// return null; // collapse this.mcs; into a single MolGraph
		MolGraph g = new MolGraph();
		for (MolGraph gg:this.mcs) g.mergeGraph(gg);
		return g;
	}

}
