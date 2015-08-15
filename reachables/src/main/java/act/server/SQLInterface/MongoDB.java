package act.server.SQLInterface;

import java.io.BufferedWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import act.server.Logger;
import act.server.Molecules.BRO;
import act.server.Molecules.BadRxns;
import act.server.Molecules.CRO;
import act.server.Molecules.ERO;
import act.server.Molecules.RO;
import act.server.Molecules.RxnWithWildCards;
import act.server.Molecules.TheoryROs;
import act.shared.Chemical;
import act.shared.Chemical.REFS;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.sar.SAR;
import act.shared.sar.SARConstraint;
import act.shared.ReactionType;
import act.shared.ReactionWithAnalytics;
import act.shared.helpers.P;
import act.shared.helpers.T;
import act.shared.helpers.MongoDBToJSON;
import act.server.ROExpansion.CurriedERO;
import act.client.CommandLineRun;

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
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONObject;

public class MongoDB implements DBInterface{
    
  private String hostname;
  private String database;
  private int port;
	
	protected DBCollection dbAct; // Act collections
	private DBCollection dbChemicals;
	private DBCollection dbChemicalsSimilarity;
	private DBCollection dbCofactorAAMs;
	protected DBCollection dbOrganisms;
	private DBCollection dbOrganismNames;
	private DBCollection dbCascades;
	private DBCollection dbWaterfalls;
	private DBCollection dbSeq;
	private DBCollection dbOperators; // TRO collection
	private DBCollection dbBRO, dbCRO, dbERO; // BRO, CRO, and ERO collections
	private DBCollection dbPubmed; // the pubmed collection is separate from actv01 db
    
	protected DB mongoDB;
  protected Mongo mongo;

  public MongoDB(String mongoActHost, int port, String dbs) {
  	this.hostname = mongoActHost;
  	this.port = port;
  	this.database = dbs;

  	initDB();
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
			mongoDB = mongo.getDB( this.database );
			
			// in case the db is protected then we would do the following:
			// boolean auth = db.authenticate(myUserName, myPassword);
			// but right now we do not care.
			
			this.dbAct = mongoDB.getCollection("actfamilies");
			this.dbChemicals = mongoDB.getCollection("chemicals");
			this.dbChemicalsSimilarity = mongoDB.getCollection("chemsimilarity");
			this.dbCofactorAAMs = mongoDB.getCollection("cofactoraams");
			this.dbOrganisms = mongoDB.getCollection("organisms");
			this.dbOrganismNames = mongoDB.getCollection("organismnames");
			this.dbOperators = mongoDB.getCollection("operators");
			this.dbBRO = mongoDB.getCollection("bros");
			this.dbCRO = mongoDB.getCollection("cros");
			this.dbERO = mongoDB.getCollection("eros");
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
		
		this.dbChemicalsSimilarity.createIndex(new BasicDBObject("c1",1));
		this.dbChemicalsSimilarity.createIndex(new BasicDBObject("c2",1));

		this.createOrganismNamesIndex("name");
		this.createOrganismNamesIndex("org_id");
	}
	
	public int port() { return this.port; }
	public String host() { return this.hostname; }
	public String dbs() { return this.database; }
	public String location() { return this.hostname + "." + this.port + "." + this.database; }
	
	public void dumpActToFile(String dumpFile) {
		try {

			BufferedWriter writer = new BufferedWriter(new FileWriter(dumpFile));

			DBCursor cursor = this.dbAct.find();
			while (cursor.hasNext()) {
				writer.write(flattenActFamily((BasicDBObject)cursor.next()) + "\n");
			}

			writer.close();
		} catch (IOException e) {
			System.out.println("Exception attempting to open dumpfile:" + dumpFile);
			System.exit(-1);
		}
	}
	
    private String flattenActFamily(BasicDBObject family) {
    	// _id,
    	// enz_summary.substrates.0.pubchem,enz_summary.substrates.1.pubchem,enz_summary.substrates.2.pubchem,enz_summary.substrates.3.pubchem,
    	// enz_summary.products.0.pubchem,enz_summary.products.1.pubchem,enz_summary.products.2.pubchem,enz_summary.products.3.pubchem,
    	// ecnum,easy_desc
    	
    	String f = (Integer)family.get("_id") + ""; f += "\t";
    	f += getReactantFromMongoDocument(family, "substrates", 0); f += "\t";
    	f += getReactantFromMongoDocument(family, "substrates", 1); f += "\t";
    	f += getReactantFromMongoDocument(family, "substrates", 2); f += "\t";
    	f += getReactantFromMongoDocument(family, "substrates", 3); f += "\t";
    	f += getReactantFromMongoDocument(family, "substrates", 4); f += "\t";
    	f += getReactantFromMongoDocument(family, "substrates", 5); f += "\t";
    	f += getReactantFromMongoDocument(family, "products", 0); f += "\t";
    	f += getReactantFromMongoDocument(family, "products", 1); f += "\t";
    	f += getReactantFromMongoDocument(family, "products", 2); f += "\t";
    	f += getReactantFromMongoDocument(family, "products", 3); f += "\t";
    	f += getReactantFromMongoDocument(family, "products", 4); f += "\t";
    	f += getReactantFromMongoDocument(family, "products", 5); f += "\t";
    	f += (String)family.get("ecnum"); f+= "\t";
    	f += (String)family.get("easy_desc");
    	return f;
	}

	private String getReactantFromMongoDocument(BasicDBObject family, String which, int i) {
		BasicDBList o = (BasicDBList)((DBObject)family.get("enz_summary")).get(which);
		if (i >= o.size())
			return "";
		return "" + (Long)((DBObject)o.get(i)).get("pubchem");
		// fails:
		// return "" + family.get("enz_summary." + which + "." + i + ".pubchem");
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
    // WTF!!!!! Who wrote this code!???? O(n) instead of O(1) 
    // for getting the size!?
		//      return new Long(this.dbChemicals.find().size());
    // replaced with collection.count()
    return this.dbChemicals.count();

	}
	
	public void submitToActWaterfallDB(Long ID, DBObject waterfall) {
		if (this.dbWaterfalls == null) {
			// in simulation mode: not writing to the MongoDB, just the screen
			return;
		}

		// insert a new doc to the collection
    waterfall.put("_id", ID);
		this.dbWaterfalls.insert(waterfall);
  }

	public void submitToActCascadeDB(Long ID, DBObject cascade) {
		if (this.dbCascades == null) {
			// in simulation mode: not writing to the MongoDB, just the screen
			return;
		}

		// insert a new doc to the collection
    cascade.put("_id", ID);
		this.dbCascades.insert(cascade);
  }

