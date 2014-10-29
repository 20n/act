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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

enum AccDB { GENBANK, UNIPROT, SWISSPROT, TREMBL, EMBL };
class AccID { 
  AccDB db; String acc_num; 
  AccID(AccDB db, String a) { this.db = db; this.acc_num = a; } 
  @Override public String toString() { return this.db + ":" + this.acc_num; }
  @Override public int hashCode() { return this.db.hashCode() ^ this.acc_num.hashCode(); }
  @Override public boolean equals(Object other) {
    if (!(other instanceof AccID)) return false;
    AccID o = (AccID)other;
    return o.db == this.db && o.acc_num.equals(this.acc_num);
  }
}

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
    HashMap<Integer, Set<AccID>> rxnid2accession = new HashMap<Integer, Set<AccID>>();
    HashMap<AccID, Integer> accession2seqid = new HashMap<AccID, Integer>();

    for (Long uuid : db.getAllReactionUUIDs()) {
      Reaction r = db.getReactionFromUUID(uuid);
      Set<AccID> accessions = getAccessionNumbers(r.getReactionName());
      if (accessions.size() > 0)
      rxnid2accession.put(r.getUUID(), accessions);
    }

    for (Long seqid : db.getAllSeqUUIDs()) {
      Seq s = db.getSeqFromID(seqid);
      for (String acc : s.get_uniprot_accession())
        accession2seqid.put(new AccID(AccDB.SWISSPROT, acc), s.getUUID());
    }

    HashMap<AccID, String> unreviewed_acc = new HashMap<AccID, String>();
    for (Set<AccID> rxnaccessions : rxnid2accession.values()) {
      for (AccID rxnacc : rxnaccessions) {
        // first check if db.seq contains the mapping to sequence
        if (accession2seqid.containsKey(rxnacc))
          continue;

        // ELSE: maybe it is unreviewed, i.e., from TrEMBL/EMBL, 
        // we currently do not have that integrated (that is a 61.800GB)
        // we only have Swiss-Prot integrated (which was about  0.789GB)
        // TrEMBL entries: <entry dataset="TrEMBL" ...> (e.g., http://www.uniprot.org/uniprot/Q7XYH5.xml)
        // SwissProt     : <entry dataset="Swiss-Prot" ...> (e.g., http://www.uniprot.org/uniprot/Q14DK4.xml)
        // Later we can keep a local copy of the 61GB TrEMBL, but for
        // now we just call the web api to retrieve the 2715 accessions
        // that we cannot locate in SwissProt
        String apiget = web_lookup(rxnacc);
        if (!apiget.equals(""))
          unreviewed_acc.put(rxnacc, apiget);
      }
    }

    HashMap<Integer, Set<AccID>> unmapped_rxns = new HashMap<Integer, Set<AccID>>();
    for (Integer rid : rxnid2accession.keySet()) {
      Long rxnid = new Long(rid);
      for (AccID rxnacc : rxnid2accession.get(rid)) {
        // check if we have an AA sequence either in db.seq or pulled from TrEMBL
        if (!accession2seqid.containsKey(rxnacc) && !unreviewed_acc.containsKey(rxnacc)) {
          if (!unmapped_rxns.containsKey(rid)) unmapped_rxns.put(rid, new HashSet<AccID>());
          unmapped_rxns.get(rid).add(rxnacc);
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
  
      Set<String> no_map_for = new HashSet<String>();
      for (Integer rid : unmapped_rxns.keySet())
        no_map_for.add(rid + " -> " + unmapped_rxns.get(rid)); // not located in seq db, so no aa seq
      System.out.println(" Accessions in Brenda that could not be resolved : " + no_map_for);
      System.out.println("|Reactions  in Brenda that could not be resolved|: " + no_map_for.size());
      System.out.println("|Accessions that were found using web lookup    |: " + unreviewed_acc.size());
      Set<AccID> rxnSqs = new HashSet<AccID>(); 
      for (Set<AccID> seqs : rxnid2accession.values()) rxnSqs.addAll(seqs);
      System.out.format("%d reactions have %d unique sequences\n", rxnid2accession.keySet().size(), rxnSqs.size());
      System.out.format("%d swissprot entries\n", accession2seqid.keySet().size());
      if (_debug_level > 1) {
        for (Integer rid: rxnid2accession.keySet()) 
          System.out.format("rxnid(%s) -> %s\n", rid, rxnid2accession.get(rid));
        System.out.println("Swissprot accessions: " + accession2seqid.keySet());
      }
    }
  }

  private String web_lookup(AccID acc) {
    switch (acc.db) {
      case UNIPROT  : return web_uniprot(acc.acc_num); 
      case SWISSPROT: return web_uniprot(acc.acc_num); 
      case TREMBL   : 
        System.out.println("INFO: Looking up TREMBL           : " + acc.acc_num); 
        return web_uniprot(acc.acc_num); 
      case EMBL     : 
        System.out.println("INFO: Looking up EMBL             : " + acc.acc_num); 
        return web_uniprot(acc.acc_num); 
      case GENBANK  : 
        return web_genbank(acc.acc_num); 
      default:
        return "";
    }
  }

  private String web_uniprot(String accession) {
    System.out.println("API GET Request: " + accession);
    String url = "http://www.uniprot.org/uniprot/" + accession + ".xml";
    String idtag = "<accession>" + accession + "</accession>";
    String xml = api_get(url, new String[] { idtag });
    return xml;
  }

  private String web_genbank(String accession) {
    System.out.println("GenBank API GET Request: " + accession);
    String url = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=nuccore&id=" + accession + "&rettype=fasta&retmode=xml"; // retmode can also be json
    // documentation for eutils: http://www.ncbi.nlm.nih.gov/books/NBK25499/
    String idtag = accession;
    String xml = api_get(url, new String[] { idtag });
    System.out.println("GenBank XML: " + xml);
    return xml;
  }

  private String api_get(String url, String[] should_contain) {
    String response = "";
    try {
      InputStream resp = new URL(url).openStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(resp));
      String line; while ((line = br.readLine())!=null) response += line + "\n";
    } catch (Exception e) {}
    for (String test : should_contain)
      if (!response.contains(test))
        return ""; // failed test, unexpected response, return null
    return response;
  }

  private String word_before(String buffer, int anchor_index) {
    int end = buffer.lastIndexOf(' ', anchor_index - 1);
    int start = buffer.lastIndexOf(' ', end - 1);
    String word = buffer.substring(start, end).trim();
    return word;
  }

  private int add_words_before(AccDB suffix, String buffer, int start_at, Set<AccID> accumulator) {
    int added = 0;
    int idx = buffer.indexOf(suffix.name(), start_at);
    if (idx == -1) return added; // if no occurance found, return

    Set<AccID> accs_list = new HashSet<AccID>();

    // match of suffix at idx, check the word that appears before it
    String word = word_before(buffer, idx);
    accs_list.add(new AccID(suffix, word));

    // check if the prefix is a "and" list, e.g., "Kalanchoe pinnata Q33557 and Q43746 and P10797 UniProt"
    int list_idx = idx;
    while(true) { 
      list_idx = list_idx - word.length() - 1;
      String preword = word_before(buffer, list_idx);
      if (preword.equals("AND")) {
        list_idx -= 4; // move backwards for the matched "AND "
        word = word_before(buffer, list_idx);
        accs_list.add(new AccID(suffix, word));
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
    int recurse_added = add_words_before(suffix, buffer, idx + suffix.name().length(), accumulator);

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

  private Set<AccID> getAccessionNumbers(String desc) {
    Set<AccID> accs = new HashSet<AccID>();
    // search for strings such as 
    // " Q8TZI9 UniProt"
    // " P42527 SwissProt" 
    // " Q18NX4 TrEMBL" -- unreviewed
    // " O70151 GenBank" 
    // " Q9RLV9 EMBL"

    // add_words_before adds to the set of accessions "accs" and returns the delta count
    this.swissprot_n += add_words_before(AccDB.SWISSPROT, desc.toUpperCase(), 0, accs);
    this.uniprot___n += add_words_before(AccDB.UNIPROT  , desc.toUpperCase(), 0, accs);
    this.trembl____n += add_words_before(AccDB.TREMBL   , desc.toUpperCase(), 0, accs);
    this.embl______n += add_words_before(AccDB.EMBL     , desc.toUpperCase(), 0, accs);
    this.genbank___n += add_words_before(AccDB.GENBANK  , desc.toUpperCase(), 0, accs);

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
