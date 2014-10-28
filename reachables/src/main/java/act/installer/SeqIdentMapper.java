package act.installer;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import act.shared.Reaction;
import act.shared.Seq;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.P;

public class SeqIdentMapper {

  private MongoDB db;
  private static final int _debug_level = 1; // 0 = no log; 1 = only main stats; 2 = all

  public SeqIdentMapper(MongoDB db) {
    this.db = db;
  }

  public void map() {
    System.out.println("\n\n\n\nmapping using brenda annotations\n\n\n\n");
    connect_using_explicit_brenda_accession_annotation();
    System.out.println("\n\n\n\nmapping using seq fingerprint\n\n\n\n");
    connect_using_fingerprint();
  }

  // for logging how many of each type of sequence reference we read in brenda
  private static int swissprot_n=0, uniprot___n=0, trembl____n=0, embl______n=0, genbank___n=0;

  private void connect_using_explicit_brenda_accession_annotation() {
    HashMap<Integer, Set<String>> rxnid2accession = new HashMap<Integer, Set<String>>();
    HashMap<String, Integer> accession2seqid = new HashMap<String, Integer>();

    for (Long uuid : db.getAllReactionUUIDs()) {
      Reaction r = db.getReactionFromUUID(uuid);
      Set<String> accessions = getAccessionNumbers(r.getReactionName());
      if (accessions.size() > 0)
      rxnid2accession.put(r.getUUID(), accessions);
    }

    for (Long seqid : db.getAllSeqUUIDs()) {
      Seq s = db.getSeqFromID(seqid);
      for (String acc : s.get_uniprot_accession())
        accession2seqid.put(acc, s.getUUID());
    }

    Set<Integer> unmapped_rxns = new HashSet<Integer>();
    for (Integer rid : rxnid2accession.keySet()) {
      Long rxnid = new Long(rid);
      for (String rxnacc : rxnid2accession.get(rid)) {
        if (!accession2seqid.containsKey(rxnacc)) {
          unmapped_rxns.add(rid);
          continue;
        }
        Long seqid = new Long(accession2seqid.get(rxnacc));
        db.addSeqRefToReactions(rxnid, seqid); 
      }
    }

    if (_debug_level > 0) {
      System.out.println("SwissProt: " + this.swissprot_n);
      System.out.println("UniProt  : " + this.uniprot___n);
      System.out.println("TrEMBL   : " + this.trembl____n);
      System.out.println("EMBL     : " + this.embl______n);
      System.out.println("GenBank  : " + this.genbank___n);
  
      System.out.println("# Brenda rxns with accession annotation not mapped: " + unmapped_rxns.size());
      Set<String> rxnSqs = new HashSet<String>(); 
      for (Set<String> seqs : rxnid2accession.values()) rxnSqs.addAll(seqs);
      System.out.format("%d reactions have %d unique sequences\n", rxnid2accession.keySet().size(), rxnSqs.size());
      System.out.format("%d swissprot entries\n", accession2seqid.keySet().size());
      if (_debug_level > 1) {
        Set<String> unmapped_acc = new HashSet<String>();
        for (Integer rid : unmapped_rxns)
          unmapped_acc.add(rxnid2accession.get(rid) + " of " + rid); // not located in seq db, so no aa seq
        System.out.println("Accessions in Brenda that could not be resolved: " + unmapped_acc);
        for (Integer rid: rxnid2accession.keySet()) 
          System.out.format("rxnid(%s) -> %s\n", rid, rxnid2accession.get(rid));
        System.out.println("Swissprot accessions: " + accession2seqid.keySet());
      }
    }
  }

  private String word_before(String buffer, int anchor_index) {
    int end = buffer.lastIndexOf(' ', anchor_index - 1);
    int start = buffer.lastIndexOf(' ', end - 1);
    String word = buffer.substring(start, end).trim();
    return word;
  }

