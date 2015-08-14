package act.installer.metacyc;

import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.DBIterator;
import com.mongodb.DBObject;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.Seq;
import act.client.CommandLineRun;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import act.installer.metacyc.references.Unification;
import act.installer.metacyc.references.Publication;
import act.installer.metacyc.references.Relationship;
import act.installer.metacyc.annotations.Term;
import act.installer.metacyc.processes.Catalysis;
import act.installer.metacyc.processes.Conversion;
import act.installer.metacyc.entities.ChemicalStructure;
import act.installer.metacyc.entities.SmallMolecule;
import act.installer.metacyc.entities.SmallMoleculeRef;
import act.installer.metacyc.entities.ProteinRNARef;
import act.installer.metacyc.NXT;
import act.installer.sequence.SequenceEntry;
import act.installer.sequence.MetacycEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import com.ggasoftware.indigo.IndigoException;
import org.json.JSONObject;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class OrganismCompositionMongoWriter {
  MongoDB db;
  OrganismComposition src;
  Chemical.REFS originDB;
  String originDBSubID;
  HashMap<Resource, SmallMolecule> smallmolecules;
  HashMap<Resource, Catalysis> enzyme_catalysis;
  boolean debugFails = false;

  // metacyc id's are in Unification DB=~name of origin, ID.matches(METACYC_URI_PREFIX)
  String METACYC_URI_IDS = "^[A-Z0-9-]+$"; // 
  // to get valid Metacyc website URL
  String METACYC_URI_PREFIX = "http://www.metacyc.org/META/NEW-IMAGE?object=";

  OrganismCompositionMongoWriter(MongoDB db, OrganismComposition o, String origin, Chemical.REFS originDB) {
    System.out.println("Writing DB: " + origin);
    this.db = db;
    this.src = o;
    this.originDB = originDB;
    this.originDBSubID = origin;
    smallmolecules = o.getMap(SmallMolecule.class);
    enzyme_catalysis = o.getMap(Catalysis.class);
  }

  public void write() {

    if (false) 
      writeStdout(); // for debugging, if you need a full copy of the data in stdout

    // while going through this organisms chemicals (optionally installing
    // into db if required), we map its rdfID to the inchi (in db)
    HashMap<String, Long> rdfID2MongoID = new HashMap<String, Long>();
    // for debugging, we log new smallmol that we see here, that were not in the db
    // HashMap<Chemical, ChemicalStructure> newSmallMol = new HashMap<Chemical, ChemicalStructure>();
    // HashMap<Chemical, ChemicalStructure> newBigMol = new HashMap<Chemical, ChemicalStructure>();
    // for debugging, we log only the number of new reactions with sequences seen
    int newRxns = 0;

    for (Resource id : smallmolecules.keySet()) {
      SmallMolecule sm = (SmallMolecule)smallmolecules.get(id);
      SmallMoleculeRef smref = (SmallMoleculeRef)this.src.resolve(sm.getSMRef());
      if (smref == null) {
        continue; // only happens in one case standardName="a ribonucleic acid"
      }

      SmallMolMetaData meta = getSmallMoleculeMetaData(sm, smref);
      ChemicalStructure c = (ChemicalStructure)this.src.resolve(smref.getChemicalStructure());
      if (c == null) {
        if (debugFails) System.out.println("No structure: " + smref.expandedJSON(this.src).toString(2));
        continue; // mostly big molecules (e.g., a ureido compound, a sulfhydryl reagent, a macrolide antibiotic), but sometimes complexes (their members fields has small molecule structures), and sometimes just no structure given (colanic acid, a reduced nitroaromatic compound)
      }

      // actually add chemical to DB
      Chemical dbChem = addChemical(c, meta);

      // put rdfID -> mongodb ID in rdfID2MongoID map
      rdfID2MongoID.put(c.getID().getLocal(), dbChem.getUuid());
    }

    for (Resource id : enzyme_catalysis.keySet()) {
      Catalysis c = enzyme_catalysis.get(id);

      // actually add reaction to DB
      Reaction rxn = addReaction(c, rdfID2MongoID);

      // Testing/debugging:
      // System.out.println("Original data: " + c.expandedJSON(this.src).toString(2));
      // System.out.println("Processed Rxn: " + rxn.toStringDetail());
      newRxns++;
    }

    // Testing/debugging:
    // for (Chemical nc : newSmallMol.keySet()) System.out.println("New: " + nc.toStringDetail() + "\nFrom: " + newSmallMol.get(nc).expandedJSON(this.src).toString(2));

    // Output stats:
    System.out.format("New writes: %s (%d) :: (rxns)\n", this.originDBSubID, newRxns);

  }

  private Chemical addChemical(ChemicalStructure c, SmallMolMetaData meta) {
    ChemStrs structure = getChemStrs(c);
    boolean bigmolecule = false;
    if (structure == null) {
      // do some hack, put something in inchi, inchikey and smiles so that
      // we do not end up loosing the reactions that have R groups in them
      structure = hackAllowingNonSmallMolecule(c);
      bigmolecule = true;
    }
    Chemical dbChem = db.getChemicalFromInChI(structure.inchi);
    if (dbChem != null) {
      // this chemical is already in the DB, only update the xref field with this id
      dbChem = addReference(dbChem, c, meta, originDB);
      db.updateActChemical(dbChem, dbChem.getUuid());
    } else {
      // DB does not contain chemical as yet, install
      dbChem = makeNewChemical(c, structure, meta, originDB);
      db.submitToActChemicalDB(dbChem, dbChem.getUuid()); 
      setIDAsUsed(dbChem.getUuid());
      // log that a new chemical was added
      /*
      if (bigmolecule) 
        newBigMol.put(dbChem, c);
      else
        newSmallMol.put(dbChem, c);
      */
    }
    return dbChem;
  }

  private Reaction addReaction(Catalysis c, HashMap<String, Long> rdfID2MongoID) {
    
    // using the map of chemical rdfID->mongodb id, construct a Reaction object
    Reaction rxn = constructReaction(c, rdfID2MongoID);

    // set the datasource
    rxn.setDataSource(Reaction.RxnDataSource.METACYC);

    // pass the Reaction to the mongodb driver to insert into act.actfamilies
    long rxnid = db.submitToActReactionDB(rxn);

    Long[] orgIDs = getOrganismIDs(c);
    Long[] seqs = getCatalyzingSequence(c, rxn, rxnid);

    System.err.println("ToImplement: Metacyc, rxn: (litref, org) -> sequence data");
    System.exit(-1);

    return rxn;
  }

  private Chemical addReference(Chemical dbc, ChemicalStructure c, SmallMolMetaData meta, Chemical.REFS originDB) {
    DBObject ref = (DBObject)dbc.getRef(originDB); 
    BasicDBList idlist = null;
    if (ref == null) {
      // great, this db's ref is not already in place. just create a new one and put it in
      ref = new BasicDBObject();
      idlist = new BasicDBList();
      idlist.add(c.getID().getLocal());
    } else {
      // a ref exists, maybe it is from installing this exact same chem, 
      // or from a replicate chemical from another organism. add the DB's ID
      // to the chemical's xref field
      idlist = ref.containsField("id") ? (BasicDBList)ref.get("id") : new BasicDBList();
      String chemID = c.getID().getLocal();
      if (!idlist.contains(chemID)) 
        idlist.add(chemID);
      // else do nothing, since the idlist already contains the id of this chem.
    }

    // install the idlist into the xref.KEGG/METACYC field
    ref.put("id", idlist);
    ref.put("meta", meta.getDBObject((BasicDBList)ref.get("meta")));
    // update the chemical with the new ref
    dbc.putRef(originDB, ref);

    // return the updated chemical
    return dbc;
  }

  private Chemical makeNewChemical(ChemicalStructure c, ChemStrs strIDs, SmallMolMetaData meta, Chemical.REFS originDB) {
    Chemical chem = new Chemical(nextOpenID());
    chem.setInchi(strIDs.inchi); // we compute our own InchiKey under setInchi
    // chem.setInchiKey(strIDs.inchikey); // the inchikey is set by setInchi
    chem.setSmiles(strIDs.smiles);
    addReference(chem, c, meta, originDB); // add c.getID().getLocal() id to xref.originDB
    return chem;
  }

  private Reaction constructReaction(Catalysis c, HashMap<String, Long> toDBID) {
    Long[] substrates, products, cofactors, orgIDs;
    String ec, readable, dir, spont, typ;

    Conversion catalyzed = getConversion(c);
    String metacycURL = getMetaCycURL(catalyzed);
    Boolean isSpontaneous = catalyzed.getSpontaneous();
    Object dirO = catalyzed.getDir();
    Object typO = catalyzed.getTyp();
    ec = singletonSet2Str(catalyzed.getEc(), metacycURL);
    spont = isSpontaneous == null ? "" : (isSpontaneous ? "Spontaneous" : "");
    dir = dirO == null ? "" : dirO.toString(); // L->R, L<->R, or L<-R
    typ = typO == null ? "" : typO.toString(); // bioc_rxn, transport, or transport+bioc
    cofactors = getCofactors(c, toDBID);
    
    // for now just write out the source RDFId as the identifier,
    // later, we can additionally get the names of reactants and products 
    // and make a s1 + s2 -> p1 string (c.controlled.left.ref
    readable = rmHTML(catalyzed.getStandardName());
    readable += " (" + catalyzed.getID().getLocal() + ": " + ec + " " + spont + " " + dir + " " + typ + " cofactors:" + Arrays.asList(cofactors).toString() + " stoichiometry:" + catalyzed.getStoichiometry(this.src) + ")";
    
    substrates = getReactants(c, toDBID, true);
    products = getReactants(c, toDBID, false);

    Reaction rxn = new Reaction(-1L, substrates, products, ec, readable);
    rxn.addReference(this.originDB + " " + this.originDBSubID);
    rxn.addReference("url: " + metacycURL);

    return rxn;
  }

  private String singletonSet2Str(Set<String> ecnums, String metadata) {
    switch (ecnums.size()) {
      case 0: 
        return "";
      case 1: 
        return ecnums.toArray(new String[0])[0];
      default:
        return ecnums.toString(); // e.g., [2.7.1.74 , 2.7.1.76 , 2.7.1.145] for http://www.metacyc.org/META/NEW-IMAGE?object=DEOXYADENOSINE-KINASE-RXN
    }
  }

  private String rmHTML(String s) {
    return s
            .replaceAll("&lt;SUP&gt;","").replaceAll("&lt;sup&gt;", "").replaceAll("<SUP>", "").replaceAll("<sup>", "").replaceAll("&lt;/SUP&gt;","").replaceAll("&lt;/sup&gt;", "").replaceAll("</SUP>", "").replaceAll("</sup>", "")
            .replaceAll("&lt;SUB&gt;","").replaceAll("&lt;sub&gt;", "").replaceAll("<SUB>", "").replaceAll("<sub>", "").replaceAll("&lt;/SUB&gt;","").replaceAll("&lt;/sub&gt;", "").replaceAll("</SUB>", "").replaceAll("</sub>", "")
            .replaceAll("&rarr;", "->")
            .replaceAll("&larr;", "<-")
            .replaceAll("&harr;", "<->")
            .replaceAll("&amp;rarr;", "->")
            .replaceAll("&amp;larr;", "<-")
            .replaceAll("&amp;harr;", "<->");
  }
  
  Conversion getConversion(Catalysis c) {
    List<NXT> path = Arrays.asList( NXT.controlled ); // get the controlled Conversion 
    Set<BPElement> convs = this.src.traverse(c, path);
    if (convs.size() == 0)
      return null;
    if (convs.size() == 1)
      for (BPElement conversion : convs)
        return (Conversion)conversion;

    // size>1!!??
    System.out.println("More than one controlled conversion (abort):" + c.expandedJSON(this.src)); System.exit(-1); return null;
  }

  Long[] getCofactors(Catalysis c, HashMap<String, Long> toDBID) {
    // cofactors = c.cofactors.smallmoleculeref.structure
    List<NXT> path = Arrays.asList( 
          NXT.cofactors, // get the SmallMolecule
          NXT.ref, // get the SmallMoleculeRef
          NXT.structure // get the ChemicalStructure
     );
    return getMappedChems(c, path, toDBID).toArray(new Long[0]);
  }

  Long[] getReactants(Catalysis c, HashMap<String, Long> toDBID, boolean left) {
    List<Long> reactants = new ArrayList<Long>();

    // default cases:
    // substrates/products = c.controlled.left.smallmolecule.smallmoleculeref.structure
    List<NXT> path = Arrays.asList( 
          NXT.controlled, // get the controlled Conversion
          left ? NXT.left : NXT.right, // get the left or right SmallMolecules
          NXT.ref, // get the SmallMoleculeRef
          NXT.structure
    );
    reactants.addAll(getMappedChems(c, path, toDBID));

    List<NXT> path_alt = Arrays.asList( 
          NXT.controlled, // get the controlled Conversion
          left ? NXT.left : NXT.right, // get the left or right SmallMolecules
          NXT.ref, // get the SmallMoleculeRef
          NXT.members, // sometimes instead there are multiple members (e.g., in transports) instead of the small mol directly.
          NXT.structure
    );
    reactants.addAll(getMappedChems(c, path_alt, toDBID));

    return reactants.toArray(new Long[0]);
  }

  private List<Long> getMappedChems(Catalysis c, List<NXT> path, HashMap<String, Long> toDBID) {
    Set<BPElement> chems = this.src.traverse(c, path);
    List<Long> chemids = new ArrayList<Long>();
    for (BPElement chem : chems) {
      if (chem == null) continue; // this can be if the path led to a smallmoleculeref that is composed of other things and does not have a structure of itself, we handle that by querying other paths later
      String id = ((ChemicalStructure)chem).getID().getLocal();
      Long dbid = toDBID.get(id);
      chemids.add(dbid);
    }
    return chemids;
  }

  Long getOrganismID(BPElement organism) {
    // orgID = organism.xref(type Unification Xref).{ db: "NCBI Taxonomy", id: ID }
    for (BPElement bpe : this.src.resolve(organism.getXrefs())) {
      Unification u = (Unification)bpe;
      if (u.getUnifID() != null && u.getUnifDB().equals("NCBI Taxonomy"))
        return Long.parseLong(u.getUnifID());
    }

    return null;
  }

  Long[] getOrganismIDs(Catalysis c) {
    // orgIDs = c.controller(type Protein).proteinRef(type ProteinRNARef).organism(type BioSource).xref(type Unification Xref).{ db : "NCBI Taxonomy", id: ID) -- that ID is to be sent in orgIDs
    List<Long> orgs = new ArrayList<Long>();
    List<NXT> path = Arrays.asList( NXT.controller, NXT.ref, NXT.organism );
    for (BPElement biosrc : this.src.traverse(c, path)) {
      for (BPElement bpe : this.src.resolve(biosrc.getXrefs())) {
        Unification u = (Unification)bpe;
        if (u.getUnifID() != null && u.getUnifDB().equals("NCBI Taxonomy"))
          orgs.add(Long.parseLong(u.getUnifID()));
      }
    }
    return orgs.toArray(new Long[0]);
  }

  Long[] getCatalyzingSequence(Catalysis c, Reaction rxn, long rxnid) {
    // c.controller(type: Protein).proteinRef(type ProteinRNARef).sequence
    // c.controller(type: Complex).component(type: Protein) .. as above
    List<NXT> proteinPath = Arrays.asList( NXT.controller, NXT.ref );
    List<NXT> complexPath = Arrays.asList( NXT.controller, NXT.components, NXT.ref );

    Set<Long> seqs = new HashSet<Long>();

    // extract the sequence of proteins that control the rxn
    for (BPElement seqRef : this.src.traverse(c, proteinPath)) {
      //  String seq = ((ProteinRNARef)seqRef).getSeq();
      //  if (seq == null) continue;
      seqs.add(getCatalyzingSequence(c, (ProteinRNARef)seqRef, rxn, rxnid));
    }
    // extract the sequences of proteins that make up complexes that control the rxn
    for (BPElement seqRef : this.src.traverse(c, complexPath)) {
      //  String seq = ((ProteinRNARef)seqRef).getSeq();
      //  if (seq == null) continue;
      seqs.add(getCatalyzingSequence(c, (ProteinRNARef)seqRef, rxn, rxnid));
    }

    return seqs.toArray(new Long[0]);
  }

  Long getCatalyzingSequence(Catalysis c, ProteinRNARef seqRef, Reaction rxn, long rxnid) {
    // the Catalysis object has ACTIVATION/INHIBITION and L->R or R->L annotations
    // put them alongside the sequence that controls the Conversion
    org.biopax.paxtools.model.level3.ControlType act_inhibit = c.getControlType();
    org.biopax.paxtools.model.level3.CatalysisDirectionType direction = c.getDirection();
    String seq = seqRef.getSeq();
    Resource org = seqRef.getOrg();
    Set<String> comments = seqRef.getComments();
    String name = seqRef.getStandardName();
    Set<JSONObject> refs = toJSONObject(seqRef.getRefs()); // this contains things like UniProt accession#s, other db references etc.

    Long org_id = getOrganismID(this.src.resolve(org));

    String dir = direction == null ? "NULL" : direction.toString();
    String act_inh = act_inhibit == null ? "NULL" : act_inhibit.toString();
    SequenceEntry entry = MetacycEntry.initFromMetacycEntry(seq, org_id, name, comments, refs, rxnid, rxn, act_inh, dir);
    long seqid = entry.writeToDB(db, Seq.AccDB.metacyc);

    return seqid;
  }

  Set<JSONObject> toJSONObject(Set<Resource> resources) {
    Set<JSONObject> rsrc = new HashSet<JSONObject>();
    for (Resource r : resources)
      rsrc.add(this.src.resolve(r).expandedJSON(this.src));
    return rsrc;
  }

  String getMetaCycURL(Conversion c) {
    for (BPElement x : this.src.resolve(c.getXrefs())) {
      if (x instanceof Unification) {
        Unification u = (Unification)x;
        // we dont check for the "DB" in the catalysis unification xref since there
        // is only one xref and that points directly to the metacyc ID
        if (u.getUnifID().matches(this.METACYC_URI_IDS)) 
          return this.METACYC_URI_PREFIX + u.getUnifID();
      }
    }
    return null;
  }

  private Long _MaxIDInDB = Long.MIN_VALUE;
  private Long nextOpenID() {
    return _MaxIDInDB + 1;
  }
  
  private void setIDAsUsed(Long id) {
    if (id > _MaxIDInDB) {
      _MaxIDInDB = id;
    } 
  }

  private HashMap<String, Chemical> getChemicalsAlreadyInDB() {
    HashMap<String, Chemical> chems = new HashMap<String, Chemical>();
    DBIterator it = this.db.getIteratorOverChemicals();
    while (it.hasNext()) {
      Chemical c = db.getNextChemical(it);
      chems.put(c.getInChI(), c);
      setIDAsUsed(c.getUuid());
    }
    return chems;
  }

  public void writeStdout() {
    for (Resource id : smallmolecules.keySet()) {
      SmallMolecule sm = (SmallMolecule)smallmolecules.get(id);
      SmallMoleculeRef smref = (SmallMoleculeRef)this.src.resolve(sm.getSMRef());
      SmallMolMetaData meta = getSmallMoleculeMetaData(sm, smref);
      ChemicalStructure c = (ChemicalStructure)this.src.resolve(smref.getChemicalStructure());
      ChemStrs str = getChemStrs(c);
      if (str == null) continue;
      System.out.println(str.inchi);
    }

    // we go through each Catalysis and Modulation, both of which refer
    // to a controller (protein/complex) and controlled (reaction)
    // for each controlled reaction we pull up its Conversion (BioCRxn, Trans, Trans+BioCRxn)
    // Conversion has left, right and other details of the reaction

    for (Resource id : enzyme_catalysis.keySet()) {
      Catalysis c = enzyme_catalysis.get(id);
      System.out.println(c.expandedJSON(this.src).toString(2));
    }

    System.out.println("******************************************************");
    System.out.println("From file: " + this.originDBSubID);
    System.out.println("Extracted " + smallmolecules.size() + " small molecule structures.");
    System.out.println();
    System.out.println("******************************************************");
    System.out.println("From file: " + this.originDBSubID);
    System.out.println("Extracted " + enzyme_catalysis.size() + " catalysis observations.");
    System.out.println();
    System.out.format("Chems: %d (fail inchi: %d, fail load: %d)\n", smallmolecules.size(), fail_inchi,  fail_load);
  }

  private SmallMolMetaData getSmallMoleculeMetaData(SmallMolecule sm, SmallMoleculeRef smref) {
    Term t = (Term)this.src.resolve(sm.getCellularLocation());
    String cellLoc = t != null ? t.getTerms().toString() : null; // returns a Set<String>, flatten it
  
    Set<String> names = new HashSet<String>();
    names.addAll(smref.getName());
    names.addAll(sm.getName());

    String metacycURL = null;
    HashMap<String, String> dbid = new HashMap<String, String>();
    for (BPElement elem : this.src.resolve(smref.getXrefs())) {
      if (elem instanceof Unification) {
        Unification u = (Unification) elem;
        dbid.put(u.getUnifDB(), u.getUnifID());
        if (u.getUnifDB().endsWith("yc") && 
            (u.getUnifID() != null && u.getUnifID().matches(this.METACYC_URI_IDS))) 
          metacycURL = this.METACYC_URI_PREFIX + u.getUnifID();
      } else if (elem instanceof Publication) {
        Publication p = (Publication) elem;
        dbid.put(p.dbid(), p.citation());
      } else if (elem instanceof Relationship) {
        Relationship u = (Relationship) elem;
        dbid.put(u.getRelnDB(), u.getRelnID());
      } else {
        System.out.println("Other xref:" + elem.expandedJSON(this.src).toString(2));
        System.exit(-1);
      }
    }
    return new SmallMolMetaData(
      smref.getStandardName(), // smref and sm should have duplicate standardName fields
      names,
      smref.getMolecularWeight(),
      cellLoc, 
      metacycURL,
      dbid);
  }

  private class SmallMolMetaData {
    String standardName;
    String cellularLoc;
    Set<String> names;
    Float molweight;
    HashMap<String, String> dbid;
    String metacycURL;
    SmallMolMetaData(String s, Set<String> n, Float mw, String cellLoc, String url, HashMap<String, String> dbid) {
      this.standardName = s; this.names = n; this.molweight = mw; this.cellularLoc = cellLoc; this.dbid = dbid; this.metacycURL = url;
    }

    public BasicDBList getDBObject(BasicDBList list) {
      DBObject thiso = getDBObject();
      if (list == null) 
        list = new BasicDBList();
      list.add(thiso);
      return list;
    }

    private DBObject getDBObject() {
      DBObject o = new BasicDBObject();
      o.put("sname", standardName);
      o.put("names", names);
      if (cellularLoc != null) o.put("loc", cellularLoc);
      if (metacycURL != null) o.put("url", metacycURL);
      o.put("molw", molweight);
      BasicDBList reflist = new BasicDBList();
      for (String db : dbid.keySet()) { 
        BasicDBObject ro = new BasicDBObject();
        ro.put("db", db);
        ro.put("id", dbid.get(db));
        reflist.add(ro);
      }
      o.put("refs", reflist);
      return o;
    }
  }

  private class ChemStrs {
    String inchi, smiles, inchikey; 
    ChemStrs(String i, String ikey, String s) {
      this.inchi = i; this.inchikey = ikey; this.smiles = s;
    }
  }

  private int fail_inchi = 0, fail_load = 0; // logging statistics
  private Indigo indigo = new Indigo();
  private IndigoInchi inchi = new IndigoInchi(indigo);
  private ChemStrs getChemStrs(ChemicalStructure c) {
    String cml = c.getStructure().replaceAll("atomRefs","atomRefs2");
    IndigoObject mol = null;
    try {
      mol = indigo.loadMolecule(cml);
    } catch (IndigoException e) {
      if (debugFails) System.out.println("Invalid CML?:\n" + cml);
      fail_load++;
      return null;
    }

    String inc = null, smiles = null, incKey = null;
    try {
      // convert to consistent inchi that we use in MongoDB
      // CommandLineRun.consistentInChI(mol); 
      inc = inchi.getInchi(mol);
      // just calling consistent inchi on this object does not work
      // we need to load it up from scratch using just the inchi string
      // and then call consistent. 
      // this is how we are going to see the mol when we load it from the DB
      // reset the inchi "inc" to the consistent one
      inc = CommandLineRun.consistentInChI(inc, "MetaCyc install");

      incKey = inchi.getInchiKey(inc);
      smiles = mol.canonicalSmiles();
    } catch (IndigoException e) {
      if (debugFails) System.out.println("Failed to get inchi:\n" + cml);
      fail_inchi++;
      return null;
    }

    return new ChemStrs(inc, incKey, smiles);

    // there seem to be some valid cases of failures because the CML contains the
    // following, non small-molecule, entities (R groups, bigger mols, just names):
    // cat out | grep cml | grep -v "\[R1\]" | grep -v "\[R\]" | grep -v "RNA" | grep -v "a nucleobase" | grep -v "DNA" | grep -v "Protein" | grep -v "RPL3" | grep -v "Purine-Bases" | grep -v "ETR-Quinones" | grep -v "Deaminated-Amine-Donors" | grep -v "Release-factors" | grep -v Acceptor | grep -v "\[R2\]" | grep -v "Peptides" | grep -v "Siderophore" | grep -v "Lipopolysaccharides" | wc -l
    // but then there are some 115/1901 (ecocyc) that are valid when converted through
    // openbabel (obabel, although conversion to inchis always happens with warnings)
    // and we have sent these to the indigo team.
  }

  private ChemStrs hackAllowingNonSmallMolecule(ChemicalStructure c) {
    String fakeinchi = "InChI=/FAKE/" + this.originDB + "/" + this.originDBSubID + "/" + c.getID().getLocal();
    String fakeinchikey = "FAKEKEY/" + fakeinchi;
    String fakesmiles = c.getStructure(); // install the CML inside SMILES
    return new ChemStrs(fakeinchi, fakeinchikey, fakesmiles);
  }

}

