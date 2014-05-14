package act.server.FnGrpDomain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;

import act.server.EnumPath.OperatorSet;
import act.server.EnumPath.OperatorSet.OpID;
import act.server.Molecules.CRO;
import act.server.Molecules.RO;
import act.server.Molecules.SMILES;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Path;
import act.shared.helpers.P;

public class FnGrpDomainSearch {
	static boolean _DumpImageFiles = true;
	
	MongoDB DB;
	
	Chemical target;
	List<Chemical> nativeMetabolites;
	HashMap<OpID, CRO> transforms;
	
	FnGrpAbstractChem abstract_target;
	List<FnGrpAbstractChem> abstract_nativeMetabolites;
	HashMap<OpID, FnGrpAbstractTransform> abstract_transforms;
	
	// These are written down as smiles patterns to search for within the molecule
	// If we had molGraphs for these we could use SMILES.QueryMolWithPseudoAtomsFromGraph or SMILES.QueryMolFromGraph
	// to get the corresponding smiles. Like we do when we call CRO/RO(RxnWithWildCards) in SMILES.ReactionFromGraphs
	String[] fngrp_basis = new String[] {
			"C[H]", // CH -- this is incredibly common, so it might be advisable to abstract this away to start with
			"O[H]", // CH -- this is incredibly common, so it might be advisable to abstract this away to start with
			"CC", // C-C bond
			"C=C", // C=C bond
			"C=O",
			"CO[H]", // COH
			"C(=O)O[H]", // COOH
			"OO", // O-O bond
			// "O=[!C;R]" -- these are smarts so custom regexes would possibly be allowed. lookup the indigo library for details
	};

	public FnGrpDomainSearch(Chemical chem, List<Chemical> metaboliteChems, int numOps, MongoDB mongoDB) {
		this.DB = mongoDB;
		this.target = chem;
		this.nativeMetabolites = metaboliteChems;
		this.transforms = getTransforms(numOps, true /* filter duplicates */, this.DB);

		this.abstract_target = new FnGrpAbstractChem(this.target, this.fngrp_basis);
		this.abstract_nativeMetabolites = new ArrayList<FnGrpAbstractChem>();
		for (Chemical c : nativeMetabolites)
			this.abstract_nativeMetabolites.add(new FnGrpAbstractChem(c, this.fngrp_basis));
		this.abstract_transforms = new HashMap<OpID, FnGrpAbstractTransform>();
		for (OpID id : this.transforms.keySet())
			this.abstract_transforms.put(id, new FnGrpAbstractTransform(this.transforms.get(id), this.fngrp_basis));
		
		debugDumpSearchInputs();
	}

	public List<Path> getPaths(int numPaths) {
		
		System.err.println("Abstract search algorithm not implemented yet.");
		System.exit(-1);
		return null;
	}

	private HashMap<OpID, CRO> getTransforms(int num, boolean filterDuplicates, MongoDB fromDB) {
		OperatorSet fwdOps = new OperatorSet(fromDB, num, filterDuplicates);
		return fwdOps.getAllCROs();
	}
	
	private void debugDumpSearchInputs() {
		System.out.println("Target: " + this.abstract_target);
		int count = 0;
		for (FnGrpAbstractChem c : this.abstract_nativeMetabolites)
			System.out.format("Native[%d]: %s\n", count++, c);
		count = 0;
		for (FnGrpAbstractTransform tx : this.abstract_transforms.values())
			System.out.format("Transform[%d]: %s\n", count++, tx);
		
		if (_DumpImageFiles) {
			Indigo indigo = new Indigo();
			IndigoObject molecule = indigo.loadMolecule(this.abstract_target.originalChemical().getSmiles());
			SMILES.renderMolecule(molecule, "FNABS-target.png", this.abstract_target.toString(), indigo);
			count = 0;
			for (FnGrpAbstractChem c : this.abstract_nativeMetabolites) {
				molecule = indigo.loadMolecule(c.originalChemical().getSmiles());
				SMILES.renderMolecule(molecule, "FNABS-native-" + (count++) + ".png", c.toString(), indigo);
			}
			count = 0;
			for (FnGrpAbstractTransform tx : this.abstract_transforms.values()) {
				IndigoObject reaction = indigo.loadQueryReaction(tx.original_ro.rxn());
				SMILES.renderReaction(reaction, "FNABS-reaction-" + (count++) + ".png", tx.toString(), indigo);
			}
			System.out.println("Dumped all chemicals and transforms as images.");
		}
		
		System.out.println("Initialization: Done.");
	}

}