	public void submitToActChemicalDB(Chemical c, Long ID) {
		
		if (this.dbChemicals == null) {
			// in simulation mode: not writing to the MongoDB, just the screen
			System.out.println("Chemical: " + c);
			return;
		}

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
		BasicDBObject doc = createChemicalDoc(c, id);
		DBObject query = new BasicDBObject();
		query.put("_id", id);
		BasicDBObject set = new BasicDBObject("$set", doc);
		this.dbChemicals.update(query, doc);
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
				// System.out.format("Installing into chem %d xref = %s\n", doc.get("_id"), xrefs);
			}
		}
		// if (cnt > 1)
		//	  System.out.format("Installing into chem %d xref = %s\n", doc.get("_id"), xrefs);
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
//          createChemicalDoc and constructChemical
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
		//			canon:String (shortestName)
		//			brendaNames:List<String>
		//			synonyms:List<String>
		//			names:Map<String,String[]> (pubchem names type->names)
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
		DBObject obj = this.dbAct.findOne(query);
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
		Set<Long> substratesNew = r.getSubstratesWCoefficients();
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
		Set<Long> productsNew = r.getProductsWCoefficients();
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
		this.dbAct.update(query, obj);
	}
	
	public void updateEstimatedEnergy(Chemical chemical) {
		BasicDBObject query = new BasicDBObject().append("_id", chemical.getUuid());
		DBObject obj = this.dbChemicals.findOne(query);
		obj.put("estimateEnergy", chemical.getEstimatedEnergy());
		this.dbChemicals.update(query, obj);
	}
	
	public void updateEstimatedEnergy(Reaction reaction) {
		BasicDBObject query = new BasicDBObject().append("_id", reaction.getUUID());
		DBObject obj = this.dbAct.findOne(query);
		obj.put("estimateEnergy", reaction.getEstimatedEnergy());
		this.dbAct.update(query, obj);
	}
	
	// D public void updateSequenceRefsOf(Reaction reaction) {
	// D 	BasicDBObject query = new BasicDBObject().append("_id", reaction.getUUID());
	// D 	DBObject obj = this.dbAct.findOne(query);
  // D   BasicDBList refs = new BasicDBList();
  // D   for (Long r : reaction.getSequences())
  // D     refs.add(r);
	// D 	obj.put("seq_refs", refs);
	// D 	this.dbAct.update(query, obj);
	// D }

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
		DBObject obj = this.dbAct.findOne(query);
		obj.put("keywords", reaction.getKeywords());
		obj.put("keywords_case_insensitive", reaction.getCaseInsensitiveKeywords());
		this.dbAct.update(query, obj);
	}
	
	public void updateReaction(ReactionWithAnalytics r) {	
		/* 
		db.act.save({
			_id: 12324,
			ecnum: "1.1.1.1",
			easy_desc: "{organism} a + b => c + d <ref1>",
			enz_summary: {
    			substrates: [{pubchem:2345, rarity:0.9}, {pubchem:456, rarity:0.1}],
    			products:   [{pubchem:1234, rarity:0.5}, {pubchem:234, rarity:0.5}]
			});
		*/
		
		BasicDBObject doc = new BasicDBObject();
		doc.put("_id", r.getUUID()); // auto index on UUID field
		doc.put("ecnum", r.getECNum());
		doc.put("easy_desc", r.getReactionName());
		
		BasicDBList substr = new BasicDBList();
		Long[] ss = r.getSubstrates();
		Float[] sRarity = r.getSubstrateRarityPDF();
		for (int i = 0; i<ss.length; i++)
			substr.put(i, getObject("pubchem", ss[i], "rarity", sRarity[i]));
		
		BasicDBList prods = new BasicDBList();
		Long[] pp = r.getProducts();
		Float[] pRarity = r.getProductRarityPDF();
		for (int i = 0; i<pp.length; i++)
			prods.put(i, getObject("pubchem", pp[i], "rarity", pRarity[i]));
			
		BasicDBObject enz = new BasicDBObject();
		enz.put("products", prods);
		enz.put("substrates", substr);
		doc.put("enz_summary", enz);
		
		if (this.dbAct == null) {
			// in simulation mode and not really writing to the MongoDB, just the screen
			System.out.println("Reaction: " + r);
		} else {
			// writing to MongoDB collection act
			// update instead of inserting....
			// we are going to update the reaction with UUID match
			BasicDBObject update = new BasicDBObject("$set",new BasicDBObject("enz_summary", enz));
			this.dbAct.update(new BasicDBObject().append("_id", r.getUUID()), update);
			//this.dbAct.update(new BasicDBObject().append("_id", r.getUUID()), doc, true /* upsert: insert if not present */, false /* no multi */);
			System.out.println("Written: " + doc);
		}
	}

	public int submitToActReactionDB(Reaction r) {
		
		// if reaction already present in Act, then ignore.
		if (alreadyEntered(r)) {
			System.out.println("___ Duplicate reaction? : " + r.getUUID());
			return -1;
		}

    if (r.getUUID() != -1) {
      // this function strictly
      System.err.println("FATAL Error: Aborting in MongoDB.submitToActReactionDB. Reaction asked to add has a populated ID field, i.e., != -1, while this function strictly appends to the DB and so will not honor the id field.\n" + r);
      System.exit(-1);
    }
		
    int id = new Long(this.dbAct.count()).intValue(); // O(1)
		BasicDBObject doc = MongoDB.createReactionDoc(r, id);

		if (this.dbAct == null) {
			// in simulation mode and not really writing to the MongoDB, just the screen
			System.out.println("Reaction: " + r);
		} else {
			// writing to MongoDB collection act
			this.dbAct.insert(doc);
		}	

    return id;
	}
	
  public long getMaxActReactionIDFor(Reaction.RxnDataSource src) {
		BasicDBObject bySrc = new BasicDBObject();
		bySrc.put("datasource", src);

		BasicDBObject descendingID = new BasicDBObject();
		bySrc.put("_id", -1);

    DBObject maxID = this.dbAct.find(bySrc).sort(descendingID).limit(1).next();
    return (Long) maxID.get("_id");
    // db.actfamilies.find( { datasrc : src } ).sort( { _id : -1 } ).limit(1).next()._id
  }

	public static BasicDBObject createReactionDoc(Reaction r, int id) {
		/* 
			_id: 12324,
			ecnum: "1.1.1.1",
			easy_desc: "{organism} a + b => c + d <ref1>",
			enz_summary: {
    			substrates: [{pubchem:2345}, {pubchem:456}],
    			products:   [{pubchem:1234}, {pubchem:234}]
			},
		*/
		
		BasicDBObject doc = new BasicDBObject();
		doc.put("_id", id); 
		doc.put("ecnum", r.getECNum());
		doc.put("easy_desc", r.getReactionName());
		
		BasicDBList substr = new BasicDBList();
		Long[] ss = r.getSubstrates();
		for (int i = 0; i<ss.length; i++) {
			DBObject o = getObject("pubchem", ss[i]);
			o.put("coefficient", r.getSubstrateCoefficient(ss[i]));
			substr.put(i, o);
		}
		
		BasicDBList prods = new BasicDBList();
		Long[] pp = r.getProducts();
		for (int i = 0; i<pp.length; i++) {
			DBObject o = getObject("pubchem", pp[i]);
			o.put("coefficient", r.getProductCoefficient(pp[i]));
			prods.put(i, o);
		}
		
		BasicDBObject enz = new BasicDBObject();
		enz.put("products", prods);
		enz.put("substrates", substr);
		doc.put("enz_summary", enz);
	
    if (r.getDataSource() != null)
      doc.put("datasource", r.getDataSource().name());
		
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

    // D We have changed how act.shared.Reaction keeps
    // D organisms and sequences... this code is obselete now 
    // D
    // D BasicDBList kms = new BasicDBList();
    // D kms.addAll(r.getKMValues());
    // D doc.put("km_values", kms);
    // D
    // D BasicDBList turnoverNums = new BasicDBList();
    // D turnoverNums.addAll(r.getTurnoverNumbers());
    // D doc.put("turnover_numbers", turnoverNums);
		// D 
    // D BasicDBList cloningData = new BasicDBList();
    // D for (Reaction.CloningExprData o : r.getCloningData()) {
    // D 	BasicDBObject clone = new BasicDBObject();
    // D 	clone.put("notes", o.notes);
    // D 	clone.put("organism",  o.organism);
    // D 	clone.put("reference",  o.reference);
    // D 	cloningData.add(clone);
    // D }
		// D 
    // D doc.put("express_data",  cloningData);
    // D     
		// D BasicDBList orgs = new BasicDBList();
		// D for(Reaction.EnzSeqData o : r.getOrganismData()) {
		// D 	BasicDBObject org = new BasicDBObject();
		// D 	org.put("id", o.orgID);
		// D 	org.put("seqSrc", o.seqDataSrc);
		// D 	org.put("seqIDs", o.seqDataIDs);
		// D 	orgs.add(org);
		// D }
    // D
		// D doc.put("organisms", orgs);
    // D
		// D BasicDBList seq_refs = new BasicDBList();
		// D seq_refs.addAll(r.getSequences());
		// D doc.put("seq_refs",seq_refs);
		
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

	public void submitToCofactorAAM(String mapped_l, String mapped_r, List<String> origin_l, List<String> origin_r) {
		BasicDBObject doc = new BasicDBObject();
		BasicDBList origin_l_id = new BasicDBList();
		BasicDBList origin_r_id = new BasicDBList();
		
		// System.out.println("Submitting: " + mapped_l + "-->" + mapped_r + " Original: " + origin_l + " --> " + origin_r);

		for (String s : origin_l) {
			Long uuid = getChemicalFromSMILES(s).getUuid();
			// System.out.format("Chemical %d\n", uuid);
			origin_l_id.add(uuid);
		}
		doc.put("substrates", origin_l_id);
		for (String p : origin_r) {
			Long uuid = getChemicalFromSMILES(p).getUuid();
			// System.out.format("Chemical %d\n", uuid);
			origin_r_id.add(uuid);
		}
		doc.put("products", origin_r_id);

		doc.put("substrates_mapped", mapped_l);
		doc.put("products_mapped", mapped_r);
		
		if(this.dbCofactorAAMs == null) {
			System.out.print("Cofactor AAM: " + mapped_l + ">>" + mapped_r);
		} else {
			this.dbCofactorAAMs.insert(doc);
		}
	}

	public void submitToActOperatorDB(TheoryROs tro, Reaction r, boolean knownGood) {
		int troid = tro.ID();
		int broid = tro.BRO().ID(), croid = tro.CRO().ID(), eroid = tro.ERO().ID();
		int rid = r.getUUID();
		
		if(this.dbOperators != null) {
			WriteResult result;
			// taking hints from http://stackoverflow.com/questions/8738432/how-to-serialize-class
			// we see that we can directly write the serialized TRO to the MongoDB....
			if (alreadyEnteredTRO(troid)) {
				if (!alreadyLoggedRxnTRO(troid, rid)) { // don't append the "rxns" subfield if it was added in a previous run...
					// the TRO already exists... we just need to update the rxn's field to indicate 
					// that another reaction with the same TRO was found....
					 BasicDBObject updateQuery = new BasicDBObject();
					 updateQuery.put( "_id", troid ); // pattern match against the unique ID...
					 BasicDBObject updateCommand = new BasicDBObject();
					 updateCommand.put( "$push", new BasicDBObject( "rxns", rid ) ); // will push a single rxnUUID onto the list
					 if (knownGood)
						 updateCommand.put("from_a_whitelist_rxn", knownGood);
					 result = this.dbOperators.update( updateQuery, updateCommand, true, true );
				}
			} else {
				BasicDBObject doc = new BasicDBObject();
				doc.put("_id", troid);
				BasicDBObject troDoc = new BasicDBObject();
				troDoc.put("bro", broid);
				troDoc.put("cro", croid);
				troDoc.put("ero", eroid);
				doc.put("tro", troDoc);
				BasicDBList rxnList = new BasicDBList();
				rxnList.put(0, rid); // create a single element list
				doc.put("rxns", rxnList);
				doc.put("from_a_whitelist_rxn", knownGood);
				result = this.dbOperators.insert(doc);
			}

			// submit the BRO, CRO, ERO into their respective collections...
			submitOperator(broid, tro.BRO(), null, troid, rid, knownGood, this.dbBRO);
			submitOperator(croid, tro.CRO(), broid, troid, rid, knownGood, this.dbCRO);
			submitOperator(eroid, tro.ERO(), croid, troid, rid, knownGood, this.dbERO);
		} else
			Logger.printf(0, "Operator [%d]: %s\n", troid, tro.toString()); // human readable...
	}

	private void submitOperator(int id, RO ro, Integer parentid, int troid, int rid, boolean knownGood, DBCollection coll) {
		if(coll != null) {
			WriteResult result;
			if (alreadyEnteredRO(coll, id)) {
				if (!alreadyLoggedTROinRO(coll, id, troid)) { // don't append the tros subfield if it was added in a previous run...
					// the RO already exists... we just need to update the TRO's field...
					 BasicDBObject updateQuery = new BasicDBObject();
					 updateQuery.put( "_id", id ); // pattern match against the unique ID...
					 BasicDBObject updateCommand = new BasicDBObject();
					 updateCommand.put( "$push", new BasicDBObject( "troRef", troid ) ); // will push the new troid onto the list
					 if (knownGood)
						 updateCommand.put("from_a_whitelist_rxn", knownGood);
					 result = coll.update( updateQuery, updateCommand, true, true );
					 System.out.format("[RXN: %d] TRO updated; Added %s: id=%d: %s\n", rid, ro.getClass().getName(), id, ro.toString());
				}
				if (!alreadyLoggedRXNSinRO(coll, id, rid)) { // don't append the "rxns" subfield if it was added in a previous run...
					// the RO already exists... we just need to update the Rxns field...
					 BasicDBObject updateQuery = new BasicDBObject();
					 updateQuery.put( "_id", id ); // pattern match against the unique ID...
					 BasicDBObject updateCommand = new BasicDBObject();
					 updateCommand.put( "$push", new BasicDBObject( "rxns", rid ) ); // will push the new rxnid onto the list
					 if (knownGood)
						 updateCommand.put("from_a_whitelist_rxn", knownGood);
					 result = coll.update( updateQuery, updateCommand, true, true );
					 System.out.format("[RXN: %d] rxnfield updated; Added %s: id=%d: %s\n", rid, ro.getClass().getName(), id, ro.toString());
				}
			} else {
				BasicDBObject doc = new BasicDBObject();
				doc.put("_id", id);
				doc.put("ro", ro.serialize());
				doc.put("readable", ro.toString());
				BasicDBList troList = new BasicDBList();
				troList.put(0, troid); // create a single element list
				doc.put("troRef", troList);
				BasicDBList ridList = new BasicDBList();
				ridList.put(0, rid); // create a single element list
				doc.put("rxns", ridList);
				doc.put("from_a_whitelist_rxn", knownGood);
				doc.put("parent", parentid==null?"NONE":parentid);
				result = coll.insert(doc); 
				
				System.out.format("[RXN: %d] Added %s: id=%d: %s\n", rid, ro.getClass().getName(), id, ro.toString());
			}
			// check WriteResult result if you need to; for success.
		} else
			Logger.printf(0, "Operator [%d with TROID:%d]: %s\n", id, troid, ro.toString()); // human readable...
		
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
		if (this.dbChemicals == null)
			return -1; // simulation mode...
		
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
		
    /*
     *  InChIs are unique, InChIKey are not necessarily unique (i.e,. there are hash collisions for inchikeys), so we can query for inchikeys for performance, but resolve conflicts using inchis.

     *  For example, the below, the inchikeys are same but unique different molecules:
     *  PubchemID: 15613703 
     *  Canon: [(2R,3S,4R,5R)-5-(7-aminotriazolo[4,5-d]pyrimidin-3-yl)-3,4-dihydroxyoxolan-2-yl]methyl dihydrogen phosphate 
     *  InChI: InChI=1S/C9H13N6O7P/c10-7-4-8(12-2-11-7)15(14-13-4)9-6(17)5(16)3(22-9)1-21-23(18,19)20/h2-3,5-6,9,16-17H,1H2,(H2,10,11,12)(H2,18,19,20)/t3-,5-,6-,9-/m1/s1 
     *  InChIKey: AQNUCRJICYNRCK-UUOKFMHZSA-N

     *  PubchemID: 15613703 
     *  Canon: [(2R,3S,4R,5R)-5-(7-aminotriazolo[4,5-d]pyrimidin-3-yl)-3,4-dihydroxyoxolan-2-yl]methyl dihydrogen phosphate 
     *  InChI: InChI=1S/C9H13N6O7P/c10-7-4-8(12-2-11-7)15(14-13-4)9-6(17)5(16)3(22-9)1-21-23(18,19)20/h2-3,5-6,9,16-17H,1H2,(H2,10,11,12)(H2,18,19,20)/t3-,5+,6?,9-/m1/s1 
     *  InChIKey: AQNUCRJICYNRCK-UUOKFMHZSA-N
       */

		// String inchiKey = c.getInChIKey();
		// query = new BasicDBObject();
		// query.put("InChI",inchi);

    // // we only care about their finding 0, 1, or 2+ documents, so limit to 2
		// DBCursor cur = this.dbChemicals.find(query).limit(2);
		// int count = cur.count();
		// if(count == 1) {
		// 	retId = (Long) cur.next().get("_id"); // checked: db type IS long

		//   System.err.println("\n\n\n\n\n\n\n\n\n\n");
		//   System.err.format("Checking if c already exists=" + c);
		//   System.err.println("***** This should have been dead code, we already queried by inchi and inchikey, and didnt find a match, how could there be a match on inchi? *****");
    //   System.exit(-1);

		// } else if(count != 0) {
		// 	System.err.println("Checking already in DB: Multiple ids for an InChI exists! InChI " + c.getInChI());
		// }
		// cur.close();
		// 
		// //if(retId == -1) {
		// //	System.err.println("Checking already in DB: Did not find: ");
		// //	System.err.println("Checking already in DB: \t" + inchiKey);
		// //	System.err.println("Checking already in DB: \t" + c.getInChI());
		// //}
		// 
		// // return true when at least one entry with this UUID exists
		// // no entry exists, return false.
		// return retId;
	}
	
	private boolean alreadyEntered(Reaction r) {
		if (this.dbAct == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", r.getUUID());

		DBObject o = this.dbAct.findOne(query);
		return o != null; // meaning there is at least one document that matches
	}

	private boolean alreadyEntered(PubmedEntry entry, int pmid) {
		if (this.dbPubmed == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", pmid);

		DBObject o = this.dbPubmed.findOne(query);
		return o != null;
	}

	private boolean alreadyEnteredRO(DBCollection coll, int id) {
		if (coll == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);

		DBObject o = coll.findOne(query);
		return o != null;
	}
	
	private boolean alreadyLoggedTROinRO(DBCollection coll, int id, int troid) {
		if (coll == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);
		query.put("troRef", troid);

		DBObject o = coll.findOne(query);
		return o != null;
	}
	
	private boolean alreadyLoggedRXNSinRO(DBCollection coll, int id, int rid) {
		if (coll == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);
		query.put("rxns", rid);

		DBObject o = coll.findOne(query);
    return o != null;
	}
	
	private boolean alreadyEnteredTRO(int troId) {
		if (this.dbOperators == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", troId);

		DBObject o = this.dbOperators.findOne(query);
    return o != null;
	}
	
	
	private boolean alreadyLoggedRxnTRO(int troId, int rxnId) {
		if (this.dbOperators == null)
			return false; // simulation mode...
		
		BasicDBObject query = new BasicDBObject();
		query.put("_id", troId);
		query.put("rxns", rxnId);

		DBObject o = this.dbOperators.findOne(query);
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
		DBCursor cur = this.dbAct.find(query);

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
		DBCursor cur = this.dbAct.find(query);
		
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
		
		DBCursor cur = this.dbAct.find(query);
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
	
    /*
     * 
     * 
     * Below is the list of functions required to implement DBInterface
     * 
     * 
     */
    
	@Override
	public List<Long> getRxnsWith(Long reactant) {
		return getRxnsWith(reactant, false);
	}
	
	@Override
	public List<Long> getRxnsWith(Long compound, Boolean product) {
		// if product is true, get reactions with compound as a product. else get reaction with compound as a reactant
		
		/*
		 *  our objective here is to locate objects (projected to their _id field) that are of the form
		{
			_id: 12324,
			ecnum: "1.1.1.1",
			easy_desc: "{organism} a + b => c + d <ref1>",
			enz_summary: {
    			substrates: [{pubchem:2345}, {pubchem:456}],
    			products:   [{pubchem:1234}, {pubchem:234}]
			}
			organisms: [{ id:1234, seqSrc:"swissprot", seqIDs[P54055,...] }, ...]
		}
		
		where enz_summary.substrate.contains({pubchem:reactant})...
		*/
		
		
		/* 
		 * The below is very flawed querying... We can do simpler, as explained here:
		 * This thread says that elemMatch is for "multiple values in an array element". There might be no need to use it here.
		 * (thread: http://groups.google.com/group/mongodb-user/browse_thread/thread/76146efac85be629?fwc=1)
		 * and once we remove the elemMatch, we get matches for the contains query.. and also index use.
		 
		 // BUGGY (performance bug)
		// This is a little complicated because we have to search within the array of enz_summary.substrates
		// On the mongo interactive mode this query would be phrased as 
		// db.actfamilies.find({'enz_summary.substrates': {$elemMatch: {'pubchem':NumberLong(173)}}})
		// See tutorial notes here http://www.mongodb.org/display/DOCS/Advanced+Queries#AdvancedQueries-%24elemMatch
		// for how to search within embedded objects and within arrays
		BasicDBObject query = new BasicDBObject();
		BasicDBObject query1 = new BasicDBObject();
		BasicDBObject query2 = new BasicDBObject();
		query2.put("pubchem", reactant);
		query1.put("$elemMatch", query2);
		query.put("enz_summary.substrates", query1);
		*/
		
		/* 
		 * Instead we can just do with db.actfamilies.find({'enz_summary.substrates': {'pubchem':NumberLong(173)}})
		 * See <<query>>.explain() for how many efficiently the index is being utilized:
		 * "cursor" : "BtreeCursor enz_summary.substrates_1",
		 * "nscanned" : 84,
		 * "nscannedObjects" : 84,
		 * "n" : 84,
		 * "millis" : 0,
		 * "nYields" : 0,
		 * "nChunkSkips" : 0,
		 * "isMultiKey" : true,
		 * "indexOnly" : false,
		 * "indexBounds" : { "enz_summary.substrates" : [ [ { "pubchem" : NumberLong(173) }, { "pubchem" : NumberLong(173) } ] ] }
		 */
			
		
		BasicDBObject query = new BasicDBObject();
		// See http://www.mongodb.org/display/DOCS/Dot+Notation+%28Reaching+into+Objects%29
		// as to why we can mix subobjects and querying within arrays...
		// So even though enz_summary.products is an array; we can still dereference into its pubchem
		// and the system would understand that we are looking for a field within the array contained objects...
		if (product) {
			query.put("enz_summary.products.pubchem", compound);
		} else {
			query.put("enz_summary.substrates.pubchem", compound);
		}

		// project to only retrieve the _id fields
		BasicDBObject keys = new BasicDBObject();
		//keys.put("_id", 1); // 1 means include, rest are included, _id is included by default
		
		DBCursor cur = this.dbAct.find(query, keys);

		List<Long> reactions = new ArrayList<Long>();
		while (cur.hasNext()) {
			DBObject o = cur.next();
			long id = (Integer) o.get("_id"); // checked: db type IS int
			if (product)
				id = Reaction.reverseID(id);
			reactions.add(id);
		}
		cur.close();
		return reactions;
	}
	
	@Override
	public HashMap<Long,Double> getRarity(Long rxn, Boolean product) {
		BasicDBObject query = new BasicDBObject();
		BasicDBObject keys = new BasicDBObject();
		String rarityQuery;
		if(product) {
			rarityQuery = "products";
		} else {
			rarityQuery = "substrates";
		}
		query.put("_id", rxn);
		keys.put("enz_summary", 1);
		DBCursor cur = this.dbAct.find(query, keys);
		HashMap<Long,Double> rarities = new HashMap<Long,Double>();
		while (cur.hasNext()) {
			DBObject o = cur.next();
			BasicDBList rs = (BasicDBList)((DBObject)o.get("enz_summary")).get(rarityQuery);
			for (int i = 0; i < rs.size(); i++) {
				DBObject compound = (DBObject)rs.get(i);
				rarities.put((Long)compound.get("pubchem"),(Double)compound.get("rarity"));
			}
		}
		cur.close();
		return rarities;
	}
	
	@Override
	public String getEC5Num(Long rxn) {
		BasicDBObject query = new BasicDBObject();
		query.put("_id", rxn);
		BasicDBObject keys = new BasicDBObject();
		keys.put("ecnum", 1);
		
		DBCursor cur = this.dbAct.find(query, keys);
		String ecnum = null;
		if (cur.hasNext()) {
			DBObject o = cur.next();
			ecnum = (String)o.get("ecnum");
		}
		cur.close();
		return ecnum;
	}
	
	@Override
	public String getDescription(Long rxn) {
		BasicDBObject query = new BasicDBObject();
		query.put("_id", rxn);
		BasicDBObject keys = new BasicDBObject();
		keys.put("easy_desc", 1);
		
		DBObject o = this.dbAct.findOne(query, keys);
    return o != null ? (String)o.get("easy_desc") : null;
	}

	@Override
	public List<Long> getReactants(Long rxn) {
		return Arrays.asList(getReactionFromUUID(rxn).getSubstrates());
		/*
		BasicDBObject query = new BasicDBObject();
		query.put("_id", rxn);
		
		// project out and retrieve only the enz_summary fields
		BasicDBObject keys = new BasicDBObject();
		keys.put("enz_summary", 1); // 1 means include, rest are excluded, _id is included by default

		DBCursor cur = this.dbAct.find(query, keys);

		List<Long> reactants = new ArrayList<Long>();
		while (cur.hasNext()) {
			DBObject o = cur.next();
			BasicDBList rs = (BasicDBList)((DBObject)o.get("enz_summary")).get("substrates");
			for (int i = 0; i < rs.size(); i++) {
				try {
					reactants.add((Long)((DBObject)rs.get(i)).get("pubchem"));
				} catch (ClassCastException e) {
					reactants.add(((Integer)((DBObject)rs.get(i)).get("pubchem")).longValue());
				}
			}
		}
		cur.close();
		return reactants;*/
	}

	@Override
	public List<Long> getProducts(Long rxn) {
		return Arrays.asList(getReactionFromUUID(rxn).getProducts());
		/*
		BasicDBObject query = new BasicDBObject();
		query.put("_id", rxn);

		// project out and retrieve only the enz_summary fields
		BasicDBObject keys = new BasicDBObject();
		keys.put("enz_summary", 1); // 1 means include, rest are excluded, _id is included by default
		
		DBCursor cur = this.dbAct.find(query, keys);

		List<Long> products = new ArrayList<Long>();
		while (cur.hasNext()) {
			DBObject o = cur.next();
			BasicDBList rs = (BasicDBList)((DBObject)o.get("enz_summary")).get("products");
			for (int i = 0; i < rs.size(); i++)
				try {
					products.add((Long)((DBObject)rs.get(i)).get("pubchem"));
				} catch (ClassCastException e) {
					products.add(((Integer)((DBObject)rs.get(i)).get("pubchem")).longValue());
				}
		}
		cur.close();
		return products;
		*/
	}

	@Override
	public List<String> getCanonNames(Iterable<Long> compounds) {
		List<String> canon = new ArrayList<String>();
		
		for (Long cmpdUUID : compounds) {
			BasicDBObject query = new BasicDBObject();
			query.put("_id", cmpdUUID);

			// project out and retrieve only the enz_summary fields
			BasicDBObject keys = new BasicDBObject();
			keys.put("canonical", 1); // 1 means include, rest are excluded, _id is included by default
		
			DBCursor cur = this.dbChemicals.find(query, keys);

			while (cur.hasNext()) {
				DBObject o = cur.next();
				canon.add((String)o.get("canonical"));
			}
			cur.close();
		}
		
		return canon;
	}

	@Override
	public List<String> convertIDsToSmiles(List<Long> ids) {
		List<String> smiles = new ArrayList<String>();
		
		for (Long cmpdUUID : ids) {
			if (cmpdUUID == null) { continue; }
			BasicDBObject query = new BasicDBObject();
			query.put("_id", cmpdUUID);
			
			// project out and retrieve only the enz_summary fields
			BasicDBObject keys = new BasicDBObject();
			keys.put("SMILES", 1); // 1 means include, rest are excluded, _id is included by default
		
			DBCursor cur = this.dbChemicals.find(query, keys);

			while (cur.hasNext()) {
				DBObject o = cur.next();
				smiles.add((String)o.get("SMILES"));
			}
			cur.close();
		}
		
		return smiles;
	}
	
	
	
    /*
     * 
     * 
     * End of functions required to implement DBInterface
     * 
     * 
     */

	
    /*
     * 
     * 
     * Other helper functions
     * 
     * 
     */
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
	
	public List<MappedCofactors> getAllMappedCofactors() {
		List<MappedCofactors> maps = new ArrayList<MappedCofactors>();
		
		DBCursor cur = this.dbCofactorAAMs.find();
		while (cur.hasNext()) {
			DBObject doc = cur.next();
			
			MappedCofactors map = new MappedCofactors();
			map.mapped_substrates = (String)doc.get("substrates_mapped");
			map.mapped_products = (String)doc.get("products_mapped");
			map.products = new ArrayList<Chemical>();
			for (Object p : (BasicDBList)doc.get("products"))
				map.products.add(this.getChemicalFromChemicalUUID((Long)p));
			map.substrates = new ArrayList<Chemical>();
			for (Object s : (BasicDBList)doc.get("substrates"))
				map.substrates.add(this.getChemicalFromChemicalUUID((Long)s));
			maps.add(map);
			
		}
		cur.close();

		return maps;
	}

  public List<ERO> eros(int limit) { return getROs(this.dbERO, limit, "ERO"); }
  public List<CRO> cros(int limit) { return getROs(this.dbCRO, limit, "CRO"); }
  public List<BRO> bros(int limit) { return getROs(this.dbBRO, limit, "BRO"); }

	private <T extends RO> List<T> getROs(DBCollection roColl, int limit, String roTyp) {
		DBCursor cur = roColl.find();
		List<T> ros = new ArrayList<T>();
		int counter = 0;
		while (cur.hasNext() && (limit == -1 || counter < limit)) {
			counter++;
			DBObject obj = cur.next();
			ros.add( (T)convertDBObjectToRO(obj, roTyp) );

      // BELOW IS UNUSED
      // int roID = ro.ID();
      // if we get the CRO the snd() is the CRO's parent, i.e., the BRO
			// int grandParentID = getCRO(parentID).snd(); 
			// String roName = "bro=" + grandParentID + ".cro=" + parentID + ".ero=" + roID;
			// int numRxns = rxnsList.size();
			// parentDir = getParentDir(outdir, parentBROid, parentCROid);
			// writeOperator(parentDir, numRxns, ero, eroName);
		}
		return ros;
	}

  private <T extends RO> T convertDBObjectToRO(DBObject obj, String roTyp) {
      T ro = null;
      if (roTyp.equals("ERO"))
        ro = (T)ERO.deserialize((String) obj.get("ro"));
      else if (roTyp.equals("CRO"))
        ro = (T)CRO.deserialize((String) obj.get("ro")); 
      else if (roTyp.equals("BRO"))
        ro = (T)BRO.deserialize((String) obj.get("ro"));
      else { System.out.println("NEED param {E,C,B}RO. Provided:" + roTyp); System.exit(-1); }

			Integer parentID = null;
      // for ERO and CRO we get an integer parent, for BRO it is the String "NONE"
      if (obj.get("parent") instanceof Integer)
        parentID = (Integer) obj.get("parent");
			BasicDBList rxnsList = (BasicDBList) obj.get("rxns");
      BasicDBList keywords = (BasicDBList) obj.get("keywords");
			Double reversibility = (Double) obj.get("reversibility");

      // set various parameters read from the DB into the RO object
			ro.setReversibility(reversibility);
      for (Object rxnid : rxnsList)
        ro.addWitnessRxn((Integer)rxnid);
      if (keywords != null)
        for (Object k : keywords)
          ro.addKeyword((String)k);
      ro.setParent(parentID);

      return ro;
  }

  public void updateEROKeywords(ERO ro) { updateROKeywords(this.dbERO, ro); }
  public void updateCROKeywords(CRO ro) { updateROKeywords(this.dbCRO, ro); }
  public void updateBROKeywords(BRO ro) { updateROKeywords(this.dbBRO, ro); }

  private void updateROKeywords(DBCollection roColl, RO ro) {
		int id = ro.ID();
		DBObject query = new BasicDBObject();
		query.put("_id", id);
		DBObject obj = roColl.findOne(query);
		if (obj == null) {
			System.err.println("[ERROR] updateROKeywords: can't find ro: " + id);
			return;
		}
		Set<String> keywords = ro.getKeywords();
    BasicDBList kwrds = new BasicDBList();
    for (String k : keywords) kwrds.add(k);
		obj.put("keywords", kwrds); 
		Set<String> keywords_ci = ro.getKeywordsCaseInsensitive();
    BasicDBList kwrds_ci = new BasicDBList();
    for (String k : keywords_ci) kwrds_ci.add(k);
		obj.put("keywords_case_insensitive", kwrds); 

		roColl.update(query, obj);
  }
	
	public void updateEROReversibility(ERO ero) {
		int id = ero.ID();
		Double reversibility = ero.getReversibility();
		if (reversibility == null) return;
		DBObject query = new BasicDBObject();
		query.put("_id", id);
		DBObject obj = this.dbERO.findOne(query);
		if (obj == null) {
			System.err.println("can't find ero: " + id);
			return;
		}
		obj.put("reversibility", reversibility);
		this.dbERO.update(query, obj);
	}
	
	public void dumpOperators(String outDir) {
		File outdir = new File(outDir); outdir.mkdir();
		File parentDir;

		parentDir = outdir;
		DBCursor cur = this.dbBRO.find();
		while (cur.hasNext()) {
			DBObject obj = cur.next();
			int numRxns = ((BasicDBList)obj.get("rxns")).size();
			BRO bro = BRO.deserialize((String)obj.get("ro"));
			int id = (Integer)obj.get("_id");
			if (bro.ID() != id) {
				System.err.format("Reread[%d]: %s vs InDB[%d]: %s\n", bro.ID(), bro.toString(), id, (String)obj.get("readable"));
				System.exit(-1);
			}
			writeOperator(parentDir, numRxns, bro, "bro=" + bro.ID());
		}
		cur.close();
		
		cur = this.dbCRO.find();
		while (cur.hasNext()) {
			DBObject obj = cur.next();
			int numRxns = ((BasicDBList)obj.get("rxns")).size();
			int parentBROid = (Integer)obj.get("parent");
			// parentDir = getParentDir(outdir, parentBROid);
			CRO cro = CRO.deserialize((String)obj.get("ro"));
			writeOperator(parentDir, numRxns, cro, "bro=" + parentBROid + ".cro=" + cro.ID());
		}
		cur.close();
		
		cur = this.dbERO.find();
		while (cur.hasNext()) {
			DBObject obj = cur.next();
			BasicDBList rxnsList = (BasicDBList)obj.get("rxns");
			int numRxns = rxnsList.size();
			int parentCROid = (Integer)obj.get("parent");
			int parentBROid = getCRO(parentCROid).snd(); // if we get the CRO the snd() is the CRO's parent, i.e., the BRO
			// parentDir = getParentDir(outdir, parentBROid, parentCROid);
			ERO ero = ERO.deserialize((String)obj.get("ro"));
			String eroName = "bro=" + parentBROid + ".cro=" + parentCROid + ".ero=" + ero.ID();
			writeOperator(parentDir, numRxns, ero, eroName);
			
			// dump the individual reactions as well...
			File rxnDumpDir = new File(parentDir, eroName + ".rxns");
			rxnDumpDir.mkdir();
			for (Object rxn : rxnsList) {
				long rxnId = (Integer)rxn;
				Reaction r = getReactionFromUUID(rxnId);
				writeOperator(rxnDumpDir, 1, r, null);
			}
		}
		
	}
	
	File getParentDir(File inDir, int subdirId) {
		File subdir = new File(inDir, "ID=" + subdirId);
		if (!subdir.exists() && !subdir.mkdir())
		{ System.out.println("Failed to create parent dir: " + subdir.getAbsolutePath()); System.exit(-1); }
		return subdir;
	}
	
	File getParentDir(File inDir, int subdir1, int subdir2) {
		File sub1 = new File(inDir, "ID=" + subdir1);
		File sub2 = new File(sub1, "ID=" + subdir2);
		if (!sub2.exists() && !sub2.mkdir())
		{ System.out.println("Failed to create parent dir: " + sub2.getAbsolutePath()); System.exit(-1); }
		return sub2;
	}
	
	private void writeOperator(File parentdir, int count, Object o, String name) {
		// File roDir = getParentDir(parentdir, ro.ID()); // reuse the getParentDir function to create RO dir..
		// if (!roDir.exists() && !roDir.mkdir())
		// { System.out.println("Failed to create parent dir: " + roDir.getAbsolutePath()); System.exit(-1); }

		File roDir = parentdir;
		String filename = roDir.getAbsolutePath() + "/" + name;
		try {
			if (o instanceof BRO) {
				BRO ro = (BRO)o;
				BufferedWriter file = new BufferedWriter(new FileWriter(filename + ".txt"));
				file.write(ro.toString());
				file.close();
			} else if (o instanceof ERO || o instanceof CRO) {
				RO ro = (RO)o;
				ro.render(filename + ".png", ro.rxn() + " ------ #times RO appears: " + count);
			} else if (o instanceof Reaction) {
				Reaction rr = (Reaction)o;
				BadRxns.logReactionToArbitraryDir(rr, parentdir, this);
			} else {
				
			}
		} catch (IOException e) {
			System.out.println("Failed to write RO information: " + filename); System.exit(-1);
		}
	}
	
	public List<T<Integer, List<Integer>, TheoryROs>> getOperators(int count, List<Integer> whitelist) {
		DBCursor cur = this.dbOperators.find();
		List<T<Integer, List<Integer>, TheoryROs>> ros = new ArrayList<T<Integer, List<Integer>, TheoryROs>>();
		while (cur.hasNext()) {
			DBObject obj = cur.next();
			List<Integer> rxns = new ArrayList<Integer>();
			for (Object r : (BasicDBList)obj.get("rxns"))
        rxns.add((Integer)r);
			int dbId = (Integer)obj.get("_id");
			if (whitelist!= null && !whitelist.contains(dbId))
				continue;
			DBObject troObj = (DBObject) obj.get("tro");
			P<CRO, Integer> cro = getCRO((Integer)troObj.get("cro"));
			P<BRO, Integer> bro = getBRO((Integer)troObj.get("bro"));
			P<ERO, Integer> ero = getERO((Integer)troObj.get("ero"));
			insertInOrder(ros, new T<Integer, List<Integer>, TheoryROs>(dbId, rxns, new TheoryROs(bro.fst(), cro.fst(), ero.fst())));
		}
		cur.close();
		List<T<Integer, List<Integer>, TheoryROs>> topK = new ArrayList<T<Integer, List<Integer>, TheoryROs>>();
		for (int i = 0; (count == -1 || i < count) && i < ros.size(); i++) {
			T<Integer, List<Integer>, TheoryROs> ro = ros.get(i);
			topK.add(new T<Integer, List<Integer>, TheoryROs>(ro.fst(), ro.snd(), ro.third()));
		}
		return topK;
	}
	
	public P<ERO, Integer> getERO(int id) {
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);
	
		ERO ero = null;
		Integer parent = null;
    DBObject o = this.dbERO.findOne(query);
		if (o != null) {
			ero = ERO.deserialize((String)o.get("ro"));
			parent = (Integer)o.get("parent");
		}
		return new P<ERO, Integer>(ero, parent);
	}

	public P<BRO, Integer> getBRO(int id) {
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);
	
		BRO bro = null;
    DBObject o = this.dbBRO.findOne(query);
		if (o != null) {
			bro = BRO.deserialize((String)o.get("ro"));
		}
		return new P<BRO, Integer>(bro, null);
	}

	public P<CRO, Integer> getCRO(int id) {
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);
	
		CRO cro = null;
		Integer parent = null;
    DBObject o = this.dbCRO.findOne(query);
		if (o != null) {
			cro = CRO.deserialize((String)o.get("ro"));
			parent = (Integer)o.get("parent");
		}
		return new P<CRO, Integer>(cro, parent);
	}

  public CRO getCROForRxn(int id) {
		BasicDBObject query = new BasicDBObject();
		query.put("rxns", id);
	
		CRO cro = null;
    DBObject o = this.dbCRO.findOne(query);
		if (o != null) 
			cro = CRO.deserialize((String)o.get("ro"));
		return cro;
  }
	
  public ERO getEROForRxn(int id) {
		BasicDBObject query = new BasicDBObject();
		query.put("rxns", id);
	
		ERO ero = null;
    DBObject o = this.dbERO.findOne(query);
		if (o != null) 
			ero = ERO.deserialize((String)o.get("ro"));
		return ero;
  }
	
	public List<Integer> getRxnsOfCRO(int id) {
		return getRxnsOfRO(id,this.dbCRO);
	}


	public List<Integer> getRxnsOfERO(int id) {
		return getRxnsOfRO(id,this.dbERO);
	}

	/**
	 * Used by getRxnsOf*
	 * @return
	 */
	private List<Integer> getRxnsOfRO(int id, DBCollection roColl) {
		BasicDBObject query = new BasicDBObject();
		query.put("_id", id);
		List<Integer> rxns = new ArrayList<Integer>();
		DBObject ob = roColl.findOne(query);
		if (ob != null) {
			BasicDBList dblist = (BasicDBList) ob.get("rxns");
			for(Object o : dblist) {
				rxns.add((Integer)o);
			}
		}
		return rxns;
	}

	public List<CRO> getTopKCRO(int k) {
		// k is ignored if == 0, and if -ve then bottom k picked
		boolean get_all = (k == 0);
		boolean bottomk = (k < 0);
		if (bottomk) k = -k; // invert sign to positive as we now have boolean to indicate it  
		DBObject sort = new BasicDBObject();
		sort.put("count", bottomk ? +1 : -1);
		DBCursor cur = dbCRO.find().sort(sort);
		int i = 0;
		
		List<CRO> list = new ArrayList<CRO>();
		while (cur.hasNext() && (get_all || i < k)) {
			DBObject o = cur.next();
			CRO cro = CRO.deserialize((String)o.get("ro"));
			list.add(cro);
			i++;
		}
		
		cur.close();
		return list;
	}


	public List<ERO> getTopKERO(int k) {
		// k is ignored if == 0, and if -ve then bottom k picked
		boolean get_all = (k == 0);
		boolean bottomk = (k < 0);
		if (bottomk) k = -k; // invert sign to positive as we now have boolean to indicate it  
		DBObject sort = new BasicDBObject();
		sort.put("count", bottomk ? +1 : -1); // -1 sorts in descending order, +1 in ascending so for topk we use -1 and for bottomk +1
		DBCursor cur = dbERO.find().sort(sort);
		int i = 0;
		List<ERO> list = new ArrayList<ERO>();

		while (cur.hasNext() && (get_all || i < k)) {
			DBObject o = cur.next();
			ERO ero = ERO.deserialize((String)o.get("ro"));
			list.add(ero);
			i++;
		}

		cur.close();
		return list;
	}

	private <A> void insertInOrder(List<T<Integer, List<Integer>, A>> l, T<Integer, List<Integer>, A> e) {
		boolean added = false;
		for (int i = 0; i< l.size(); i++)
			if (l.get(i).snd().size() <= e.snd().size()) {
				l.add(i, e);
				added = true;
				break;
			}
		if (!added)
			l.add(e);
	}

	private DBCollection getROColl(String whichDB) {
		DBCollection coll = null;
		if (whichDB.equals("OP"))
			coll = this.dbOperators;
		else if (whichDB.equals("CRO")) 
			coll = this.dbCRO;
		else if (whichDB.equals("ERO"))
			coll = this.dbERO;
		else if (whichDB.equals("BRO"))
			coll = this.dbBRO;
		else {
			System.err.println("Operator collection not recognized: Accept CRO, ERO, BRO, OP");
			System.exit(-1);
		}
		return coll;
	}

	private RO ro_deserialize(DBObject res, String whichDB) {
		RO ro = null;

		if (whichDB.equals("OP")) {
			String id = ((Integer)res.get("_id")).toString();
			// hack ROID_STUFF_INTO_RO
			// hack to stuff an RO id into a RO, for db.operators, this is the only field 
			// that gets pulled out by the function below
			ro = new RO(new RxnWithWildCards(id, null, null));
		} else if (whichDB.equals("CRO")) 
			ro = CRO.deserialize((String)res.get("ro"));
		else if (whichDB.equals("ERO"))
			ro = ERO.deserialize((String)res.get("ro"));
		else if (whichDB.equals("BRO"))
			ro = BRO.deserialize((String)res.get("ro"));
		else {
			System.err.println("Operator collection not recognized: Accept CRO, ERO, BRO, OP");
			System.exit(-1);
		}
		
		return ro;
	}
	
	public Set<RO> getROForEC(String ecnum, String whichDB) {

		// first find all reactions referencing that EC#
		List<Long> rxns = new ArrayList<Long>();
		BasicDBObject query = new BasicDBObject();
		BasicDBObject keys = new BasicDBObject();
		query.put("ecnum", ecnum);
		keys.put("_id", 1); // 0 means exclude, rest are included
		DBCursor cur = this.dbAct.find(query, keys);

		while (cur.hasNext()) {
			// pull up the ero for this rxn
			DBObject o = cur.next();
			long uuid = (Integer)o.get("_id"); // checked: db type IS int
			rxns.add(uuid);
		}
		cur.close();
	
		// now convert each rxn to its corresponding RO, accumulate the unique set 
		DBCollection rocoll = getROColl(whichDB);
		Set<RO> ros = new HashSet<RO>();
		for (Long r : rxns) {
			query = new BasicDBObject();
			query.put("rxns", r);
			DBObject res = rocoll.findOne(query);
			if(res == null)
				continue;
			ros.add(ro_deserialize(res, whichDB));
		}
		
		return ros;
	}
	
	public RO getROForRxnID(Long rxnID, String whichDB, boolean dummy) {

		DBCollection coll = getROColl(whichDB);
			
		BasicDBObject query = new BasicDBObject();
		query.put("rxns", rxnID);
		DBObject res = coll.findOne(query);
		if(res == null)
			return null;
		return ro_deserialize(res, whichDB);
	}
	
	public Integer getROForRxnID(Long rxnID, String whichDB) {
		RO ro = getROForRxnID(rxnID, whichDB, true);
		if (whichDB.equals("OP") && ro != null) // see hack ROID_STUFF_INTO_RO above
			return Integer.parseInt(ro.rxn()); 
		else if (ro != null)
			return ro.ID();
		return null;
	}

	public List<Chemical> getFAKEInChIChems() {
		DBObject fakeRegex = new BasicDBObject();
		fakeRegex.put("$regex", "FAKE");
		return constructAllChemicalsFromActData("InChI", fakeRegex);
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
		// System.out.format("MongoDB.getCofactorChemicals] _definiteCofactorsIDs: %s\n", _definiteCofactorsIDs);

	}
	// These should all be by default in the DB, but if not we augment the DB cofactors tags with these chemicals
  // It is ok for this list to not be exhaustive.... this is just for parent assignment in visualization
	public enum SomeCofactorNames { 
		Water(0), ATP(1), Acceptor(2), AcceptorH2(3),
		ReducedAcceptor(4), OxidizedFerredoxin(5), ReducedFerredoxin(6),
		CO2(7),	BicarbonateHCO3(8), CoA(9), H(10), NH3(11), HCl(12), Cl(13), O2(14), 
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

    /*
       * This harcoded set is from the older version where we were stripping stereochemistry off the molecules before installing into the db.
       * instead now, we install with a flag around CommandLineRun.consistentInChI.
       * Therefore, we have to hardcode the "raw" versions, and then call consistentInChI to construct the actual inchi for lookups

		private String[] _definiteCofactors = {
			// 0 Water:
			"InChI=1S/H2O/h1H2", // [H2o, H2O, h2O][water, Dihydrogen oxide, Water vapor, Distilled water, oxidane, Deionized water, Purified water, Water, purified, Dihydrogen Monoxide, DHMO, oxygen, OH-, monohydrate, aqua, hydrate, o-]
			// 1 ATP: 
			"InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)(H2,11,12,13)(H2,18,19,20)", // [L-ATP, D-ATP, araATP, alphaATP, adenosyl-ribose triphosphate, adenosine 5'-triphosphate, 5'-ATP, ATP, adenosine triphosphate][Adenosine triphosphate, Striadyne, Myotriphos, Triadenyl, Triphosphaden, Atriphos, Glucobasin, Adephos, Adetol, Triphosaden, AC1NSUB1, [[(2S,5S)-5-(6-aminopurin-9-yl)-3,4-dihydroxyoxolan-2-yl]methoxy-hydroxyphosphoryl] phosphono hydrogen phosphate, Adenosine 5'-(tetrahydrogen triphosphate)]
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
			"InChI=1S/C21H36N7O16P3S/c1-21(2,16(31)19(32)24-4-3-12(29)23-5-6-48)8-41-47(38,39)44-46(36,37)40-7-11-15(43-45(33,34)35)14(30)20(42-11)28-10-27-13-17(22)25-9-26-18(13)28/h9-11,14-16,20,30-31,48H,3-8H2,1-2H3,(H,23,29)(H,24,32)(H,36,37)(H,38,39)(H2,22,25,26)(H2,33,34,35)", // [coenzyme A, CoA-SH, CoASH]
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
			"InChI=1S/C9H16N3O14P3/c10-5-1-2-12(9(15)11-5)8-7(14)6(13)4(24-8)3-23-28(19,20)26-29(21,22)25-27(16,17)18/h1-2,4,6-8,13-14H,3H2,(H,19,20)(H,21,22)(H2,10,11,15)(H2,16,17,18)", // L-CTP, D-CTP, cytosine arabinoside 5'-triphosphate
			// 16 dATP
			"InChI=1S/C10H16N5O12P3/c11-9-8-10(13-3-12-9)15(4-14-8)7-1-5(16)6(25-7)2-24-29(20,21)27-30(22,23)26-28(17,18)19/h3-7,16H,1-2H2,(H,20,21)(H,22,23)(H2,11,12,13)(H2,17,18,19)", // deoxyATP, L-dATP, L-2'-dATP
			// 17 hydrogen sulfide
			"InChI=1S/H2S/h1H2", // hydrogensulfide, hydrogen sulfide, hydrogen sulfide
			// 18 dGTP
			"InChI=1S/C10H16N5O13P3/c11-10-13-8-7(9(17)14-10)12-3-15(8)6-1-4(16)5(26-6)2-25-30(21,22)28-31(23,24)27-29(18,19)20/h3-6,16H,1-2H2,(H,21,22)(H,23,24)(H2,18,19,20)(H3,11,13,14,17)", // 2'-dGTP, D-GTP, deoxyGTP
			// 19 Phosphoric acid
			"InChI=1S/H3O4P/c1-5(2,3)4/h(H3,1,2,3,4)", // phosphate/out, phosphate/in, Phosphoric acid		
			// 20 Iodide ion
			"InChI=1S/HI/h1H/p-1", // [iodide, Iodide, Iodide ion]
			// 21 Molecular iodine
			"InChI=1S/I2/c1-2", // [Molecular iodine, Iodine solution, Tincture iodine]
			// 22 AMP
			"InChI=1S/C10H14N5O7P/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(22-10)1-21-23(18,19)20/h2-4,6-7,10,16-17H,1H2,(H2,11,12,13)(H2,18,19,20)", // 5'AMP, arabinosyl adenine 5'-phosphate, arabinosyl adenine 5'-monophosphate
			// 23 3-phosphoadenylylsulfate
			"InChI=1S/C10H15N5O13P2S/c11-8-5-9(13-2-12-8)15(3-14-5)10-6(16)7(27-29(17,18)19)4(26-10)1-25-30(20,21)28-31(22,23)24/h2-4,6-7,10,16H,1H2,(H,20,21)(H2,11,12,13)(H2,17,18,19)(H,22,23,24)", // [3'-phosphoadenylylsulfate, 3'-phosphoadenylyl 5'-phosphosulfate, 3-phosphoadenylylsulfate]
			// 24 Sulfur dioxide solution
			"InChI=1S/H2O3S/c1-4(2)3/h(H2,1,2,3)", // [Sulfurous acid, Sulphurous acid, Sulfur dioxide solution]
			// 25 adenylylsulfate
			"InChI=1S/C10H14N5O10PS/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(24-10)1-23-26(18,19)25-27(20,21)22/h2-4,6-7,10,16-17H,1H2,(H,18,19)(H2,11,12,13)(H,20,21,22)", // adenosine 5-phosphosulfate, adenylylsulfate, adenosine 5'-phosphate 5'-sulfate
			// 26 GTP
			"InChI=1S/C10H16N5O14P3/c11-10-13-7-4(8(18)14-10)12-2-15(7)9-6(17)5(16)3(27-9)1-26-31(22,23)29-32(24,25)28-30(19,20)21/h2-3,5-6,9,16-17H,1H2,(H,22,23)(H,24,25)(H2,19,20,21)(H3,11,13,14,18)", // guanosine 5'-triphosphate, GUANOSINE TRIPHOSPHATE, 5'-GTP
			// 27 NADPH
			"InChI=1S/C21H30N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1,3-4,7-8,10-11,13-16,20-21,29-31H,2,5-6H2,(H2,23,32)(H,36,37)(H,38,39)(H2,22,24,25)(H2,33,34,35)", // NAD(P)H, 2'-NADPH, NADPH
			// 28 dADP
			"InChI=1S/C10H15N5O9P2/c11-9-8-10(13-3-12-9)15(4-14-8)7-1-5(16)6(23-7)2-22-26(20,21)24-25(17,18)19/h3-7,16H,1-2H2,(H,20,21)(H2,11,12,13)(H2,17,18,19)", // 2'-dADP, 2'-deoxy-ADP, deoxyADP
			// 29 NADP+
			"InChI=1S/C21H28N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1-4,7-8,10-11,13-16,20-21,29-31H,5-6H2,(H7-,22,23,24,25,32,33,34,35,36,37,38,39)/p+1", // NAD(P)+, beta-NADP+, 2'-NADP+
			// 30 UMP
			"InChI=1S/C9H13N2O9P/c12-5-1-2-11(9(15)10-5)8-7(14)6(13)4(20-8)3-19-21(16,17)18/h1-2,4,6-8,13-14H,3H2,(H,10,12,15)(H2,16,17,18)", // D-UMP, deazauridine 5'-phosphate, ara-UMP
			// 31 dCDP
			"InChI=1S/C9H15N3O10P2/c10-7-1-2-12(9(14)11-7)8-3-5(13)6(21-8)4-20-24(18,19)22-23(15,16)17/h1-2,5-6,8,13H,3-4H2,(H,18,19)(H2,10,11,14)(H2,15,16,17)", // L-dCDP, D-dCDP, 2'-deoxy-CDP
			// 32 ADP
			"InChI=1S/C10H15N5O10P2/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(24-10)1-23-27(21,22)25-26(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H2,11,12,13)(H2,18,19,20)", // L-ADP, D-ADP, araADP
			
			
		};
  */
	};

  private static String[] convertToConsistent(String[] raw, String debug_tag) {
    String[] consistent = new String[raw.length];
    for (int i = 0; i<raw.length; i++) {
      consistent[i] = CommandLineRun.consistentInChI(raw[i], debug_tag);
    }
    return consistent;
  }

    /*
       * The harcoded set marked as OLD is where we were stripping stereochemistry off the molecules before installing into the db.
       * instead now, we install with a flag around CommandLineRun.consistentInChI.
       * Therefore, we have to hardcode the "raw" versions, and then call consistentInChI to construct the actual inchi for lookups
       */
	private final String[] raw_markedReachableInchis = {
		// OLD: "InChI=1S/C18H34O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h9-10H,2-8,11-17H2,1H3,(H,19,20)", // [elaidiate, cis-9-octadecenoic acid, (9E)-octadecenoic acid]
    "InChI=1S/C18H34O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h9-10H,2-8,11-17H2,1H3,(H,19,20)/b10-9+", // "names":{"synonyms":["oleic acid","cis-9-Octadecenoic acid","cis-Oleic acid","Elaidoic acid","Glycon wo","Wecoline OO","Glycon RO","Vopcolene 27","Groco 5l","oleate","(9Z)-Octadecenoic acid","(Z)-Octadec-9-enoic acid","Oleate","Oleic acid"]}}
    "InChI=1S/C18H34O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h9-10H,2-8,11-17H2,1H3,(H,19,20)/p-1", // "names":{"synonyms":["octadec-9-enoate","AC1L3M2M","9-octadecenoate","oleate","Oleate","cis-9-octadecenoate"]}}
    "InChI=1S/C18H34O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h9-10H,2-8,11-17H2,1H3,(H,19,20)/p-1/b10-9+", // "names":{"synonyms":["oleate","cis-9-octadecenoate","(Z)-octadec-9-enoate","Oleat","oleic acid anion","omega fatty acid","omega-3 Fatty acid","OLEATE-CPD","AC1NUSYL","(9Z)-octadec-9-enoate"]}}
    "InChI=1S/C18H34O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h9-10H,2-8,11-17H2,1H3,(H,19,20)", // "names":{"synonyms":["9-Octadecenoic acid (Z)-","Oleic acid","9-Octadecenoic acid","Elaidic acid","Oleoyl alcohol","OLEATE-CPD","9-Octadecenoicacid","9-Octadecenoic acid (9Z)-","(E)-Octadec-9-enoic acid"]}}

		// OLD: "InChI=1S/C18H36O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h2-17H2,1H3,(H,19,20)", // [fatty acid, stearic acid, octadecanoic acid]
    "InChI=1S/C18H36O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h2-17H2,1H3,(H,19,20)", // "names":{"synonyms":["stearic acid","Octadecanoic acid","n-Octadecanoic acid","Stearophanic acid","Stearex Beads","Octadecansaeure","Stearinsaeure","Vanicol","Pearl stearic","Cetylacetic acid","Stearic acid","OCTADECANOIC ACID","Stearate"]}}
    "InChI=1S/C18H36O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h2-17H2,1H3,(H,19,20)/p-1", // "names":{"synonyms":["Octadecanoate","Stearate","ion(1-)","ion(1-)","CHEBI:25629","STEARIC_ACID","AC1MHWEJ","646-29-7","octadecanoate (n-C18:0)","ion(1-) (8CI)","octadecanoate","stearate"]}}

    // OLD: "InChI=1S/C26H45NO6S/c1-16(4-7-23(30)27-12-13-34(31,32)33)19-5-6-20-24-21(9-11-26(19,20)3)25(2)10-8-18(28)14-17(25)15-22(24)29/h16-22,24,28-29H,4-15H2,1-3H3,(H,27,30)(H,31,32,33)", // [3beta,7beta-dihydroxy-5beta-cholanoyltaurine, 3beta,7alpha-dihydroxy-5beta-cholanoyltaurine, 3alpha,7beta-dihydroxy-5beta-cholanoyl taurine]
    "InChI=1S/C26H45NO6S/c1-16(4-7-23(30)27-12-13-34(31,32)33)19-5-6-20-24-21(9-11-26(19,20)3)25(2)10-8-18(28)14-17(25)15-22(24)29/h16-22,24,28-29H,4-15H2,1-3H3,(H,27,30)(H,31,32,33)/p-1/t16-,17+,18-,19?,20?,21?,22+,24+,25+,26-/m1/s1", // "names":{"synonyms":["Taurochenodeoxycholate","Chenodeoxycholoyltaurine","taurochenodeoxycholate anion","taurochenodeoxycholate(1-)","CHEBI:9407","CPD-7283","2-[(3alpha,7alpha-dihydroxy-24-oxo-5beta-cholan-24-yl)amino]ethanesulfonate"]}}
    "InChI=1S/C26H45NO6S/c1-16(4-7-23(30)27-12-13-34(31,32)33)19-5-6-20-24-21(9-11-26(19,20)3)25(2)10-8-18(28)14-17(25)15-22(24)29/h16-22,24,28-29H,4-15H2,1-3H3,(H,27,30)(H,31,32,33)/t16-,17+,18+,19-,20+,21+,22-,24+,25+,26-/m1/s1", // "names":{"synonyms":["Taurochenodeoxycholate","TAUROCHENODEOXYCHOLIC ACID","CHEBI:16525","n-(3a,7a-dihydroxy-5b-cholan-24-oyl)-taurine","Chenodeoxycholoyltaurine","Chenyltaurine","TUD","12-Deoxycholyltaurine","Taurochenodesoxycholate","12-Desoxycholyltaurine","Taurochenodeoxycholic acid"]}}
    "InChI=1S/C26H45NO6S/c1-16(4-7-23(30)27-12-13-34(31,32)33)19-5-6-20-24-21(9-11-26(19,20)3)25(2)10-8-18(28)14-17(25)15-22(24)29/h16-22,24,28-29H,4-15H2,1-3H3,(H,27,30)(H,31,32,33)", // "names":{"synonyms":["Taurochenodeoxycholate","Chenodeoxycholoyltaurine"]}}
    "InChI=1S/C26H45NO6S/c1-16(4-7-23(30)27-12-13-34(31,32)33)19-5-6-20-24-21(9-11-26(19,20)3)25(2)10-8-18(28)14-17(25)15-22(24)29/h16-22,24,28-29H,4-15H2,1-3H3,(H,27,30)(H,31,32,33)/t16-,17+,18-,19-,20+,21+,22+,24+,25+,26-/m1/s1", // "names":{"synonyms":[]}}

    // OLD: "InChI=1S/C27H46O/c1-18(2)7-6-8-19(3)23-11-12-24-22-10-9-20-17-21(28)13-15-26(20,4)25(22)14-16-27(23,24)5/h9,18-19,21-25,28H,6-8,10-17H2,1-5H3", // [epicholesterol, cholesterol/out, cholesterol/in]
    "InChI=1S/C27H46O/c1-18(2)7-6-8-19(3)23-11-12-24-22-10-9-20-17-21(28)13-15-26(20,4)25(22)14-16-27(23,24)5/h9,18-19,21-25,28H,6-8,10-17H2,1-5H3/t19-,21+,22+,23-,24+,25+,26+,27-/m1/s1", // "names":{"synonyms":["14|A-cholest-5-en-3|A-ol","AC1L4SL1","14b-Cholest-5-en-3b-ol","14beta-Cholest-5-en-3beta-ol","(3S,8S,9S,10R,13R,14R,17R)-10,13-dimethyl-17-[(2R)-6-methylheptan-2-yl]-2,3,4,7,8,9,11,12,14,15,16,17-dodecahydro-1H-cyclopenta[a]phenanthren-3-ol","57759-45-2","AR-1C0853","ZINC05453222","AG-G-04017","Cholest-5-en-3-ol","(3beta,14beta)-","Cholesterol","Cholest-5-en-3beta-ol"]}}
    "InChI=1S/C27H46O/c1-18(2)7-6-8-19(3)23-11-12-24-22-10-9-20-17-21(28)13-15-26(20,4)25(22)14-16-27(23,24)5/h9,18-19,21-25,28H,6-8,10-17H2,1-5H3/t19?,21-,22?,23+,24-,25-,26-,27+/m0/s1", // "names":{"synonyms":["AC1L7W8H","AKOS004907707","AG-K-44715","(3S,8S,10R,13R,17R)-10,13-dimethyl-17-(6-methylheptan-2-yl)-2,3,4,7,8,9,11,12,14,15,16,17-dodecahydro-1H-cyclopenta[a]phenanthren-3-ol"]}}
    "InChI=1S/C27H46O/c1-18(2)7-6-8-19(3)23-11-12-24-22-10-9-20-17-21(28)13-15-26(20,4)25(22)14-16-27(23,24)5/h9,18-19,21-25,28H,6-8,10-17H2,1-5H3", // "names":{"synonyms":["Cholesterol","Cholest-5-en-3-ol (3beta)-","(3beta)-Cholest-5-en-3-ol","Cholest-5-en-3-ol (3.beta.)-","Cholest-5-en-3-ol"]}}
    "InChI=1S/C27H46O/c1-18(2)7-6-8-19(3)23-11-12-24-22-10-9-20-17-21(28)13-15-26(20,4)25(22)14-16-27(23,24)5/h9,18-19,21-25,28H,6-8,10-17H2,1-5H3/t19-,21-,22+,23-,24+,25-,26-,27-/m1/s1", // "names":{"synonyms":[]}}

    // OLD: "InChI=1S/C9H9BrO3/c10-5-6-1-3-7(4-2-6)8(11)9(12)13/h1-4,8,11H,5H2,(H,12,13)/p-1", // [p-(bromomethyl)mandelate]
    "InChI=1S/C9H9BrO3/c10-5-6-1-3-7(4-2-6)8(11)9(12)13/h1-4,8,11H,5H2,(H,12,13)/p-1", // "names":{"synonyms":[]}}

    // OLD: "InChI=1S/C12H8Cl2/c13-10-5-3-4-9(8-10)11-6-1-2-7-12(11)14/h1-8H", // [2,3'-dichlorobiphenyl, 2,3'-DICHLOROBIPHENYL, 1,1'-Biphenyl 2,3'-dichloro-]
    "InChI=1S/C12H8Cl2/c13-10-5-3-4-9(8-10)11-6-1-2-7-12(11)14/h1-8H", // "names":{"synonyms":["2,3'-DICHLOROBIPHENYL","1,1'-Biphenyl 2,3'-dichloro-","25569-80-6","AC1L1OYK","2,3'-Dichloro-1,1'-biphenyl","2,3'-dichloro-","1-chloro-2-(3-chlorophenyl)benzene","AG-E-78551","Biphenyl,2,3'-dichloro- (7CI,8CI);2,3'-Dichlorobiphenyl;PCB 6;"]}}

    // OLD: "InChI=1S/C8H12N2O2/c1-5-8(12)7(2-9)6(4-11)3-10-5/h3,11-12H,2,4,9H2,1H3", // [pyridoxamine, pyridoxamine, 4-(AMINOMETHYL)-5-(HYDROXYMETHYL)-2-METHYLPYRIDIN-3-OL]
    "InChI=1S/C8H12N2O2/c1-5-8(12)7(2-9)6(4-11)3-10-5/h3,11-12H,2,4,9H2,1H3", // "names":{"synonyms":["pyridoxamine","4-(AMINOMETHYL)-5-(HYDROXYMETHYL)-2-METHYLPYRIDIN-3-OL","4-(aminomethyl)-5-hydroxy-6-methyl-","CHEBI:16410","NCIStruc1_000457","NCIStruc2_000537","Oprea1_400404","CBDivE_013510","NCI21278","Pyridoxamine","PM"]}}

    // OLD: "InChI=1S/C6H13I/c1-2-3-4-5-6-7/h2-6H2,1H3", // [1-iodohexane, 1-IODOHEXANE, Hexyl iodide]
    "InChI=1S/C6H13I/c1-2-3-4-5-6-7/h2-6H2,1H3", // "names":{"synonyms":["1-IODOHEXANE","Hexyl iodide","n-Hexyl iodide","1-iodo-","1-Hexyl iodide","638-45-9","NSC 9251","EINECS 211-339-0","25495-92-5","1-iodanylhexane"]}}

    "InChI=1S/C64H104R2N8O41/c1-17(83)67-33-42(92)50(108-59-35(69-19(3)85)44(94)52(28(12-78)101-59)110-61-37(71-21(5)87)46(96)54(30(14-80)103-61)112-63-39(73-23(7)89)48(98)56(114-65)32(16-82)105-63)26(10-76)99-57(33)107-49-25(9-75)100-58(34(41(49)91)68-18(2)84)109-51-27(11-77)102-60(36(43(51)93)70-20(4)86)111-53-29(13-79)104-62(38(45(53)95)72-22(6)88)113-55-31(15-81)106-64(115-66)40(47(55)97)74-24(8)90/h25-64,75-82,91-98H,9-16H2,1-8H3,(H,67,83)(H,68,84)(H,69,85)(H,70,86)(H,71,87)(H,72,88)(H,73,89)(H,74,90)/t25-,26-,27-,28-,29-,30-,31-,32-,33-,34-,35-,36-,37-,38-,39-,40-,41-,42-,43-,44-,45-,46-,47-,48-,49-,50-,51-,52-,53-,54-,55-,56-,57+,58+,59+,60+,61+,62+,63+,64+/m1/s1", // [acetylated chitin]

    // OLD: "InChI=1S/C15H26N2O6S/c1-10(19)8-12(21)24-7-6-16-11(20)4-5-17-14(23)13(22)15(2,3)9-18/h13,18,22H,4-9H2,1-3H3,(H,16,20)(H,17,23)", // [acetoacetyl-S-pantetheine]
    "InChI=1S/C15H26N2O6S/c1-10(19)8-12(21)24-7-6-16-11(20)4-5-17-14(23)13(22)15(2,3)9-18/h13,18,22H,4-9H2,1-3H3,(H,16,20)(H,17,23)", // "names":{"synonyms":[]}}
    "InChI=1S/C15H26N2O6S/c1-10(19)8-12(21)24-7-6-16-11(20)4-5-17-14(23)13(22)15(2,3)9-18/h13,18,22H,4-9H2,1-3H3,(H,16,20)(H,17,23)/t13-/m0/s1", // "names":{"synonyms":[]}}

    "InChI=1S/C34H34N4O4.Mg/c1-7-21-17(3)25-13-26-19(5)23(9-11-33(39)40)31(37-26)16-32-24(10-12-34(41)42)20(6)28(38-32)15-30-22(8-2)18(4)27(36-30)14-29(21)35-25;/h7-8,13-16H,1-2,9-12H2,3-6H3,(H4,35,36,37,38,39,40,41,42);/q;+2/b25-13-,26-13-,27-14-,28-15-,29-14-,30-15-,31-16-,32-16-;", // "names":{"synonyms":["Mgproto","Mg-protoporphyrin IX","Divinyl-Mg-protoporphyrin","Mg Protoporphyrin","Magnesium protoporphyrin","magnesium protoporphyrin"]}}

    "InChI=1S/C11H20RNO4/c1-13(2,3)8-9(7-10(14)15)17-11(16)5-4-6-12/h9H,4-8H2,1-3H3/t9-/m1/s1", // [acyl-L-carnitine]

    // OLD: "InChI=1S/C10H14N2O4/c11-4-8-7(3-10(15)16)6(5-12-8)1-2-9(13)14/h5,12H,1-4,11H2,(H,13,14)(H,15,16)", // [porphobilinogen, porphobilinogen, 5-(aminomethyl)-4-(carboxymethyl)-pyrrole-3-propionic acid]
    "InChI=1S/C10H14N2O4/c11-4-8-7(3-10(15)16)6(5-12-8)1-2-9(13)14/h5,12H,1-4,11H2,(H,13,14)(H,15,16)", // "names":{"synonyms":["porphobilinogen","487-90-1","5-(aminomethyl)-4-(carboxymethyl)-pyrrole-3-propionic acid","PBG","2-aminomethylpyrrol-3-acetic acid 4-propionic acid","3-[5-(AMINOMETHYL)-4-(CARBOXYMETHYL)-1H-PYRROL-3-YL]PROPANOIC ACID","5-(Aminomethyl)-4-(carboxymethyl)-1H-pyrrole-3-propanoic acid","5-(Aminomethyl)-4-(carboxymethyl)-1H-pyrrole-3-propionic acid","5-(aminomethyl)-4-(carboxymethyl)-","AG-J-05118","Porphobilinogen"]}}

    // OLD: "InChI=1S/C34H34N4O4.Fe/c1-7-21-17(3)25-13-26-19(5)23(9-11-33(39)40)31(37-26)16-32-24(10-12-34(41)42)20(6)28(38-32)15-30-22(8-2)18(4)27(36-30)14-29(21)35-25;/h7-8,13-16H,1-2,9-12H2,3-6H3,(H4,35,36,37,38,39,40,41,42);/q;+2/p-2/b25-13-,26-13-,27-14-,28-15-,29-14-,30-15-,31-16-,32-16-;", // [DB02577, DB03014]
    "InChI=1S/C34H34N4O4.Fe/c1-7-21-17(3)25-13-26-19(5)23(9-11-33(39)40)31(37-26)16-32-24(10-12-34(41)42)20(6)28(38-32)15-30-22(8-2)18(4)27(36-30)14-29(21)35-25;/h7-8,13-16H,1-2,9-12H2,3-6H3,(H4,35,36,37,38,39,40,41,42);/q;+2/p-2/b25-13-,26-13-,27-14-,28-15-,29-14-,30-15-,31-16-,32-16-;", // "names":{"synonyms":["DB02577","DB03014","Heme","Haem","Protoheme","Heme B","Protoheme IX"]}}

    // OLD: "InChI=1S/C6H11NO2/c1-2-4-3-6(4,7)5(8)9/h4H,2-3,7H2,1H3,(H,8,9)/p-1", // [1-amino-2-ethylcyclopropane-1-carboxylate]
    "InChI=1S/C6H11NO2/c1-2-4-3-6(4,7)5(8)9/h4H,2-3,7H2,1H3,(H,8,9)/p-1", // "names":{"synonyms":[]}}

    // OLD: "InChI=1S/C170H282N2O62P2/c1-94(2)43-24-44-95(3)45-25-46-96(4)47-26-48-97(5)49-27-50-98(6)51-28-52-99(7)53-29-54-100(8)55-30-56-101(9)57-31-58-102(10)59-32-60-103(11)61-33-62-104(12)63-34-64-105(13)65-35-66-106(14)67-36-68-107(15)69-37-70-108(16)71-38-72-109(17)73-39-74-110(18)75-40-76-111(19)77-41-78-112(20)79-42-80-113(21)81-82-213-235(207,208)234-236(209,210)233-161-128(172-115(23)183)139(194)152(124(91-181)222-161)225-160-127(171-114(22)182)138(193)153(123(90-180)221-160)226-166-151(206)155(228-169-159(146(201)135(190)121(88-178)219-169)232-170-158(145(200)134(189)122(89-179)220-170)231-165-149(204)142(197)131(186)118(85-175)216-165)137(192)126(224-166)92-211-162-150(205)154(227-168-157(144(199)133(188)120(87-177)218-168)230-164-148(203)141(196)130(185)117(84-174)215-164)136(191)125(223-162)93-212-167-156(143(198)132(187)119(86-176)217-167)229-163-147(202)140(195)129(184)116(83-173)214-163/h43,45,47,49,51,53,55,57,59,61,63,65,67,69,71,73,75,77,79,113,116-170,173-181,184-206H,24-42,44,46,48,50,52,54,56,58,60,62,64,66,68,70,72,74,76,78,80-93H2,1-23H3,(H,171,182)(H,172,183)(H,207,208)(H,209,210)", // [dolichyl diphosphooligosaccharide, oligosaccharide-diphosphodolichol, (D-mannose)9-(N-acetyl-D-glucosaminyl)2-dolichyl-diphosphate]
    "InChI=1S/C170H282N2O62P2/c1-94(2)43-24-44-95(3)45-25-46-96(4)47-26-48-97(5)49-27-50-98(6)51-28-52-99(7)53-29-54-100(8)55-30-56-101(9)57-31-58-102(10)59-32-60-103(11)61-33-62-104(12)63-34-64-105(13)65-35-66-106(14)67-36-68-107(15)69-37-70-108(16)71-38-72-109(17)73-39-74-110(18)75-40-76-111(19)77-41-78-112(20)79-42-80-113(21)81-82-213-235(207,208)234-236(209,210)233-161-128(172-115(23)183)139(194)152(124(91-181)222-161)225-160-127(171-114(22)182)138(193)153(123(90-180)221-160)226-166-151(206)155(228-169-159(146(201)135(190)121(88-178)219-169)232-170-158(145(200)134(189)122(89-179)220-170)231-165-149(204)142(197)131(186)118(85-175)216-165)137(192)126(224-166)92-211-162-150(205)154(227-168-157(144(199)133(188)120(87-177)218-168)230-164-148(203)141(196)130(185)117(84-174)215-164)136(191)125(223-162)93-212-167-156(143(198)132(187)119(86-176)217-167)229-163-147(202)140(195)129(184)116(83-173)214-163/h43,45,47,49,51,53,55,57,59,61,63,65,67,69,71,73,75,77,79,113,116-170,173-181,184-206H,24-42,44,46,48,50,52,54,56,58,60,62,64,66,68,70,72,74,76,78,80-93H2,1-23H3,(H,171,182)(H,172,183)(H,207,208)(H,209,210)/b95-45+,96-47+,97-49-,98-51-,99-53-,100-55-,101-57-,102-59-,103-61-,104-63-,105-65-,106-67-,107-69-,108-71-,109-73-,110-75-,111-77-,112-79-/t113?,116-,117-,118-,119-,120-,121-,122-,123-,124-,125-,126-,127-,128-,129-,130-,131-,132-,133-,134-,135-,136-,137-,138-,139-,140+,141+,142+,143+,144+,145+,146+,147+,148+,149+,150+,151+,152-,153-,154-,155+,156+,157+,158+,159+,160+,161+,162+,163-,164-,165-,166+,167+,168-,169-,170-/m1/s1", // "names":{"synonyms":[]}}

    "InChI=1S/C24H35RO2/c1-19(13-14-22-21(3)11-7-16-24(22,4)5)9-6-10-20(2)15-18-27-23(26)12-8-17-25/h6,9-10,13-15H,7-8,11-12,16-18H2,1-5H3/b10-6+,14-13+,19-9+,20-15+", // [all-trans-retinyl acyl ester, retinyl ester-(cellular-retinol-binding-protein), all-trans-retinyl ester]

    // OLD: "InChI=1S/C22H18O11/c23-10-5-12(24)11-7-18(33-22(31)9-3-15(27)20(30)16(28)4-9)21(32-17(11)6-10)8-1-13(25)19(29)14(26)2-8/h1-6,18,21,23-30H,7H2", // [(+/-)-epigallocatechin-3-gallate, gallocatechin gallate, epigallocatechin gallate]
    "InChI=1S/C22H18O11/c23-10-5-12(24)11-7-18(33-22(31)9-3-15(27)20(30)16(28)4-9)21(32-17(11)6-10)8-1-13(25)19(29)14(26)2-8/h1-6,18,21,23-30H,7H2", // "names":{"synonyms":["AC1L1B55","CHEMBL311663","CHEBI:234784","4233-96-9","AG-K-23772","NCGC00095722-01","[5,7-dihydroxy-2-(3,4,5-trihydroxyphenyl)-3,4-dihydro-2H-chromen-3-yl] 3,4,5-trihydroxybenzoate","5,7-dihydroxy-2-(3,4,5-trihydroxyphenyl)-3,4-dihydro-2H-chromen-3-yl 3,4,5-trihydroxybenzoate"]}}
    "InChI=1S/C22H18O11/c23-10-5-12(24)11-7-18(33-22(31)9-3-15(27)20(30)16(28)4-9)21(32-17(11)6-10)8-1-13(25)19(29)14(26)2-8/h1-6,18,21,23-30H,7H2/t18-,21-/m1/s1", // "names":{"synonyms":["(-)-Epigallocatechin gallate","EGCG","Epigallocatechin gallate","Epigallocatechin 3-gallate","Tea catechin","Epigallocatechin-3-gallate","Teavigo","989-51-5","Catechin deriv.","(-)-Epigallocatechin-3-o-gallate"]}}
    "InChI=1S/C22H18O11/c23-10-5-12(24)11-7-18(33-22(31)9-3-15(27)20(30)16(28)4-9)21(32-17(11)6-10)8-1-13(25)19(29)14(26)2-8/h1-6,18,21,23-30H,7H2/t18-,21+/m1/s1", // "names":{"synonyms":[]}}

    // OLD: "InChI=1S/AsH3O4/c2-1(3,4)5/h(H3,2,3,4,5)", // [arsenate/out, arsenate/in, Arsenic acid]
    "InChI=1S/AsH3O4/c2-1(3,4)5/h(H3,2,3,4,5)", // "names":{"synonyms":["Arsenic acid","Orthoarsenic acid","Arsenic acid (H3AsO4)","Scorch","arsoric acid","Zotox","Crab grass killer","Desiccant L-10","Dessicant L-10","Arsenic acid","liquid"]}}
    "InChI=1S/AsH3O4/c2-1(3,4)5/h(H3,2,3,4,5)/p-3", // "names":{"synonyms":["arsenate","Arsenate ion","Arsenate"]}}

    // OLD: "InChI=1S/C34H34N4O4.Co/c1-7-21-17(3)25-13-26-19(5)23(9-11-33(39)40)31(37-26)16-32-24(10-12-34(41)42)20(6)28(38-32)15-30-22(8-2)18(4)27(36-30)14-29(21)35-25;/h7-8,13-16H,1-2,9-12H2,3-6H3,(H4,35,36,37,38,39,40,41,42);/q;+2/p-2/b25-13-,26-13-,27-14-,28-15-,29-14-,30-15-,31-16-,32-16-;", // [N-formyl-L-methionyl-Met-Phe-tRNA, Co2+-protoporphyrin, DB02110]
    "InChI=1S/C34H34N4O4.Co/c1-7-21-17(3)25-13-26-19(5)23(9-11-33(39)40)31(37-26)16-32-24(10-12-34(41)42)20(6)28(38-32)15-30-22(8-2)18(4)27(36-30)14-29(21)35-25;/h7-8,13-16H,1-2,9-12H2,3-6H3,(H4,35,36,37,38,39,40,41,42);/q;+2/p-2/b25-13-,26-13-,27-14-,28-15-,29-14-,30-15-,31-16-,32-16-;", // "names":{"synonyms":["DB02110"]}}
    "InChI=1S/C34H34N4O4.Co/c1-7-21-17(3)25-13-26-19(5)23(9-11-33(39)40)31(37-26)16-32-24(10-12-34(41)42)20(6)28(38-32)15-30-22(8-2)18(4)27(36-30)14-29(21)35-25;/h7-8,13-16H,1-2,9-12H2,3-6H3,(H4,35,36,37,38,39,40,41,42);/q;+4/p-2/b25-13-,26-13-,27-14-,28-15-,29-14-,30-15-,31-16-,32-16-;", // "names":{"synonyms":[]}}
	};

  private String[] _markedReachableInchis = convertToConsistent(raw_markedReachableInchis, "Marked Reachables");
	
	public HashMap<Long, Chemical> getManualMarkedReachables() {
		HashMap<Long, Chemical> markedReachable = new HashMap<Long, Chemical>();
		for (String inchi : _markedReachableInchis) {
			Chemical c = getChemicalFromInChI(inchi);
			markedReachable.put(c.getUuid(), c);
		}
		return markedReachable;
	}
	
	@Deprecated
	// This will memout. Use a filter criterion or get a cursor and iterate.
	public List<Chemical> getAllChemicalInChIs() {
		BasicDBObject keys = new BasicDBObject();
		keys.put("InChI", 1);
		return constructAllChemicalsFromActData(null, null, keys);
	}
	
	/**
	 * Includes chemicals marked as native or cofactor.
	 * @return
	 */
	public Set<Long> getNativeIDs() {
		List<Chemical> cofactorChemicals = getCofactorChemicals();
		List<Chemical> nativeChemicals = getNativeMetaboliteChems();
		Set<Long> ids = new HashSet<Long>();
		for (Chemical c : cofactorChemicals) ids.add(c.getUuid());
		for (Chemical c : nativeChemicals) ids.add(c.getUuid());
		return ids;
	}
	
	public Chemical getChemicalFromSMILES(String smile) {
		return constructChemicalFromActData("SMILES", smile);
	}
	
	public Chemical getChemicalFromInChIKey(String inchiKey) {
		return constructChemicalFromActData("InChIKey", inchiKey);
	}
	
	public Chemical getChemicalFromInChI(String inchi) {
		return constructChemicalFromActData("InChI", inchi);
	}
	
	public Chemical getChemicalFromChemicalUUID(Long cuuid) {
		return constructChemicalFromActData("_id", cuuid);
	}
	
	public Chemical getChemicalFromCanonName(String chemName) {
		return constructChemicalFromActData("canonical", chemName);
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
	
	// See http://ggasoftware.com/opensource/indigo/api#molecule-and-reaction-similarity
	// for description of similarity metrics:
	// Tanimoto is essentially Jaccard Similarity: intersection/union
	// euclidsub:
	public enum SimilarityMetric { TANIMOTO, EUCLIDSUB, TVERSKY }
	
	public void insertChemicalSimilarity(Long c1, Long c2, double similarity, SimilarityMetric metric) {
		addSimilarity(c1, c2, similarity, metric);
		addSimilarity(c2, c1, similarity, metric);
	}
	
	private void addSimilarity(Long c1, Long c2, double similarity, SimilarityMetric metric) {
		BasicDBObject updateQuery = new BasicDBObject();
		updateQuery.put( "c1", c1 ); 
		updateQuery.put( "c2", c2 ); 
		BasicDBObject updateCommand = new BasicDBObject();
		updateCommand.put( "$set", new BasicDBObject( metric.name(), similarity ) ); // will push the new similarity metric onto 
		WriteResult result = this.dbChemicalsSimilarity.update( updateQuery, updateCommand, 
				 true, // upsert: i.e.,  if the record(s) do not exist, insert one. Upsert only inserts a single document.
				 true  // multi: i.e., if all documents matching criteria should be updated rather than just one.
				 );
	}
	
	public void addSimilarityBetweenAllChemicalsToDB(Indigo indigo, IndigoInchi indigoinchi) {
		List<Chemical> allchems = constructAllChemicalsFromActData(null /* no filter, get all chems */, null);
		
		for (int i = 0; i< allchems.size(); i++) {
			Chemical c1 = allchems.get(i);
			IndigoObject c1obj = getIndigoObject(c1, indigo, indigoinchi);
			for (int j=0; j<allchems.size(); j++) {
				Chemical c2 = allchems.get(j);
				IndigoObject c2obj = getIndigoObject(c2, indigo, indigoinchi);
				for (SimilarityMetric metric : SimilarityMetric.values()) {
					double similarity = c1obj == null || c2obj == null ? 0.0F : calculateSimilarity(c1obj, c2obj, metric, indigo);
					insertChemicalSimilarity(c1.getUuid(), c2.getUuid(), similarity, metric);
				}
			}
		}
	}

	private IndigoObject getIndigoObject(Chemical c, Indigo indigo, IndigoInchi indigoinchi) {
		String smiles = c.getSmiles();
		String inchi = c.getInChI();
		try {
			return smiles != null ? indigo.loadMolecule(smiles) : indigoinchi.loadMolecule(inchi);
		} catch (IndigoException e) {
			System.err.println("Failed to load SMILES/InChi: " + smiles + "/" + inchi);
		}
		return null;
	}

	private double calculateSimilarity(IndigoObject c1, IndigoObject c2, SimilarityMetric metric, Indigo indigo) {
		switch (metric) {
		case TANIMOTO: return indigo.similarity(c1, c2);
		case EUCLIDSUB: return indigo.similarity(c1, c2, "euclid-sub");
		case TVERSKY: return indigo.similarity(c1, c2, "tversky");
		default: System.err.println("invalid similarity metric specified"); System.exit(-1); return Double.NaN;
		}
	}
	
	public double getSimilarity(Long c1, Long c2, SimilarityMetric metric) {
		BasicDBObject query = new BasicDBObject();
		query.put("c1", c1);
		query.put("c2", c2);
		DBObject o = this.dbChemicalsSimilarity.findOne(query);
		if (o != null) {
			Double sim = (Double)o.get(metric.name());
			// System.out.format("DB sim lookup (%s, %s)=%s\n", c1, c2, sim);
			return sim == null ? Double.NaN : sim;
		}
		return Double.NaN;
	}
	
	public List<P<Long, Double>> getChemicalsMostSimilarTo(Long c, int howmany, SimilarityMetric metric) {
		List<P<Long, Double>> list = new ArrayList<P<Long, Double>>();
		
		DBObject sort = new BasicDBObject();
		sort.put(metric.name(), -1);
		BasicDBObject query = new BasicDBObject();
		query.put("c1", c);
		DBCursor cur = this.dbChemicalsSimilarity.find(query).sort(sort);
		int k=0;
		while (cur.hasNext() && k < howmany) {
			DBObject o = cur.next();
			long c2 = (Long)o.get("c2");
			double sim = (Double)o.get(metric.name());
			k++;
			list.add(new P<Long, Double>(c2, sim));
		}
		cur.close();
		return list;
	}

	public List<Long> getMostSimilarChemicalsToNewChemical(String targetSMILES, int numSimilar, Indigo indigo, IndigoInchi indigoinchi) {
		DBCursor cur = this.dbChemicals.find();
		List<P<Long, Double>> similarChems = new ArrayList<P<Long, Double>>();
		IndigoObject target = indigo.loadMolecule(targetSMILES);
		while (cur.hasNext()) {
			DBObject o = cur.next();
			long uuid = (Long)o.get("_id"); // checked: db type IS long
			String smiles = (String)o.get("SMILES");
			String inchi = (String)o.get("InChI");
			String inchiKey = (String)o.get("InChIKey");
			// System.out.println("Loading: " + smiles + "/" + inchi);
			try {
				IndigoObject dbmol = smiles != null ? indigo.loadMolecule(smiles) : indigoinchi.loadMolecule(inchi);
				HashMap<SimilarityMetric, Double> similarity = new HashMap<SimilarityMetric, Double>();
				for (SimilarityMetric metric : SimilarityMetric.values())
					similarity.put(metric, calculateSimilarity(target, dbmol, metric, indigo)); // indigo.similarity(target, dbmol);
				System.out.format("Similarity: %s; ID: %d\n", similarity, uuid );
				insertInOrder(similarChems, new P<Long, Double>(uuid, similarity.get(SimilarityMetric.TANIMOTO)));
			} catch (IndigoException e) {
				System.err.println("Failed to load SMILES/InChi: " + smiles + "/" + inchi);
			}
		}
		cur.close();
		return getTop(similarChems, numSimilar);
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
	
	private List<Long> getTop(List<P<Long, Double>> l, int num) {
		List<Long> ids = new ArrayList<Long>();
		for (int i = 0; i < l.size() && i < num; i++)
			ids.add(l.get(i).fst());
		return ids;
	}

	private <A> void insertInOrder(List<P<A, Double>> l, P<A, Double> e) {
		boolean added = false;
		for (int i = 0; i< l.size(); i++)
			if (l.get(i).snd() <= e.snd()) {
				l.add(i, e);
				added = true;
				break;
			}
		if (!added)
			l.add(e);
	}
	
	public List<Chemical> constructAllChemicalsFromActData(String field, Object val) {
		return constructAllChemicalsFromActData(field, val, new BasicDBObject());
	}
	
	public List<Chemical> constructAllChemicalsFromActData(String field, Object val, BasicDBObject keys) {
		DBCursor cur;
		if (field != null) {
			BasicDBObject query;
			query = new BasicDBObject();
			query.put(field, val);
			cur = this.dbChemicals.find(query, keys);
		} else {
			cur = this.dbChemicals.find();
		}

		List<Chemical> chems = new ArrayList<Chemical>();
		while (cur.hasNext())
			chems.add(constructChemical(cur.next()));
		
		cur.close();
		return chems;
	}

  public Map<String, Long> constructAllInChIs() {
		Map<String, Long> chems = new HashMap<String, Long>();
		DBCursor cur = this.dbChemicals.find();
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
		
		DBCursor cur = this.dbChemicals.find();
		IndigoObject mol = null, matcher;
		int cnt;
		while (cur.hasNext()) {
			Chemical c = constructChemical(cur.next());
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
	
	private Chemical constructChemicalFromActData(String field, Object val) {
		BasicDBObject query = new BasicDBObject();
		query.put(field, val);

		// project out the synonyms field, even though we don't have anything in it right now.
		BasicDBObject keys = new BasicDBObject();
		// keys.put("names", 0); // 0 means exclude, rest are included
		DBObject o = this.dbChemicals.findOne(query, keys);
		if (o == null)
			return null;
		return constructChemical(o);
	}
	
	private Chemical constructChemical(DBObject o) {
		long uuid;
    // WTF!? Are some chemicals ids int and some long?
    // this code below should not be needed, unless our db is mucked up
		try {
			uuid = (Long) o.get("_id"); // checked: db type IS long
		} catch (ClassCastException e) {
      System.err.println("WARNING: MongoDB.constructChemical ClassCast db.chemicals.id is not Long?");
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
		// c.setInchiKey(inchiKey); // we compute our own inchikey when setInchi is called
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
		DBCursor cursor = this.dbChemicals.find();
		return new DBIterator(cursor);
	}
	
	public Chemical getNextChemical(DBIterator iterator) {
		if (!iterator.hasNext()) {
			iterator.close();
			return null;
		}
		
		DBObject o = iterator.next();
		return constructChemical(o);
	}

	public DBIterator getIteratorOverReactions(boolean notimeout) {
		return getIteratorOverReactions(new BasicDBObject(), notimeout, null);
	}
	
	private DBIterator getIteratorOverReactions(Long low, Long high, boolean notimeout) {
		return getIteratorOverReactions(getRangeUUIDRestriction(low, high), notimeout, null);
	}
	
	public DBIterator getIteratorOverReactions(BasicDBObject matchCriterion, boolean notimeout, BasicDBObject keys) {

		if (keys == null) {
			keys = new BasicDBObject();
			// keys.put(projection, 1); // 1 means include, rest are excluded
		}
		
		DBCursor cursor = this.dbAct.find(matchCriterion, keys);
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

  public Reaction convertDBObjectToReaction(DBObject o) {
		long uuid = (Integer)o.get("_id"); // checked: db type IS int
		String ecnum = (String)o.get("ecnum");
		String name_field = (String)o.get("easy_desc");
		BasicDBList substrates = (BasicDBList)((DBObject)o.get("enz_summary")).get("substrates");
		BasicDBList products = (BasicDBList)((DBObject)o.get("enz_summary")).get("products");
		BasicDBList refs = (BasicDBList) (o.get("references"));
		BasicDBList proteins = (BasicDBList) (o.get("proteins"));

		BasicDBList keywords = (BasicDBList) (o.get("keywords"));
		BasicDBList cikeywords = (BasicDBList) (o.get("keywords_case_insensitive"));
		
		List<Long> substr = new ArrayList<Long>();
		List<Long> prod = new ArrayList<Long>();

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

		Reaction result = new Reaction(uuid, 
				(Long[]) substr.toArray(new Long[0]), 
				(Long[]) prod.toArray(new Long[0]), 
				ecnum, name_field, ReactionType.CONCRETE);
		
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
		
		// D BasicDBList expressData = (BasicDBList) (o.get("express_data"));
		// D BasicDBList seq_refs = (BasicDBList) (o.get("seq_refs"));
		// D BasicDBList orgIDs = (BasicDBList)o.get("organisms");
		// D Long[] org = new Long[orgIDs.size()];
		// D for (int i = 0; i<orgIDs.size(); i++)
		// D 	org[i] = (Long)((DBObject)orgIDs.get(i)).get("id"); // checked: db IS Long
		// D if (expressData != null) {
		// D 	for (Object temp : expressData) {
		// D 		DBObject e = (DBObject) temp;
		// D 		result.addCloningData(
		// D 				(Long) e.get("organism"), 
		// D 				(String) e.get("notes"), 
		// D 				(String) e.get("reference"));
		// D 	}
		// D }
    // D if (seq_refs != null)
    // D   for (Object seq_ref : seq_refs)
    // D     result.addSequence((Long)seq_ref);

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

	private Long getEnzSummaryIDAsLong(BasicDBList products, int i) {
		try {
			return (Long)((DBObject)products.get(i)).get("pubchem");
		} catch (ClassCastException e) {
			return ((Integer)((DBObject)products.get(i)).get("pubchem")).longValue();
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
		DBCursor cur = this.dbAct.find(query);
		
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
		BasicDBObject query = new BasicDBObject();
		query.put(in_field, keyword);

		BasicDBObject keys = new BasicDBObject();

		DBCursor cur = this.dbChemicals.find(query, keys);
		while (cur.hasNext()) {
			DBObject o = cur.next();
		  chemicals.add( constructChemical(o) );
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

		DBCursor cur = this.dbAct.find(query, keys);
		while (cur.hasNext()) {
			DBObject o = cur.next();
		  rxns.add( convertDBObjectToReaction(o) );
		}
		cur.close();
	
    return rxns;
  }
	
  public List<RO> keywordInRO(String keyword) {
    return keywordInRO("keywords", keyword);
  }

  public List<RO> keywordInROCaseInsensitive(String keyword) {
    return keywordInRO("keywords_case_insensitive", keyword);
  }

  private List<RO> keywordInRO(String in_field, String keyword) {
    List<RO> ros = new ArrayList<RO>();
		BasicDBObject query = new BasicDBObject();
		query.put(in_field, keyword);

    ros.addAll(queryFindROs(this.dbERO, "ERO", query));
    ros.addAll(queryFindROs(this.dbCRO, "CRO", query));
    ros.addAll(queryFindROs(this.dbBRO, "BRO", query));

    return ros;
  }
  
  private List<RO> queryFindROs(DBCollection coll, String roTyp, BasicDBObject query) {
    List<RO> ros = new ArrayList<RO>();
		BasicDBObject keys = new BasicDBObject();
		DBCursor cur = coll.find(query, keys);
		while (cur.hasNext()) {
			DBObject o = cur.next();
		  ros.add( convertDBObjectToRO(o, roTyp) );
		}
		cur.close();
    return ros;
  }
	
	
	public Reaction getReactionFromUUID(Long reactionUUID) {
		if (reactionUUID < 0) {
			Reaction reaction = getReactionFromUUID(Reaction.reverseID(reactionUUID));
			reaction.reverse();
			return reaction;
		}
		BasicDBObject query = new BasicDBObject();
		query.put("_id", reactionUUID);

		BasicDBObject keys = new BasicDBObject();
		DBObject o = this.dbAct.findOne(query, keys);
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
    return getAllCollectionUUIDs(this.dbAct);
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

  // D public void addSeqRefToReactions(Long rxn_id, Long seq_id) {

  // D   // TO db.actfamilies{_id:rxn_id}.seq_refs ADD seq_id

  // D   // read reaction object into memory from db
  // D   Reaction rxn = getReactionFromUUID(rxn_id);
  // D   // update reaction object in memory
  // D   rxn.addSequence(seq_id);

  // D   // update the reaction entry in DB
  // D   updateSequenceRefsOf(rxn);

  // D   // TO db.actfamilies{_id:rxn_id}.seq_refs ADD seq_id

  // D   // read sequence object into memory from db
  // D   Seq seq = getSeqFromID(seq_id);

  // D   // update sequence object with reaction id, substrates, products
  // D   Set<Long> substrates = new HashSet<Long>(), products = new HashSet<Long>();
  // D   for (Long s : rxn.getSubstrates()) if (!isCofactor(s)) substrates.add(s);
  // D   for (Long p : rxn.getProducts()) if (!isCofactor(p)) products.add(p);

  // D   seq.addReactionsCatalyzed(rxn_id);
  // D   seq.addCatalysisProducts(rxn_id, products);
  // D   seq.addCatalysisSubstrates(rxn_id, substrates);

  // D   // update the sequence object in db
  // D   updateReactionRefsOf(seq);
  // D }

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
		DBObject reaction = this.dbAct.findOne(query);
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
		DBObject reaction = this.dbAct.findOne(query);
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
		DBObject reaction = this.dbAct.findOne(query);
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
		DBObject reaction = this.dbAct.findOne(query);
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
	
	// D public List<String> getSequencesDEPRECATED(Long orgID, String ecnum) {
	// D 	String orgName;
	// D 	List<String> sequences = new ArrayList<String>();
	// D 	DBObject getOrgName = new BasicDBObject();
	// D 	getOrgName.put("org_id", orgID);
	// D 	DBCursor org_c = this.dbOrganismNames.find(getOrgName);
	// D 	while (org_c.hasNext()) {
	// D 		DBObject organism = org_c.next();
	// D 		orgName = (String) organism.get("name");
	// D 		if (orgName == null) continue;
	// D 		System.out.format("\t\t For org_id:%d, org name: %s\n", orgID, orgName);

	// D 		DBObject query = new BasicDBObject();
	// D 		query.put("org", orgName);
	// D 		if (ecnum != null)
	// D 			query.put("ecnum", ecnum);
	// D 		DBCursor cur = this.dbSequencesDEPRECATED.find(query);
	// D 		while(cur.hasNext()) {
	// D 			DBObject o = cur.next();
	// D 			sequences.add((String) o.get("seq"));
	// D 		}
	// D 	}
	// D 	return sequences;
	// D }
	// D 
	// D public List<String> getSequencesDEPRECATED(Long rxnUUID) {
	// D 	List<String> seq = new ArrayList<String>();
	// D 	Reaction r = getReactionFromUUID(rxnUUID);
	// D 	String ecnum = r.getECNum();
	// D 	Long[] organisms = r.getOrganismIDs();
	// D 	System.out.println("Organism IDs: " + Arrays.asList(organisms));
	// D 	for (Long org : organisms) {
	// D 		List<String> seqs = getSequencesDEPRECATED(org, ecnum);
	// D 		seq.addAll(seqs);
	// D 		System.out.format("\t For ecnum:%s, org_id: %d, got #Seq:%d\n", ecnum, org, seqs.size());
	// D 	}
	// D 	return seq;
	// D }
	
	
    /*
     * 
     * 
     * End of other helper functions
     * 
     * 
     */
	
	/**
	 * Create graph collections name and name_nodes
	 * using this DB instance.
	 * @param name
	 * @return
	 */
	public MongoDBGraph getGraph(String name) {
		return new MongoDBGraph(mongoDB, name);
	}
	
	// for ERO search, deals with a collection for storing curried EROs
	public void putCurriedEROs(List<CurriedERO> eros){
		DBCollection dbCurriedERO = mongoDB.getCollection("curriederos");
		dbCurriedERO.remove(new BasicDBObject()); //remove all old entries
		for (CurriedERO ero : eros){
			String xml = ero.serialize();
			BasicDBObject doc = new BasicDBObject();
			doc.put("curriedero", xml);
			dbCurriedERO.insert(doc); 
		}
	}

	public List<CurriedERO> getCurriedEROs(){
		DBCollection dbCurriedERO = mongoDB.getCollection("curriederos");
		DBCursor cur = dbCurriedERO.find();
		List<CurriedERO> eros = new ArrayList<CurriedERO>();
		while (cur.hasNext()) {
			DBObject obj = cur.next();
			CurriedERO ero = CurriedERO.deserialize((String)obj.get("curriedero"));
			eros.add(ero);
		}
		cur.close();
		return eros;
	}

    public List<String> getSerializedCurriedEROs() {
		DBCollection dbCurriedERO = mongoDB.getCollection("curriederos");
		DBCursor cur = dbCurriedERO.find();
		List<String> eros = new ArrayList<String>();
		while (cur.hasNext()) {
			DBObject obj = cur.next();
            eros.add((String)obj.get("curriedero"));
		}
		cur.close();
		return eros;
    }
	
	public void addEROActFamily(List<String> canonicalDotNotationSubstrateSmiles, ERO ero, List<String> canonicalDotNotationProductSmiles){
		/*
		List<ObjectId> substrateIds = new ArrayList<ObjectId>();
		for (String smiles : canonicalDotNotationSubstrateSmiles){
			substrateIds.add(this.getOrMakeIdForChemicalFromEROExpansion(smiles));
		}
		List<ObjectId> productIds = new ArrayList<ObjectId>();
		for (String smiles : canonicalDotNotationProductSmiles){
			productIds.add(this.getOrMakeIdForChemicalFromEROExpansion(smiles));
		}
		*/
		int eroId = ero.ID();
		
		DBCollection eroActFamilies = mongoDB.getCollection("eroactfamilies");
		
		BasicDBObject doc = new BasicDBObject();
		//doc.put("substrateids", substrateIds);
		//doc.put("productids", productIds);
		doc.put("substratesmiles",canonicalDotNotationSubstrateSmiles);
		doc.put("productsmiles",canonicalDotNotationProductSmiles);
		doc.put("eroid", eroId);
		doc.put("erorxn", ero.rxn());//a little redundancy since there are so many versions of the db running around right now
		eroActFamilies.insert(doc); 
	}

	public ObjectId getIdForChemicalFromEROExpansion(String canonicalDotNotationSmiles){
		DBCollection eroChemicals = mongoDB.getCollection("erochemicals");
		
		BasicDBObject query = new BasicDBObject("canonicaldotnotationsmiles", canonicalDotNotationSmiles);
		DBCursor cursor = eroChemicals.find(query);
		ObjectId chemId = null;
		try {
		   while(cursor.hasNext()) {
				DBObject obj = cursor.next();
				chemId = (ObjectId) obj.get("_id");
		   }
		} finally {
		   cursor.close();
		}
		//System.out.println(chemId);
		return chemId;
	}
	
	public ObjectId getOrMakeIdForChemicalFromEROExpansion(String canonicalDotNotationSmiles){
		DBCollection eroChemicals = mongoDB.getCollection("erochemicals");
		ObjectId chemId = null;
		chemId = getIdForChemicalFromEROExpansion(canonicalDotNotationSmiles);
		
		//if find didn't give us a result, let's go ahead and add the chemical
		if (chemId == null){
			BasicDBObject doc = new BasicDBObject();
			doc.put("canonicaldotnotationsmiles", canonicalDotNotationSmiles);
			eroChemicals.insert(doc); 
			//System.out.println("Had to add the chem.");
		}
		else{
			//System.out.println("Found the chem already");
		}
		return chemId;
	}
	

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
		
		DBCursor reactionCursor = this.dbAct.find(query);
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
		List<Long> ids = (List<Long>) this.dbAct.distinct("organisms.id");
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
}
