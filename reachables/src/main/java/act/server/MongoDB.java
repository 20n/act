package act.server;

import act.installer.bing.MoleculeNames;
import act.installer.bing.UsageTermUrlSet;
import act.shared.ConsistentInChI;
import act.shared.Chemical;
import act.shared.Cofactor;
import act.shared.Chemical.REFS;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.helpers.P;
import act.shared.sar.SAR;
import act.shared.sar.SARConstraint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.Bytes;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;
import org.apache.commons.lang3.StringUtils;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


public class MongoDB {

  private static ObjectMapper mapper = new ObjectMapper();

  private String hostname;
  private String database;
  private int port;

  private DBCollection dbReactions; 
  private DBCollection dbChemicals;
  private DBCollection dbCofactors;
  private DBCollection dbOrganisms;
  private DBCollection dbOrganismNames;
  private DBCollection dbCascades;
  private DBCollection dbWaterfalls;
  private DBCollection dbSeq;
  private DBCollection dbPubmed; // the pubmed collection is separate from actv01 db

  private DB mongoDB;
  private Mongo mongo;

  public MongoDB(String mongoActHost, int port, String dbs) {
    this.hostname = mongoActHost;
    this.port = port;
    this.database = dbs;

    initDB();
  }


  public static void dropDB(String mongoActHost, int port, String dbs) {
    dropDB(mongoActHost, port, dbs, false);
  }

