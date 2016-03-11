package act.server.Molecules;

import java.util.ArrayList;
import java.util.List;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;

public class KnownCofactors {
  public class MappedCofactors {
    public List<Chemical> substrates, products;
    public String mapped_substrates, mapped_products;
  }

  public MappedCofactors newMappedCofactors(List<Chemical> schems, List<Chemical> pchems, String s, String p) { 
    MappedCofactors m = new MappedCofactors();
    m.substrates = schems;
    m.products = pchems;
    m.mapped_products = p; 
    m.mapped_substrates = s; 
    return m;
  }

  // we need to organize these better, essentially they are [s_ids],[p_ids] -> s_mapped,p_mapped
  // and we need to (ideally) find the set of entries that together cover (in pairs) the largest set of the given input substrate_ids, product_ids
  List<MappedCofactors> mapped;
  MongoDB DB;

  public KnownCofactors(MongoDB db) {
    this.DB = db;
    this.mapped = getAllMappedCofactors();
  }


  private List<MappedCofactors> getAllMappedCofactors() {




    // Populate from file:
    // act.installer.Main.addCofactorPreComputedAAMs
    //    https://github.com/20n/act/blob/master/reachables/src/main/java/act/installer/Main.java#L123
    // act.server.SQLInterface.MongoDB.submitToCofactorAAM
    //    https://github.com/20n/act/blob/master/reachables/src/main/java/act/server/SQLInterface/MongoDB.java#L1106

    // Read:
    // act.server.SQLInterface.MongoDB.getAllMappedCofactors
    // https://github.com/20n/act/blob/master/reachables/src/main/java/act/server/SQLInterface/MongoDB.java#L1797



    System.exit(-1);






    return null;
  }

  public MappedCofactors computeMaximalCofactorPairing(Long[] sIDs, Long[] pIDs, List<String> substratesReduced, List<String> productsReduced) {
    String s = "", p = "";
    List<Chemical> remaining_s = new ArrayList<Chemical>(), remaining_p = new ArrayList<Chemical>();
    for (Long ss : sIDs) remaining_s.add(this.DB.getChemicalFromChemicalUUID(ss));
    for (Long pp : pIDs) remaining_p.add(this.DB.getChemicalFromChemicalUUID(pp));

    for (MappedCofactors cf : this.mapped) {
      if (patternApplies(cf.substrates, cf.products, remaining_s, remaining_p)) {
        // if this pattern matches, then first remove those substrates and products from the unmatched list
        remaining_s = removeAllBySMILES(remaining_s, cf.substrates);
        remaining_p = removeAllBySMILES(remaining_p, cf.products);
        // then add those to what we will return as the mapped SMILES
        String substrates = cf.mapped_substrates;
        String products = cf.mapped_products;
        s += s.equals("") ? substrates : "." + substrates;
        p += p.equals("") ? products : "." + products;
        // and also add them as split separate entries.
        for (String ss : substrates.split("[.]")) substratesReduced.add(ss);
        for (String pp : products.split("[.]")) productsReduced.add(pp);
      }
    }

    return newMappedCofactors(remaining_s, remaining_p, s, p);
  }

  private List<Chemical> removeAllBySMILES(List<Chemical> from, final List<Chemical> toRemove) {
    List<Chemical> newFrom = new ArrayList<Chemical>();
    for (Chemical c : from) {
      if (existsIn(toRemove, c.getSmiles()))
          continue;
      newFrom.add(c);
    }
    return newFrom;
  }
  private boolean patternApplies(List<Chemical> s_mapped, List<Chemical> p_mapped, List<Chemical> s_rxn, List<Chemical> p_rxn) {
    // check if all the s_mapped \in s_rxn, and p_mapped \in p_rxn

    // the chemicals might have different UUIDs as different chemicals might have the same SMILES, e.g., L-ATP, ATP
    for (Chemical c : s_mapped)
      if (!existsIn(s_rxn, c.getSmiles()))
        return false;
    for (Chemical c : p_mapped)
      if (!existsIn(p_rxn, c.getSmiles()))
        return false;

    return true;
  }

  private boolean existsIn(List<Chemical> rxn, String smiles) {
    for (Chemical c : rxn) {
      if (smiles.equals(c.getSmiles()))
        return true;
    }
    return false;
  }

}
