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
import org.json.XML;
import org.json.JSONObject;

import act.installer.swissprot.SequenceEntry;
import act.installer.swissprot.SwissProtEntry;
import act.installer.swissprot.GenBankEntry;

class AccID { 
  Seq.AccDB db; String acc_num; 
  AccID(Seq.AccDB db, String a) { this.db = db; this.acc_num = a; } 
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
    System.out.println("[MAP_SEQ] *** Phase 1: mapping using brenda annotations");
    connect_using_explicit_brenda_accession_annotation();
    System.out.println("[MAP_SEQ] *** Phase 2: mapping using seq fingerprint");
    connect_using_fingerprint();
  }

  private void connect_using_explicit_brenda_accession_annotation() {
    HashMap<Integer, Set<AccID>> rxnid2accession = new HashMap<Integer, Set<AccID>>();
    HashMap<AccID, Integer> accession2seqid = new HashMap<AccID, Integer>();
    double done, total;

    System.out.println("[MAP_SEQ] mapping all reactions to accession numbers");
    List<Long> reactionids = db.getAllReactionUUIDs();
    done = 0; total = reactionids.size(); 
    for (Long uuid : reactionids) {
      Reaction r = db.getReactionFromUUID(uuid);
      Set<AccID> accessions = getAccessionNumbers(r.getReactionName());
      if (accessions.size() > 0)
        rxnid2accession.put(r.getUUID(), accessions);
      System.out.format("[MAP_SEQ] Done: %.0f%%\r", (100*done++/total));
    }
    System.out.println();

    System.out.println("[MAP_SEQ] mapping all sequences to accession numbers");
    List<Long> seqids = db.getAllSeqUUIDs();
    done = 0; total = seqids.size(); 
    for (Long seqid : seqids) {
      Seq s = db.getSeqFromID(seqid);
      for (String acc : s.get_uniprot_accession())
        accession2seqid.put(new AccID(s.get_srcdb(), acc), s.getUUID());
      System.out.format("[MAP_SEQ] Done: %.0f%%\r", (100*done++/total));
    }
    System.out.println();

    System.out.println("[MAP_SEQ] resolving unmapped accessions from web api");
    HashSet<AccID> from_web_lookup = new HashSet<AccID>();
    for (Set<AccID> rxnaccessions : rxnid2accession.values()) {
      for (AccID rxnacc : rxnaccessions) {
        // first check if db.seq contains the mapping to sequence
        if (accession2seqid.containsKey(rxnacc))
          continue;

        // ELSE: maybe it is unreviewed, i.e., from TrEMBL/EMBL, 
        // we currently do not have that integrated (that is a 61.800GB)
        // we only have Swiss-Prot integrated (which was about  0.789GB)
        // TrEMBL entries: <entry dataset="TrEMBL" ...> 
        //               : E.g., http://www.uniprot.org/uniprot/Q7XYH5.xml)
        // SwissProt     : <entry dataset="Swiss-Prot" ...>
        //               : E.g., http://www.uniprot.org/uniprot/Q14DK4.xml)
        // Later we can keep a local copy of the 61GB TrEMBL, but for
        // now we just call the web api to retrieve the 2715 accessions
        // that we cannot locate in SwissProt
        // System.out.println("Did not find in db.seq. Doing web lookup: " + rxnacc);
        Set<SequenceEntry> apiget_entries = web_lookup(rxnacc);
        for (SequenceEntry apiget : apiget_entries) {
          // insert the newly retrieved data from the web api into db.seq
          int seqid = apiget.writeToDB(this.db, rxnacc.db);

          for (String acc_num : db.getSeqFromID(new Long(seqid)).get_uniprot_accession()) {
            AccID ret_acc = new AccID(rxnacc.db, acc_num);
            // update the map of accession2seqid
            accession2seqid.put(ret_acc, seqid);
            from_web_lookup.add(ret_acc);
          }
        }
      }
    }

    HashMap<Integer, Set<AccID>> unmapped_rxns = new HashMap<Integer, Set<AccID>>();
    for (Integer rid : rxnid2accession.keySet()) {
      Long rxnid = new Long(rid);
      for (AccID rxnacc : rxnid2accession.get(rid)) {
        // check if we have an AA sequence either db.seq
        if (!accession2seqid.containsKey(rxnacc)) {
          if (!unmapped_rxns.containsKey(rid)) 
            unmapped_rxns.put(rid, new HashSet<AccID>());
          unmapped_rxns.get(rid).add(rxnacc);
          continue;
        }
        Long seqid = new Long(accession2seqid.get(rxnacc));

        // insert the mapping rxnid <-> seqid into the db
        db.addSeqRefToReactions(rxnid, seqid); 
      }
    }

    if (_debug_level > 0) {
      Set<AccID> extractedAcc = new HashSet<AccID>();
      for (Set<AccID> as : rxnid2accession.values()) extractedAcc.addAll(as);
      System.out.println("SwissProt: " + count_type(Seq.AccDB.swissprot , extractedAcc));
      System.out.println("UniProt  : " + count_type(Seq.AccDB.uniprot   , extractedAcc));
      System.out.println("TrEMBL   : " + count_type(Seq.AccDB.trembl    , extractedAcc));
      System.out.println("EMBL     : " + count_type(Seq.AccDB.embl      , extractedAcc));
      System.out.println("GenBank  : " + count_type(Seq.AccDB.genbank   , extractedAcc));
  
      Set<String> no_map_for = new HashSet<String>();
      for (Integer rid : unmapped_rxns.keySet())
        no_map_for.add(rid + " -> " + unmapped_rxns.get(rid)); // not located in seq db, so no aa seq
      System.out.println(" Brenda Accessions that could not be resolved : " + no_map_for);
      System.out.println("|Breada Reactions  that could not be resolved|: " + no_map_for.size());
      System.out.println("|Accessions that were found using web lookup |: " + from_web_lookup.size());
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

  private int count_type(Seq.AccDB db, Set<AccID> ids) {
    int c = 0; for (AccID a : ids) if (db == a.db) c++;
    return c;
  }

  private Set<SequenceEntry> web_lookup(AccID acc) {
    Set<SequenceEntry> entries = new HashSet<SequenceEntry>();
    switch (acc.db) {
      case swissprot: // fallthrough
      case uniprot:   // fallthrough
      case embl:      // fallthrough
      case trembl:    
        String api_xml = web_uniprot(acc.acc_num); 
        entries = SwissProtEntry.parsePossiblyMany(api_xml);
        break;
      case genbank:
        String try_uniprot = web_uniprot(acc.acc_num);
        if (!try_uniprot.equals("")) {
          api_xml = try_uniprot;
          entries = SwissProtEntry.parsePossiblyMany(api_xml);
        } else { 
          api_xml = web_genbank(acc.acc_num); 
          entries = GenBankEntry.parsePossiblyMany(api_xml);
        }
        break;
      default: System.out.println("Unrecognized AccDB = " + acc.db); System.exit(-1); return null;
    }
    if (entries.size() > 1) {
      // System.out.println("Multiple entries: " + entries);
      System.out.println("XML from api call returned > 1 entry");
      // System.console().readLine();
    }
    return entries; 
  }

  private String web_uniprot(String accession) {
    String url = "http://www.uniprot.org/uniprot/" + accession + ".xml";
    String idtag = accession;
    String xml = api_get(url, new String[] { idtag });
    System.out.println("API GET (UniProt): " + accession + " " + (!xml.equals("")?"success":"fail"));
    return xml;
  }

  private String web_genbank(String accession) {
    String url = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=nuccore&id=" + accession + "&rettype=native&retmode=xml"; // retmode can also be json
    // documentation for eutils: http://www.ncbi.nlm.nih.gov/books/NBK25499/
    String idtag = accession;
    String xml = api_get(url, new String[] { idtag });
    System.out.println("API GET (GenBank): " + accession + " " + (!xml.equals("")?"success":"fail"));
    return xml;
  }

  private String api_get(String url, String[] should_contain) {
    String response = "";
    try {
      InputStream resp = new URL(url).openStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(resp));
      String line; int lno = 0;
      while ((line = br.readLine())!=null) {
        response += line + "\n";
        if (lno++ > 10000) {
          // receiving more than 50k lines => probably means 
          // the accession is for the entire genome; abandon
          System.out.println("[MAP_SEQ] >10k lines read. Abondoning fetch. " + url);
          response = "";
          break;
        }
      }
    } catch (Exception e) {}
    for (String test : should_contain) {
      if (!response.contains(test)) {
        // System.out.format("Failed to find [%s] in xml: %s\n", test, response.substring(0, Math.min(400, response.length())));
        return ""; // failed test, unexpected response
      }
    }
    return response;
  }

  private String word_before(String buffer, int anchor_index) {
    int end = buffer.lastIndexOf(' ', anchor_index - 1);
    int start = buffer.lastIndexOf(' ', end - 1);
    String word = buffer.substring(start, end).trim();
    return word;
  }

  private void add_words_before(Seq.AccDB suffix, String buffer, int start_at, Set<AccID> accumulator) {
    String pattern = suffix.name().toUpperCase();

    int added = 0;
    int idx = buffer.indexOf(pattern, start_at);
    if (idx == -1) return; // if no occurance found, return

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

    if (_debug_level > 1) {
      System.out.format("Accession refs found: %s: %s\n", suffix, accs_list);
      // System.out.format("\tFrom sentence: %s\n\tParsed: %s\n", buffer, accs_list);
    }
    
    // recurse to after where the current suffix was found
    add_words_before(suffix, buffer, idx + pattern.length(), accumulator);

    return;
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
    add_words_before(Seq.AccDB.swissprot, desc.toUpperCase(), 0, accs);
    add_words_before(Seq.AccDB.uniprot  , desc.toUpperCase(), 0, accs);
    add_words_before(Seq.AccDB.trembl   , desc.toUpperCase(), 0, accs);
    add_words_before(Seq.AccDB.embl     , desc.toUpperCase(), 0, accs);
    add_words_before(Seq.AccDB.genbank  , desc.toUpperCase(), 0, accs);

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
    double done, total;

    // take entries from db.actfamilies
    // map them to (ref_set, org_set, ec)
    // if (ref, org, ec) matches an entry in db.seq
    // map that sequence to the actfamilies entry

    System.out.println("[MAP_SEQ] mapping reactions -> (ec, org, pmid)");
    // Populate rxnIdent
    List<Long> reactionids = db.getAllReactionUUIDs();
    done = 0; total = reactionids.size();
    for (Long uuid : reactionids) {
      Reaction r = db.getReactionFromUUID(uuid);
      Set<SeqFingerPrint> si = SeqFingerPrint.createFrom(r, db);
      rxnIdent.put(uuid, si);
      System.out.format("[MAP_SEQ] Done: %.0f%%\r", (100*done++/total));
    }
    System.out.println();

    System.out.println("[MAP_SEQ] mapping sequences -> (ec, org, pmid)");
    // Populate seqIdent
    List<Long> seqids = db.getAllSeqUUIDs();
    done = 0; total = seqids.size();
    for (Long seqid : seqids) {
      Seq s = db.getSeqFromID(seqid);
      Set<SeqFingerPrint> si = SeqFingerPrint.createFrom(s);
      seqIdent.put(seqid, si);
      System.out.format("[MAP_SEQ] Done: %.0f%%\r", (100*done++/total));
    }
    System.out.println();

    // SeqIndent holds the (ref, org, ec) -> inferReln find connections
    Set<P<Long, Long>> rxn2seq = SeqFingerPrint.inferReln(rxnIdent, seqIdent);

    // for each pair (rxnid, seqid) in rxn2seq
    // insert the mapping rxnid <-> seqid into the db
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
    System.out.println("[MAP_SEQ] Intersecting maps of reactions and sequences)");
    // inverting the hashmaps gets to a O(n) intersection 
    // algorithm, as opposed to O(n^2) otherwise
    HashMap<SeqFingerPrint, Set<I>> A_inv = invert_map(A);
    HashMap<SeqFingerPrint, Set<I>> B_inv = invert_map(B);
    double total = A_inv.size(), done = 0;
    for (SeqFingerPrint a : A_inv.keySet()) {
      System.out.format("Done: %.2f%%\r", 100*(done++/total));
      if (!B_inv.containsKey(a)) continue;
      // shared fingerprint found. means for each of the I a, and I b
      // that shared this in their original mapped sets, we have a -> b
      for (I a_key : A_inv.get(a))
        for (I b_key : B_inv.get(a))
          reln.add(new P<I, I>(a_key, b_key));
    }
    System.out.println();

    // System.out.println("Performance bug: This is an O(n^2) older version of the above");
    // total = A.size() * B.size(); done = 0;
    // for (I a_key : A.keySet()) {
    //   for (I b_key : B.keySet()) {
    //     System.out.format("Done: %.2f%%\r", 100*(done++/total));
    //     if (! intersect(A.get(a_key), B.get(b_key)).isEmpty()) {
    //       reln.add(new P<I, I>(a_key, b_key));
    //     }
    //   }
    // }
    // System.out.println();

    return reln;
  }

  public static <I, X> HashMap<X, Set<I>> invert_map(HashMap<I, Set<X>> map) {
    HashMap<X, Set<I>> inverted = new HashMap<X, Set<I>>();
    for (I i : map.keySet()) {
      for (X x : map.get(i)) {
        if (!inverted.containsKey(x))
          inverted.put(x, new HashSet<I>());
        inverted.get(x).add(i);
      }
    }
    return inverted;
  }

  public static <X> Set<X> intersect(Set<X> set1, Set<X> set2) {
    boolean set1IsLarger = set1.size() > set2.size();
    Set<X> cloneSet = new HashSet<X>(set1IsLarger ? set2 : set1);
    cloneSet.retainAll(set1IsLarger ? set1 : set2);
    return cloneSet;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SeqFingerPrint)) return false;
    SeqFingerPrint that = (SeqFingerPrint)o;

    // we dont want to assign two fingerprints as equal if one of them is null
    if (this.ref == null || this.ec == null || this.org == null) return false;

    return
        this.ref.equals(that.ref) &&
        this.ec.equals(that.ec) &&
        this.org.equals(that.org);
  }

  @Override
  public int hashCode() {
    int hash = "magic".hashCode();
    if (this.ref != null) hash ^= this.ref.hashCode(); 
    if (this.ec != null) hash ^= this.ec.hashCode();
    if (this.org != null) hash ^= this.org.hashCode();
    return hash;
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
