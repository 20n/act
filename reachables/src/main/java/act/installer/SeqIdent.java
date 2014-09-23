package act.installer;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import act.shared.Reaction;
import act.shared.Seq;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.P;

class SeqIdent {
  public static boolean track_ref = true;
  public static boolean track_ec = true;
  public static boolean track_org = true;

  String ec, org, ref;
  SeqIdent(String e, String o, String r) {
    this.ref = track_ref ? r : "";
    this.ec  = track_ec ? e : "";
    this.org = track_org ? o : "";
  }

  public static Set<SeqIdent> expansion(String ec, List<String> orgs_e, List<String> refs_e) {
    Set<SeqIdent> ident = new HashSet<SeqIdent>();
    // if we are not tracking something (e.g., ref, or org) then that field will be singleton
    // this way, we wont ignore the rest of the data. e.g., if ref.isEmpty and !track_ref
    Set<String> filler = new HashSet<String>(); filler.add("");
    Set<String> refs = !track_ref ? filler : new HashSet<String>(refs_e);
    Set<String> orgs = !track_org ? filler : new HashSet<String>(orgs_e);
    for (String ref : refs)
      for (String org : orgs)
        ident.add(new SeqIdent(ec, org, ref));
    return ident;
  }

  public static Set<SeqIdent> createFrom(Reaction r, MongoDB db) {
    String ec = r.getECNum();
    Long[] orgids = r.getOrganismIDs(); // translate these to org_names
    List<String> orgs = new ArrayList<String>();
    for (Long oid : orgids) orgs.add(db.getOrganismNameFromId(oid));
    List<String> refs = r.getReferences();
    return expansion(ec, orgs, refs);
  }

  public static Set<SeqIdent> createFrom(Seq s) {
    String ec = s.get_ec();
    String org = s.get_org_name();
    List<String> orgs = new ArrayList<String>();
    orgs.add(org);
    return expansion(ec, orgs, s.get_references());
  }

  public static <I> Set<P<I,I>> inferReln(HashMap<I, Set<SeqIdent>> A, HashMap<I, Set<SeqIdent>> B) {
    HashSet<P<I,I>> reln = new HashSet<P<I, I>>();
    // performance could be improved by inverting the hashmaps and then using an O(n) traversal
    // over the inverted map as opposed to doing an O(n^2) over the hashmaps
    for (I a_key : A.keySet())
      for (I b_key : B.keySet())
        if (nonEmptyIntersection(A.get(a_key), B.get(b_key)))
          reln.add(new P<I, I>(a_key, b_key));
    return reln;
  }

  public static <X> boolean nonEmptyIntersection(Set<X> set1, Set<X> set2) {
    boolean set1IsLarger = set1.size() > set2.size();
    Set<X> cloneSet = new HashSet<X>(set1IsLarger ? set2 : set1);
    cloneSet.retainAll(set1IsLarger ? set1 : set2);
    return ! cloneSet.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SeqIdent)) return false;
    SeqIdent that = (SeqIdent)o;
    return
        this.ref.equals(that.ref) &&
        this.ec.equals(that.ec) &&
        this.org.equals(that.org);
  }

  @Override
  public int hashCode() {
    return this.ref.hashCode() ^ this.ec.hashCode() ^ this.org.hashCode();
  }

  @Override
  public String toString() {
    List<String> data = new ArrayList<String>();
    List<String> not_tracking = new ArrayList<String>();
    if (track_ref) data.add(this.ref); else not_tracking.add("ref");
    if (track_ec) data.add(this.ec); else not_tracking.add("ec");
    if (track_org) data.add(this.org); else not_tracking.add("org");
    String mode = "";
    if (!track_ref || !track_ec || !track_org) 
      mode = " not tracking" + not_tracking;
    return data + mode;
  }
  
}
