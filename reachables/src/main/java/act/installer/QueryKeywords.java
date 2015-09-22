package act.installer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
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

public class QueryKeywords {
  private MongoDB db;
  QueryKeywords(MongoDB db) {
    this.db = db;
  }

  public static String actid(Chemical c)  { return "act:c" + c.getUuid(); }
  public static String actid(Reaction r)  { return "act:r" + r.getUUID(); }
  public static String actid(RO ro)       { return "act:ro" + ro.ID();    }
  public static String actid(Seq seq)     { return "act:seq" + seq.getUUID();    }
  private static String actidChemical(long cid) { return "act:c" + cid; }
  private static String actidReaction(long rid) { return "act:r" + rid; }

  public void mine_all() {
    System.out.println("act.installer.QueryKeywords: Mining cascades");
    mine_cascades();

    System.out.println("act.installer.QueryKeywords: Mining waterfalls");
    mine_waterfalls();

    System.out.println("act.installer.QueryKeywords: Mining ROs");
    mine_reaction_operators();

    System.out.println("act.installer.QueryKeywords: Mining chemicals");
    mine_chemicals();

    System.out.println("act.installer.QueryKeywords: Mining reactions");
    mine_reactions();

    System.out.println("act.installer.QueryKeywords: Mining sequences");
    mine_sequences();
  }

  private void mine_waterfalls() {
    DBObject c = null;
    DBIterator cursor = this.db.getIteratorOverWaterfalls();
    while ((c = this.db.getNextWaterfall(cursor)) != null) {
      Set<String> kwrds = new HashSet<String>();
      Set<String> cikwrds = new HashSet<String>();
      for (String k : extractKeywordsWaterfall(c)) {
        if (k == null) continue;
        kwrds.add(k);
        cikwrds.add(k.toLowerCase());
      }
      long id = getUuidWaterfall(c);
      this.db.updateKeywordsWaterfall(id, kwrds, cikwrds);
    }
  }

  private void mine_cascades() {
    DBObject c = null;
    DBIterator cursor = this.db.getIteratorOverCascades();
    while ((c = this.db.getNextCascade(cursor)) != null) {
      Set<String> kwrds = new HashSet<String>();
      Set<String> cikwrds = new HashSet<String>();
      for (String k : extractKeywordsCascade(c)) {
        if (k == null) continue;
        kwrds.add(k);
        cikwrds.add(k.toLowerCase());
      }
      long id = getUuidCascade(c);
      this.db.updateKeywordsCascade(id, kwrds, cikwrds);
    }
  }

  // TODO: this needs to be in shared.Cascade
  Set<String> extractKeywordsCascade(DBObject c) {
    Set<String> keywords = new HashSet<String>();
    Long chem_id = getUuidCascade(c);
    Chemical chem = db.getChemicalFromChemicalUUID(chem_id);
    if (chem != null) {
      // is null when chem_id == -1 and -2
      Set<String> chem_keywords = extractKeywords(chem);
      keywords.addAll(chem_keywords);
    }
    keywords.add("reachable");

    return keywords;
  }

  // TODO: this needs to be in shared.Cascade
  Long getUuidCascade(DBObject c) {
    return (Long)c.get("_id");
  }

  // TODO: this needs to be in shared.Cascade
  Set<String> extractKeywordsWaterfall(DBObject c) {
    Set<String> keywords = new HashSet<String>();
    Long chem_id = getUuidWaterfall(c);
    Chemical chem = db.getChemicalFromChemicalUUID(chem_id);
    if (chem != null) {
      // is null when chem_id == -1 and -2
      Set<String> chem_keywords = extractKeywords(chem);
      keywords.addAll(chem_keywords);
    }
    keywords.add("reachable");

    return keywords;
  }

  // TODO: this needs to be in shared.Cascade
  Long getUuidWaterfall(DBObject c) {
    return (Long)c.get("_id");
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
    // DBIterator cursor = this.db.getIteratorOverReactions (0L, null, true);
    DBIterator cursor = this.db.getIteratorOverReactions (true);
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
    Collections.sort(ro_sizes, new Comparator<P<T, Integer>>() {
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
    // Add actid
    keywords.add(actid(r));

    // act.shared.Reaction does not have a direct access to
    // sequences and organism IDs anymore...

    Set<String> molIDs;
    // Add the substates as the most relevant name for them
    for (Long s : r.getSubstrates()) {
      molIDs = chemicalMainIdentifiers(this.db.getChemicalFromChemicalUUID(s));
      keywords.addAll(molIDs);
    }
    // Add the products as the most relevant names for them
    for (Long p : r.getProducts()) {
      molIDs = chemicalMainIdentifiers(this.db.getChemicalFromChemicalUUID(p));
      keywords.addAll(molIDs);
    }
    // Add the entire reaction txt (nobody will search this entirely, but yet)
    keywords.add(r.getReactionName());

    return keywords;
  }

  private String organismName(Long orgID) {
    return this.db.getOrganismNameFromId(orgID);
  }

  private Set<String> sequenceAccessions(Long seqID) {
    Seq seq = this.db.getSeqFromID(seqID);
    return seq.get_uniprot_accession();
  }

  private Set<String> extractKeywords(Seq seq) {
    Set<String> keywords = new HashSet<String>();
    String ec = seq.get_ec();
    String org = seq.get_org_name();
    String gene = seq.get_gene_name();
    Set<String> acc = seq.get_uniprot_accession();
    Set<Long> rxns, subs, prod;

    rxns = seq.getReactionsCatalyzed();
    subs = seq.getCatalysisSubstratesDiverse(); subs.addAll(seq.getCatalysisSubstratesUniform());
    prod = seq.getCatalysisProductsDiverse(); prod.addAll(seq.getCatalysisProductsUniform());

    keywords.add(actid(seq));
    if (!"".equals(ec)) keywords.add(ec);
    if (!"".equals(org)) keywords.add(org);
    if (!"".equals(gene)) keywords.add(gene);
    keywords.addAll(seq.get_uniprot_accession());
    for (Long r : rxns) keywords.add(actidReaction(r));
    Set<String> molIDs;
    for (Long s : subs) {
      keywords.add(actidChemical(s));
      molIDs = chemicalMainIdentifiers(this.db.getChemicalFromChemicalUUID(s));
      keywords.addAll(molIDs);
    }
    for (Long p : prod) {
      keywords.add(actidChemical(p));
      molIDs = chemicalMainIdentifiers(this.db.getChemicalFromChemicalUUID(p));
      keywords.addAll(molIDs);
    }
    return keywords;
  }

  private Set<String> extractKeywords(RO ro) {
    Set<String> keywords = new HashSet<String>();
    keywords.add(actid(ro));
    for (Integer witness : ro.getWitnessRxns()) {
      Reaction r = this.db.getReactionFromUUID(new Long(witness));
      keywords.add(actid(r));
      for (Long s : r.getSubstrates())
        keywords.add(actidChemical(s));
      for (Long p : r.getProducts())
        keywords.add(actidChemical(p));
    }
    return keywords;
  }
}
