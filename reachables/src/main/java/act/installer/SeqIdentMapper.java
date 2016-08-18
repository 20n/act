package act.installer;

import act.installer.sequence.GenBankEntry;
import act.installer.sequence.SequenceEntry;
import act.installer.sequence.SwissProtEntry;
import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.P;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  boolean LOOK_FOR_EC_ORG_IN_NCBI_PROTEIN = false;
  boolean SEQ_RXN_MAP_USING_EC_ORG_PMID_TRIPLE = false;

  private MongoDB db;
  private static final int _debug_level = 1; // 0 = no log; 1 = only main stats; 2 = all

  public SeqIdentMapper(MongoDB db) {
    this.db = db;
  }

  public void map() {

    /* This one (connect_using_fingerprint), maps db.actfamilies entries to db.seq entries by keying both on
     * "EC ORG PMID", i.e., ec# organism and pubmed literature ref.
     *
     * This might be useful later, in the corner cases where BRENDA people did not put an accession number there, but
     * the same paper talking about the same ec# and organism is a pretty decent signal that the sequence is the right
     * one. Later, when we resurrect this, we should see how many links this makes (that weren't already made with the
     * explicit accession numbers specified by the BRENDA people). And for those extra links this makes, manually
     * examine how accurate those mappings are."
     */
    if (SEQ_RXN_MAP_USING_EC_ORG_PMID_TRIPLE) {
      System.out.println("[MAP_SEQ] *** Phase 2: mapping using seq fingerprint");
      connect_using_fingerprint();
    } else {
      System.out.println("[MAP_SEQ] SKIPPING: mapping using seq fingerprint");
    }

    /* This one (connect_using_ncbi_protein_ec_org_lookup) is the most broad search for the sequence. It uses the EC#
     * and Organism as the keys to do a web lookup in NCBI Protein for a protein that matches that. Those might hit
     * hypothetical inferred proteins, or annotated proteins. NCBI Protein's description says: "The Protein database is
     * a collection of sequences from several sources, including translations from annotated coding regions in GenBank,
     * RefSeq and TPA, as well as records from SwissProt, PIR, PRF, and PDB. Protein sequences are the fundamental
     * determinants of biological structure and function."
     *
     * In theory that sounds great, if you want are looking to assign a sequence (and if you are ok with the confidence
     * in the sequence being low). In practice 1) This function does a web lookup, and does not cache the results across
     * different installer runs, 2) We already have all of SwissProt in db.seq, and have downloaded genbank to the NAS
     * (and would integrate that into db.seq soon), and we will integrate PDB entries soon as well; RefSeq, TPA, PIR,
     * PRF need to be looked into. So once these are local, we should do a local lookup rather than going through the
     * NCBI web api.
     */
    if (LOOK_FOR_EC_ORG_IN_NCBI_PROTEIN) {
      System.out.println("[MAP_SEQ] *** Phase 3: mapping using NCBI Protein ec# + org lookup");
      connect_using_ncbi_protein_ec_org_lookup();
    } else {
      System.out.println("[MAP_SEQ] SKIPPING: mapping using NCBI Protein ec# + org lookup");
    }

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
    for (int rxnid : rxnid2accession.keySet()) {
      System.out.println("Getting accessions in rxn: " + rxnid);
      Set<AccID> rxnaccessions = rxnid2accession.get(rxnid);
      System.out.println("Accessions: " + rxnaccessions);
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
        addToDB(rxnid, seqid);
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
    try {
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
        default:
          System.out.println("Unrecognized AccDB = " + acc.db);
          System.exit(-1);
          return null;
      }
      if (entries.size() > 1) {
        // System.out.println("Multiple entries: " + entries);
        System.out.println("XML from api call returned > 1 entry");
        // System.console().readLine();
      }
    } catch (IOException e) {
      // TODO: do better (propagate upwards probably).
      System.err.println("Caught IOException when attempting to look up accession number " +
          acc.acc_num + " in " + acc.db);
      e.printStackTrace(System.err);
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

  private String web_ncbiprotein(long id) {
    String url = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=protein&id=" + id + "&rettype=native&retmode=xml";
    // documentation for eutils: http://www.ncbi.nlm.nih.gov/books/NBK25499/
    String xml = api_get(url, new String[] { id + "" });
    System.out.println("API GET (NCBI Protein Genbank): " + id + " " + (!xml.equals("")?"success":"fail"));
    return xml;
  }

  private String web_ncbi(String ec, String organism) {
    String query = ec + "[EC/RN Number] AND " + organism + "[Primary Organism]";
    String url = null;
    try {
      url = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=protein&term=" + URLEncoder.encode(query, "UTF-8") + "&rettype=native&retmode=xml";
    } catch (Exception e) {
      System.out.println("[NCBI search] Could not encode query to url: " + query);
    }
    // documentation for eutils: http://www.ncbi.nlm.nih.gov/books/NBK25499/
    String xml = api_get(url, new String[] { "<Id>" });
    System.out.println("API GET (NCBI): " + ec + "/" + organism + " " + (!xml.equals("")?"success":"fail"));
    return xml;
  }

  private String api_get(String url, String[] should_contain) {
    String response = "";
    try {
      System.out.println("\t GET: " + url);
      InputStream resp = new URL(url).openStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(resp));
      String line; int lno = 0;
      while ((line = br.readLine())!=null) {
        response += line + "\n";
        if (lno++ > 5000) {
          // receiving more than 5k lines => probably means
          // the accession is for the entire genome; abandon
          System.out.println("[MAP_SEQ] >5k lines read. Abondoning fetch. " + url +
                             "Cause: We use rettype=native, instead of rettype=fasta. Use fasta for just the seq. Returned XML is formatted different, so GenBankEntry changes needed. See parsePossiblyMany there.");
          response = "";
          break;
        }
      }
      resp.close();
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
    // make sure that the suffix is by itself, and not within a word
    // e.g., we were crashing earlier because we matched
    // " {Homo sapiens} adenovirus 100K assembly protein + H2O -?> ?"
    // and were extracting '100K ass' from it because EMBL is a
    // substring of assEMBLy
    String pattern = " " + suffix.name().toUpperCase() + " ";

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
      addToDB(r2s.fst(), r2s.snd());

    System.out.format("Found SwissProt sequences for %d rxns\n", rxn2seq.size());
    System.out.format("   using exact matches: ref:%s, org:%s, ec:%s between db.actfamilies and db.seq\n", SeqFingerPrint.track_ref, SeqFingerPrint.track_org, SeqFingerPrint.track_ec);
  }

  class SequenceCache {
    String ec;
    String org;
  }

  private void connect_using_ncbi_protein_ec_org_lookup() {
    double done, total;

    System.out.println("[MAP_SEQ] NCBI EC+Org Lookup: installing seq <> rxn map");

    // read cache that is map "ec + org" -> Set(SequenceEntry)
    Map<String, Set<SequenceEntry>> cache = readCachedSeqs();

    List<Long> reactionids = db.getAllReactionUUIDs();
    done = 0; total = reactionids.size();
    for (Long uuid : reactionids) {
      Reaction r = db.getReactionFromUUID(uuid);

      Set<String> organisms = organismsForRxn(r);

      // now lookup the sequence mapping using ec# and these organisms
      try {
        // this can throw an exception if the data cannot be serialized
        // to the DB. in that case just ignore and continue to the next
        ncbi_protein_ec_org_lookup(uuid, r.getECNum(), organisms, cache);
      } catch (Exception e) {}
      System.out.format("[MAP_SEQ] Done: %.0f%% (%.0f/%.0f)\n", (100*done++/total), done, total);
    }
    System.out.println();

  }

  Set<String> organismsForRxn(Reaction r) {
    // OLD way of extracting organisms from Reaction does not work anymore
    // we have changed the act.shared.Reaction

    System.err.println("act.installer.SeqIdentMapper: ABORT");
    System.err.println("act.shared.Reaction has changed, and");
    System.err.println("organisms are not as directly within the Reaction object");
    System.exit(-1);

    return new HashSet<String>();
  }

  Map<String, Set<SequenceEntry>> readCachedSeqs() {
    Map<String, Set<SequenceEntry>> cache = new HashMap<String, Set<SequenceEntry>>();

    // TODO: WE NEED TO READ the cache from disk...
    // or from a local dump of NCBI protein...
    System.out.println("Resolving from NCBI protein is not fully implemented. TODO: (a) either read from Protein DB dump, (b) Read from web as code does, but write to disk so that we do not fetch in every run. Aborting.");
    System.exit(-1);

    return cache;
  }

  private String cacheId(String ec, String org) {
    return ec + " + " + org;
  }

  private void ncbi_protein_ec_org_lookup(Long rxnid, String ec, Set<String> organisms, Map<String, Set<SequenceEntry>> cache) {
    Seq.AccDB ncbidb = Seq.AccDB.ncbi_protein;

    Set<SequenceEntry> entries;
    Set<SequenceEntry> apiget_entries = new HashSet<SequenceEntry>();
    for (String org : organisms) {
      // check if this "ec + org" is already in the cache
      String cacheid = cacheId(ec, org);
      if (cache.containsKey(cacheid)) {
        entries = cache.get(cacheid);
      } else {
        String api_xml = web_ncbi(ec, org);
        if (!api_xml.isEmpty()) {
          // process the xml and get Set(SequenceEntry) out
          entries = genbankEntriesFromSearchRslts(api_xml);
        } else {
          // no xml or invalid xml returned, send out an empty hashmap
          entries = new HashSet<SequenceEntry>();
        }
      }

      apiget_entries.addAll(entries);
      System.out.println("[NCBI]\t" + ec + "\t" + org + "\t" + entries);
    }

    // TODO: WE NEED TO WRITE the fetched entries to disk cache...
    // See readCachedSeqs ABOVE!
    System.out.println("Resolving from NCBI protein is not fully implemented. TODO: (a) either read from Protein DB dump, (b) Read from web as code does, but write to disk so that we do not fetch in every run. Aborting.");
    System.exit(-1);

    for (SequenceEntry apiget : apiget_entries) {
      // insert the newly retrieved data from the web api into db.seq
      long seqid = apiget.writeToDB(this.db, ncbidb);

      // insert the mapping rxnid <-> seqid into the db
      addToDB(rxnid, seqid);
      System.out.println("Mapped rxn<>db.seq: " + rxnid + " <> " + seqid);
    }
  }

  private void addToDB(Long rxnid, Long seqid) {
    // The function below is deprecated now.
    // Instead the link between a rxn <> seq goes through
    // (organism, ec#) which identifies a sequence in db.seq
    // so a function like below addToDB(rxnid, seqid, orgid)
    // is more appropriate
    // db.addSeqRefToReactions(rxnid, seqid);
  }

  private void addToDB(Long rxnid, Long seqid, Long orgid) {
    // Need to call appropriate function in MongoDB
  }

  private Set<String> extractOrganisms(String desc) {
    // You can find all organisms referenced in brenda easy_desc fields using:
    // mongo localhost/actv01 --eval "rxns=db.actfamilies.find({},{easy_desc:1}); rxns.forEach(function (r) { print(r.easy_desc); });" > all_rxns.txt
    // cat all_rxns.txt | grep "{" | grep -v BiochemicalReaction | sed 's/^ *{\(.*\)} .*/\1/' | tr ',' '\n' | sort | uniq | cut -f1-2 -d ' '
    Set<String> organisms = new HashSet<String>();
    if (desc.contains("BiochemicalReaction"))
      return organisms;

    int start = desc.indexOf('{');
    int end = desc.indexOf('}', start);
    if (start == -1 || end == -1 || !desc.substring(0, start).trim().isEmpty())
      return organisms;

    String org_str = desc.substring(start + 1, end);
    String[] orgs = org_str.split(",");
    for (String org : orgs) {
      String org_name = genus_species(org.trim().split(" "));
      if (org_name != null)
        organisms.add(org_name);
    }
    return organisms;
  }

  private String genus_species(String[] org_words) {
    if (org_words[0].equals("unidentified") ||
        org_words[0].equals("uncultured") ||
        org_words[0].equals("null"))
      return null;
    if ( org_words[0].equals("synthetic") && org_words[1].equals("construct") ) return null;
    if ( org_words[0].equals("soil") && org_words[1].equals("organism") ) return null;
    if ( org_words[0].equals("soil") && org_words[1].equals("bacterium") ) return null;
    if ( org_words[0].equals("acetic") && org_words[1].equals("acid") ) return null;
    if ( org_words.length == 1 && org_words[0].equals("artificial") ) return null;

    if (org_words[0].equals("yeast"))
      return "Saccharomyces";

    if (org_words.length == 1)
      // only the genus specified, query just that
      return org_words[0];
    else if (org_words.length == 2 && (org_words[1].equals("sp") || org_words[1].equals("sp.")))
      // when the second is the generic "species" short form just query the genus
      return org_words[0];
    else
      // everything looks fine; return 1st word genus and 2nd species
      return org_words[0] + org_words[1];
  }

  public Set<SequenceEntry> genbankEntriesFromSearchRslts(String ncbi_xml) {
    Set<SequenceEntry> all_entries = new HashSet<SequenceEntry>();
    try {
      // example structure of this object "jo" is after this fn.
      JSONObject jo = XML.toJSONObject(ncbi_xml);
      // System.out.println("RECEIVED\n*******\n" + jo.toString(4) + "\n*******\n");
      JSONObject main = jo.getJSONObject("eSearchResult");
      int count = main.getInt("Count");

      if (count > 0) {
        // found some hits. their Id are under parsed.IdList.Id
        Object ids = main.getJSONObject("IdList").get("Id");

        JSONArray id_list;
        // parsed could be an array if more than one hit, or object
        // so wrap it into an array if required
        if (ids instanceof JSONArray)
          id_list = (JSONArray)ids;
        else
          id_list = new JSONArray(new Long[] { (Long)ids });

        for (int i = 0; i < id_list.length(); i++) {
          long entry_id = id_list.getLong(i);
          try {
            String genbank_xml = web_ncbiprotein(entry_id);
            // returns in Genbank xml format... parsed through GenbankEntry
            Set<SequenceEntry> entries = GenBankEntry.parsePossiblyMany(genbank_xml);
            all_entries.addAll(entries);
          } catch (JSONException je) { }
        }
      }
    } catch (JSONException je) {
      System.out.println("Failed NCBI Protein Entry parse: " + je.toString() + " XML: " + ncbi_xml);
    }
    return all_entries;
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



    Long[] orgids = new Long[0]; // r.getOrganismIDs();
    System.err.println("act.installer.SeqIdentMapper: ABORT");
    System.err.println("act.shared.Reaction has changed, and");
    System.err.println("organismIDs are not as directly within the Reaction object");
    System.exit(-1);

    List<String> orgs = new ArrayList<String>();
    // translate orgids to org_names
    for (Long oid : orgids) orgs.add(db.getOrganismNameFromId(oid));



    List<String> pmids = new ArrayList<String>(r.getReferences(Reaction.RefDataSource.PMID));
    return expansion(ec, orgs, pmids);
  }

  public static Set<SeqFingerPrint> createFrom(Seq s) {
    String ec = s.getEc();
    String org = s.getOrgName();
    List<String> orgs = new ArrayList<String>();
    orgs.add(org);

    /* The structure of get_references was changed from List<String> to List<JSONObject>. This loop converts PMID
    JSONObjects back into a List<String> of PMIDs. This allows for the expansion call to occur without error */
    List<String> references = new ArrayList<>();
    for (JSONObject obj : s.getReferences()) {
      if (obj.get("src").equals("PMID")) {
        references.add((String) obj.get("val"));
      }
    }
    return expansion(ec, orgs, references);
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


/*
--- output of:
--- NCBI protein query 1.2.1.50[EC/RN Number] AND Photobacterium leiognathi[Primary Organism] converted to json

  {
    "eSearchResult": {
      "Count": "2",
      "RetMax": "2",
      "RetStart": "0",
      "IdList": {
        "Id": [
          "547874",
          "126514"
        ]
      },
      "TranslationStack": {
        "TermSet": [
          {
            "Term": "1.2.1.50[EC/RN Number]",
            "Field": "EC/RN Number",
            "Count": "111",
            "Explode": "N"
          },
          {
            "Term": "Photobacterium leiognathi[Primary Organism]",
            "Field": "Primary Organism",
            "Count": "16377",
            "Explode": "Y"
          }
        ],
        "OP": "AND"
      },
      "QueryTranslation": "1.2.1.50[EC/RN Number] AND Photobacterium leiognathi[Primary Organism]"
    }
  }
*/


/*
--- output of:
--- curl -s "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=protein&id=547874&rettype=native&retmode=xml"

<?xml version="1.0"?>
 <!DOCTYPE Bioseq-set PUBLIC "-//NCBI//NCBI Seqset/EN" "http://www.ncbi.nlm.nih.gov/dtd/NCBI_Seqset.dtd">
 <Bioseq-set>
 <Bioseq-set_seq-set>
<Seq-entry>
  <Seq-entry_seq>
    <Bioseq>
      <Bioseq_id>
        <Seq-id>
          <Seq-id_swissprot>
            <Textseq-id>
              <Textseq-id_name>LUXC1_PHOLE</Textseq-id_name>
              <Textseq-id_accession>Q03324</Textseq-id_accession>
              <Textseq-id_release>reviewed</Textseq-id_release>
              <Textseq-id_version>1</Textseq-id_version>
            </Textseq-id>
          </Seq-id_swissprot>
        </Seq-id>
        <Seq-id>
          <Seq-id_gi>547874</Seq-id_gi>
        </Seq-id>
      </Bioseq_id>
      <Bioseq_descr>
        <Seq-descr>
          <Seqdesc>
            <Seqdesc_title>RecName: Full=Acyl-CoA reductase</Seqdesc_title>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_source>
              <BioSource>
                <BioSource_org>
                  <Org-ref>
                    <Org-ref_taxname>Photobacterium leiognathi</Org-ref_taxname>
                    <Org-ref_db>
                      <Dbtag>
                        <Dbtag_db>taxon</Dbtag_db>
                        <Dbtag_tag>
                          <Object-id>
                            <Object-id_id>553611</Object-id_id>
                          </Object-id>
                        </Dbtag_tag>
                      </Dbtag>
                    </Org-ref_db>
                    <Org-ref_orgname>
                      <OrgName>
                        <OrgName_name>
                          <OrgName_name_binomial>
                            <BinomialOrgName>
                              <BinomialOrgName_genus>Photobacterium</BinomialOrgName_genus>
                              <BinomialOrgName_species>leiognathi</BinomialOrgName_species>
                            </BinomialOrgName>
                          </OrgName_name_binomial>
                        </OrgName_name>
                        <OrgName_lineage>Bacteria; Proteobacteria; Gammaproteobacteria; Vibrionales; Vibrionaceae; Photobacterium</OrgName_lineage>
                        <OrgName_gcode>11</OrgName_gcode>
                        <OrgName_div>BCT</OrgName_div>
                      </OrgName>
                    </Org-ref_orgname>
                  </Org-ref>
                </BioSource_org>
              </BioSource>
            </Seqdesc_source>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_molinfo>
              <MolInfo>
                <MolInfo_biomol value="peptide">8</MolInfo_biomol>
                <MolInfo_completeness value="complete">1</MolInfo_completeness>
              </MolInfo>
            </Seqdesc_molinfo>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_pub>
              <Pubdesc>
                <Pubdesc_pub>
                  <Pub-equiv>
                    <Pub>
                      <Pub_gen>
                        <Cit-gen>
                          <Cit-gen_serial-number>1</Cit-gen_serial-number>
                        </Cit-gen>
                      </Pub_gen>
                    </Pub>
                    <Pub>
                      <Pub_pmid>
                        <PubMedId>8447834</PubMedId>
                      </Pub_pmid>
                    </Pub>
                    <Pub>
                      <Pub_article>
                        <Cit-art>
                          <Cit-art_title>
                            <Title>
                              <Title_E>
                                <Title_E_name>Nucleotide sequence of the luxC gene encoding fatty acid reductase of the lux operon from Photobacterium leiognathi.</Title_E_name>
                              </Title_E>
                            </Title>
                          </Cit-art_title>
                          <Cit-art_authors>
                            <Auth-list>
                              <Auth-list_names>
                                <Auth-list_names_std>
                                  <Author>
                                    <Author_name>
                                      <Person-id>
                                        <Person-id_name>
                                          <Name-std>
                                            <Name-std_last>Lin</Name-std_last>
                                            <Name-std_initials>J.W.</Name-std_initials>
                                          </Name-std>
                                        </Person-id_name>
                                      </Person-id>
                                    </Author_name>
                                    <Author_affil>
                                      <Affil>
                                        <Affil_str>Institute of Molecular Biology and Agricultural Biotechnology Laboratories, National Chung Hsing University, Taichung, Taiwan, R.O.C.</Affil_str>
                                      </Affil>
                                    </Author_affil>
                                  </Author>
                                  <Author>
                                    <Author_name>
                                      <Person-id>
                                        <Person-id_name>
                                          <Name-std>
                                            <Name-std_last>Chao</Name-std_last>
                                            <Name-std_initials>Y.F.</Name-std_initials>
                                          </Name-std>
                                        </Person-id_name>
                                      </Person-id>
                                    </Author_name>
                                  </Author>
                                  <Author>
                                    <Author_name>
                                      <Person-id>
                                        <Person-id_name>
                                          <Name-std>
                                            <Name-std_last>Weng</Name-std_last>
                                            <Name-std_initials>S.F.</Name-std_initials>
                                          </Name-std>
                                        </Person-id_name>
                                      </Person-id>
                                    </Author_name>
                                  </Author>
                                </Auth-list_names_std>
                              </Auth-list_names>
                            </Auth-list>
                          </Cit-art_authors>
                          <Cit-art_from>
                            <Cit-art_from_journal>
                              <Cit-jour>
                                <Cit-jour_title>
                                  <Title>
                                    <Title_E>
                                      <Title_E_iso-jta>Biochem. Biophys. Res. Commun.</Title_E_iso-jta>
                                    </Title_E>
                                    <Title_E>
                                      <Title_E_ml-jta>Biochem Biophys Res Commun</Title_E_ml-jta>
                                    </Title_E>
                                    <Title_E>
                                      <Title_E_issn>0006-291X</Title_E_issn>
                                    </Title_E>
                                    <Title_E>
                                      <Title_E_name>Biochemical and biophysical research communications</Title_E_name>
                                    </Title_E>
                                  </Title>
                                </Cit-jour_title>
                                <Cit-jour_imp>
                                  <Imprint>
                                    <Imprint_date>
                                      <Date>
                                        <Date_std>
                                          <Date-std>
                                            <Date-std_year>1993</Date-std_year>
                                            <Date-std_month>2</Date-std_month>
                                            <Date-std_day>26</Date-std_day>
                                          </Date-std>
                                        </Date_std>
                                      </Date>
                                    </Imprint_date>
                                    <Imprint_volume>191</Imprint_volume>
                                    <Imprint_issue>1</Imprint_issue>
                                    <Imprint_pages>314-318</Imprint_pages>
                                    <Imprint_language>eng</Imprint_language>
                                    <Imprint_pubstatus>
                                      <PubStatus value="ppublish">4</PubStatus>
                                    </Imprint_pubstatus>
                                    <Imprint_history>
                                      <PubStatusDateSet>
                                        <PubStatusDate>
                                          <PubStatusDate_pubstatus>
                                            <PubStatus value="pubmed">8</PubStatus>
                                          </PubStatusDate_pubstatus>
                                          <PubStatusDate_date>
                                            <Date>
                                              <Date_std>
                                                <Date-std>
                                                  <Date-std_year>1993</Date-std_year>
                                                  <Date-std_month>2</Date-std_month>
                                                  <Date-std_day>26</Date-std_day>
                                                </Date-std>
                                              </Date_std>
                                            </Date>
                                          </PubStatusDate_date>
                                        </PubStatusDate>
                                        <PubStatusDate>
                                          <PubStatusDate_pubstatus>
                                            <PubStatus value="medline">12</PubStatus>
                                          </PubStatusDate_pubstatus>
                                          <PubStatusDate_date>
                                            <Date>
                                              <Date_std>
                                                <Date-std>
                                                  <Date-std_year>1993</Date-std_year>
                                                  <Date-std_month>2</Date-std_month>
                                                  <Date-std_day>26</Date-std_day>
                                                  <Date-std_hour>0</Date-std_hour>
                                                  <Date-std_minute>1</Date-std_minute>
                                                </Date-std>
                                              </Date_std>
                                            </Date>
                                          </PubStatusDate_date>
                                        </PubStatusDate>
                                        <PubStatusDate>
                                          <PubStatusDate_pubstatus>
                                            <PubStatus value="other">255</PubStatus>
                                          </PubStatusDate_pubstatus>
                                          <PubStatusDate_date>
                                            <Date>
                                              <Date_std>
                                                <Date-std>
                                                  <Date-std_year>1993</Date-std_year>
                                                  <Date-std_month>2</Date-std_month>
                                                  <Date-std_day>26</Date-std_day>
                                                  <Date-std_hour>0</Date-std_hour>
                                                  <Date-std_minute>0</Date-std_minute>
                                                </Date-std>
                                              </Date_std>
                                            </Date>
                                          </PubStatusDate_date>
                                        </PubStatusDate>
                                      </PubStatusDateSet>
                                    </Imprint_history>
                                  </Imprint>
                                </Cit-jour_imp>
                              </Cit-jour>
                            </Cit-art_from_journal>
                          </Cit-art_from>
                          <Cit-art_ids>
                            <ArticleIdSet>
                              <ArticleId>
                                <ArticleId_pubmed>
                                  <PubMedId>8447834</PubMedId>
                                </ArticleId_pubmed>
                              </ArticleId>
                              <ArticleId>
                                <ArticleId_pii>
                                  <PII>S0006-291X(83)71219-2</PII>
                                </ArticleId_pii>
                              </ArticleId>
                              <ArticleId>
                                <ArticleId_doi>
                                  <DOI>10.1006/bbrc.1993.1219</DOI>
                                </ArticleId_doi>
                              </ArticleId>
                            </ArticleIdSet>
                          </Cit-art_ids>
                        </Cit-art>
                      </Pub_article>
                    </Pub>
                  </Pub-equiv>
                </Pubdesc_pub>
                <Pubdesc_comment>NUCLEOTIDE SEQUENCE [GENOMIC DNA].;~STRAIN=741</Pubdesc_comment>
              </Pubdesc>
            </Seqdesc_pub>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_comment>[FUNCTION] LuxC is the fatty acid reductase enzyme responsible for synthesis of the aldehyde substrate for the luminescent reaction catalyzed by luciferase.</Seqdesc_comment>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_comment>[CATALYTIC ACTIVITY] A long-chain aldehyde + CoA + NADP(+) = a long-chain acyl-CoA + NADPH.</Seqdesc_comment>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_comment>[PATHWAY] Lipid metabolism; fatty acid reduction for biolumincescence.</Seqdesc_comment>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_comment>[SIMILARITY] Belongs to the LuxC family. {ECO:0000305}.</Seqdesc_comment>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_sp>
              <SP-block>
                <SP-block_class value="standard"/>
                <SP-block_seqref>
                  <Seq-id>
                    <Seq-id_gi>45566</Seq-id_gi>
                  </Seq-id>
                  <Seq-id>
                    <Seq-id_gi>45567</Seq-id_gi>
                  </Seq-id>
                  <Seq-id>
                    <Seq-id_gi>419592</Seq-id_gi>
                  </Seq-id>
                </SP-block_seqref>
                <SP-block_dbref>
                  <Dbtag>
                    <Dbtag_db>ProteinModelPortal</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>Q03324</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>UniPathway</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>UPA00569</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>GO</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>GO:0003995</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>GO</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>GO:0050062</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>GO</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>GO:0008218</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>Gene3D</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>3.40.605.10</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>InterPro</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>IPR008670</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>InterPro</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>IPR016161</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>InterPro</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>IPR016162</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>Pfam</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>PF05893</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>PIRSF</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>PIRSF009414</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                  <Dbtag>
                    <Dbtag_db>SUPFAM</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_str>SSF53720</Object-id_str>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                </SP-block_dbref>
                <SP-block_keywords>
                  <SP-block_keywords_E>Luminescence</SP-block_keywords_E>
                  <SP-block_keywords_E>NADP</SP-block_keywords_E>
                  <SP-block_keywords_E>Oxidoreductase</SP-block_keywords_E>
                </SP-block_keywords>
                <SP-block_created>
                  <Date>
                    <Date_std>
                      <Date-std>
                        <Date-std_year>1994</Date-std_year>
                        <Date-std_month>6</Date-std_month>
                        <Date-std_day>1</Date-std_day>
                      </Date-std>
                    </Date_std>
                  </Date>
                </SP-block_created>
                <SP-block_sequpd>
                  <Date>
                    <Date_std>
                      <Date-std>
                        <Date-std_year>1994</Date-std_year>
                        <Date-std_month>6</Date-std_month>
                        <Date-std_day>1</Date-std_day>
                      </Date-std>
                    </Date_std>
                  </Date>
                </SP-block_sequpd>
                <SP-block_annotupd>
                  <Date>
                    <Date_std>
                      <Date-std>
                        <Date-std_year>2014</Date-std_year>
                        <Date-std_month>10</Date-std_month>
                        <Date-std_day>1</Date-std_day>
                      </Date-std>
                    </Date_std>
                  </Date>
                </SP-block_annotupd>
              </SP-block>
            </Seqdesc_sp>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_create-date>
              <Date>
                <Date_std>
                  <Date-std>
                    <Date-std_year>1994</Date-std_year>
                    <Date-std_month>6</Date-std_month>
                    <Date-std_day>1</Date-std_day>
                  </Date-std>
                </Date_std>
              </Date>
            </Seqdesc_create-date>
          </Seqdesc>
          <Seqdesc>
            <Seqdesc_update-date>
              <Date>
                <Date_std>
                  <Date-std>
                    <Date-std_year>2014</Date-std_year>
                    <Date-std_month>10</Date-std_month>
                    <Date-std_day>1</Date-std_day>
                  </Date-std>
                </Date_std>
              </Date>
            </Seqdesc_update-date>
          </Seqdesc>
        </Seq-descr>
      </Bioseq_descr>
      <Bioseq_inst>
        <Seq-inst>
          <Seq-inst_repr value="raw"/>
          <Seq-inst_mol value="aa"/>
          <Seq-inst_length>478</Seq-inst_length>
          <Seq-inst_seq-data>
            <Seq-data>
              <Seq-data_iupacaa>
                <IUPACaa>MIKKIPLIIGGEVQDTSEHDVRELTLNNNTVNVPIITDKDAESITSLKIENKLNINQIVNFLYTVGQKWKSENYSRRLTYIRDLVKFMGYSPEMAKLEANWISMILCSKSALYDIVENDLSSRHIVDEWLPQGDCYVKALPKGKSIHLLAGNVPLSGVTSILRAILTKNECIIKTSSADPFTATALASSFIDTDANHPITRSMSVMYWSHNEDITIPQKIMNCADVVVAWGGNDAIKWATKHSPAHVDILKFGPKKSISIVDNPTDIKAAAIGVAHDICFYDQQACFSTQDIYYMGDKLDVFFDELTKQLNIYKVILPKGDQSFDEKGAFSLTERECLFAKYKVQKGEEQAWLLTQSPAGTFGNQPLSRSAYIHHVNDISEITPYIQNDITQTVSITPWEASFKYRDTLASHGAERIIESGMNNIFRVGGAHDGMRPLQRLVKYISHERPSTYTTKDVAVKIEQTRYLEEDKFLVFVP</IUPACaa>
              </Seq-data_iupacaa>
            </Seq-data>
          </Seq-inst_seq-data>
          <Seq-inst_hist>
            <Seq-hist>
              <Seq-hist_replaces>
                <Seq-hist-rec>
                  <Seq-hist-rec_date>
                    <Date>
                      <Date_std>
                        <Date-std>
                          <Date-std_year>2005</Date-std_year>
                          <Date-std_month>7</Date-std_month>
                          <Date-std_day>26</Date-std_day>
                        </Date-std>
                      </Date_std>
                    </Date>
                  </Seq-hist-rec_date>
                  <Seq-hist-rec_ids>
                    <Seq-id>
                      <Seq-id_gi>419592</Seq-id_gi>
                    </Seq-id>
                  </Seq-hist-rec_ids>
                </Seq-hist-rec>
              </Seq-hist_replaces>
            </Seq-hist>
          </Seq-inst_hist>
        </Seq-inst>
      </Bioseq_inst>
      <Bioseq_annot>
        <Seq-annot>
          <Seq-annot_data>
            <Seq-annot_data_ftable>
              <Seq-feat>
                <Seq-feat_data>
                  <SeqFeatData>
                    <SeqFeatData_region>Mature chain</SeqFeatData_region>
                  </SeqFeatData>
                </Seq-feat_data>
                <Seq-feat_comment>Acyl-CoA reductase. /FTId=PRO_0000220196.</Seq-feat_comment>
                <Seq-feat_location>
                  <Seq-loc>
                    <Seq-loc_int>
                      <Seq-interval>
                        <Seq-interval_from>0</Seq-interval_from>
                        <Seq-interval_to>477</Seq-interval_to>
                        <Seq-interval_id>
                          <Seq-id>
                            <Seq-id_gi>547874</Seq-id_gi>
                          </Seq-id>
                        </Seq-interval_id>
                      </Seq-interval>
                    </Seq-loc_int>
                  </Seq-loc>
                </Seq-feat_location>
                <Seq-feat_exp-ev value="experimental"/>
              </Seq-feat>
              <Seq-feat>
                <Seq-feat_data>
                  <SeqFeatData>
                    <SeqFeatData_gene>
                      <Gene-ref>
                        <Gene-ref_locus>luxC</Gene-ref_locus>
                      </Gene-ref>
                    </SeqFeatData_gene>
                  </SeqFeatData>
                </Seq-feat_data>
                <Seq-feat_location>
                  <Seq-loc>
                    <Seq-loc_int>
                      <Seq-interval>
                        <Seq-interval_from>0</Seq-interval_from>
                        <Seq-interval_to>477</Seq-interval_to>
                        <Seq-interval_id>
                          <Seq-id>
                            <Seq-id_gi>547874</Seq-id_gi>
                          </Seq-id>
                        </Seq-interval_id>
                      </Seq-interval>
                    </Seq-loc_int>
                  </Seq-loc>
                </Seq-feat_location>
              </Seq-feat>
              <Seq-feat>
                <Seq-feat_data>
                  <SeqFeatData>
                    <SeqFeatData_prot>
                      <Prot-ref>
                        <Prot-ref_name>
                          <Prot-ref_name_E>Acyl-CoA reductase</Prot-ref_name_E>
                        </Prot-ref_name>
                        <Prot-ref_ec>
                          <Prot-ref_ec_E>1.2.1.50</Prot-ref_ec_E>
                        </Prot-ref_ec>
                      </Prot-ref>
                    </SeqFeatData_prot>
                  </SeqFeatData>
                </Seq-feat_data>
                <Seq-feat_location>
                  <Seq-loc>
                    <Seq-loc_int>
                      <Seq-interval>
                        <Seq-interval_from>0</Seq-interval_from>
                        <Seq-interval_to>477</Seq-interval_to>
                        <Seq-interval_id>
                          <Seq-id>
                            <Seq-id_gi>547874</Seq-id_gi>
                          </Seq-id>
                        </Seq-interval_id>
                      </Seq-interval>
                    </Seq-loc_int>
                  </Seq-loc>
                </Seq-feat_location>
                <Seq-feat_qual>
                  <Gb-qual>
                    <Gb-qual_qual>UniProtKB_evidence</Gb-qual_qual>
                    <Gb-qual_val>Inferred from homology</Gb-qual_val>
                  </Gb-qual>
                </Seq-feat_qual>
              </Seq-feat>
            </Seq-annot_data_ftable>
          </Seq-annot_data>
        </Seq-annot>
        <Seq-annot>
          <Seq-annot_db value="other">255</Seq-annot_db>
          <Seq-annot_name>Annot:CDD</Seq-annot_name>
          <Seq-annot_desc>
            <Annot-descr>
              <Annotdesc>
                <Annotdesc_name>CddSearch</Annotdesc_name>
              </Annotdesc>
              <Annotdesc>
                <Annotdesc_user>
                  <User-object>
                    <User-object_type>
                      <Object-id>
                        <Object-id_str>CddInfo</Object-id_str>
                      </Object-id>
                    </User-object_type>
                    <User-object_data>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>version</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_str>3.13</User-field_data_str>
                        </User-field_data>
                      </User-field>
                    </User-object_data>
                  </User-object>
                </Annotdesc_user>
              </Annotdesc>
              <Annotdesc>
                <Annotdesc_create-date>
                  <Date>
                    <Date_std>
                      <Date-std>
                        <Date-std_year>2015</Date-std_year>
                        <Date-std_month>1</Date-std_month>
                        <Date-std_day>6</Date-std_day>
                        <Date-std_hour>15</Date-std_hour>
                        <Date-std_minute>43</Date-std_minute>
                        <Date-std_second>14</Date-std_second>
                      </Date-std>
                    </Date_std>
                  </Date>
                </Annotdesc_create-date>
              </Annotdesc>
            </Annot-descr>
          </Seq-annot_desc>
          <Seq-annot_data>
            <Seq-annot_data_ftable>
              <Seq-feat>
                <Seq-feat_data>
                  <SeqFeatData>
                    <SeqFeatData_region>ALDH_Acyl-CoA-Red_LuxC</SeqFeatData_region>
                  </SeqFeatData>
                </Seq-feat_data>
                <Seq-feat_comment>Acyl-CoA reductase LuxC</Seq-feat_comment>
                <Seq-feat_location>
                  <Seq-loc>
                    <Seq-loc_int>
                      <Seq-interval>
                        <Seq-interval_from>29</Seq-interval_from>
                        <Seq-interval_to>444</Seq-interval_to>
                        <Seq-interval_id>
                          <Seq-id>
                            <Seq-id_gi>547874</Seq-id_gi>
                          </Seq-id>
                        </Seq-interval_id>
                      </Seq-interval>
                    </Seq-loc_int>
                  </Seq-loc>
                </Seq-feat_location>
                <Seq-feat_ext>
                  <User-object>
                    <User-object_type>
                      <Object-id>
                        <Object-id_str>cddScoreData</Object-id_str>
                      </Object-id>
                    </User-object_type>
                    <User-object_data>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>domain_from</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>0</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>domain_to</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>421</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>definition</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_str>cd07080</User-field_data_str>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>short_name</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_str>ALDH_Acyl-CoA-Red_LuxC</User-field_data_str>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>score</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>1148</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>evalue</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_real>2.97592e-153</User-field_data_real>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>bit_score</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_real>445.954</User-field_data_real>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>specific</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_bool value="true"/>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>superfamily</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_str>cl11961</User-field_data_str>
                        </User-field_data>
                      </User-field>
                    </User-object_data>
                  </User-object>
                </Seq-feat_ext>
                <Seq-feat_dbxref>
                  <Dbtag>
                    <Dbtag_db>CDD</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_id>143399</Object-id_id>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                </Seq-feat_dbxref>
              </Seq-feat>
              <Seq-feat>
                <Seq-feat_data>
                  <SeqFeatData>
                    <SeqFeatData_site value="active"/>
                  </SeqFeatData>
                </Seq-feat_data>
                <Seq-feat_comment>putative catalytic cysteine [active]</Seq-feat_comment>
                <Seq-feat_location>
                  <Seq-loc>
                    <Seq-loc_mix>
                      <Seq-loc-mix>
                        <Seq-loc>
                          <Seq-loc_pnt>
                            <Seq-point>
                              <Seq-point_point>285</Seq-point_point>
                              <Seq-point_id>
                                <Seq-id>
                                  <Seq-id_gi>547874</Seq-id_gi>
                                </Seq-id>
                              </Seq-point_id>
                            </Seq-point>
                          </Seq-loc_pnt>
                        </Seq-loc>
                      </Seq-loc-mix>
                    </Seq-loc_mix>
                  </Seq-loc>
                </Seq-feat_location>
                <Seq-feat_ext>
                  <User-object>
                    <User-object_type>
                      <Object-id>
                        <Object-id_str>cddSiteScoreData</Object-id_str>
                      </Object-id>
                    </User-object_type>
                    <User-object_data>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>completeness</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_real>1</User-field_data_real>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>feature-ID</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>0</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>specific</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_bool value="true"/>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>nonredundant</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_bool value="true"/>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>definition</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_str>cd07080</User-field_data_str>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>short_name</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_str>ALDH_Acyl-CoA-Red_LuxC</User-field_data_str>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>from</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>29</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>to</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>444</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>score</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_int>1148</User-field_data_int>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>evalue</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_real>2.97592e-153</User-field_data_real>
                        </User-field_data>
                      </User-field>
                      <User-field>
                        <User-field_label>
                          <Object-id>
                            <Object-id_str>bit_score</Object-id_str>
                          </Object-id>
                        </User-field_label>
                        <User-field_data>
                          <User-field_data_real>445.954</User-field_data_real>
                        </User-field_data>
                      </User-field>
                    </User-object_data>
                  </User-object>
                </Seq-feat_ext>
                <Seq-feat_dbxref>
                  <Dbtag>
                    <Dbtag_db>CDD</Dbtag_db>
                    <Dbtag_tag>
                      <Object-id>
                        <Object-id_id>143399</Object-id_id>
                      </Object-id>
                    </Dbtag_tag>
                  </Dbtag>
                </Seq-feat_dbxref>
              </Seq-feat>
            </Seq-annot_data_ftable>
          </Seq-annot_data>
        </Seq-annot>
      </Bioseq_annot>
    </Bioseq>
  </Seq-entry_seq>
</Seq-entry>

</Bioseq-set_seq-set>
 </Bioseq-set>
*/