  private int add_words_before(String suffix, String buffer, int start_at, Set<String> accumulator) {
    int added = 0;
    int idx = buffer.indexOf(suffix, start_at);
    if (idx == -1) return added; // if no occurance found, return

    Set<String> accs_list = new HashSet<String>();

    // match of suffix at idx, check the word that appears before it
    String word = word_before(buffer, idx);
    accs_list.add(word);

    // check if the prefix is a "and" list, e.g., "Kalanchoe pinnata Q33557 and Q43746 and P10797 UniProt"
    int list_idx = idx;
    while(true) { 
      list_idx = list_idx - word.length() - 1;
      String preword = word_before(buffer, list_idx);
      if (preword.equals("AND")) {
        list_idx -= 4; // move backwards for the matched "AND "
        word = word_before(buffer, list_idx);
        accs_list.add(word);
      } else {
        break;
      }
    }

    // update the cummulative accession list
    accumulator.addAll(accs_list);

    // log the count of new entries found
    added += accs_list.size();

    if (_debug_level > 1) {
      System.out.format("Accession refs found: %s: %s\n", suffix, accs_list);
      // System.out.format("\tFrom sentence: %s\n\tParsed: %s\n", buffer, accs_list);
    }
    
    // recurse to after where the current suffix was found
    int recurse_added = add_words_before(suffix, buffer, idx + suffix.length(), accumulator);

    // the accumulator has been updated; return the num new added to it
    return added + recurse_added;
  }

  private Set<String> extract6LetterWords(String desc) {
    // six character; last character is 0-9
    String regex = " ([A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][A-Z0-9][0-9]) ";
    Pattern r = Pattern.compile(regex);
    Matcher m = r.matcher(desc);
    Set<String> matches = new HashSet<String>();
    while (m.find()) {
      String extracted = m.group(1); // desc.substring(m.start(), m.end());
      matches.add(extracted);
    }
    return matches;
  }

  private Set<String> getAccessionNumbers(String desc) {
    final String SWISSP = "SWISSPROT", UNIP = "UNIPROT", GENB = "GENBANK", TREMBL = "TREMBL", EMBL = "EMBL";
    Set<String> accs = new HashSet<String>();
    // search for strings such as 
    // " Q8TZI9 UniProt"
    // " P42527 SwissProt" 
    // " Q18NX4 TrEMBL" -- unreviewed
    // " O70151 GenBank" 
    // " Q9RLV9 EMBL"

    // add_words_before adds to the set of accessions "accs" and returns the delta count
    this.swissprot_n += add_words_before(SWISSP, desc.toUpperCase(), 0, accs);
    this.uniprot___n += add_words_before(UNIP, desc.toUpperCase(), 0, accs);
    this.trembl____n += add_words_before(TREMBL, desc.toUpperCase(), 0, accs);
    this.embl______n += add_words_before(EMBL, desc.toUpperCase(), 0, accs);
    this.genbank___n += add_words_before(GENB, desc.toUpperCase(), 0, accs);

    if (_debug_level > 1) {
      Set<String> candidates = extract6LetterWords(desc);
      candidates.removeAll(accs);
      if (candidates.size() > 0) {
        System.out.println();
        System.out.println("From reaction string    : " + desc);
        System.out.println("Candidates not extracted: " + candidates);
      }
    }

    return accs;
  }

