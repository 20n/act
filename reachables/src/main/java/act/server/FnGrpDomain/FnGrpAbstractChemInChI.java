package act.server.FnGrpDomain;

import java.util.Arrays;
import java.util.HashMap;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoException;

import act.shared.Chemical;

public class FnGrpAbstractChemInChI {
	private String[] basisVector;
  private IndigoObject[] basisQuery;
	private Indigo indigo = new Indigo();
  private IndigoInchi indigoinchi = new IndigoInchi(indigo);

	public FnGrpAbstractChemInChI(String[] fngrp_basis) {
		this.basisVector = fngrp_basis;
		this.basisQuery = new IndigoObject[fngrp_basis.length];
	  this.indigo = new Indigo();
    this.indigoinchi = new IndigoInchi(indigo);

		for (int i = 0; i < basisVector.length; i++) {
			String basis = basisVector[i];
		  this.basisQuery[i] = indigo.loadSmarts(basis);
      this.basisQuery[i].optimize();
    }

	}

	public HashMap<String, Integer> createAbstraction(String inchi) {
    try { 
		  IndigoObject mol = indigoinchi.loadMolecule(inchi);
		  IndigoObject matcher = indigo.substructureMatcher(mol);

      HashMap<String, Integer> abs = new HashMap<String, Integer>();
		  for (int i = 0; i < basisVector.length; i++) {
		    int count = matcher.countMatches(basisQuery[i]);
        if (count > 0)
          abs.put(basisVector[i], count);

		    // System.out.format("%s in %s occurs %d times\n", basis, smiles, count);
		    // for (IndigoObject match : matcher.iterateMatches(query))
		    //   System.out.println(match.highlightedTarget().smiles());
		  }
		  return abs;
    } catch (IndigoException e) {
      return null;
    }
	}
}
