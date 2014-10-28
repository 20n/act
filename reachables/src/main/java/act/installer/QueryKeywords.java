package act.installer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.DBIterator;
import act.server.Molecules.RO;
import act.server.Molecules.BRO;
import act.server.Molecules.CRO;
import act.server.Molecules.ERO;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.Organism;
import act.shared.helpers.P;

class QueryKeywords {
  private MongoDB db;
  QueryKeywords(MongoDB db) {
    this.db = db;
  }

  String actid(Chemical c)  { return "act:c" + c.getUuid(); }
  String actid(Reaction r)  { return "act:r" + r.getUUID(); }
  String actidChemID(long cid)  { return "act:c" + cid; }
  String actid(RO ro)       { return "act:ro" + ro.ID();    }
  String actid(Seq seq)     { return "act:seq" + seq.getUUID();    }

  public void mine_all() {
    mine_reaction_operators();
    mine_chemicals();
    mine_reactions();
    mine_sequences();
  }

  private void mine_chemicals() {
    Chemical c = null;
    DBIterator cursor = this.db.getIteratorOverChemicals();
    while ((c = this.db.getNextChemical(cursor)) != null) {
      for (String k : extractKeywords(c)) {
        if (k == null) continue;
        c.addKeyword(k);
        c.addCaseInsensitiveKeyword(k.toLowerCase());
      }
      long id = c.getUuid();
      this.db.updateActChemical(c, id);
    }
  }

  private void mine_reactions() {
    Reaction r = null;
    // get the entire range of [0, ..] reactions by id, notimeout = true
    DBIterator cursor = this.db.getIteratorOverReactions (0L, null, true);
    while ((r = this.db.getNextReaction(cursor)) != null) {
      for (String k : extractKeywords(r)) {
        if (k == null) continue;
        r.addKeyword(k);
        r.addCaseInsensitiveKeyword(k.toLowerCase());
      }
      this.db.updateKeywords(r);
    }
  }

  private void mine_sequences() {
    Seq s = null;
    // get the entire range of [0, ..] db.seq by id, notimeout = true
    DBIterator cursor = this.db.getIteratorOverSeq();
    while ((s = this.db.getNextSeq(cursor)) != null) {
      for (String k : extractKeywords(s)) {
        if (k == null) continue;
        s.addKeyword(k);
        s.addCaseInsensitiveKeyword(k.toLowerCase());
      }
      this.db.updateKeywords(s);
    }
  }

  private void mine_reaction_operators() {
    // db.eros; db.cros; db.bros all extend RO so have the same format
    List<P<ERO, Integer>> eros = getROsAndRank(this.db.eros(-1));
    for (P<ERO, Integer> e_rank : eros) {
      // addKeywords converts to lowercase and adds that too
      ERO e = e_rank.fst();
      e.addKeywords(extractKeywords(e)); 
      e.addKeyword("ero:rank:" + e_rank.snd());
      this.db.updateEROKeywords(e);
    }
    List<P<CRO, Integer>> cros = getROsAndRank(this.db.cros(-1));
    for (P<CRO, Integer> c_rank : cros) {
      // addKeywords converts to lowercase and adds that too
      CRO c = c_rank.fst();
      c.addKeywords(extractKeywords(c)); 
      c.addKeyword("cro:rank:" + c_rank.snd());
      this.db.updateCROKeywords(c);
    }
    List<P<BRO, Integer>> bros = getROsAndRank(this.db.bros(-1));
    for (P<BRO, Integer> b_rank : bros) {
      // addKeywords converts to lowercase and adds that too
      BRO b = b_rank.fst();
      b.addKeywords(extractKeywords(b)); 
      b.addKeyword("bro:rank:" + b_rank.snd());
      this.db.updateBROKeywords(b);
    }

    // db.operators: TheoryROs (does not extend RO): need to mine?
    System.out.println("[WARN] mine_reaction_operators: We do not mine in db.operators/TheoryROs.");
    // System.exit(-1);
  }

  private <T extends RO> List<P<T, Integer>> getROsAndRank(List<T> ros) {
    List<P<T, Integer>> ro_sizes = new ArrayList<P<T, Integer>>();
    List<P<T, Integer>> ro_ranks = new ArrayList<P<T, Integer>>();
    for (T ro : ros) 
      ro_sizes.add(new P<T, Integer>(ro, ro.getWitnessRxns().size()));
    ro_sizes.sort(new Comparator<P<T, Integer>>() {
      @Override
      public int compare(final P<T, Integer> lhs, P<T, Integer> rhs) {
        //TODO return 1 if rhs should be before lhs 
        //     return -1 if lhs should be before rhs
        //     return 0 otherwise
        return rhs.snd().compareTo(lhs.snd()); 
        // we want descending, so instead of lhs.compare(rhs) the opposite
     }
    });
    int rank = 1;
    for (P<T, Integer> ro_sz : ro_sizes) 
      ro_ranks.add(new P<T, Integer>(ro_sz.fst(), rank++));
    return ro_ranks;
  }

