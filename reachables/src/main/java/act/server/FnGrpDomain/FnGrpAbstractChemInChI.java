package act.server.FnGrpDomain;

import java.util.Arrays;
import java.util.HashMap;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoException;

import act.shared.Chemical;

public class FnGrpAbstractChemInChI {
  // this is a map of substructure pattern -> common name,
  // e.g., "C(=O)O[H]" -> "carboxylic_acid"
  // Note that this does not need to be a 1-1 map, more than
  // one smarts could be mapped to a single common name, 
  // e.g., glycosides or halogen, and we will just accumulate matches
	private HashMap<String, String> basisVector;
  // with the same keyset as the above map, this maps them to 
  // optimized smarts matchers
  private HashMap<String, IndigoObject> basisQuery;
	private Indigo indigo = new Indigo();
  private IndigoInchi indigoinchi = new IndigoInchi(indigo);

	public FnGrpAbstractChemInChI(HashMap<String, String> fngrp_basis) {
		this.basisVector = fngrp_basis;
		this.basisQuery = new HashMap<String, IndigoObject>();
	  this.indigo = new Indigo();
    this.indigoinchi = new IndigoInchi(indigo);

		for (String basis : basisVector.keySet()) {
		  IndigoObject q = indigo.loadSmarts(basis);
      q.optimize();
      this.basisQuery.put(basis, q);
    }

	}

	public HashMap<String, Integer> createAbstraction(String inchi) {
    try { 
		  IndigoObject mol = indigoinchi.loadMolecule(inchi);
		  IndigoObject matcher = indigo.substructureMatcher(mol);

      HashMap<String, Integer> abs = new HashMap<String, Integer>();
		  for (String basis: basisVector.keySet()) {
		    int count = matcher.countMatches(this.basisQuery.get(basis));
        if (count > 0) {
          String qname = this.basisVector.get(basis);
          if (!abs.containsKey(qname)) {
            abs.put(qname, count); 
          } else {
            // this can happen if multiple smarts are associated
            // with the same common name, e.g., if glycoside
            // means one of many substructures then we want to 
            // sum over all matches

            // compute the accumulated count and update the map
            int acc_count = count + abs.get(qname);
            abs.put(qname, acc_count);
          }
        }

		    // System.out.format("%s in %s occurs %d times\n", basis, smiles, count);
		    // for (IndigoObject match : matcher.iterateMatches(query))
		    //   System.out.println(match.highlightedTarget().smiles());
		  }
		  return abs;
    } catch (IndigoException e) {
      return null;
    }
	}

  public static boolean doesMatch(String smartsPattern, String inchi) {
	  Indigo ind = new Indigo();
    IndigoInchi indinchi = new IndigoInchi(ind);
		IndigoObject q = ind.loadSmarts(smartsPattern);
    q.optimize();
    try { 
		  IndigoObject mol = indinchi.loadMolecule(inchi);
		  IndigoObject matcher = ind.substructureMatcher(mol);

      HashMap<String, Integer> abs = new HashMap<String, Integer>();
      int count = matcher.countMatches(q);
      return count > 0;
    } catch (IndigoException e) {
      return false;
    }
  }

}