  private void connect_using_fingerprint() {
    // Map of rxn_id -> sequence fingerprint
    HashMap<Long, Set<SeqFingerPrint>> rxnIdent = new HashMap<Long, Set<SeqFingerPrint>>();
    // Map of seq_id -> sequence fingerprint
    HashMap<Long, Set<SeqFingerPrint>> seqIdent = new HashMap<Long, Set<SeqFingerPrint>>();

    // take entries from db.actfamilies
    // map them to (ref_set, org_set, ec)
    // if (ref, org, ec) matches an entry in db.seq
    // map that sequence to the actfamilies entry

    System.out.println("Mapping reactions -> (ec, org, pmid)");
    // Populate rxnIdent
    for (Long uuid : db.getAllReactionUUIDs()) {
      Reaction r = db.getReactionFromUUID(uuid);
      Set<SeqFingerPrint> si = SeqFingerPrint.createFrom(r, db);
      rxnIdent.put(uuid, si);
    }
    // System.out.format("--- #maps: %d (10 examples below)\n", rxnIdent.size());
    // int c=0; for (Long i : rxnIdent.keySet()) if (c++<10) System.out.println(rxnIdent.get(i));

    System.out.println("Mapping sequences -> (ec, org, pmid)");
    // Populate seqIdent
    for (Long seqid : db.getAllSeqUUIDs()) {
      Seq s = db.getSeqFromID(seqid);
      Set<SeqFingerPrint> si = SeqFingerPrint.createFrom(s);
      seqIdent.put(seqid, si);
    }
    // System.out.format("--- #maps: %d (10 examples below)\n", seqIdent.size());
    // c=0; for (Long i : seqIdent.keySet()) if (c++<10) System.out.println(seqIdent.get(i));

    // SeqIndent holds the (ref, org, ec) -> inferReln find connections
    System.out.println("Intersecting maps of reactions and sequences");
    Set<P<Long, Long>> rxn2seq = SeqFingerPrint.inferReln(rxnIdent, seqIdent);
    for (P<Long, Long> r2s : rxn2seq)
      db.addSeqRefToReactions(r2s.fst(), r2s.snd());

    System.out.format("Found SwissProt sequences for %d rxns\n", rxn2seq.size());
    System.out.format("   using exact matches: ref:%s, org:%s, ec:%s between db.actfamilies and db.seq\n", SeqFingerPrint.track_ref, SeqFingerPrint.track_org, SeqFingerPrint.track_ec);
  }
}

class SeqFingerPrint {
  public static boolean track_ref = true;
  public static boolean track_ec = true;
  public static boolean track_org = true;

  String ec, org, ref;
  SeqFingerPrint(String e, String o, String r) {
    this.ref = track_ref ? r : "";
    this.ec  = track_ec ? e : "";
    this.org = track_org ? o : "";
  }

  public static Set<SeqFingerPrint> expansion(String ec, List<String> orgs_e, List<String> refs_e) {
    Set<SeqFingerPrint> ident = new HashSet<SeqFingerPrint>();
    // if we are not tracking something (e.g., ref, or org) then that field will be singleton
    // this way, we wont ignore the rest of the data. e.g., if ref.isEmpty and !track_ref
    Set<String> filler = new HashSet<String>(); filler.add("");
    Set<String> refs = !track_ref ? filler : new HashSet<String>(refs_e);
    Set<String> orgs = !track_org ? filler : new HashSet<String>(orgs_e);
    for (String ref : refs)
      for (String org : orgs)
        ident.add(new SeqFingerPrint(ec, org, ref));
    return ident;
  }

  public static Set<SeqFingerPrint> createFrom(Reaction r, MongoDB db) {
    String ec = r.getECNum();
    Long[] orgids = r.getOrganismIDs(); // translate these to org_names
    List<String> orgs = new ArrayList<String>();
    for (Long oid : orgids) orgs.add(db.getOrganismNameFromId(oid));
    List<String> refs = r.getReferences();
    return expansion(ec, orgs, refs);
  }

  public static Set<SeqFingerPrint> createFrom(Seq s) {
    String ec = s.get_ec();
    String org = s.get_org_name();
    List<String> orgs = new ArrayList<String>();
    orgs.add(org);
    return expansion(ec, orgs, s.get_references());
  }

  public static <I> Set<P<I,I>> inferReln(HashMap<I, Set<SeqFingerPrint>> A, HashMap<I, Set<SeqFingerPrint>> B) {
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
    if (!(o instanceof SeqFingerPrint)) return false;
    SeqFingerPrint that = (SeqFingerPrint)o;
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