  public static void dropDB(String mongoActHost, int port, String dbs, boolean force) {
    try {
      DB toDropDB = new Mongo(mongoActHost, port).getDB(dbs);

      if (!force) {
        // Require explicit confirmation from the user before dropping an existing DB.
        System.out.format("Going to drop: %s:%d/%s. Type \"DROP\" (without quotes) and press enter to proceed.\n",
            mongoActHost, port, dbs);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
          String readLine = reader.readLine();
          if (!"DROP".equals(readLine)) {
            System.out.format("Invalid input \"%s\", not dropping DB\n", readLine);
          } else {
            System.out.format("Dropping DB\n");
            // drop DB!
            toDropDB.dropDatabase();
          }
        }
      } else {
        System.out.format("[Force] Dropping DB %s\n", dbs);
        toDropDB.dropDatabase();
      }
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid host for Mongo Act server.");
    } catch (MongoException e) {
      throw new IllegalArgumentException("Could not initialize Mongo driver.");
    } catch (IOException e) {
      throw new RuntimeException("Unable to read from stdin");
    }
  }

  public MongoDB(String host) {
    this.hostname = host;
    this.port = 27017;
    this.database = "actv01"; // default act database; this constructor is rarely, if ever called.
    initDB();
  }

  public MongoDB() {
    this.hostname = "localhost";
    this.port = 27017;
    this.database = "actv01"; // default act database; this constructor is rarely, if ever called.
    initDB();
  }

  public String toString(){
    return this.hostname+" "+this.port;
  }

  public void close() {
    this.mongo.close();
  }

  private void initDB() {
    try {
      mongo = new Mongo(this.hostname, this.port);
      mongoDB = mongo.getDB(this.database);

      // in case the db is protected then we would do the following:
      // boolean auth = db.authenticate(myUserName, myPassword);
      // but right now we do not care.

      this.dbReactions = mongoDB.getCollection("reactions");
      this.dbChemicals = mongoDB.getCollection("chemicals");
      this.dbCofactors = mongoDB.getCollection("cofactors");
      this.dbOrganisms = mongoDB.getCollection("organisms");
      this.dbOrganismNames = mongoDB.getCollection("organismnames");
      this.dbSeq = mongoDB.getCollection("seq");
      this.dbCascades = mongoDB.getCollection("cascades");
      this.dbWaterfalls = mongoDB.getCollection("waterfalls");
      this.dbPubmed = mongoDB.getCollection("pubmed");

      initIndices();
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid host for Mongo Act server.");
    } catch (MongoException e) {
      throw new IllegalArgumentException("Could not initialize Mongo driver.");
    }
  }

  private void initIndices() {

    this.createChemicalsIndex("InChI", true);    // create a hashed index
    this.createChemicalsIndex("InChIKey");       // create a normal index
    this.createChemicalsIndex("names.brenda");   // create a normal index
    this.createChemicalsIndex("names.pubchem.values"); // normal index
    this.createChemicalsIndex("names.synonyms"); // create a normal index

    this.createCofactorsIndex("InChI", true);    // create a hashed index

    this.createOrganismNamesIndex("name");
    this.createOrganismNamesIndex("org_id");
  }

  public int port() {
    return this.port;
  }

  public String host() {
    return this.hostname;
  }

  public String dbs() {
    return this.database;
  }

  public String location() {
    return this.hostname + "." + this.port + "." + this.database;
  }

  private String getReactantFromMongoDocument(BasicDBObject family, String which, int i) {
    BasicDBList o = (BasicDBList)((DBObject)family.get("enz_summary")).get(which);
    if (i >= o.size())
      return "";
    return "" + (Long)((DBObject)o.get(i)).get("pubchem");
  }

  /*
     Sanity checks against a ref DB, returns:
     - P<List, List>: pair(added, deleted) in this over ref DB, list of ids (Object)
     - Map<Object, DBObject>: id->object map of changed docs
     */
  public static P<P<List, List>, Map<Object, Object>> compare(String coll, String id_key, int thisport, int refport, boolean listsAreSet) throws UnknownHostException {
    String host = "localhost";
    String dbs = "actv01";

    List<Object> add = new ArrayList<Object>();
    List<Object> del = new ArrayList<Object>();
    Set<Object> seen = new HashSet<Object>();
    Map<Object, Object> upd = new HashMap<Object, Object>();

    DBCollection c = new Mongo(host, thisport).getDB( dbs ).getCollection(coll);
    DBCollection cref = new Mongo(host, refport).getDB( dbs ).getCollection(coll);

    // yes, we indeed need to iterate over the entire collection! so unrestricted find() ok here.
    DBCursor cur = c.find();
    while (cur.hasNext()) {
      DBObject doc = cur.next();
      Object id = doc.get(id_key);

      DBObject docref = findOneDoc(cref, id_key, id);
      if (docref == null) {
        // reference collection does not have doc, log as newly created
        add.add(id);
      } else {
        // reference collection has doc:
        // compare the differences between these two docs and log it as updated if they differ

        Object diff = compare(doc, docref, listsAreSet);
        if (diff != null) {
          // the docs differ. Log it as updated, and note the diff
          upd.put(id, diff);
        }
      }
      seen.add(id);
    }

    // now iterate over ref db and see if there are any docs deleted (i.e., not in notDeleted)
    DBCursor curref = c.find();
    while (curref.hasNext()) {
      DBObject doc = curref.next();
      Object id = doc.get(id_key);

      if (!seen.contains(id)) {
        // this doc was not seen in the updated collection, so deleted. log that
        del.add(id);
      }
    }

    return new P<P<List, List>, Map<Object, Object>>(new P<List, List>(add, del), upd);
  }

  private static DBObject findOneDoc(DBCollection c, String id_key, Object id) {
    BasicDBObject query = new BasicDBObject();
    query.put(id_key, id);
    DBObject res = c.findOne(query);
    return res;
  }

  private static Object compare(Object d, Object dref, boolean listsAreSet) {
    if (d == null && dref == null)
      return null; // identical; return null which indicates identicalicity
    else if (d == null && dref != null)
      return "+" + dref;
    else if (d != null && dref == null)
      return "-" + d;

    if ((d instanceof Long && dref instanceof Long) ||
        (d instanceof Double && dref instanceof Double) ||
        (d instanceof Integer && dref instanceof Integer) ||
        (d instanceof Boolean && dref instanceof Boolean) ||
        (d instanceof String && dref instanceof String))
      return compare_primitive(d, dref);
    else if (d instanceof BasicDBList && dref instanceof BasicDBList)
      return compare((BasicDBList) d, (BasicDBList) dref, listsAreSet);
    else if (d instanceof DBObject && dref instanceof DBObject)
      return compare((DBObject) d, (DBObject) dref, listsAreSet);
    else {
      System.out.println("+" + d);
      System.out.println("-" + dref);
      System.out.println();
      return "TYPEDIFF: +" + d.getClass().getName() + " vs -" + dref.getClass().getName();
    }
  }

  private static Object compare_primitive(Object p, Object pref) {
    return p.equals(pref) ? null : "+" + p + " vs -" + pref;
  }

  private static DBObject compare(DBObject doc, DBObject docref, boolean listsAreSet) {
    boolean different = false;

    BasicDBObject diff = new BasicDBObject();
    Set<String> refKeys = new HashSet<String>();
    refKeys.addAll(docref.keySet());
    for (String k : doc.keySet()) {
      // as numerical calculations are improved, some computed fields are
      // bound to change: e.g., rarity and estimateEnergy
      // so make a special exception for those and ignore its val field...
      // but compare any other key recursively for differences...
      if (k.equals("rarity") || k.equals("estimateEnergy") || k.equals("coefficient"))
        continue;

      Object val = doc.get(k);

      if(!docref.containsKey(k)) {
        // this field is new
        diff.put("+" + k, val);
        different = true;
      } else {
        // field exists in old doc, recursively compare
        Object refval = docref.get(k);
        refKeys.remove(k);

        Object d;
          if ((d = compare(val, refval, listsAreSet)) != null) {
          // keys identical but values differ, add without the + or - to key
          different = true;
          diff.put(k, d);
        } else {
          // values identical and keys same too, do not put in diff.
        }
      }
    }

    // all remaining fields were deleted from old doc
    for (String kref : refKeys) {
      if (kref.equals("rarity") || kref.equals("estimateEnergy") || kref.equals("coefficient")) // see why in loop above
        continue;

      diff.put("-" + kref, docref.get(kref));
      different = true;
    }

    return different ? diff : null;

    // the following is not order invariant and therefore problematic:
    // return org.apache.commons.lang.StringUtils.difference(doc.toString(), docref.toString());
  }

  private static BasicDBList compare(BasicDBList l, BasicDBList refl, boolean listsAreSet) {
    boolean different = false;
    BasicDBList diff = new BasicDBList();

    if (!listsAreSet) {
      // lists are to be treated as ordered sets and so we can compare element by element
      for (int i = 0; i < l.size(); i++){
        Object val = l.get(i);
        Object refv = refl.get(i);
        Object d;
        if ((d = compare(val, refv, listsAreSet)) != null) {
          different = true;
          diff.add(d);
        } else {
          // elements at this index are identical, but we don't want to muck up the order
          // in case future elements are not identical... so add a null to the diff,
          // BUT IMP: do not set the flag that the list is different
          diff.add(null);
        }
      }
    } else {
      // lists are to be treated as unordered sets: we try to match each element best
      // effort to any one of the list elements, and if it does proceed greedily

      // we keep this as a list as opposed to a true set because the original (ref)
      // and the current (new) might have (identical) replicates, and so should not
      // be flagged different because of that.
      List<Object> refset = new ArrayList<Object>();
      refset.addAll(refl);

      for (Object e : l) {
        boolean matches_some = false;
        for (Object eref : refset) {
          if (compare(e, eref, listsAreSet) == null) {
            // this object matches something, great, lets move to the next object
            // also remove the matched object from the ref list, so that we have
            // a 1-1 mapping between this and the ref list object
            matches_some = true;
            refset.remove(eref);
            break;
          }
        }
        if (!matches_some) {
          // if this object in new list could not be matched against something,
          // the lists are different
          different = true;
          diff.add(e);
        }
      }

      if (refset.size() != 0) {
        // still some elements remain in the ref list, i.e., sets different
        different = true;
        diff.addAll(refset);
      }

    }

    return different ? diff : null;
  }

  /*
     *
     *
     * Below is the list of functions required for populating MongoAct
     *
     *
     */
  public Long getNextAvailableChemicalDBid() {
    return this.dbChemicals.count();
  }

  public Long getNextAvailableCofactorDBid() {
    // TODO: do something more robust than this hack.
    return this.dbCofactors.count();
  }

  public void submitToActWaterfallDB(Long ID, DBObject waterfall) {
    // insert a new doc to the collection
    waterfall.put("_id", ID);
    this.dbWaterfalls.insert(waterfall);
  }

  public void submitToActCascadeDB(Long ID, DBObject cascade) {
    // insert a new doc to the collection
    cascade.put("_id", ID);
    this.dbCascades.insert(cascade);
  }

  public void submitToActCofactorsDB(Cofactor c, Long ID) {
    // check if this is already in the DB.
    long alreadyid = alreadyEntered(c);
    if (alreadyid != -1) {
      // cofactor already in DB; what sorcery is this?
      // hard abort. We do not expect to repeatedly see cofactors
      throw new RuntimeException("Duplicate entry for cofactor seen! Install abort.");
    }

    BasicDBObject doc = createCofactorDoc(c, ID);

    // insert a new doc to the collection
    this.dbCofactors.insert(doc);

  }

  public BasicDBObject createCofactorDoc(Cofactor c, Long ID) {
    BasicDBObject doc = new BasicDBObject();

    doc.put("_id", ID);
    doc.put("InChI", c.getInChI());

    BasicDBList names = new BasicDBList();
    names.addAll(c.getNames());
    doc.put("names", names);

    return doc;
  }

  public void submitToActChemicalDB(Chemical c, Long ID) {
    // check if this is already in the DB.
    long alreadyid = alreadyEntered(c);
    if (alreadyid != -1) {
      mergeIntoDB(alreadyid, c); // chemical already exists: merge
      return;
    }

    BasicDBObject doc = createChemicalDoc(c, ID);

    // insert a new doc to the collection
    this.dbChemicals.insert(doc);

  }

  public void updateActChemical(Chemical c, Long id) {
    // See comment in updateActReaction about
    // db.collection.update, and $set

    BasicDBObject doc = createChemicalDoc(c, id);
    DBObject query = new BasicDBObject();
    query.put("_id", id);
    this.dbChemicals.update(query, doc);
  }

  /**
   * Appends XRef data for the chemical with the specified inchi.  Might only apply to Metacyc for now.  Does not crash
   * if idPath or metaPath are null.
   *
   * This uses Mongo's query mechanism to add new ids to a set of xref ids only if they don't already exist, and to
   * append (without comparison) new xref metadata to an existing list without having to read/de-serialize/add/serialize
   * the object ourselves.  This results in a significant performance improvement, especially towards the end of the
   * Metacyc installation process.
   *
   * TODO: this API is awful.  Fix it up to be less Metacyc-specific and more explicit in its behavior.
   *
   * @param inchi The inchi of the chemical to update in Mongo.
   * @param idPath The path to the field where ids should be added, like xref.METACYC.id.
   * @param id The id for this chemical reference to write.
   * @param metaPath The path to the field where metadata blobs should be stored, like xref.METACYC.meta.
   * @param metaObjects A list of metadata objects to append to the metadata list in Mongo.
   */
  public void appendChemicalXRefMetadata(
      String inchi, String idPath, String id, String metaPath, BasicDBList metaObjects) {
    if (idPath == null && metaPath == null) {
      return;
    }

    // Get chemical by InChI.
    BasicDBObject query = new BasicDBObject("InChI", inchi);
    BasicDBObject update = new BasicDBObject();
    if (idPath != null) {
      // Add to set will add an id to the array of xref ids only if it doesn't already exist in the array.
      update.put("$addToSet", new BasicDBObject(idPath, id));
    }

    if (metaPath != null) {
      /* Add all metadata objects to the xref list containing metadata for this source.
       * Note: $push + $each applied to an array of objects is like $pushAll, which is now deprecated. */
      update.put("$push", new BasicDBObject(metaPath, new BasicDBObject("$each", metaObjects)));
    }
    // Run exactly one query to update, which should save a lot of time over the course of the installation.
    this.dbChemicals.update(query, update);
  }

  public static String chemicalAsString(Chemical c, Long ID) {
    // called by cytoscape plugin to serialize the entire chemical as a fulltxt string
    return createChemicalDoc(c, ID).toString();
  }

  public static BasicDBObject createChemicalDoc(Chemical c, Long ID) {
    BasicDBObject doc = new BasicDBObject();

    doc.put("_id", ID);

    doc.put("canonical", c.getCanon());

    doc.put("SMILES", c.getSmiles());
    doc.put("InChI", c.getInChI());
    doc.put("InChIKey", c.getInChIKey());

    doc.put("isCofactor", c.isCofactor());
    doc.put("isNative", c.isNative());

    BasicDBObject names = new BasicDBObject();
    BasicDBList synonyms = new BasicDBList();
    synonyms.addAll(c.getSynonyms());
    names.put("synonyms", synonyms);

    BasicDBList pubchemNames = new BasicDBList();

    for (String type : c.getPubchemNameTypes()) {
      String[] temp = c.getPubchemNames(type);
      BasicDBList dbNames = new BasicDBList();
      for (String t : temp) {
        dbNames.add(t);
      }
      BasicDBObject dbNameObj = new BasicDBObject();
      dbNameObj.put("type", type);
      dbNameObj.put("values", dbNames);
      pubchemNames.add(dbNameObj);
    }
    names.put("pubchem", pubchemNames);
    BasicDBList brendaNames = new BasicDBList(); // will really get its fields later if initial install
    brendaNames.addAll(c.getBrendaNames()); // but for cases where we call it post install, we construct full chem entry
    names.put("brenda", brendaNames);

    doc.put("names", names);

    BasicDBObject xrefs = new BasicDBObject();
    xrefs.put("pubchem", c.getPubchemID());
    int cnt = 0;
    for (REFS xrefTyp : Chemical.REFS.values()) {
      if (c.getRef(xrefTyp) != null) {
        xrefs.put(xrefTyp.name(), MongoDBToJSON.conv((JSONObject)c.getRef(xrefTyp)));
        cnt++;
      }
    }
    doc.put("xref", xrefs);

    doc.put("estimateEnergy", c.getEstimatedEnergy());

    doc.put("keywords", c.getKeywords());
    doc.put("keywords_case_insensitive", c.getCaseInsensitiveKeywords());

    doc.put("csid", c.getChemSpiderID());
    doc.put("num_vendors", c.getChemSpiderNumUniqueVendors());
    doc.put("vendors", MongoDBToJSON.conv(c.getChemSpiderVendorXrefs()));

    return doc;
  }

  private void mergeIntoDB(long id, Chemical c) {
    Chemical oldc = getChemicalFromChemicalUUID(id);
    Chemical mergedc = c.createNewByMerge(oldc);

    if (mergedc == null) {
      // whoa! inconsistent values on unmergables, so recover

      System.err.println("\n\n\n\n\n\n\n\n\n\n");
      System.err.println("---- Conflicting uuid or name or smiles or inchi or inchikey or pubchem_id:");
      System.err.println("---- NEW\t " + c);
      System.err.println("---- OLD\t " + oldc);
      System.err.println("---- Keeping OLD entry");
      System.err.println("\n\n\n\n\n\n\n\n\n\n");

      return;
    }

    BasicDBObject withID = new BasicDBObject();
    withID.put("_id", id);
    this.dbChemicals.remove(withID, WriteConcern.SAFE); // remove the old entry oldc from the collection
    submitToActChemicalDB(mergedc, id); // now that the old entry is removed, we can simply add
  }

  public void updateChemicalWithBrenda(Chemical c, String brendaName) {
    long id = alreadyEntered(c);

    if(id < 0) {
      System.err.println("Update chemical with brenda: " + brendaName + " can't find matching inchi");
      return;
    }
    BasicDBObject query = new BasicDBObject();
    query.put("_id", id);
    BasicDBObject update = new BasicDBObject();
    update.put("$push", new BasicDBObject("names.brenda",brendaName.toLowerCase()));
    this.dbChemicals.update(query, update);

  }

  public void updateChemicalAsNative(String inchi) {
    Chemical c = this.getChemicalFromInChI(inchi);
    if (c == null) {
      System.err.println("Can't find native in DB: " + inchi);
      return;
    }
    long id = c.getUuid();

    BasicDBObject query = new BasicDBObject();
    query.put("_id", id);
    BasicDBObject update = new BasicDBObject();
    update.put("$set", new BasicDBObject("isNative", true));
    this.dbChemicals.update(query, update);
  }

  // 1. update the chemical entry to point to all these patents
  // 2. update the patents collection with the (patent_id, scores, patent_text)
  public void updateChemicalWithPatents(String inchi, Integer num_patents, DBObject patents) {
    Chemical c = this.getChemicalFromInChI(inchi);
    if (c == null) {
      System.err.println("Attempting to add patent. Can't find chem in DB: " + inchi);
      return;
    }
    long id = c.getUuid();

    BasicDBObject query = new BasicDBObject();
    query.put("_id", id);
    BasicDBObject update = new BasicDBObject();
    BasicDBObject set = new BasicDBObject();


// TODO!!!!!!!
//     patents is Array of {  patent_num: Int, patent_txt: String, patent_score: Int }
//                     ie  {   patent ID, full text of patent, relevance to biosynthesis }
//
// put the patents DBObject (all elements of Array) in db.patents.
// put the references to the entries within it in db.chemicals
//     i.e., only an array { patent ID }
// TODO!!!!!!!

// TODO!!!!!!!
//
// Need to update functions that serialize and deserialize from the db :
//          createChemicalDoc and convertDBObjectToChemical
// to recreate vendors, patents etc fields....
//
// TODO!!!!!!!
    System.out.println("Installing patents needs to go into separate collections.. see code.");
    System.exit(-1);


    set.put("patents", patents);
    set.put("num_patents", num_patents);
    update.put("$set", set);
    this.dbChemicals.update(query, update);
  }

  public void updateChemicalWithVendors(String inchi, Integer csid, Integer num_vendors, JSONArray vendors) {
    Chemical c = this.getChemicalFromInChI(inchi);
    if (c == null) {
      System.err.println("Attempting to add vendor. Can't find chem in DB: " + inchi);
      return;
    }
    long id = c.getUuid();

    BasicDBObject query = new BasicDBObject();
    query.put("_id", id);
    BasicDBObject update = new BasicDBObject();
    BasicDBObject set = new BasicDBObject();
    DBObject vendors_dbobject = MongoDBToJSON.conv(vendors);
    set.put("vendors", vendors_dbobject);
    set.put("csid", csid);
    set.put("num_vendors", num_vendors);
    update.put("$set", set);
    this.dbChemicals.update(query, update);
  }

  static boolean jeff_cleanup_quiet = true;

  // retrieve the entry with InChI = @inchi (or create if one does not exist)
  // set one of its synonyms to @synonym
  public long updateOrCreateWithSynonym(String inchi, String synonym) {
    Chemical c = this.getChemicalFromInChI(inchi);
    if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] Synonym: " + synonym);
    if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] InChI  : " + inchi);
    long id = -1;
    if (c != null) {
      id = c.getUuid();
      if (c.getSynonyms().contains(synonym)) {
        if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] Already in synonyms. This move gets a -1 count.");
      } else {
        c.addSynonym(synonym);
        BasicDBObject update = createChemicalDoc(c, id);
        this.dbChemicals.save(update);
        if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] MOVED to id=" + id);
      }
    } else {
      id = getNextAvailableChemicalDBid();
      c = new Chemical(id);
      c.setInchi(inchi);
      c.addSynonym(synonym);

      submitToActChemicalDB(c, id);
      if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] NEW ENTRY id=" + id);
    }
    return id;
  }

  // lookup the entry corresponding to @inchi and remove this @synonym from its list of synonyms.
  public long removeSynonym(String inchi, String synonym) {
    Chemical c = this.getChemicalFromInChI(inchi);
    if (c == null) {
      System.err.println("[Jeff cleanup] ERROR? Can't find chemical entry to remove synonym from: " + inchi);
      return -1;
    }
    long id = c.getUuid();

    // the synonym can be either under:
    //      canon:String (shortestName)
    //      brendaNames:List<String>
    //      synonyms:List<String>
    //      names:Map<String,String[]> (pubchem names type->names)
    if (c.getCanon() != null && c.getCanon().trim().equals(synonym)) {
      c.setCanon(null);
      if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] Removed from Canonical");
    }
    if (c.getBrendaNames() != null) {
      // the trim is the important bit in all of this. else we could have just .remove(synonym)'ed
      List<String> toRemove = new ArrayList<String>();
      for (String s : c.getBrendaNames()) {
        if (s.trim().equals(synonym))
          toRemove.add(s);
      }
      if (toRemove.size() > 0) {
        if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] Removed from Brenda");
        c.getBrendaNames().removeAll(toRemove);
      }
    }
    if (c.getSynonyms() != null) {
      // the trim is the important bit in all of this. else we could have just c.getSynonyms().remove(synonym);
      List<String> toRemove = new ArrayList<String>();
      for (String s : c.getSynonyms()) {
        if (s.trim().equals(synonym))
          toRemove.add(s);
      }
      if (toRemove.size() > 0) {
        if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] Removed from Synonyms");
        c.getSynonyms().removeAll(toRemove);
      }
    }
    for (String type : c.getPubchemNameTypes()) {
      List<String> names = new ArrayList<String>();
      for (String s : c.getPubchemNames(type)) {
        if(s.trim().equals(synonym)) {
          if (!jeff_cleanup_quiet) System.err.println("[Jeff cleanup] Removed from Pubchem");
          continue;
        }
        names.add(s);
      }
      c.getPubchemNames().put(type, names.toArray(new String[0]));
    }
    BasicDBObject update = createChemicalDoc(c, id);

    this.dbChemicals.save(update);
    return id;
  }

  public void updateStoichiometry(Reaction r) {
    BasicDBObject query = new BasicDBObject().append("_id", r.getUUID());
    DBObject obj = this.dbReactions.findOne(query);
    DBObject enz_summary = (DBObject) obj.get("enz_summary");
    BasicDBList substrates = (BasicDBList) enz_summary.get("substrates");
    BasicDBList newSubstrates = new BasicDBList();
    Set<Long> originalSubstrateIDs = new HashSet<Long>();
    for (int i = 0; i < substrates.size(); i++) {
      DBObject substrate = (DBObject) substrates.get(i);
      Long substrateID = (Long) substrate.get("pubchem");
      Boolean isForBalance = (Boolean) substrate.get("balance");
      if (isForBalance != null && isForBalance) continue;
      originalSubstrateIDs.add(substrateID);
      substrate.put("coefficient", r.getSubstrateCoefficient(substrateID));
      newSubstrates.add(substrate);
    }
    Set<Long> substratesNew = r.getSubstrateIdsOfSubstrateCoefficients();
    for (Long s : substratesNew) {
      if (originalSubstrateIDs.contains(s)) continue;
      if (r.getSubstrateCoefficient(s) == null) continue;
      DBObject substrate = new BasicDBObject();
      substrate.put("pubchem", s);
      substrate.put("coefficient", r.getSubstrateCoefficient(s));
      substrate.put("balance", true);
      newSubstrates.add(substrate);
    }

    BasicDBList products = (BasicDBList) enz_summary.get("products");
    BasicDBList newProducts = new BasicDBList();
    Set<Long> originalProductIDs = new HashSet<Long>();
    for (int i = 0; i < products.size(); i++) {
      DBObject product = (DBObject) products.get(i);
      Long productID = (Long) product.get("pubchem");
      Boolean isForBalance = (Boolean) product.get("balance");
      if (isForBalance != null && isForBalance) continue;
      originalProductIDs.add(productID);
      product.put("coefficient", r.getProductCoefficient(
          productID) );
      newProducts.add(product);
    }
    Set<Long> productsNew = r.getProductIdsOfProductCoefficients();
    for (Long p : productsNew) {
      if (originalProductIDs.contains(p)) continue;
      if (r.getProductCoefficient(p) == null) continue;
      DBObject product = new BasicDBObject();
      product.put("pubchem", p);
      product.put("coefficient", r.getProductCoefficient(p));
      product.put("balance", true);
      newProducts.add(product);
    }
    enz_summary.put("substrates", newSubstrates);
    enz_summary.put("products", newProducts);
    this.dbReactions.update(query, obj);
  }

  public void updateEstimatedEnergy(Chemical chemical) {
    BasicDBObject query = new BasicDBObject().append("_id", chemical.getUuid());
    DBObject obj = this.dbChemicals.findOne(query);
    obj.put("estimateEnergy", chemical.getEstimatedEnergy());
    this.dbChemicals.update(query, obj);
  }

  public void updateEstimatedEnergy(Reaction reaction) {
    BasicDBObject query = new BasicDBObject().append("_id", reaction.getUUID());
    DBObject obj = this.dbReactions.findOne(query);
    obj.put("estimateEnergy", reaction.getEstimatedEnergy());
    this.dbReactions.update(query, obj);
  }

  public void updateSARConstraint(Seq seq) {
    BasicDBObject query = new BasicDBObject().append("_id", seq.getUUID());
    DBObject obj = this.dbSeq.findOne(query);
    BasicDBList constraints = new BasicDBList();
    HashMap<Object, SARConstraint> sarConstraints = seq.getSAR().getConstraints();
    for (Object data : sarConstraints.keySet()) {
      SARConstraint sc = sarConstraints.get(data);
      DBObject c = new BasicDBObject();
      c.put("data"         , data);
      c.put("presence_req" , sc.presence.toString()); // should_have/should_not_have
      c.put("contents_req" , sc.contents.toString()); // substructure
      c.put("requires_req" , sc.requires.toString()); // soft/hard
      constraints.add(c);
    }
    obj.put("sar_constraints", constraints);

    this.dbSeq.update(query, obj);
  }

  public void updateReactionRefsOf(Seq seq) {
    BasicDBObject query = new BasicDBObject().append("_id", seq.getUUID());
    DBObject obj = this.dbSeq.findOne(query);
    BasicDBList refs = new BasicDBList();
    BasicDBList substrates_uniform = new BasicDBList();
    BasicDBList substrates_diverse = new BasicDBList();
    BasicDBList products_uniform = new BasicDBList();
    BasicDBList products_diverse = new BasicDBList();
    BasicDBList rxn2reactants = new BasicDBList();
    for (Long r : seq.getReactionsCatalyzed())
      refs.add(r);
    for (Long s : seq.getCatalysisSubstratesUniform())
      substrates_uniform.add(s);
    for (Long s : seq.getCatalysisSubstratesDiverse())
      substrates_diverse.add(s);
    for (Long p : seq.getCatalysisProductsUniform())
      products_uniform.add(p);
    for (Long p : seq.getCatalysisProductsDiverse())
      products_diverse.add(p);
    HashMap<Long, Set<Long>> rxn2substrates = seq.getReaction2Substrates();
    HashMap<Long, Set<Long>> rxn2products = seq.getReaction2Products();
    for (Long r : rxn2substrates.keySet()) {
      BasicDBList rsub = to_dblist(rxn2substrates.get(r));
      BasicDBList rprd = to_dblist(rxn2products.get(r));

      DBObject robj = new BasicDBObject();
      robj.put("rxn", r);
      robj.put("substrates", rsub);
      robj.put("products", rprd);
      rxn2reactants.add(robj);
    }
    obj.put("rxn_refs", refs);
    obj.put("substrates_diverse_refs", substrates_diverse);
    obj.put("substrates_uniform_refs", substrates_uniform);
    obj.put("products_diverse_refs", products_diverse);
    obj.put("products_uniform_refs", products_uniform);
    obj.put("rxn_to_reactants", rxn2reactants);

    this.dbSeq.update(query, obj);
  }

  public void updateKeywordsCascade(Long id, Set<String> kwrds, Set<String> ciKwrds) {
    BasicDBObject query = new BasicDBObject().append("_id", id);
    DBObject obj = this.dbCascades.findOne(query);
    obj.put("keywords", kwrds);
    obj.put("keywords_case_insensitive", ciKwrds);
    this.dbCascades.update(query, obj);
  }

  public void updateKeywordsWaterfall(Long id, Set<String> kwrds, Set<String> ciKwrds) {
    BasicDBObject query = new BasicDBObject().append("_id", id);
    DBObject obj = this.dbWaterfalls.findOne(query);
    obj.put("keywords", kwrds);
    obj.put("keywords_case_insensitive", ciKwrds);
    this.dbWaterfalls.update(query, obj);
  }

  public void updateKeywords(Reaction reaction) {
    BasicDBObject query = new BasicDBObject().append("_id", reaction.getUUID());
    DBObject obj = this.dbReactions.findOne(query);
    obj.put("keywords", reaction.getKeywords());
    obj.put("keywords_case_insensitive", reaction.getCaseInsensitiveKeywords());
    this.dbReactions.update(query, obj);
  }

  public int submitToActReactionDB(Reaction r) {
    // if reaction already present in Act, then ignore.
    if (alreadyEntered(r)) {
      System.out.println("___ Duplicate reaction? : " + r.getUUID());
      return -1;
    }

    if (r.getUUID() != -1) {
      // this function is designed to only submit a new entry
      // if you need to update an existing entry, use updateActReaction
      String msg = StringUtils.join(new String[] {
        "FATAL Error: Aborting in MongoDB.submitToActReactionDB.",
        "Reaction asked to add has a populated ID field," ,
        "i.e., != -1, while this function strictly appends" ,
        "to the DB and so will not honor the id field.",
        r.toString()
      }, "\n");
      System.err.println(msg);
      throw new RuntimeException(msg);
    }

    int id = new Long(this.dbReactions.count()).intValue(); // O(1)
    BasicDBObject doc = createReactionDoc(r, id);

    // writing to MongoDB collection act
    this.dbReactions.insert(doc);

    return id;
  }

  public void updateActReaction(Reaction r, int id) {
    // db.collection.update(query, update, options)
    // updates document(s) that match query with the update doc
    // Ref: http://docs.mongodb.org/manual/reference/method/db.collection.update/
    //
    // Update doc: Can be { $set : { <field> : <val> } }
    // in case you need to keep the old document, but just update
    // some fields inside of it.
    // Ref: http://docs.mongodb.org/manual/reference/operator/update/set/
    //
    // But here (and in updateActChemical) we want to overwrite
    // the entire document with a new one, and so
    // a simple update call with the new document is what we need.

    BasicDBObject doc = createReactionDoc(r, id);
    DBObject query = new BasicDBObject();
    query.put("_id", id);
    this.dbReactions.update(query, doc);
  }

  public static BasicDBObject createReactionDoc(Reaction r, int id) {
    BasicDBObject doc = new BasicDBObject();
    doc.put("_id", id);
    doc.put("ecnum", r.getECNum());
    doc.put("easy_desc", r.getReactionName());

    BasicDBList substr = new BasicDBList();
    Long[] ss = r.getSubstrates();
    for (int i = 0; i < ss.length; i++) {
      DBObject o = getObject("pubchem", ss[i]);
      o.put("coefficient", r.getSubstrateCoefficient(ss[i]));
      substr.put(i, o);
    }

    BasicDBList prods = new BasicDBList();
    Long[] pp = r.getProducts();
    for (int i = 0; i < pp.length; i++) {
      DBObject o = getObject("pubchem", pp[i]);
      o.put("coefficient", r.getProductCoefficient(pp[i]));
      prods.put(i, o);
    }

    BasicDBList prodCofactors = new BasicDBList();
    Long[] ppc = r.getProductCofactors();
    for (int i = 0; i < ppc.length; i++) {
      DBObject o = getObject("pubchem", ppc[i]);
      prodCofactors.put(i, o);
    }

    BasicDBList substrCofactors = new BasicDBList();
    Long[] ssc = r.getSubstrateCofactors();
    for (int i = 0; i < ssc.length; i++) {
      DBObject o = getObject("pubchem", ssc[i]);
      substrCofactors.put(i, o);
    }

    BasicDBList coenzymes = new BasicDBList();
    Long[] coenz = r.getCoenzymes();
    for (int i = 0; i < coenz.length; i++) {
      DBObject o = getObject("pubchem", coenz[i]);
      coenzymes.put(i, o);
    }

    BasicDBObject enz = new BasicDBObject();
    enz.put("products", prods);
    enz.put("substrates", substr);
    enz.put("product_cofactors", prodCofactors);
    enz.put("substrate_cofactors", substrCofactors);
    enz.put("coenzymes", coenzymes);
    doc.put("enz_summary", enz);

    doc.put("is_abstract", r.getRxnDetailType().name());

    if (r.getDataSource() != null)
      doc.put("datasource", r.getDataSource().name());

    if (r.getMechanisticValidatorResult() != null) {
      doc.put("mechanistic_validator_result", MongoDBToJSON.conv(r.getMechanisticValidatorResult()));
    }

    BasicDBList refs = new BasicDBList();
    for (P<Reaction.RefDataSource, String> ref : r.getReferences()) {
      BasicDBObject refEntry = new BasicDBObject();
      refEntry.put("src", ref.fst().toString());
      refEntry.put("val", ref.snd());
      refs.add(refEntry);
    }
    doc.put("references",refs);

    BasicDBList proteins = new BasicDBList();
    for (JSONObject proteinData : r.getProteinData()) {
      proteins.add(MongoDBToJSON.conv(proteinData));
    }
    doc.put("proteins", proteins);
    ConversionDirectionType cd = r.getConversionDirection();
    doc.put("conversion_direction", cd == null ? null : cd.toString());
    StepDirection psd = r.getPathwayStepDirection();
    doc.put("pathway_step_direction", psd == null ? null : psd.toString());

    return doc;
  }

  public void submitToActOrganismDB(Organism o) {
    BasicDBObject doc = new BasicDBObject();
    doc.put("_id", o.getUUID());
    doc.put("parent_id", o.getParent());
    doc.put("rank", o.getRank());

    if(this.dbOrganisms == null) {
      System.out.print("Organism: " + o);
    } else {
      this.dbOrganisms.insert(doc);
    }
  }

  public void submitToActOrganismNameDB(Organism o) {
    BasicDBObject doc = new BasicDBObject();
    doc.put("org_id", o.getUUID());
    doc.put("name", o.getName());
    if(this.dbOrganismNames == null) {
      System.out.print("Organism: " + o);
    } else {
      this.dbOrganismNames.insert(doc);
    }
  }

  public void submitToPubmedDB(PubmedEntry entry) {
    List<String> xPath = new ArrayList<String>();
    xPath.add("MedlineCitation"); xPath.add("PMID");
    int pmid = Integer.parseInt(entry.getXPathString(xPath));
    if(this.dbPubmed != null) {
      WriteResult result;
      if (alreadyEntered(entry, pmid))
        return;
      DBObject doc = (DBObject)JSON.parse(entry.toJSON());
      doc.put("_id", pmid);
      this.dbPubmed.insert(doc);
    } else
      Logger.printf(0, "Pubmed Entry [%d]: %s\n", pmid, entry); // human readable...
  }

  private static BasicDBObject getObject(String field, Long val) {
    BasicDBObject singularObj = new BasicDBObject();
    singularObj.put(field, val);
    return singularObj;
  }

  private BasicDBObject getObject(String f1, Long v1, String f2, Float v2) {
    BasicDBObject o = new BasicDBObject();
    o.put(f1, v1); o.put(f2, v2);
    return o;
  }

  /*
   * Return -1 if the chemical doesn't exist in the database yet.
   * Else return the id.
   */
  private long alreadyEntered(Chemical c) {
    BasicDBObject query;
    String inchi = c.getInChI();
    long retId = -1;

    if(inchi != null) {
      query = new BasicDBObject();
      query.put("InChI", inchi);
      DBObject o = this.dbChemicals.findOne(query);
      if(o != null)
        retId = (Long) o.get("_id"); // checked: db type IS long
    }
    return retId;
  }

  public boolean alreadyEnteredChemical(String inchi) {
    if (this.dbChemicals == null)
      return false; // TODO: should this throw an exception instead?

    BasicDBObject query = new BasicDBObject("InChI", inchi);
    long c = this.dbChemicals.count(query);
    return c > 0;
  }

  public Long getExistingDBIdForInChI(String inchi) { // TODO: should this return some UUID type instead of Long?
    if (this.dbChemicals == null)
      return null; // TODO: should this throw an exception instead?

    BasicDBObject query = new BasicDBObject("InChI", inchi);
    BasicDBObject fields = new BasicDBObject("_id", true);
    DBObject o = this.dbChemicals.findOne(query, fields);
    if (o == null) {
      return null;
    }
    // TODO: does this need to be checked?
    return (Long) o.get("_id");
  }

  private long alreadyEntered(Cofactor cof) {
    BasicDBObject query;
    String inchi = cof.getInChI();
    long retId = -1;

    if(inchi != null) {
      query = new BasicDBObject();
      query.put("InChI", inchi);
      DBObject o = this.dbCofactors.findOne(query);
      if(o != null)
        retId = (Long) o.get("_id"); // checked: db type IS long
    }
    return retId;
  }

  private boolean alreadyEntered(Reaction r) {
    BasicDBObject query = new BasicDBObject();
    query.put("_id", r.getUUID());

    DBObject o = this.dbReactions.findOne(query);
    return o != null; // meaning there is at least one document that matches
  }

  private boolean alreadyEntered(PubmedEntry entry, int pmid) {
    BasicDBObject query = new BasicDBObject();
    query.put("_id", pmid);

    DBObject o = this.dbPubmed.findOne(query);
    return o != null;
  }

    /*
     *
     *
     * End of functions required for populating MongoAct
     *
     *
     */

  public List<Long> getRxnsWith(Long reactant, Long product) {

    BasicDBObject query = new BasicDBObject();
    query.put("enz_summary.products.pubchem", product);
    query.put("enz_summary.substrates.pubchem", reactant);
    DBCursor cur = this.dbReactions.find(query);

    List<Long> reactions = new ArrayList<Long>();
    while (cur.hasNext()) {
      DBObject o = cur.next();
      long id = (Integer) o.get("_id"); // checked: db type IS int
      reactions.add(id);
    }
    cur.close();
    return reactions;
  }

  public List<Long> getRxnsWithEnzyme(String enzyme, Long org, List<Long> substrates) {
    BasicDBObject query = new BasicDBObject();
    query.put("ecnum", enzyme);
    query.put("organisms.id", org);
    for (Long substrate : substrates) {
      BasicDBObject mainQuery = new BasicDBObject();
      mainQuery.put("$ne", substrate);
      BasicDBList queryList = new BasicDBList();
      BasicDBObject productQuery = new BasicDBObject();
      productQuery.put("enz_summary.products.pubchem", mainQuery);
      BasicDBObject substrateQuery = new BasicDBObject();
      substrateQuery.put("enz_summary.substrates.pubchem", mainQuery);
      queryList.add(substrateQuery);
      queryList.add(productQuery);
      query.put("$or", queryList);
    }
    DBCursor cur = this.dbReactions.find(query);

    List<Long> reactions = new ArrayList<Long>();
    while (cur.hasNext()) {
      DBObject o = cur.next();
      long id = (Integer) o.get("_id"); // checked: db type IS int
      reactions.add(id);
    }
    cur.close();
    return reactions;
  }

  public List<Long> getRxnsWithSubstrate(String enzyme, Long org, List<Long> substrates) {
    BasicDBObject query = new BasicDBObject();
    query.put("organisms.id", org);
    BasicDBObject enzymeQuery = new BasicDBObject();
    enzymeQuery.put("ecnum", enzyme);
    query.put("$ne", enzymeQuery);
    for (Long substrate: substrates) {
      BasicDBList queryList = new BasicDBList();
      DBObject querySubstrate = new BasicDBObject();
      querySubstrate.put("enz_summary.substrates.pubchem", substrate);
      DBObject queryProduct = new BasicDBObject();
      queryProduct.put("enz_summary.products.pubchem", substrate);
      queryList.add(querySubstrate);
      queryList.add(queryProduct);
      query.put("$or", queryList);
    }

    DBCursor cur = this.dbReactions.find(query);
    List<Long> reactions = new ArrayList<Long>();
    while (cur.hasNext()) {
      DBObject o = cur.next();
      long id = (Integer) o.get("_id"); // checked: db type IS int
      reactions.add(id);
    }
    cur.close();
    return reactions;
  }

  public String getShortestName(Long id) {
    Chemical chem = this.getChemicalFromChemicalUUID(id);
    if (chem == null) return "unknown_chemical";
    String name = chem.getShortestBRENDAName();
    if (name == null) name = chem.getShortestName();
    if (name == null) name = "no_name";
    return name;
  }

  public List<Chemical> getNativeMetaboliteChems() {
    return constructAllChemicalsFromActData("isNative", true);
  }

  private List<Long> _cofactor_ids_cache = null;
  private List<Chemical> _cofactor_chemicals_cache = null;

  public List<Chemical> getCofactorChemicals() {
    List<Chemical> cof = constructAllChemicalsFromActData("isCofactor", true);

    // before we return this set, we need to make sure some
    // cases that for some reason are not in the db as cofactors
    // are marked as such.
    HashMap<String, Chemical> inchis = new HashMap<String, Chemical>();
    for (Chemical c : cof)
      if (c.getInChI() != null)
        inchis.put(c.getInChI(), c);
      else
        Logger.print(1, String.format("[MongoDB.getCofactorChemicals] No inchi for cofactor(id:%d): %s\n " + c.getUuid(), c.getSynonyms()));

    for (SomeCofactorNames cofactor : SomeCofactorNames.values()) {
      String shouldbethere = cofactor.getInChI();
      if (!inchis.containsKey(shouldbethere)) {
        List<Chemical> toAdd = constructAllChemicalsFromActData("InChI", shouldbethere);
        cof.addAll(toAdd);
        for (Chemical c : toAdd) {
          addToDefiniteCofactorsMaps(cofactor, c);
          //Logger.print(1, String.format("MongoDB.getCofactorChemicals] Added extra cofactor: id=%d, Synonyms=%s, Inchi=%s\n", c.getUuid(), c.getSynonyms(), c.getInChI()));
        }
      } else {
        addToDefiniteCofactorsMaps(cofactor, inchis.get(shouldbethere));
      }
    }

    // on first call, install the cofactors read from db into cache
    if (_cofactor_ids_cache == null) {
      _cofactor_chemicals_cache = cof;
      _cofactor_ids_cache = new ArrayList<Long>();
      for (Chemical c : cof)
        _cofactor_ids_cache.add(c.getUuid());
    }

    return cof;
  }

  private boolean isCofactor(Long c) {
    if (_cofactor_ids_cache == null) {
      // getCofactorChemicals inits cache as a side-effect
      getCofactorChemicals();
    }

    return _cofactor_ids_cache.contains(c);
  }


  private void addToDefiniteCofactorsMaps(SomeCofactorNames cofactor, Chemical c) {
    Long id = c.getUuid();
    switch (cofactor) {
    case Water: SomeCofactorNames.Water.setMongoDBId(id); break;
    case ATP: SomeCofactorNames.ATP.setMongoDBId(id); break;
    case Acceptor: SomeCofactorNames.Acceptor.setMongoDBId(id); break;
    case AcceptorH2: SomeCofactorNames.AcceptorH2.setMongoDBId(id); break;
    case ReducedAcceptor: SomeCofactorNames.ReducedAcceptor.setMongoDBId(id); break;
    case OxidizedFerredoxin: SomeCofactorNames.OxidizedFerredoxin.setMongoDBId(id); break;
    case ReducedFerredoxin: SomeCofactorNames.ReducedFerredoxin.setMongoDBId(id); break;
    case CO2: SomeCofactorNames.CO2.setMongoDBId(id); break;
    case BicarbonateHCO3: SomeCofactorNames.BicarbonateHCO3.setMongoDBId(id); break;
    case CoA: SomeCofactorNames.CoA.setMongoDBId(id); break;
    case H: SomeCofactorNames.H.setMongoDBId(id); break;
    case NH3: SomeCofactorNames.NH3.setMongoDBId(id); break;
    case HCl: SomeCofactorNames.HCl.setMongoDBId(id); break;
    case Cl: SomeCofactorNames.Cl.setMongoDBId(id); break;
    case O2: SomeCofactorNames.O2.setMongoDBId(id); break;
    case CTP: SomeCofactorNames.CTP.setMongoDBId(id); break;
    case dATP: SomeCofactorNames.dATP.setMongoDBId(id); break;
    case H2S: SomeCofactorNames.H2S.setMongoDBId(id); break;
    case dGTP: SomeCofactorNames.dGTP.setMongoDBId(id); break;
    case PhosphoricAcid: SomeCofactorNames.PhosphoricAcid.setMongoDBId(id); break;
    case I: SomeCofactorNames.I.setMongoDBId(id); break;
    case MolI: SomeCofactorNames.MolI.setMongoDBId(id); break;
    case AMP: SomeCofactorNames.AMP.setMongoDBId(id); break;
    case Phosphoadenylylsulfate: SomeCofactorNames.Phosphoadenylylsulfate.setMongoDBId(id); break;
    case H2SO3: SomeCofactorNames.H2SO3.setMongoDBId(id); break;
    case adenylylsulfate: SomeCofactorNames.adenylylsulfate.setMongoDBId(id); break;
    case GTP: SomeCofactorNames.GTP.setMongoDBId(id); break;
    case NADPH: SomeCofactorNames.NADPH.setMongoDBId(id); break;
    case dADP: SomeCofactorNames.dADP.setMongoDBId(id); break;
    case NADP: SomeCofactorNames.NADP.setMongoDBId(id); break;
    case UMP: SomeCofactorNames.UMP.setMongoDBId(id); break;
    case dCDP: SomeCofactorNames.dCDP.setMongoDBId(id); break;
    case ADP: SomeCofactorNames.ADP.setMongoDBId(id); break;
    case ADPm: SomeCofactorNames.ADPm.setMongoDBId(id); break;
    case UDP: SomeCofactorNames.UDP.setMongoDBId(id); break;
    default: break;
    }

  }
  // These should all be by default in the DB, but if not we augment the DB cofactors tags with these chemicals
  // It is ok for this list to not be exhaustive.... this is just for parent assignment in visualization
  public enum SomeCofactorNames {
    Water(0), ATP(1), Acceptor(2), AcceptorH2(3),
    ReducedAcceptor(4), OxidizedFerredoxin(5), ReducedFerredoxin(6),
    CO2(7),  BicarbonateHCO3(8), CoA(9), H(10), NH3(11), HCl(12), Cl(13), O2(14),
    CTP(15), dATP(16), H2S(17), dGTP(18), PhosphoricAcid(19), I(20), MolI(21), AMP(22),
    Phosphoadenylylsulfate(23), H2SO3(24), adenylylsulfate(25), GTP(26), NADPH(27), dADP(28),
    NADP(29), UMP(30), dCDP(31), ADP(32), ADPm(33), UDP(34);

    int internalId;
    Long mongodbId;
    private SomeCofactorNames(int id) { this.internalId = id; this.mongodbId = null; }
    public String getInChI() { return this._definiteCofactors[internalId]; }
    public void setMongoDBId(Long id) { this.mongodbId = id; }
    public Long getMongoDBId() { return this.mongodbId; }

    private static final String[] raw_definiteCofactors = {
      // 0 Water:
      "InChI=1S/H2O/h1H2", // [H2o, H2O, h2O][water, Dihydrogen oxide, Water vapor, Distilled water, oxidane, Deionized water, Purified water, Water, purified, Dihydrogen Monoxide, DHMO, oxygen, OH-, monohydrate, aqua, hydrate, o-]
      // 1 ATP:
      "InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)(H2,11,12,13)(H2,18,19,20)/t4-,6+,7?,10-/m1/s1", // [L-ATP, D-ATP, araATP, alphaATP, adenosyl-ribose triphosphate, adenosine 5'-triphosphate, 5'-ATP, ATP, adenosine triphosphate][Adenosine triphosphate, Striadyne, Myotriphos, Triadenyl, Triphosphaden, Atriphos, Glucobasin, Adephos, Adetol, Triphosaden, AC1NSUB1, [[(2S,5S)-5-(6-aminopurin-9-yl)-3,4-dihydroxyoxolan-2-yl]methoxy-hydroxyphosphoryl] phosphono hydrogen phosphate, Adenosine 5'-(tetrahydrogen triphosphate)]
      // 2 Acceptor:
      "InChI=1S/R", // [acceptor, oxidized adrenal ferredoxin, oxidized adrenodoxin][]
      // 3 AcceptorH2:
      "InChI=1S/RH2/h1H2", // [reduced adrenal ferredoxin, reduced adrenodoxin, acceptor-H2, acceptorH2][]
      // 4 ReducedAcceptor:
      "InChI=1S/RH3/h1H3", // [reduced acceptor, AH2, putidaredoxin, donor][]
      // 5 OxidizedFerredoxin:
      "InChI=1S/4RS.2Fe.2S/c4*1-2;;;;/q4*-1;2*+5;;", // [oxidized ferredoxin][]
      // 6 ReducedFerredoxin:
      "InChI=1S/4RS.2Fe.2S/c4*1-2;;;;/q4*-1;2*+4;;", // [reduced ferredoxin][]
      // 7 CO2:
      "InChI=1S/CO2/c2-1-3", // [carbon dioxide, carbon dioxide, carbonic acid gas]
      // 8 BicarbonateHCO3:
      "InChI=1S/CH2O3/c2-1(3)4/h(H2,2,3,4)/p-1", // [HCO3-, bicarbonate, bicarbonate]
      // 9 CoA
      "InChI=1S/C21H36N7O16P3S/c1-21(2,16(31)19(32)24-4-3-12(29)23-5-6-48)8-41-47(38,39)44-46(36,37)40-7-11-15(43-45(33,34)35)14(30)20(42-11)28-10-27-13-17(22)25-9-26-18(13)28/h9-11,14-16,20,30-31,48H,3-8H2,1-2H3,(H,23,29)(H,24,32)(H,36,37)(H,38,39)(H2,22,25,26)(H2,33,34,35)/t11-,14-,15-,16+,20-/m1/s1", // [coenzyme A, CoA-SH, CoASH]
      // 10 H
      "InChI=1S/p+1", // [H+/out, H+/in, H+out]
      // 11 NH3
      "InChI=1S/H3N/h1H3", // Ammonia Gas
      // 12 HCl, Cl-
      "InChI=1S/ClH/h1H", // hydrochloric acid, hydrogen chloride, Muriatic acid
      // 13 Cl-
      "InChI=1S/ClH/h1H/p-1", // [Cl-/out, Cl-/in, chloride]
      // 14 O2
      "InChI=1S/O2/c1-2", // oxygen molecule, Molecular oxygen, Dioxygen
      // 15 CTP
      "InChI=1S/C9H16N3O14P3/c10-5-1-2-12(9(15)11-5)8-7(14)6(13)4(24-8)3-23-28(19,20)26-29(21,22)25-27(16,17)18/h1-2,4,6-8,13-14H,3H2,(H,19,20)(H,21,22)(H2,10,11,15)(H2,16,17,18)/t4-,6-,7+,8-/m1/s1", // L-CTP, D-CTP, cytosine arabinoside 5'-triphosphate
      // 16 dATP
      "InChI=1S/C10H16N5O12P3/c11-9-8-10(13-3-12-9)15(4-14-8)7-1-5(16)6(25-7)2-24-29(20,21)27-30(22,23)26-28(17,18)19/h3-7,16H,1-2H2,(H,20,21)(H,22,23)(H2,11,12,13)(H2,17,18,19)/t5-,6+,7+/m0/s1", // deoxyATP, L-dATP, L-2'-dATP
      // 17 hydrogen sulfide
      "InChI=1S/H2S/h1H2", // hydrogensulfide, hydrogen sulfide, hydrogen sulfide
      // 18 dGTP
      "InChI=1S/C10H16N5O13P3/c11-10-13-8-7(9(17)14-10)12-3-15(8)6-1-4(16)5(26-6)2-25-30(21,22)28-31(23,24)27-29(18,19)20/h3-6,16H,1-2H2,(H,21,22)(H,23,24)(H2,18,19,20)(H3,11,13,14,17)/t4-,5+,6+/m0/s1", // 2'-dGTP, D-GTP, deoxyGTP
      // 19 Phosphoric acid
      "InChI=1S/H3O4P/c1-5(2,3)4/h(H3,1,2,3,4)", // phosphate/out, phosphate/in, Phosphoric acid
      // 20 Iodide ion
      "InChI=1S/HI/h1H/p-1", // [iodide, Iodide, Iodide ion]
      // 21 Molecular iodine
      "InChI=1S/I2/c1-2", // [Molecular iodine, Iodine solution, Tincture iodine]
      // 22 AMP
      "InChI=1S/C10H14N5O7P/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(22-10)1-21-23(18,19)20/h2-4,6-7,10,16-17H,1H2,(H2,11,12,13)(H2,18,19,20)/t4-,6-,7+,10-/m1/s1", // 5'AMP, arabinosyl adenine 5'-phosphate, arabinosyl adenine 5'-monophosphate
      // 23 3-phosphoadenylylsulfate
      "InChI=1S/C10H15N5O13P2S/c11-8-5-9(13-2-12-8)15(3-14-5)10-6(16)7(27-29(17,18)19)4(26-10)1-25-30(20,21)28-31(22,23)24/h2-4,6-7,10,16H,1H2,(H,20,21)(H2,11,12,13)(H2,17,18,19)(H,22,23,24)/t4-,6-,7-,10-/m1/s1", // [3'-phosphoadenylylsulfate, 3'-phosphoadenylyl 5'-phosphosulfate, 3-phosphoadenylylsulfate]
      // 24 Sulfur dioxide solution
      "InChI=1S/H2O3S/c1-4(2)3/h(H2,1,2,3)", // [Sulfurous acid, Sulphurous acid, Sulfur dioxide solution]
      // 25 adenylylsulfate
      "InChI=1S/C10H14N5O10PS/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(24-10)1-23-26(18,19)25-27(20,21)22/h2-4,6-7,10,16-17H,1H2,(H,18,19)(H2,11,12,13)(H,20,21,22)/t4-,6-,7-,10-/m1/s1", // adenosine 5-phosphosulfate, adenylylsulfate, adenosine 5'-phosphate 5'-sulfate
      // 26 GTP
      "InChI=1S/C10H16N5O14P3/c11-10-13-7-4(8(18)14-10)12-2-15(7)9-6(17)5(16)3(27-9)1-26-31(22,23)29-32(24,25)28-30(19,20)21/h2-3,5-6,9,16-17H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H3,11,13,14,18)/t3-,5-,6-,9-/m1/s1", // guanosine 5'-triphosphate, GUANOSINE TRIPHOSPHATE, 5'-GTP
      // 27 NADPH
      "InChI=1S/C21H30N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1,3-4,7-8,10-11,13-16,20-21,29-31H,2,5-6H2,(H2,23,32)(H,36,37)(H,38,39)(H2,22,24,25)(H2,33,34,35)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1", // NAD(P)H, 2'-NADPH, NADPH
      // 28 dADP
      "InChI=1S/C10H15N5O9P2/c11-9-8-10(13-3-12-9)15(4-14-8)7-1-5(16)6(23-7)2-22-26(20,21)24-25(17,18)19/h3-7,16H,1-2H2,(H,20,21)(H2,11,12,13)(H2,17,18,19)/t5-,6+,7+/m0/s1", // 2'-dADP, 2'-deoxy-ADP, deoxyADP
      // 29 NADP+
      "InChI=1S/C21H28N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1-4,7-8,10-11,13-16,20-21,29-31H,5-6H2,(H7-,22,23,24,25,32,33,34,35,36,37,38,39)/p+1/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1", // NAD(P)+, beta-NADP+, 2'-NADP+
      // 30 UMP
      "InChI=1S/C9H13N2O9P/c12-5-1-2-11(9(15)10-5)8-7(14)6(13)4(20-8)3-19-21(16,17)18/h1-2,4,6-8,13-14H,3H2,(H,10,12,15)(H2,16,17,18)/t4-,6+,7?,8-/m1/s1", // D-UMP, deazauridine 5'-phosphate, ara-UMP
      // 31 dCDP
      "InChI=1S/C9H15N3O10P2/c10-7-1-2-12(9(14)11-7)8-3-5(13)6(21-8)4-20-24(18,19)22-23(15,16)17/h1-2,5-6,8,13H,3-4H2,(H,18,19)(H2,10,11,14)(H2,15,16,17)/t5-,6+,8+/m0/s1", // L-dCDP, D-dCDP, 2'-deoxy-CDP
      // 32 ADP
      "InChI=1S/C10H15N5O10P2/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(24-10)1-23-27(21,22)25-26(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H2,11,12,13)(H2,18,19,20)/t4-,6-,7+,10-/m1/s1", // L-ADP, D-ADP, araADP
      // 33 ADP from metacyc
      "InChI=1S/C10H15N5O10P2/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(24-10)1-23-27(21,22)25-26(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H2,11,12,13)(H2,18,19,20)/p-3", // ADP
      // 34 UDP from metacyc
      "InChI=1S/C9H14N2O12P2/c12-5-1-2-11(9(15)10-5)8-7(14)6(13)4(22-8)3-21-25(19,20)23-24(16,17)18/h1-2,4,6-8,13-14H,3H2,(H,19,20)(H,10,12,15)(H2,16,17,18)", // UDP
    };

    private static String[] _definiteCofactors = convertToConsistent(raw_definiteCofactors, "Installed cofactors");
  };

  private static String[] convertToConsistent(String[] raw, String debug_tag) {
    String[] consistent = new String[raw.length];
    for (int i = 0; i<raw.length; i++) {
      consistent[i] = ConsistentInChI.consistentInChI(raw[i], debug_tag);
    }
    return consistent;
  }

  public Set<Long> getNativeIDs() {
    List<Chemical> cofactorChemicals = getCofactorChemicals();
    List<Chemical> nativeChemicals = getNativeMetaboliteChems();
    Set<Long> ids = new HashSet<Long>();
    for (Chemical c : cofactorChemicals) ids.add(c.getUuid());
    for (Chemical c : nativeChemicals) ids.add(c.getUuid());
    return ids;
  }

  public Chemical getChemicalFromSMILES(String smile) {
    return convertDBObjectToChemicalFromActData("SMILES", smile);
  }

  public Chemical getChemicalFromInChI(String inchi) {
    return convertDBObjectToChemicalFromActData("InChI", inchi);
  }

  public Chemical getChemicalFromChemicalUUID(Long cuuid) {
    return convertDBObjectToChemicalFromActData("_id", cuuid);
  }

  public Chemical getChemicalFromCanonName(String chemName) {
    return convertDBObjectToChemicalFromActData("canonical", chemName);
  }

  public long getChemicalIDFromName(String chemName) {
    return getChemicalIDFromName(chemName, false);
  }

  public long getChemicalIDFromName(String chemName, boolean caseInsensitive) {
    BasicDBObject query = new BasicDBObject();
    DBObject brenda = new BasicDBObject();
    DBObject pubchem = new BasicDBObject();
    DBObject synonyms = new BasicDBObject();
    if (caseInsensitive) {
      String escapedName = Pattern.quote(chemName);
      Pattern regex = Pattern.compile("^" + escapedName + "$", Pattern.CASE_INSENSITIVE);
      brenda.put("names.brenda", regex);
      pubchem.put("names.pubchem.values", regex);
      synonyms.put("names.synonyms", regex);
    } else {
      brenda.put("names.brenda", chemName);
      pubchem.put("names.pubchem.values", chemName);
      synonyms.put("names.synonyms", chemName);
    }
    BasicDBList ors = new BasicDBList();
    ors.add(brenda);
    ors.add(pubchem);
    ors.add(synonyms);
    query.put("$or", ors);
    Long id;
    DBObject o = this.dbChemicals.findOne(query);
    if(o != null)
      id = (Long)o.get("_id"); // checked: db type IS Long
    else
      id = -1L;
    return id;
  }

  public long getChemicalIDFromExactBrendaName(String chemName) {
    BasicDBObject query = new BasicDBObject();
    query.put("names.brenda", chemName.toLowerCase());
    Long id;
    DBObject o = this.dbChemicals.findOne(query);
    if(o != null)
      id = (Long)o.get("_id"); // checked: db type IS Long
    else
      id = -1L;
    return id;
  }

  public String getChemicalDBJSON(Long uuid) {
    BasicDBObject query = new BasicDBObject();
    query.put("_id", uuid);

    DBObject o = this.dbChemicals.findOne(query);
    if (o == null)
      return null;

    Set<String> keys = o.keySet();
    String json = "{\n";
    for (String key : keys) {
      json += "\t" + key + " : " + o.get(key) + ",\n";
    }
    json += "}";
    return json;
  }

  public List<Chemical> getChemicalsThatHaveField(String field) {
    DBObject val = new BasicDBObject();
    val.put("$exists", "true");
    return constructAllChemicalsFromActData(field, val);
  }
  public List<Chemical> getDrugbankChemicals() {
    DBObject val = new BasicDBObject();
    val.put("$ne", null);
    String field = "xref.DRUGBANK";

    return constructAllChemicalsFromActData(field, val);
  }
  public List<Chemical> getSigmaChemicals() {
    DBObject val = new BasicDBObject();
    val.put("$ne", null);
    String field = "xref.SIGMA";

    return constructAllChemicalsFromActData(field, val);
  }

  public List<Chemical> constructAllChemicalsFromActData(String field, Object val) {
    return constructAllChemicalsFromActData(field, val, new BasicDBObject());
  }

  public List<Chemical> constructAllChemicalsFromActData(String field, Object val, BasicDBObject keys) {
    DBCursor cur = constructCursorForMatchingChemicals(field, val, keys);

    List<Chemical> chems = new ArrayList<Chemical>();
    while (cur.hasNext())
      chems.add(convertDBObjectToChemical(cur.next()));

    cur.close();
    return chems;
  }

  public DBCursor getIdCursorForFakeChemicals() {
    DBObject fakeRegex = new BasicDBObject();
    fakeRegex.put("$regex", "^InChI=/FAKE");
    return constructCursorForMatchingChemicals("InChI", fakeRegex, new BasicDBObject("_id", true));
  }

  private DBCursor constructCursorForAllChemicals() {
    return constructCursorForMatchingChemicals(null, null, null);
  }

  private static final BasicDBObject DEFAULT_CURSOR_ORDER_BY_ID =
      new BasicDBObject("$query", new BasicDBObject()).append("$orderby", new BasicDBObject("_id", 1));
  private DBCursor constructCursorForMatchingChemicals(String field, Object val, BasicDBObject keys) {
    DBCursor cur;
    if (field != null) {
      BasicDBObject query;
      query = new BasicDBObject();
      query.put(field, val);
      if (keys == null) {
        cur = this.dbChemicals.find(query);
      } else {
        cur = this.dbChemicals.find(query, keys);
      }
    } else if (keys != null) {
      cur = this.dbChemicals.find(new BasicDBObject(), keys);
    } else {
      /* Ensure a default ordering when iterating over a whole collection.
       * This helps maintain result stability and should have minimal performance cost since we're iterating over
       * the primary keys in their natural order. */
      cur = this.dbChemicals.find(DEFAULT_CURSOR_ORDER_BY_ID);
    }

    return cur;
  }

  private DBCursor constructCursorForAllCofactors() {
    return this.dbCofactors.find();
  }

  public Map<String, Long> constructAllInChIs() {
    Map<String, Long> chems = new HashMap<String, Long>();
    BasicDBObject keys = new BasicDBObject();
    keys.append("_id", true);
    keys.append("InChI", true);
    DBCursor cur = constructCursorForMatchingChemicals(null, null, keys);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      long uuid = (Long)o.get("_id"); // checked: db type IS long
      String inchi = (String)o.get("InChI");
      chems.put(inchi, uuid);
    }

    cur.close();
    return chems;
  }

  public void smartsMatchAllChemicals(String target) {
    Indigo indigo = new Indigo(); IndigoInchi inchi = new IndigoInchi(indigo);
    IndigoObject query = indigo.loadSmarts(target);
    query.optimize();

    DBCursor cur = constructCursorForAllChemicals();
    IndigoObject mol = null, matcher;
    int cnt;
    while (cur.hasNext()) {
      Chemical c = convertDBObjectToChemical(cur.next());
      try {
        mol = inchi.loadMolecule(c.getInChI());
      } catch (IndigoException e) {
        if (e.getMessage().startsWith("core: Indigo-InChI: InChI loading failed:"))
          continue; // could not load
      }
      matcher = indigo.substructureMatcher(mol);
      if ((cnt = matcher.countMatches(query)) > 0) {
        // matches.add(c); memout's
        System.out.format("%d\t%s\n", c.getUuid(), c.getInChI());
      }
    }
    cur.close();
  }

  private Chemical convertDBObjectToChemicalFromActData(String field, Object val) {
    BasicDBObject query = new BasicDBObject();
    query.put(field, val);

    // project out the synonyms field, even though we don't have anything in it right now.
    BasicDBObject keys = new BasicDBObject();
    // keys.put("names", 0); // 0 means exclude, rest are included
    DBObject o = this.dbChemicals.findOne(query, keys);
    if (o == null)
      return null;
    return convertDBObjectToChemical(o);
  }

  public Chemical convertDBObjectToChemical(DBObject o) {
    long uuid;
    // WTF!? Are some chemicals ids int and some long?
    // this code below should not be needed, unless our db is mucked up
    try {
      uuid = (Long) o.get("_id"); // checked: db type IS long
    } catch (ClassCastException e) {
      System.err.println("WARNING: MongoDB.convertDBObjectToChemical ClassCast db.chemicals.id is not Long?");
      uuid = ((Integer) o.get("_id")).longValue(); // this should be dead code
    }

    String chemName = (String)o.get("canonical");
    DBObject xrefs = (DBObject)o.get("xref");
    Long pcid = null;
    try {
      pcid = (Long)(xrefs.get("pubchem"));
    } catch (Exception e) {

    }
    if(pcid == null) {
      pcid = (long)-1;
    }
    String inchi = (String)o.get("InChI");
    String inchiKey = (String)o.get("InChIKey");
    String smiles = (String)o.get("SMILES");
    Chemical c = new Chemical(uuid, pcid, chemName, smiles);
    c.setInchi(inchi);
    c.setCanon((String)o.get("canonical"));
    try {
      for (String typ : xrefs.keySet()) {
        if (typ.equals("pubchem"))
          continue;
        c.putRef(Chemical.REFS.valueOf(typ), MongoDBToJSON.conv((DBObject)xrefs.get(typ)));
      }
    } catch (Exception e) {

    }

    BasicDBList names = (BasicDBList)((DBObject)o.get("names")).get("brenda");
    if (names != null) {
      for (Object n : names) {
        c.addBrendaNames((String)n);
      }
    }
    if (names != null) {
      names = (BasicDBList)((DBObject)o.get("names")).get("synonyms");
      for (Object n : names) {
        c.addSynonym((String)n);
      }
    }
    if (names != null) {
      names = (BasicDBList)((DBObject)o.get("names")).get("pubchem");
      for (Object n : names) {
        String typ = (String)((DBObject)n).get("type");
        BasicDBList pnames = (BasicDBList)((DBObject)n).get("values");
        List<String> s = new ArrayList<String>();
        for (Object os : pnames)
          s.add((String)os);
        c.addNames(typ, s.toArray(new String[0]));
      }
    }
    if ((Boolean)o.get("isCofactor"))
      c.setAsCofactor();
    if ((Boolean)o.get("isNative"))
      c.setAsNative();
    if ((Double)o.get("estimateEnergy") != null)
      c.setEstimatedEnergy((Double)o.get("estimateEnergy"));
    BasicDBList keywords = (BasicDBList)o.get("keywords");
    if (keywords != null)
      for (Object k : keywords)
        c.addKeyword((String)k);
    BasicDBList cikeywords = (BasicDBList)o.get("keywords_case_insensitive");
    if (cikeywords != null)
      for (Object k : cikeywords)
        c.addCaseInsensitiveKeyword((String)k);

    BasicDBList vendors = (BasicDBList)o.get("vendors");
    Integer num_vendors = (Integer)o.get("num_vendors");
    Integer chemspiderid = (Integer)o.get("csid");

    c.setChemSpiderVendorXrefs(vendors == null ? null : MongoDBToJSON.conv(vendors));
    c.setChemSpiderNumUniqueVendors(num_vendors);
    c.setChemSpiderID(chemspiderid);

    /**
     * Shortest name  is most useful so just use that.
     */
    //TODO: what are we doing with shortest name here?
    String shortestName = c.getCanon();

    for(String name : c.getBrendaNames()) {
      if (shortestName == null || name.length() < shortestName.length())
        shortestName = name;
    }
    for(String name : c.getSynonyms()) {
      if (shortestName == null || name.length() < shortestName.length())
        shortestName = name;
    }

    return c;
  }

  public DBIterator getIteratorOverSeq() {
    DBCursor cursor = this.dbSeq.find();
    return new DBIterator(cursor);
  }

  public DBIterator getIteratorOverSeq(BasicDBObject matchCriterion, boolean notimeout, BasicDBObject keys) {
    if (keys == null) {
      keys = new BasicDBObject();
    }

    DBCursor cursor = this.dbSeq.find(matchCriterion, keys);
    if (notimeout) {
      cursor = cursor.addOption(Bytes.QUERYOPTION_NOTIMEOUT);
    }
    return new DBIterator(cursor);
  }

  public Seq getNextSeq(DBIterator iterator) {
    if (!iterator.hasNext()) {
      iterator.close();
      return null;
    }

    DBObject o = iterator.next();
    return convertDBObjectToSeq(o);
  }

  public DBIterator getIteratorOverWaterfalls() {
    DBCursor cursor = this.dbWaterfalls.find();
    return new DBIterator(cursor);
  }

  public DBObject getNextWaterfall(DBIterator iterator) {
    if (!iterator.hasNext()) {
      iterator.close();
      return null;
    }

    DBObject o = iterator.next();
    return convertDBObjectToWaterfall(o);
  }

  public DBIterator getIteratorOverCascades() {
    DBCursor cursor = this.dbCascades.find();
    return new DBIterator(cursor);
  }

  public DBObject getNextCascade(DBIterator iterator) {
    if (!iterator.hasNext()) {
      iterator.close();
      return null;
    }

    DBObject o = iterator.next();
    return convertDBObjectToCascade(o);
  }

  public DBIterator getIteratorOverChemicals() {
    DBCursor cursor = constructCursorForAllChemicals();
    return new DBIterator(cursor);
  }

  public DBIterator getIteratorOverReactions(boolean notimeout) {
    return getIteratorOverReactions(DEFAULT_CURSOR_ORDER_BY_ID, notimeout, null);
  }

  private DBIterator getIteratorOverReactions(Long low, Long high, boolean notimeout) {
    return getIteratorOverReactions(getRangeUUIDRestriction(low, high), notimeout, null);
  }

  public DBIterator getIteratorOverReactions(BasicDBObject matchCriterion, boolean notimeout, BasicDBObject keys) {

    if (keys == null) {
      keys = new BasicDBObject();
      // keys.put(projection, 1); // 1 means include, rest are excluded
    }

    DBCursor cursor = this.dbReactions.find(matchCriterion, keys);
    if (notimeout)
      cursor = cursor.addOption(Bytes.QUERYOPTION_NOTIMEOUT);
    return new DBIterator(cursor); // DBIterator is just a wrapper classs
  }

  public Reaction getNextReaction(DBIterator iterator) {

    if (!iterator.hasNext()) {
      iterator.close();
      return null;
    }

    DBObject o = iterator.next();

    return convertDBObjectToReaction(o);
  }

  public Chemical getNextChemical(DBIterator iterator) {
    if (!iterator.hasNext()) {
      iterator.close();
      return null;
    }

    DBObject o = iterator.next();
    return convertDBObjectToChemical(o);
  }

  public Cofactor getNextCofactor(DBIterator iterator) {
    if (!iterator.hasNext()) {
      iterator.close();
      return null;
    }

    DBObject o = iterator.next();
    return convertDBObjectToCofactor(o);
  }

  public DBIterator getIteratorOverCofactors() {
    DBCursor cursor = constructCursorForAllCofactors();
    return new DBIterator(cursor);
  }

  public Reaction convertDBObjectToReaction(DBObject o) {
    long uuid = (Integer)o.get("_id"); // checked: db type IS int
    String ecnum = (String)o.get("ecnum");
    String name_field = (String)o.get("easy_desc");
    Reaction.RxnDetailType type = Reaction.RxnDetailType.valueOf((String)o.get("is_abstract"));
    BasicDBList substrates = (BasicDBList)((DBObject)o.get("enz_summary")).get("substrates");
    BasicDBList products = (BasicDBList)((DBObject)o.get("enz_summary")).get("products");
    BasicDBList substrateCofactors = (BasicDBList)((DBObject)o.get("enz_summary")).get("substrate_cofactors");
    BasicDBList productCofactors = (BasicDBList)((DBObject)o.get("enz_summary")).get("product_cofactors");
    BasicDBList coenzymes = (BasicDBList)((DBObject)o.get("enz_summary")).get("coenzymes");
    BasicDBList refs = (BasicDBList) (o.get("references"));
    BasicDBList proteins = (BasicDBList) (o.get("proteins"));

    BasicDBList keywords = (BasicDBList) (o.get("keywords"));
    BasicDBList cikeywords = (BasicDBList) (o.get("keywords_case_insensitive"));

    List<Long> substr = new ArrayList<Long>();
    List<Long> prod = new ArrayList<Long>();
    List<Long> substrCofact = new ArrayList<Long>();
    List<Long> prodCofact = new ArrayList<Long>();
    List<Long> coenz = new ArrayList<Long>();

    String conversionDirectionString = (String) o.get("conversion_direction");
    ConversionDirectionType conversionDirection = conversionDirectionString == null ? null :
        ConversionDirectionType.valueOf(conversionDirectionString);

    String pathwayStepDirectionString = (String) o.get("pathway_step_direction");
    StepDirection pathwayStepDirection = pathwayStepDirectionString == null ? null :
        StepDirection.valueOf(pathwayStepDirectionString);

    for (int i = 0; i < substrates.size(); i++) {
      Boolean forBalance = (Boolean)((DBObject)substrates.get(i)).get("balance");
      if (forBalance != null && forBalance) continue;
      substr.add(getEnzSummaryIDAsLong(substrates, i));
    }
    for (int i = 0; i < products.size(); i++) {
      Boolean forBalance = (Boolean)((DBObject)products.get(i)).get("balance");
      if (forBalance != null && forBalance) continue;
      prod.add(getEnzSummaryIDAsLong(products, i));
    }
    for (int i = 0; i < substrateCofactors.size(); i++) {
      substrCofact.add(getEnzSummaryIDAsLong(substrateCofactors, i));
    }
    for (int i = 0; i < productCofactors.size(); i++) {
      prodCofact.add(getEnzSummaryIDAsLong(productCofactors, i));
    }
    for (int i = 0; i < coenzymes.size(); i++) {
      coenz.add(getEnzSummaryIDAsLong(coenzymes, i));
    }

    Reaction result = new Reaction(uuid,
        (Long[]) substr.toArray(new Long[0]),
        (Long[]) prod.toArray(new Long[0]),
        (Long[]) substrCofact.toArray(new Long[0]),
        (Long[]) prodCofact.toArray(new Long[0]),
        (Long[]) coenz.toArray(new Long[0]),
        ecnum, conversionDirection, pathwayStepDirection, name_field, type
    );

    for (int i = 0; i < substrates.size(); i++) {
      Integer c = (Integer)((DBObject)substrates.get(i)).get("coefficient");
      if (c != null) result.setSubstrateCoefficient(getEnzSummaryIDAsLong(substrates, i), c);
    }
    for (int i = 0; i < products.size(); i++) {
      Integer c = (Integer)((DBObject)products.get(i)).get("coefficient");
      if(c != null) result.setProductCoefficient(getEnzSummaryIDAsLong(products, i), c);
    }

    Double estimatedEnergy = (Double) o.get("estimateEnergy");
    result.setEstimatedEnergy(estimatedEnergy);

    String datasrc = (String)o.get("datasource");
    if (datasrc != null && !datasrc.equals(""))
      result.setDataSource(Reaction.RxnDataSource.valueOf( datasrc ));

    if (refs != null) {
      for (Object oo : refs) {
        DBObject ref = (DBObject) oo;
        Reaction.RefDataSource src = Reaction.RefDataSource.valueOf((String)ref.get("src"));
        String val = (String)ref.get("val");
        result.addReference(src, val);
      }
    }

    if (proteins != null) {
      for (Object oo : proteins) {
        result.addProteinData(MongoDBToJSON.conv((DBObject) oo));
      }
    }

    if (keywords != null)
      for (Object k : keywords)
        result.addKeyword((String) k);

    if (cikeywords != null)
      for (Object k : cikeywords)
        result.addCaseInsensitiveKeyword((String) k);

    return result;
  }

  private Long getEnzSummaryIDAsLong(BasicDBList reactant, int i) {
    try {
      return (Long)((DBObject)reactant.get(i)).get("pubchem");
    } catch (ClassCastException e) {
      return ((Integer)((DBObject)reactant.get(i)).get("pubchem")).longValue();
    }
  }

  public Set<Reaction> getReactionsConstrained(Map<String, Object> equalityCriteria) {
    BasicDBList andList = new BasicDBList();
    for (String k : equalityCriteria.keySet()) {
      BasicDBObject query = new BasicDBObject();
      query.put(k, equalityCriteria.get(k));
      andList.add(query);
    }
    BasicDBObject query = new BasicDBObject();
    query.put("$and", andList);
    DBCursor cur = this.dbReactions.find(query);

    Set<Reaction> results = new HashSet<Reaction>();
    while (cur.hasNext()) {
      results.add(convertDBObjectToReaction(cur.next()));
    }
    return results;
  }

  public List<Chemical> keywordInChemicals(String keyword) {
    return keywordInChemicals("keywords", keyword);
  }

  public List<Chemical> keywordInChemicalsCaseInsensitive(String keyword) {
    return keywordInChemicals("keywords_case_insensitive", keyword);
  }

  private List<Chemical> keywordInChemicals(String in_field, String keyword) {
    List<Chemical> chemicals = new ArrayList<Chemical>();

    DBCursor cur = constructCursorForMatchingChemicals(in_field, keyword, null);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      chemicals.add( convertDBObjectToChemical(o) );
    }
    cur.close();

    return chemicals;
  }

  public List<Seq> keywordInSequence(String keyword) {
    return keywordInSequence("keywords", keyword);
  }

  public List<Seq> keywordInSequenceCaseInsensitive(String keyword) {
    return keywordInSequence("keywords_case_insensitive", keyword);
  }

  private List<Seq> keywordInSequence(String in_field, String keyword) {
    List<Seq> seqs = new ArrayList<Seq>();
    BasicDBObject query = new BasicDBObject();
    query.put(in_field, keyword);

    BasicDBObject keys = new BasicDBObject();

    DBCursor cur = this.dbSeq.find(query, keys);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      seqs.add( convertDBObjectToSeq(o) );
    }
    cur.close();

    return seqs;
  }

  public List<DBObject> keywordInCascade(String keyword) {
    return keywordInCascade("keywords", keyword);
  }

  public List<DBObject> keywordInCascadeCaseInsensitive(String keyword) {
    return keywordInCascade("keywords_case_insensitive", keyword);
  }

  private List<DBObject> keywordInCascade(String in_field, String keyword) {
    List<DBObject> cascades = new ArrayList<DBObject>();
    BasicDBObject query = new BasicDBObject();
    query.put(in_field, keyword);

    BasicDBObject keys = new BasicDBObject();

    DBCursor cur = this.dbCascades.find(query, keys);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      cascades.add( convertDBObjectToCascade(o) );
    }
    cur.close();

    return cascades;
  }

  DBObject convertDBObjectToCascade(DBObject o) {
    // TODO: later on, we will have a cascade object that is
    // more descriptive object of cascades rather than just a DBObject
    return o;
  }

  public List<DBObject> keywordInWaterfall(String keyword) {
    return keywordInWaterfall("keywords", keyword);
  }

  public List<DBObject> keywordInWaterfallCaseInsensitive(String keyword) {
    return keywordInWaterfall("keywords_case_insensitive", keyword);
  }

  private List<DBObject> keywordInWaterfall(String in_field, String keyword) {
    List<DBObject> waterfalls = new ArrayList<DBObject>();
    BasicDBObject query = new BasicDBObject();
    query.put(in_field, keyword);

    BasicDBObject keys = new BasicDBObject();

    DBCursor cur = this.dbWaterfalls.find(query, keys);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      waterfalls.add( convertDBObjectToWaterfall(o) );
    }
    cur.close();

    return waterfalls;
  }

  DBObject convertDBObjectToWaterfall(DBObject o) {
    // TODO: later on, we will have a waterfall object that is
    // more descriptive object of cascades rather than just a DBObject
    return o;
  }

  public List<Reaction> keywordInReaction(String keyword) {
    return keywordInReaction("keywords", keyword);
  }

  public List<Reaction> keywordInReactionCaseInsensitive(String keyword) {
    return keywordInReaction("keywords_case_insensitive", keyword);
  }

  private List<Reaction> keywordInReaction(String in_field, String keyword) {
    List<Reaction> rxns = new ArrayList<Reaction>();
    BasicDBObject query = new BasicDBObject();
    query.put(in_field, keyword);

    BasicDBObject keys = new BasicDBObject();

    DBCursor cur = this.dbReactions.find(query, keys);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      rxns.add( convertDBObjectToReaction(o) );
    }
    cur.close();

    return rxns;
  }

  public Cofactor getCofactorFromUUID(Long cofactorUUID) {
    return getCofactorFromDB("_id", cofactorUUID);
  }

  public Cofactor getCofactorFromInChI(String inchi) {
    return getCofactorFromDB("InChI", inchi);
  }

  private Cofactor getCofactorFromDB(String field, Object val) {
    BasicDBObject query = new BasicDBObject();
    query.put(field, val);
    BasicDBObject keys = new BasicDBObject();
    DBObject o = this.dbCofactors.findOne(query, keys);
    if (o == null)
      return null;
    return convertDBObjectToCofactor(o);
  }

  public Cofactor convertDBObjectToCofactor(DBObject o) {
    long uuid = (Long) o.get("_id");
    String inchi = (String)o.get("InChI");
    BasicDBList ns = (BasicDBList)o.get("names");
    List<String> names = new ArrayList<>();
    if (ns != null) {
      for (Object n : ns) {
        names.add((String)n);
      }
    }
    Cofactor cofactor = new Cofactor(uuid, inchi, names);

    return cofactor;
  }

  public Reaction getReactionFromUUID(Long reactionUUID) {
    if (reactionUUID < 0) {
      throw new RuntimeException(String.format(
          "getReactionFromUUID called with a negaive number (%d).  It used to reverse the reaction.", reactionUUID));
    }
    BasicDBObject query = new BasicDBObject();
    query.put("_id", reactionUUID);

    BasicDBObject keys = new BasicDBObject();
    DBObject o = this.dbReactions.findOne(query, keys);
    if (o == null)
      return null;
    return convertDBObjectToReaction(o);
  }

  public BasicDBObject getRangeUUIDRestriction(Long lowUUID, Long highUUID) {
    BasicDBObject restrictTo = new BasicDBObject();
    // need to encode { "_id" : { $gte : lowUUID, $lte : highUUID } }
    BasicDBObject range = new BasicDBObject();
    if (lowUUID != null)
      range.put("$gte", lowUUID);
    if (highUUID != null)
      range.put("$lte", highUUID);
    restrictTo.put("_id", range);
    return restrictTo;
  }

  public List<Long> getAllReactionUUIDs() {
    return getAllCollectionUUIDs(this.dbReactions);
  }

  public List<Long> getAllSeqUUIDs() {
    return getAllCollectionUUIDs(this.dbSeq);
  }

  public List<Long> getAllCollectionUUIDs(DBCollection collection) {

    List<Long> ids = new ArrayList<Long>();

    BasicDBObject query = new BasicDBObject();
    BasicDBObject keys = new BasicDBObject();
    keys.put("_id", 1); // 0 means exclude, rest are included
    DBCursor cur = collection.find(query, keys);

    while (cur.hasNext()) {
      DBObject o = cur.next();
      long uuid = (Integer)o.get("_id"); // checked: db type IS int
      ids.add(uuid);
    }
    cur.close();

    return ids;
  }

  public Seq getSeqFromID(Long seqID) {
    BasicDBObject query = new BasicDBObject();
    query.put("_id", seqID);

    BasicDBObject keys = new BasicDBObject();
    DBObject o = this.dbSeq.findOne(query, keys);
    if (o == null)
      return null;
    return convertDBObjectToSeq(o);
  }

  public Seq getSeqFromAccession(String accession) {
    BasicDBObject query = new BasicDBObject();
    query.put("metadata.accession", accession);

    BasicDBObject keys = new BasicDBObject();
    DBObject o = this.dbSeq.findOne(query, keys);
    if (o == null)
      return null;
    return convertDBObjectToSeq(o);
  }

  public List<Seq> getSeqWithSARConstraints() {
    List<Seq> seqs = new ArrayList<Seq>();
    BasicDBObject query = new BasicDBObject();
    query.put("sar_constraints", new BasicDBObject("$exists", true));

    BasicDBObject keys = new BasicDBObject();

    DBCursor cur = this.dbSeq.find(query, keys);
    while (cur.hasNext()) {
      DBObject o = cur.next();
      seqs.add( convertDBObjectToSeq(o) );
    }
    cur.close();

    return seqs;
  }

  private Seq convertDBObjectToSeq(DBObject o) {
    long id = (Integer)o.get("_id"); // checked: db type IS int
    String ecnum = (String)o.get("ecnum");
    String org_name = (String)o.get("org");
    Long org_id = (Long)o.get("org_id");
    String aa_seq = (String)o.get("seq");
    String srcdb = (String)o.get("src");

    BasicDBList refs = (BasicDBList)o.get("references");
    DBObject meta = (DBObject)o.get("metadata");
    BasicDBList keywords = (BasicDBList) (o.get("keywords"));
    BasicDBList cikeywords = (BasicDBList) (o.get("keywords_case_insensitive"));
    BasicDBList rxn_refs = (BasicDBList) (o.get("rxn_refs"));
    BasicDBList substrates_uniform_refs = (BasicDBList) (o.get("substrates_uniform_refs"));
    BasicDBList products_uniform_refs = (BasicDBList) (o.get("products_uniform_refs"));
    BasicDBList substrates_diverse_refs = (BasicDBList) (o.get("substrates_diverse_refs"));
    BasicDBList products_diverse_refs = (BasicDBList) (o.get("products_diverse_refs"));
    BasicDBList rxn2reactants = (BasicDBList) (o.get("rxn_to_reactants"));
    BasicDBList sar_constraints = (BasicDBList) (o.get("sar_constraints"));

    if (srcdb == null) srcdb = Seq.AccDB.swissprot.name();
    Seq.AccDB src = Seq.AccDB.valueOf(srcdb); // genbank | uniprot | trembl | embl | swissprot

    List<String> references = new ArrayList<String>();
    if (refs != null) for (Object r : refs) references.add((String)r);

    String dummyString = ""; // for type differentiation in overloaded method
    Long dummyLong = 0L; // for type differentiation in overloaded method

    Set<String> kywrds = from_dblist(keywords, dummyString);
    Set<String> cikywrds = from_dblist(cikeywords, dummyString);
    Set<Long> rxns_catalyzed = from_dblist(rxn_refs, dummyLong);
    Set<Long> substrates_uniform = from_dblist(substrates_uniform_refs, dummyLong);
    Set<Long> substrates_diverse = from_dblist(substrates_diverse_refs, dummyLong);
    Set<Long> products_uniform = from_dblist(products_uniform_refs, dummyLong);
    Set<Long> products_diverse = from_dblist(products_diverse_refs, dummyLong);

    HashMap<Long, Set<Long>> rxn2substrates = new HashMap<Long, Set<Long>>();
    HashMap<Long, Set<Long>> rxn2products = new HashMap<Long, Set<Long>>();
    if (rxn2reactants != null) {
      for (Object oo : rxn2reactants) {
        DBObject robj = (DBObject) oo;
        Long rid = (Long) robj.get("rxn");
        Set<Long> r_substrates = from_dblist((BasicDBList) robj.get("substrates"), dummyLong);
        Set<Long> r_products = from_dblist((BasicDBList) robj.get("products"), dummyLong);
        rxn2substrates.put(rid, r_substrates);
        rxn2products.put(rid, r_products);
      }
    }

    SAR sar = new SAR();
    if (sar_constraints != null) {
      for (Object oo : sar_constraints) {
        DBObject sc_obj = (DBObject) oo;
        Object d = sc_obj.get("data");
        SAR.ConstraintPresent p = SAR.ConstraintPresent.valueOf((String) sc_obj.get("presence_req")); // should_have/should_not_have
        SAR.ConstraintContent c = SAR.ConstraintContent.valueOf((String) sc_obj.get("contents_req")); // substructure
        SAR.ConstraintRequire r = SAR.ConstraintRequire.valueOf((String) sc_obj.get("requires_req")); // soft/hard
        sar.addConstraint(p, c, r, d);
      }
    }

    return Seq.rawInit(id, ecnum, org_id, org_name, aa_seq, references, meta, src,
                        // the rest of the params are the ones that are typically
                        // "constructed". But since we are reading from the DB, we manually init
                        kywrds, cikywrds, rxns_catalyzed,
                        substrates_uniform, substrates_diverse,
                        products_uniform, products_diverse,
                        rxn2substrates, rxn2products, sar
                       );
  }

  public String getOrganismNameFromId(Long id) {
    BasicDBObject query = new BasicDBObject();
    query.put("org_id", id);
    BasicDBObject keys = new BasicDBObject();
    keys.put("name", 1);

    if(this.dbOrganismNames!=null) {
      DBObject cur = this.dbOrganismNames.findOne(query, keys);
      if(cur == null) {
        //System.out.println("Did not find in organismnames: " + name);
        return null;
      }
      return (String)cur.get("name");
    } else {
      //System.out.println("no organism names collection");
    }
    return null;
  }

  public long getOrganismId(String name) {
    BasicDBObject query = new BasicDBObject();
    query.put("name", name);
    BasicDBObject keys = new BasicDBObject();
    keys.put("org_id", 1);

    if(this.dbOrganismNames!=null) {
      DBObject cur = this.dbOrganismNames.findOne(query, keys);
      if(cur == null) {
        //System.out.println("Did not find in organismnames: " + name);
        return -1;
      }
      return (Long)cur.get("org_id"); // checked: db type IS long
    } else {
      //System.out.println("no organism names collection");
    }
    return -1;
  }

  /*
   * Returns set of all organism ids involved in reactions
   */
  public Set<Long> getOrganismIDs() {
    DBIterator iterator = getIteratorOverReactions(new BasicDBObject(), false, null);
    Set<Long> ids = new HashSet<Long>();
    while (iterator.hasNext()) {
      DBObject r = iterator.next();
      BasicDBList orgs = (BasicDBList) r.get("organisms");
      for(Object o : orgs) {
        ids.add((Long)((DBObject) o).get("id")); // checked: db type IS Long
      }
    }
    return ids;
  }

  public Set<Long> getOrganismIDs(Long reactionID) {
    if (reactionID < 0) {
      reactionID = Reaction.reverseID(reactionID);
    }
    DBObject query = new BasicDBObject();
    query.put("_id", reactionID);
    Set<Long> ids = new HashSet<Long>();
    DBObject reaction = this.dbReactions.findOne(query);
    if (reaction != null) {
      BasicDBList orgs = (BasicDBList) reaction.get("organisms");
      for(Object o : orgs) {
        ids.add((Long)((DBObject) o).get("id")); // checked: db type IS long
      }
    }
    return ids;
  }

  public List<P<Reaction.RefDataSource, String>> getReferences(Long reactionID) {
    if (reactionID < 0) {
      reactionID = Reaction.reverseID(reactionID);
    }
    DBObject query = new BasicDBObject();
    query.put("_id", reactionID);
    List<P<Reaction.RefDataSource, String>> refs = new ArrayList<>();
    DBObject reaction = this.dbReactions.findOne(query);
    if (reaction != null) {
      BasicDBList dbrefs = (BasicDBList) reaction.get("references");
      if (dbrefs != null)
        for (Object oo : dbrefs) {
          DBObject ref = (DBObject) oo;
          Reaction.RefDataSource src = Reaction.RefDataSource.valueOf((String)ref.get("src"));
          String val = (String)ref.get("val");
          refs.add(new P<Reaction.RefDataSource, String>(src, val));
      }
    }
    return refs;
  }

  public Set<String> getKMValues(Long reactionID) {
    DBObject query = new BasicDBObject();
    query.put("_id", reactionID);
    Set<String> kmSet = new HashSet<String>();
    DBObject reaction = this.dbReactions.findOne(query);
    if (reaction != null) {
      BasicDBList kms = (BasicDBList) reaction.get("km_values");
      if (kms != null) {
        for(Object km : kms) {
          kmSet.add((String) km);
        }
      }
    }
    return kmSet;
  }

  public Set<String> getTurnoverNumbers(Long reactionID) {
    DBObject query = new BasicDBObject();
    query.put("_id", reactionID);
    Set<String> turnoverSet = new HashSet<String>();
    DBObject reaction = this.dbReactions.findOne(query);
    if (reaction != null) {
      BasicDBList turnovers = (BasicDBList) reaction.get("turnover_numbers");
      if (turnovers != null) {
        for(Object turnover : turnovers) {
          turnoverSet.add((String) turnover);
        }
      }
    }
    return turnoverSet;
  }

  private void createCofactorsIndex(String field) {
    createCofactorsIndex(field, false); // create normal/non-hashed index
  }

  private void createCofactorsIndex(String field, boolean hashedIndex) {
    if (hashedIndex)  {
      this.dbCofactors.createIndex(new BasicDBObject(field, "hashed"));
    } else {
      this.dbCofactors.createIndex(new BasicDBObject(field, 1));
    }
  }

  private void createChemicalsIndex(String field) {
    createChemicalsIndex(field, false); // create normal/non-hashed index
  }

  private void createChemicalsIndex(String field, boolean hashedIndex) {
    if (hashedIndex)  {
      this.dbChemicals.createIndex(new BasicDBObject(field, "hashed"));
    } else {
      this.dbChemicals.createIndex(new BasicDBObject(field, 1));
    }
  }

  private void createOrganismNamesIndex(String field) {
    this.dbOrganismNames.createIndex(new BasicDBObject(field,1));
  }

  public int submitToActSeqDB(Seq.AccDB src, String ec, String org, Long org_id, String seq, List<String> pmids, Set<Long> rxns, HashMap<Long, Set<Long>> rxn2substrates, HashMap<Long, Set<Long>> rxn2products, Set<Long> substrates_uniform, Set<Long> substrates_diverse, Set<Long> products_uniform, Set<Long> products_diverse, SAR sar, DBObject meta) {
    BasicDBObject doc = new BasicDBObject();
    int id = new Long(this.dbSeq.count()).intValue();
    doc.put("_id", id);
    doc.put("src", src.name()); // genbank, uniprot, swissprot, trembl, embl
    doc.put("ecnum", ec);
    doc.put("org", org);
    doc.put("org_id", org_id); // this is the NCBI Taxonomy id, should correlate with db.organismnames{org_id} and db.organisms.{id}
    doc.put("seq", seq);
    BasicDBList refs = new BasicDBList();
    if (pmids != null) refs.addAll(pmids);
    doc.put("references", refs);
    doc.put("metadata", meta); // the metadata contains the uniprot acc#, name, uniprot catalytic activity,
    Object accession = meta.get("accession");

    doc.put("rxn_refs", to_dblist(rxns));
    doc.put("substrates_uniform_refs", to_dblist(substrates_uniform));
    doc.put("substrates_diverse_refs", to_dblist(substrates_diverse));
    doc.put("products_uniform_refs", to_dblist(products_uniform));
    doc.put("products_diverse_refs", to_dblist(products_diverse));

    Set<DBObject> rxn2reactants = new HashSet<DBObject>();
    for (Long r : rxn2substrates.keySet()) {
      BasicDBList rsub = to_dblist(rxn2substrates.get(r));
      BasicDBList rprd = to_dblist(rxn2products.get(r));

      DBObject robj = new BasicDBObject();
      robj.put("rxn", r);
      robj.put("substrates", rsub);
      robj.put("products", rprd);
      rxn2reactants.add(robj);
    }
    doc.put("rxn_to_reactants", to_dblist(rxn2reactants));
    BasicDBList constraints = new BasicDBList();
    HashMap<Object, SARConstraint> sarConstraints = sar.getConstraints();
    for (Object data : sarConstraints.keySet()) {
      SARConstraint sc = sarConstraints.get(data);
      DBObject c = new BasicDBObject();
      c.put("data"         , data);
      c.put("presence_req" , sc.presence.toString()); // should_have/should_not_have
      c.put("contents_req" , sc.contents.toString()); // substructure
      c.put("requires_req" , sc.requires.toString()); // soft/hard
      constraints.add(c);
    }
    doc.put("sar_constraints", constraints);

    this.dbSeq.insert(doc);

    if (org != null && seq !=null)
      System.out.format("Inserted %s = [%s, %s] = %s %s\n", accession, ec, org.substring(0,Math.min(10, org.length())), seq.substring(0,Math.min(20, seq.length())), refs);

    return id;
  }

  <X> BasicDBList to_dblist(Set<X> set) {
    BasicDBList dblist = new BasicDBList();
    if (set != null) dblist.addAll(set);
    return dblist;
  }

  <X> Set<X> from_dblist(BasicDBList dblist, X dummy) {
    Set<X> set = new HashSet<X>();
    if (dblist != null)
      for (Object o : dblist) set.add((X) o);
    return set;
  }

  public void updateKeywords(Seq seq) {
    BasicDBObject query = new BasicDBObject().append("_id", seq.getUUID());
    DBObject obj = this.dbSeq.findOne(query);
    obj.put("keywords", seq.getKeywords());
    obj.put("keywords_case_insensitive", seq.getCaseInsensitiveKeywords());
    this.dbSeq.update(query, obj);
  }

    /*
     *
     *
     * End of other helper functions
     *
     *
     */


  /**
   * The following functions are for performing organism specific retrievals.
   */

  /**
   * Retrieve all reaction ids observed in given species
   * @param speciesID
   * @return
   */
  public Set<Long> getReactionsBySpecies(Long speciesID) {
    Map<Long, Set<Long>> speciesIDs = getOrganisms();
    Set<Long> relevantIDs = speciesIDs.get(speciesID);
    Set<Long> result = new HashSet<Long>();
    for (Long id : relevantIDs) {
      result.addAll(graphByOrganism(id));
    }
    return result;
  }


  /**
   * graphByOrganism() returns a list of all reactionIDs containing the given organismID.
   *
   * @param organismID
   * @return List<Long> List of reaction IDs for given organismID
   */
  public List<Long> graphByOrganism(Long organismID) {

    DBObject query = new BasicDBObject();
    if(organismID == null || organismID > -1)
      query.put("organisms.id", organismID);
    List<Long> graphList = new ArrayList<Long>();

    DBCursor reactionCursor = this.dbReactions.find(query);
    for (DBObject i : reactionCursor) {
      graphList.add(((Integer)i.get("_id")).longValue());  // checked: db type IS int
    }
    return  graphList;
  }

  /**
   * getOrganisms() returns a list of all unique species IDs in database
   * mapped to itself, its parents, and descendants
   *
   * @return
   */
  @SuppressWarnings("unchecked")
  public Map<Long,Set<Long>> getOrganisms() {
    List<Long> ids = (List<Long>) this.dbReactions.distinct("organisms.id");
    //map species id to all ids associated with it
    Map<Long,Set<Long>> speciesIDs = new HashMap<Long,Set<Long>>();
    for(Long organismID : ids) {
      //check if organism id on species level
      List<Long> idsToAdd = new ArrayList<Long>();
      Long speciesID;
      DBObject orgQuery = new BasicDBObject();
      orgQuery.put("_id", organismID);
      DBObject org = dbOrganisms.findOne(orgQuery);
      String rank = (String) org.get("rank");
      Long parent = (Long) org.get("parent_id"); // checked: db type IS long
      speciesID = null;
      while(organismID!=1) {
        idsToAdd.add(organismID);
        if(rank.equals("species")) {
          speciesID = organismID;
          //break;
        }
        orgQuery.put("_id", parent);
        org = dbOrganisms.findOne(orgQuery);
        organismID = parent;
        rank = (String) org.get("rank");
        parent = (Long) org.get("parent_id"); // checked: db type IS long
      }
      if(speciesID==null) continue;
      if(!speciesIDs.containsKey(speciesID)) {
        speciesIDs.put(speciesID, new HashSet<Long>());
      }
      speciesIDs.get(speciesID).addAll(idsToAdd);
    }
    return speciesIDs;
  }

  /**
   * End of organism queries.
   */


  /**
   * Getting KEGG data
   */

  private Map<String, Long> keggID_ActID;
  public Map<String, Long> getKeggID_ActID(boolean useCached) {
    if (keggID_ActID == null || !useCached)
      keggID_ActID = new HashMap<String, Long>();
    else
      return keggID_ActID;
    DBIterator it = getIteratorOverChemicals();
    while (it.hasNext()) {
      Chemical c = getNextChemical(it);
      DBObject o = (DBObject) c.getRef(Chemical.REFS.KEGG);
      if (o == null) continue;
      BasicDBList list = (BasicDBList) o.get("id");
      for (Object s : list) {
        keggID_ActID.put((String) s, c.getUuid());
      }
    }
    return keggID_ActID;
  }

  /**
   * Following methods are related to Bing cross-references installation in the Installer DB along with various
   * queries to obtain names (aka synonyms)
   */

  public BasicDBObject createBingMetadataDoc(HashSet<UsageTermUrlSet> usageTerms,
                                             Long totalCountSearchResults,
                                             String bestName) {
    BasicDBObject metadata = new BasicDBObject();
    if (usageTerms != null) {
      BasicDBList usageTermsDBObject = new BasicDBList();
      for (UsageTermUrlSet usageTerm : usageTerms) {
        // What happens if you don't translate to basic db obj in the next line?
        usageTermsDBObject.add(usageTerm.getBasicDBObject());
      }
      metadata.put("usage_terms", usageTermsDBObject);
    }
    if (totalCountSearchResults >= 0) {
      metadata.put("total_count_search_results", totalCountSearchResults);
    }
    if (!bestName.equals("")) {
      metadata.put("best_name", bestName);
    }
    return metadata;
  }

  public void updateChemicalWithBingSearchResults(String inchi, String bestName, BasicDBObject metadata) {
    Chemical c = this.getChemicalFromInChI(inchi);
    if (c != null) {
      long id = c.getUuid();
      BasicDBObject set = new BasicDBObject().append("xref.BING.metadata", metadata);
      set.put("xref.BING.dbid", bestName);
      BasicDBObject query = new BasicDBObject();
      query.put("_id", id);
      BasicDBObject update = new BasicDBObject();
      update.put("$set", set);
      this.dbChemicals.update(query, update);
    }
  }

  public MoleculeNames fetchNamesFromInchi(String inchi) {

    MoleculeNames moleculeNames = new MoleculeNames(inchi);

    BasicDBObject whereQuery = new BasicDBObject();
    BasicDBObject fields = new BasicDBObject();
    fields.put("names.brenda", 1);
    fields.put("xref.CHEBI.metadata.Synonym", 1);
    fields.put("xref.DRUGBANK.metadata", 1);
    fields.put("xref.METACYC.meta", 1);

    whereQuery.put("InChI", inchi);
    BasicDBObject c = (BasicDBObject) dbChemicals.findOne(whereQuery, fields);

    BasicDBObject names = (BasicDBObject) c.get("names");
    BasicDBList brendaNamesList = (BasicDBList) names.get("brenda");
    if (brendaNamesList != null) {
      HashSet<String> brendaNames = new HashSet<>();
      for(Object brendaName: brendaNamesList) {
        brendaNames.add((String) brendaName);
      }
      moleculeNames.setBrendaNames(brendaNames);
    }
    // XREF
    BasicDBObject xref = (BasicDBObject) c.get("xref");
    if (xref != null) {
      // CHEBI
      BasicDBObject chebi = (BasicDBObject) xref.get("CHEBI");
      if (chebi != null) {
        HashSet<String> chebiNames = new HashSet<>();
        BasicDBObject chebiMetadata = (BasicDBObject) chebi.get("metadata");
        BasicDBList chebiSynonymsList = (BasicDBList) chebiMetadata.get("Synonym");
        if (chebiSynonymsList != null) {
          for (Object chebiName : chebiSynonymsList) {
            chebiNames.add((String) chebiName);
          }
          moleculeNames.setChebiNames(chebiNames);
        }
      }
      // METACYC
      BasicDBObject metacyc = (BasicDBObject) xref.get("METACYC");
      if (metacyc != null) {
        HashSet<String> metacycNames = new HashSet<>();
        BasicDBList metacycMetadata = (BasicDBList) metacyc.get("meta");
        if (metacycMetadata != null) {
          for (Object metaCycMeta : metacycMetadata) {
            BasicDBObject metaCycMetaDBObject = (BasicDBObject) metaCycMeta;
            metacycNames.add((String) metaCycMetaDBObject.get("sname"));
          }
          moleculeNames.setMetacycNames(metacycNames);
        }
      }
      // DRUGBANK
      BasicDBObject drugbank = (BasicDBObject) xref.get("DRUGBANK");
      if (drugbank != null) {
        HashSet<String> drugbankNames = new HashSet<>();
        BasicDBObject drugbankMetadata = (BasicDBObject) drugbank.get("metadata");
        drugbankNames.add((String) drugbankMetadata.get("name"));
        BasicDBObject drugbankSynonyms = (BasicDBObject) drugbankMetadata.get("synonyms");
        if (drugbankSynonyms.get("synonym") instanceof String) {
          drugbankNames.add((String) drugbankSynonyms.get("synonym"));
          moleculeNames.setDrugbankNames(drugbankNames);
        } else {
          BasicDBList drugbankSynonymsList = (BasicDBList) drugbankSynonyms.get("synonym");
          if (drugbankSynonymsList != null) {
            for (Object drugbankSynonym : drugbankSynonymsList) {
              drugbankNames.add((String) drugbankSynonym);
            }
            moleculeNames.setDrugbankNames(drugbankNames);
          }
        }
      }
    }
    return moleculeNames;
  }

  public boolean hasBingSearchResultsFromInchi(String inchi) {
    BasicDBObject whereQuery = new BasicDBObject().append("InChI", inchi);
    BasicDBObject existsQuery = new BasicDBObject().append("$exists", true);
    whereQuery.put("xref.BING", existsQuery);
    BasicDBObject fields = new BasicDBObject();
    BasicDBObject c = (BasicDBObject) dbChemicals.findOne(whereQuery, fields);
    return (c != null);
  }
}
