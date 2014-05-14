package act.server.EnumPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoRenderer;

import act.graph.Node;
import act.server.Logger;
import act.server.Molecules.CRO;
import act.server.Molecules.RxnTx;
import act.server.Molecules.RO;
import act.server.Molecules.RxnWithWildCards;
import act.server.Molecules.SMILES;
import act.shared.Chemical;
import act.shared.helpers.P;

public class Enumerator {
	
	EnumeratedGraph G;
	
	public Enumerator(EnumeratedGraph G) {
		this.G = G;
	}

	@Deprecated // expandChemicalUsingOperator calls RxnTx.naiveApplyOpOneProduct: should not (instead should be expandChemical2AllProducts), but we need to test first
	public boolean expandOneNode() {
		Node<ReachableChems> originalNode = this.G.pickNextChemToExpandFrom();
		if (originalNode == null)
			return false; // exhausted all possibilities of expanding chemicals....
	
		Chemical c = originalNode.atom.getChem();
		// query the OperatorSet for the next usable operator...
		P<CRO, OperatorSet.OpID> op = originalNode.atom.operatorBasis.getNext(originalNode.atom.alreadyAppliedOps);
		if (op == null) { System.err.println("There should be no exhausted chem in the worklist. What happened here?"); System.exit(-1); }
		
		// expand to all possible chemicals that can result from this operator application...
		List<Chemical> cNext = expandChemicalUsingOperator(c, op.fst());
		
		// which operator did we use..
		OperatorSet.OpID defaultOpSet = op.snd();
		
		 // check if operator application actually resulted in a transformation...
		if (cNext != null) {
			// add the edge to the graph, and create a new chemical node (if it doesn't already exist there)
			for (Chemical cNextChem : cNext)
				this.G.addTransformation(cNextChem, originalNode, defaultOpSet);
		} else {
			// inform the graph that this operator does not apply on this node; i.e., the transformation application leaves the chemical unchanged...
			this.G.noTransformation(originalNode, defaultOpSet);
		}
		
		// add the operator that was applied to the list of used operators...
		this.G.updateWorklist(originalNode, defaultOpSet);
		
		return true; // there are more nodes left to expand...
	}

	@Deprecated // this should not call RxnTx.naiveApplyOpOneProduct as that is naive and not a true possible matching into the molecule
	private List<Chemical> expandChemicalUsingOperator(Chemical c, RO ro) {
		List<Chemical> newChems = new ArrayList<Chemical>();
		String smiles = c.getSmiles();
		for (String s : RxnTx.naiveApplyOpOneProduct(smiles, ro)) {
			if (s.equals(smiles)) continue;
			newChems.add(new Chemical(-1 /* uuid */, -1L /* pubchemid */, s /* canon */, s));
		}
		return newChems;
	}
	
	
	
	
}
