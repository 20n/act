package act.server.FnGrpDomain;

import java.util.Arrays;
import java.util.HashMap;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;

import act.shared.Chemical;

public class FnGrpAbstractChem {
	private Integer[] abstraction;
	private String[] basisVector;
	private Chemical original;

	public FnGrpAbstractChem(Chemical target, String[] fngrp_basis) {
		this.original = target;
		this.basisVector = fngrp_basis;
		this.abstraction = createAbstraction(target.getSmiles());
	}

	public FnGrpAbstractChem(String smiles, String[] fngrp_basis) {
		this.original = null;
		this.basisVector = fngrp_basis;
		this.abstraction = createAbstraction(smiles);
	}

	private Integer[] createAbstraction(String smiles) {
		Integer[] abs = new Integer[this.basisVector.length];
		for (int i = 0; i < basisVector.length; i++) {
			String basis = basisVector[i];
			abs[i] = findNumInstancesInMolecule(smiles, basis);
		}
		return abs;
	}

	private Integer findNumInstancesInMolecule(String smiles, String basis) {
		Indigo indigo = new Indigo();
		IndigoObject mol = indigo.loadMolecule(smiles);
		IndigoObject matcher = indigo.substructureMatcher(mol);
		IndigoObject query = indigo.loadSmarts(basis);

		int count = matcher.countMatches(query);
		// System.out.format("%s in %s occurs %d times\n", basis, smiles, count);
		// for (IndigoObject match : matcher.iterateMatches(query))
		//   System.out.println(match.highlightedTarget().smiles());
		return count;
	}

	@Override
	public String toString() {
		return Arrays.asList(this.abstraction).toString() + " / " + Arrays.asList(this.basisVector).toString() + " == " + this.original.getSmiles();
	}

	public Integer[] abstraction() {
		return this.abstraction;
	}
	
	public Chemical originalChemical() {
		return this.original;
	}

}
