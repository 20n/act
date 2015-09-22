package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import act.server.Logger;
import act.shared.AAMFailException;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.thoughtworks.xstream.XStream;

public class RO {

  public RxnWithWildCards ro;
  // we could probably remove the RxnWithWildCards class
  // and put its members right here...

  private Set<Integer> witness_rxns;
  private Integer parent;
  private Double reversibility;
  private Set<String> keywords;
  private Set<String> keywordsCaseInsensitive;

  public RO(RxnWithWildCards ro) {
    this.ro = ro;
    this.witness_rxns = new HashSet<Integer>();
    this.keywords = new HashSet<String>();
    this.keywordsCaseInsensitive = new HashSet<String>();
    this.added_ondemand = null; // instantiated on the first call to getAddedMolGraph
    this.deleted_ondemand = null; // instantiated on the first call to getDeletedMolGraph
  }

  protected RO() {
  } // for deserialization?...


  @Override
  public String toString() {
    return "{ " + this.ro + " }";
  }

  public boolean hasRxnSMILES() {
    return this.ro != null;
  }

  public String rxn() {
    return this.ro.rxn;
  }

  public void addWitnessRxn(Integer rxnid) {
    this.witness_rxns.add(rxnid);
  }

  public Set<Integer> getWitnessRxns() {
    return this.witness_rxns;
  }

  public void addKeyword(String k) {
    this.keywords.add(k);
    this.keywordsCaseInsensitive.add(k.toLowerCase());
  }

  public void addKeywords(Set<String> ks) {
    for (String k : ks) addKeyword(k);
  }

  public Set<String> getKeywords() {
    return this.keywords;
  }

  public Set<String> getKeywordsCaseInsensitive() {
    return this.keywordsCaseInsensitive;
  }

  public void setParent(Integer id) {
    this.parent = id;
  }

  public Double getReversibility() {
    return reversibility;
  }

  public void setReversibility(Double reversibility) {
    this.reversibility = reversibility;
  }

  private MolGraph added_ondemand, deleted_ondemand;

  private void instantiateAddedDeleted() {
    P<MolGraph, MolGraph> addDel;
    try {
      addDel = SMILES.getAddedDeletedInQueryRxn(this.ro.rxn);
      this.added_ondemand = addDel.fst();
      this.deleted_ondemand = addDel.snd();
    } catch (AAMFailException e) {
      Logger.println(
          0,
          "Wierdest thing happened today. From a rxn we had already constructed, we could not map the AAM back!");
      System.exit(-1);
    }
  }

  private MolGraph getAddedMolGraph() {
    if (this.added_ondemand == null)
      instantiateAddedDeleted();
    return this.added_ondemand;
  }

  private MolGraph getDeletedMolGraph() {
    if (this.deleted_ondemand == null)
      instantiateAddedDeleted();
    return this.deleted_ondemand;
  }

  public boolean direction(RO other) {
    // called through ReactionDiff.processAll, when we process(r) to get a
    // theoryRO out.
    MolGraph thisAdded = getAddedMolGraph(), thisDeleted = getDeletedMolGraph();
    MolGraph otherAdded = other.getAddedMolGraph(), otherDeleted = other
        .getDeletedMolGraph();
    if ((thisAdded.queryEquals(otherAdded) && thisDeleted
        .queryEquals(otherDeleted))) // add = add and del = del
      return true;
    if ((thisAdded.queryEquals(otherDeleted) && thisDeleted
        .queryEquals(otherAdded))) // add = del and del = add
      return false;

    System.err
        .println("Precondition assertion violated. Unequal RO passed as input.");
    System.exit(-1);
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RO))
      return false;
    RO other = (RO) o;
    MolGraph thisAdded = getAddedMolGraph(), thisDeleted = getDeletedMolGraph();
    MolGraph otherAdded = other.getAddedMolGraph(), otherDeleted = other
        .getDeletedMolGraph();
    return (thisAdded.queryEquals(otherAdded) && thisDeleted.queryEquals(otherDeleted))  // add = add and del = del
        || (thisAdded.queryEquals(otherDeleted) && thisDeleted.queryEquals(otherAdded)); // add = del and del = add

  }

  @Override
  public int hashCode() {
    MolGraph thisAdded = getAddedMolGraph(), thisDeleted = getDeletedMolGraph();
    // xor is commutative so add/del are interchangeable
    return thisAdded.queryHashCode() ^ thisDeleted.queryHashCode();
  }

  public int ID() {
    return hashCode();
  }

  public void render(String filename, String comment) {
    Indigo indigo = new Indigo();
    SMILES.renderReaction(indigo.loadQueryReaction(ro.rxn), filename,
        comment, indigo);
  }

  protected static XStream getXStream() {
    XStream xstream = new XStream();
    xstream.alias("with_wc_atoms", RxnWithWildCards.class);
    xstream.alias("mol_atom", Atom.class);
    xstream.alias("bond", Bond.class);
    return xstream;
  }

  public String serialize() {
    return getXStream().toXML(this.ro);
  }


  @Deprecated
  private String console_printing_delta2str(MolGraph g,
                                            HashMap<Atom, List<Integer>> seen) {
    String s = "";
    boolean added = false;
    HashMap<Integer, Boolean> covered = new HashMap<Integer, Boolean>();
    for (Integer n : g.AdjList().keySet()) {
      for (P<Integer, BondType> adj : g.AdjList().get(n)) {
        if (adj.fst() < n)
          continue;
        String src = console_printing_nodes(seen, g.GetNodeType(n), n);
        String dst = console_printing_nodes(seen,
            g.GetNodeType(adj.fst()), adj.fst());
        s += (added ? ", " : "") + src
            + console_printing_edge(adj.snd()) + dst;
        added = true;
        // mark these two nodes as printed in the string
        covered.put(n, true);
        covered.put(adj.fst(), true);
      }
    }
    for (Integer n : g.Nodes().keySet())
      if (!covered.containsKey(n) || !covered.get(n))
        s += (s.isEmpty() ? "" : ", ")
            + console_printing_nodes(seen, g.GetNodeType(n), n);
    return "[ " + s + " ]";
  }

  @Deprecated
  private String console_printing_nodes(HashMap<Atom, List<Integer>> seen,
                                        Atom atom, int id) {
    if (!seen.containsKey(atom))
      seen.put(atom, new ArrayList<Integer>());
    List<Integer> alreadySeen = seen.get(atom);
    // add it to the tail if it is not already there.
    if (!alreadySeen.contains(id))
      alreadySeen.add(id);
    // the position in the list indicates how many "primes" we add to the
    // atom
    String primes = "";
    int num = alreadySeen.indexOf(id);
    for (int i = 0; i < num; primes += "'", i++)
      ; // operation in the loop declaration
    return atom + primes;
  }

  @Deprecated
  private String console_printing_edge(BondType e) {
    String bnd = "";
    switch (e) {
      case Single:
        bnd = "-";
        break;
      case Double:
        bnd = "=";
        break;
      case Triple:
        bnd = "#";
        break;
      default:
        bnd = "";
        break;
    }
    return bnd;
  }

}