  Set<String> extractKeywords(Chemical c) {
    Set<String> keywords = new HashSet<String>();
    // pick inchi, smiles, main names
    keywords.addAll(chemicalMainIdentifiers(c)); 
    // add rest of the common names
    keywords.addAll(c.getSynonyms());
    keywords.addAll(c.getBrendaNames());
    for (String[] pcNames : c.getPubchemNames().values())
      for (String pcName : pcNames)
        keywords.add(pcName);
    // add actid
    keywords.add(actid(c));
    // add xref IDs
    keywords.addAll(xrefID(c, "wikipedia:", Chemical.REFS.WIKIPEDIA, new String[] {"metadata", "article"}));
    keywords.addAll(xrefID(c, "drugbank:" , Chemical.REFS.DRUGBANK,  new String[] {"dbid"}));
    keywords.addAll(xrefID(c, "kegg_drug:", Chemical.REFS.KEGG_DRUG, new String[] {"dbid"}));
    keywords.addAll(xrefID(c, "sigma:"    , Chemical.REFS.SIGMA,     new String[] {"dbid"}));
    // metacyc id's is a list rather than a single id so need iteration
    // keywords.add("metacyc:" + getChemXref(Chemical.REFS.METACYC, {"id"}));
    return keywords;
  }

  private Set<String> xrefID(Chemical c, String prefix, Chemical.REFS field, String[] xpath) {
    Set<String> optIDs = new HashSet<String>();
    Object o = c.getRef(field, xpath);
    if (o != null) {
      if (o instanceof String) 
        optIDs.add(prefix + (String)o);
    }
    return optIDs;
  }

  private Set<String> chemicalMainIdentifiers(Chemical c) {
    Set<String> ident = new HashSet<String>();
    ident.add(actid(c));
    ident.add(c.getSmiles());
    ident.add(c.getInChI());
    // add some names
    ident.add(c.getCanon());
    ident.add(c.getShortestBRENDAName());
    ident.add(c.getFirstName());
    return ident;
  }

  Set<String> extractKeywords(Reaction r) {
    Set<String> keywords = new HashSet<String>();
    // Add EC number
    keywords.add(r.getECNum());
    // Add PMIDS
    keywords.addAll(r.getReferences());
    // Add sequence refs
    for (Long swissprot : r.getSwissProtSeqRefs())
      keywords.add(swissprot.toString());
    // Add actid
    keywords.add(actid(r));
    // Add organism names
    for (Long orgid : r.getOrganismIDs())
      keywords.add(organismName(orgid));
    // Add the substates as the most relevant name for them
    for (Long s : r.getSubstrates())
      keywords.addAll(chemicalMainIdentifiers(this.db.getChemicalFromChemicalUUID(s)));
    // Add the products as the most relevant names for them
    for (Long p : r.getProducts())
      keywords.addAll(chemicalMainIdentifiers(this.db.getChemicalFromChemicalUUID(p)));
    // Add the entire reaction txt (nobody will search this entirely, but yet)
    keywords.add(r.getReactionName());

    return keywords;
  }

  private String organismName(Long orgID) {
    return this.db.getOrganismNameFromId(orgID);
  }

  private Set<String> extractKeywords(Seq seq) {
    Set<String> keywords = new HashSet<String>();
    keywords.add(actid(seq));
    keywords.add(seq.get_ec());
    keywords.add(seq.get_org_name());
    keywords.add(seq.get_gene_name());
    keywords.add(seq.get_uniprot_accession());
    return keywords;
  }

  private Set<String> extractKeywords(RO ro) {
    Set<String> keywords = new HashSet<String>();
    keywords.add(actid(ro));
    for (Integer witness : ro.getWitnessRxns()) {
      Reaction r = this.db.getReactionFromUUID(new Long(witness));
      keywords.add(actid(r));
      for (Long s : r.getSubstrates())
        keywords.add(actidChemID(s));
      for (Long p : r.getProducts())
        keywords.add(actidChemID(p));
    }
    return keywords;
  }
}
