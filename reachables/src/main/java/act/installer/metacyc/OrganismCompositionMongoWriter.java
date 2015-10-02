package act.installer.metacyc;

import act.client.CommandLineRun;
import act.installer.metacyc.annotations.Stoichiometry;
import act.installer.metacyc.annotations.Term;
import act.installer.metacyc.entities.ChemicalStructure;
import act.installer.metacyc.entities.ProteinRNARef;
import act.installer.metacyc.entities.SmallMolecule;
import act.installer.metacyc.entities.SmallMoleculeRef;
import act.installer.metacyc.processes.BiochemicalPathwayStep;
import act.installer.metacyc.processes.Catalysis;
import act.installer.metacyc.processes.Conversion;
import act.installer.metacyc.references.Publication;
import act.installer.metacyc.references.Relationship;
import act.installer.metacyc.references.Unification;
import act.installer.sequence.MetacycEntry;
import act.installer.sequence.SequenceEntry;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.Seq;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.apache.commons.lang3.tuple.Pair;
import org.biopax.paxtools.model.level3.CatalysisDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrganismCompositionMongoWriter {
  MongoDB db;
  OrganismComposition src;
  Chemical.REFS originDB;
  String originDBSubID;
  HashMap<Resource, SmallMolecule> smallmolecules;
  HashMap<Resource, Catalysis> enzyme_catalysis;
  HashMap<Resource, BiochemicalPathwayStep> biochemicalPathwaySteps;
  HashMap<String, String> uniqueKeyToInChImap;
  boolean debugFails = false;

  // metacyc id's are in Unification DB=~name of origin, ID.matches(METACYC_URI_PREFIX)
  String METACYC_URI_IDS = "^[A-Z0-9-]+$"; //
  // to get valid Metacyc website URL
  String METACYC_URI_PREFIX = "http://www.metacyc.org/META/NEW-IMAGE?object=";

  // Metacyc ids/metadata will be written to these fields in the DB.
  public static final String METACYC_OBJECT_MODEL_XREF_ID_PATH = "xref.METACYC.id";
  public static final String METACYC_OBJECT_MODEL_XREF_METADATA_PATH = "xref.METACYC.meta";

  Indigo indigo = new Indigo();
  IndigoInchi indigoInchi = new IndigoInchi(indigo);

  int ignoredMoleculesWithMultipleStructures = 0;
  int totalSmallMolecules = 0;

  OrganismCompositionMongoWriter(MongoDB db, OrganismComposition o, String origin, Chemical.REFS originDB) {
    System.out.println("Writing DB: " + origin);
    this.db = db;
    this.src = o;
    this.originDB = originDB;
    this.originDBSubID = origin;
    smallmolecules = o.getMap(SmallMolecule.class);
    enzyme_catalysis = o.getMap(Catalysis.class);
    this.biochemicalPathwaySteps = o.getMap(BiochemicalPathwayStep.class);
    this.uniqueKeyToInChImap = o.getUniqueKeyToInChImap();
  }

  /**
   * Each Metacyc biopax file contains collections of reactions and chemicals, organized by organism.
   * The reactions reference the chemicals using biopax-specific (or Metacyc-specific?) identifiers that don't match
   * our internal id scheme (for good reason--our identifier approach is far less complex!).  This method writes the
   * contents of one organism's reactions and chemicals to the DB.  The chemicals are written first so that we can
   * accumulate a mapping of Metacyc small molecule reference ids to our DB's chemical ids.  The reactions' substrates
   * and products are then written to the DB using our internal chemical IDs, allowing us to unify Metacyc's chemical
   * and reaction data with whatever has already been written. */
  public void write() {


    if (false)
      writeStdout(); // for debugging, if you need a full copy of the data in stdout

    // while going through this organisms chemicals (optionally installing
    // into db if required), we map its rdfID to the inchi (in db)
    HashMap<String, Long> rdfID2MongoID = new HashMap<String, Long>();
    // for debugging, we log only the number of new reactions with sequences seen
    int newRxns = 0;
    int resolvedViaDirectInChISpecified = 0;
    int resolvedViaSmallMoleculeRelationship = 0;

    // Stores chemical strings derived from CML to avoid repeated processing for reused small molecule references.
    HashMap<Resource, ChemInfoContainer> smRefsCollections = new HashMap<>();

    for (Resource id : smallmolecules.keySet()) {
      SmallMolecule sm = (SmallMolecule) smallmolecules.get(id);
      SmallMoleculeRef smref = (SmallMoleculeRef) this.src.resolve(sm.getSMRef());
      if (smref == null) {
        continue; // only happens in one case standardName="a ribonucleic acid"
      }

      /* De-duplicate structureToChemStrs calls by storing already accessed small molecule structures in a hash.
       * If we find the same molecule in our hash, we don't need to process it again! */
      ChemInfoContainer chemInfoContainer = smRefsCollections.get(sm.getSMRef());
      if (chemInfoContainer == null) {
        ChemicalStructure c = (ChemicalStructure) this.src.resolve(smref.getChemicalStructure());

        ChemStrs chemStrs = null;
        if (c != null) { // Only produce ChemStrs if we have a chemical structure to store.
          String lookupInChI;
          if (c.getInChI() != null) {
            chemStrs = new ChemStrs(c.getInChI(), null, null);
            resolvedViaDirectInChISpecified++;
          } else if ((lookupInChI = lookupInChIByXRefs(sm)) != null) {
            // TODO: should we track these?  They could just be bogus compounds or compound classes.
            chemStrs = new ChemStrs(lookupInChI, null, null);
            resolvedViaSmallMoleculeRelationship++;
          } else {
            // Extract various canonical representations (like InChI) for this molecule based on the structure.
            chemStrs = structureToChemStrs(c);
          }
        } else {
          /* This occurs for Metacyc entries that are treated as classes of molecules rather than individual molecules.
           * See https://github.com/20n/act/issues/40. */
          System.out.format("--- warning, null ChemicalStructure for %s; %s; %s\n",
              smref.getStandardName(), smref.getID(), smref.getChemicalStructure());
          // TODO: we could probably call `continue` here safely.
        }

        // Wrap all of the nominal/structural information for this molecule together for de-duplication.
        chemInfoContainer = new ChemInfoContainer(smref, chemStrs, c);
        smRefsCollections.put(sm.getSMRef(), chemInfoContainer);
      }

      if (chemInfoContainer.c == null) {
        if (debugFails) System.out.println("No structure: " + smref.expandedJSON(this.src).toString(2));
        continue; // mostly big molecules (e.g., a ureido compound, a sulfhydryl reagent, a macrolide antibiotic), but sometimes complexes (their members fields has small molecule structures), and sometimes just no structure given (colanic acid, a reduced nitroaromatic compound)
      }

      SmallMolMetaData meta = getSmallMoleculeMetaData(sm, smref);

      chemInfoContainer.addSmallMolMetaData(meta);
    }

    System.out.format("*** Resolved %d of %d small molecules' InChIs via InChI structures.\n",
        resolvedViaDirectInChISpecified, smallmolecules.size());
    System.out.format("*** Resolved %d of %d small molecules' InChIs via compounds.dat lookup.\n",
        resolvedViaSmallMoleculeRelationship, smallmolecules.size());
    System.out.format("--- writing chemicals for %d collections from %d molecules\n",
        smRefsCollections.size(), smallmolecules.size());

    // Write all referenced small molecules only once.  We de-duplicated while reading, so we should be ready to go!
    for (ChemInfoContainer cic : smRefsCollections.values()) {
      // actually add chemical to DB
      Long dbId = writeChemicalToDB(cic.structure, cic.c, cic.metas);
      if (dbId == null) {
        System.err.format("ERROR: unable to find/write chemical '%s'\n",
            cic.smRef == null ? null : cic.smRef.getStandardName());
        continue;
      }

      /* Put rdfID -> mongodb ID in rdfID2MongoID map.  These ids will be used to reference the chemicals in Metacyc
       * substrates/products entries, so it's important to get them right (and for the mapping to be complete). */
      rdfID2MongoID.put(cic.c.getID().getLocal(), dbId);
    }

    /* It appears that Catalysis objects can appear outside of BiochemicalPathwaySteps in biopax files.  Record which
     * catalyses we've installed from BiochemicalPathwaySteps so that we can ensure full coverage without duplicating
     * reactions in the DB. */
    Set<Resource> seenCatalyses = new HashSet<>(this.enzyme_catalysis.size());

    // Iterate over the BiochemicalPathwaySteps, extracting either Catalyses if available or the raw Conversion if not.
    for (Map.Entry<Resource, BiochemicalPathwayStep> entry : this.biochemicalPathwaySteps.entrySet()) {
      BiochemicalPathwayStep bps = entry.getValue();

      // TODO: does this correctly handle the case where the process consists only of Modulations?  Is that possible?
      Set<Resource> catalyses = bps.getProcess();
      if (catalyses == null || catalyses.size() == 0) {
        System.out.format("%s: No catalyses, falling back to conversion %s\n",
            bps.getID(), bps.getConversion());
        Conversion c = (Conversion)this.src.resolve(bps.getConversion());
        if (c == null) {
          System.err.format("ERROR: could not find expected conversion %s for %s\n", bps.getConversion(), bps.getID());
        } else {
          addReaction(c, rdfID2MongoID, bps.getDirection());
        }
      } else {
        System.out.format("%s: Found %d catalyses\n", bps.getID(), catalyses.size());
        for (Resource res : catalyses) {
          Catalysis c = this.enzyme_catalysis.get(res);
          // Don't warn here, as the stepProcess could be a Modulation and we don't necessarily care about those.
          if (c != null) {
            seenCatalyses.add(res);
            addReaction(c, rdfID2MongoID, bps.getDirection());
          }
        }
        newRxns++;
      }
    }

    /* Some Catalysis objects exist outside BiochemicalPathwaySteps, so iterate over all the Catalyses in this file
     * and install any we haven't already seen. */
    for (Map.Entry<Resource, Catalysis> entry : enzyme_catalysis.entrySet()) {
      // Don't re-install Catalysis objects that were part of BiochemicalPathwaySteps, but make sure we get 'em all.
      if (seenCatalyses.contains(entry.getKey())) {
        continue;
      }
      // actually add reaction to DB
      addReaction(entry.getValue(), rdfID2MongoID, null);
      newRxns++;
    }

    // Output stats:
    System.out.format("New writes: %s (%d) :: (rxns)\n", this.originDBSubID, newRxns);
    System.out.format("Ignored %d of %d small molecules with multiple chemical structures\n",
        ignoredMoleculesWithMultipleStructures, totalSmallMolecules);
  }

  // A container for SMRefs and their associated Indigo-derived ChemStrs.  Used for deduplication of chemical entries.
  private class ChemInfoContainer {
    public SmallMoleculeRef smRef;
    public ChemStrs structure;
    public ChemicalStructure c;
    public List<SmallMolMetaData> metas; // This list of `metas` will become the xref metadata on the DB chemical entry.

    public ChemInfoContainer(SmallMoleculeRef smRef, ChemStrs structure, ChemicalStructure c) {
      this.smRef = smRef;
      this.structure = structure;
      this.c = c;
      this.metas = new LinkedList<>();
    }

    public void addSmallMolMetaData(SmallMolMetaData meta) {
      metas.add(meta);
    }
  }

  private ChemStrs structureToChemStrs(ChemicalStructure c) {
    ChemStrs structure = getChemStrsFromChemicalStructure(c);
    if (structure == null) {
      // do some hack, put something in inchi, inchikey and smiles so that
      // we do not end up loosing the reactions that have R groups in them
      structure = hackAllowingNonSmallMolecule(c);
    }
    return structure;
  }

  private Long writeChemicalToDB(ChemStrs structure, ChemicalStructure c, List<SmallMolMetaData> metas) {
    if (structure == null) {
      return null;
    }
    // Do an indexed query to determine whether the chemical already exists in the DB.
    Long dbId = db.getExistingDBIdForInChI(structure.inchi);
    if (dbId == null) { // InChI doesn't appear in DB.
      // DB does not contain chemical as yet, create and install.
      // TODO: if needed, we can optimize this by querying the DB count on construction and incrementing locally.
      Chemical dbChem = new Chemical(-1l);
      dbChem.setInchi(structure.inchi); // we compute our own InchiKey under setInchi (well, now only InChI!)
      dbChem.setSmiles(structure.smiles);
      // Be sure to create the initial set of references in the initial object write to avoid another query.
      dbChem = addReferences(dbChem, c, metas, originDB);
      Long installid = db.getNextAvailableChemicalDBid();
      db.submitToActChemicalDB(dbChem, installid);
      dbId = installid;
    } else { // We found the chemical in our DB already, so add on Metacyc xref data.
      /* If the chemical already exists, just add the xref id and metadata entries.  Mongo will do the heavy lifting
       * for us, so this should hopefully be fast. */
      String id = c.getID().getLocal();
      BasicDBList dbMetas = metaReferencesToDBList(id, metas);
      db.appendChemicalXRefMetadata(
          structure.inchi,
          METACYC_OBJECT_MODEL_XREF_ID_PATH, id, // Specify the paths where the Metacyc xref fields should be added.
          METACYC_OBJECT_MODEL_XREF_METADATA_PATH, dbMetas
      );
    }
    return dbId;
  }

  /* Add a reaction to the DB based on a complete Catalysis.  This will extract the underlying Conversion and append
   * available sequence/organism data.  This is preferred over the Conversion variant of this function as we want the
   * extra data to appear in the DB. */
  private Reaction addReaction(Catalysis c, HashMap<String, Long> rdfID2MongoID, StepDirection pathwayStepDirection) {
    // using the map of chemical rdfID->mongodb id, construct a Reaction object
    Reaction rxn = constructReaction(c, rdfID2MongoID, pathwayStepDirection);
    // set the datasource
    rxn.setDataSource(Reaction.RxnDataSource.METACYC);

    // pass the Reaction to the mongodb driver to insert into act.actfamilies
    int rxnid = db.submitToActReactionDB(rxn);

    // construct protein info object to be installed into the rxn
    Long[] orgIDs = getOrganismIDs(c);
    Long[] seqs = getCatalyzingSequence(c, rxn, rxnid);
    JSONObject proteinInfo = constructProteinInfo(c, orgIDs, seqs);

    // add it to the in-memory object
    rxn.addProteinData(proteinInfo);

    // rewrite the rxn to update the protein data
    // ** Reason for double write: It is the wierdness of us
    // wanting to install a back pointer from the db.seq
    // entries back to metacyc db.actfamilies rxns
    // which is why we first write and get a _id of the
    // written metacyc rxn, and then construct db.seq entries
    // (which have the _id installed) and then write those
    // pointers under actfamilies.protein.
    //
    // ** Now note in brenda we do not do this wierd back
    // pointer stuff from db.seq. In brenda actfamilies entries
    // the actfamilies entry itself has the protein seq directly
    // there. Not ideal. TODO: FIX THAT.
    db.updateActReaction(rxn, rxnid);

    return rxn;
  }

  // Add a Conversion to the DB without sequence or organism data.
  private Reaction addReaction(Conversion c, HashMap<String, Long> rdfID2MongoID, StepDirection pathwayStepDirection) {
    Reaction rxn = constructReaction(c, rdfID2MongoID, pathwayStepDirection);
    rxn.setDataSource(Reaction.RxnDataSource.METACYC);
    // There's no organism/sequence information available on Conversions, so just write the reaction without it.
    int rxnid = db.submitToActReactionDB(rxn);
    db.updateActReaction(rxn, rxnid);

    return rxn;
  }

  private JSONObject constructProteinInfo(Catalysis c, Long[] orgs, Long[] seqs) {
    JSONObject protein = new JSONObject();
    JSONArray orglist = new JSONArray();
    for (Long o : orgs) orglist.put(o);
    protein.put("organisms", orglist);
    JSONArray seqlist = new JSONArray();
    for (Long s : seqs) seqlist.put(s);
    protein.put("sequences", seqlist);
    protein.put("datasource", "METACYC");
    CatalysisDirectionType cdt = c.getDirection();
    protein.put("catalysis_direction", cdt == null ? null : cdt.toString());

    return protein;
  }

  private BasicDBList metaReferencesToDBList(String id, List<SmallMolMetaData> metas) {
    BasicDBList dbList = new BasicDBList();
    for (SmallMolMetaData meta : metas) {
      DBObject metaObj = meta.getDBObject();
      metaObj.put("id", id);
      dbList.add(metaObj);
    }
    return dbList;
  }

  private Chemical addReferences(Chemical dbc, ChemicalStructure c, List<SmallMolMetaData> metas, Chemical.REFS originDB) {
    JSONObject ref = dbc.getRef(originDB);
    JSONArray idlist = null;
    String chemID = c.getID().getLocal();
    if (ref == null) {
      // great, this db's ref is not already in place. just create a new one and put it in
      ref = new JSONObject();
      idlist = new JSONArray();
      idlist.put(chemID);
    } else {
      // a ref exists, maybe it is from installing this exact same chem,
      // or from a replicate chemical from another organism. add the DB's ID
      // to the chemical's xref field
      idlist = ref.has("id") ? (JSONArray)ref.get("id") : new JSONArray();
      boolean contains = false;
      for (int i = 0; i < idlist.length(); i++)
        if (idlist.get(i).equals(chemID))
          contains = true;
      if (!contains)
        idlist.put(chemID);
      // else do nothing, since the idlist already contains the id of this chem.
    }

    // install the idlist into the xref.KEGG/METACYC field
    ref.put("id", idlist);

    Object existing = null;
    if (ref.has("meta"))
      existing = ref.get("meta");
    JSONArray newMeta = addAllToExistingMetaList(chemID, existing, metas);
    ref.put("meta", newMeta);

    // update the chemical with the new ref
    dbc.putRef(originDB, ref);

    // return the updated chemical
    return dbc;
  }

  private JSONArray addAllToExistingMetaList(String id, Object existing, List<SmallMolMetaData> metas) {
    JSONArray metaData = null;
    if (existing == null) {
      metaData = new JSONArray();
    } else if (existing instanceof JSONArray) {
      metaData = (JSONArray)existing;
    } else {
      System.out.println("SmallMolMetaDataList[0] = " + metas.get(0).toString());
      System.out.println("Existing Chemical.refs[Chemical.REFS.METACYC] not a list! = " + existing);
      System.out.println("It is of type " + existing.getClass().getSimpleName());
      System.out.println("Want to add SmallMolMetaData to list, but its not a list!");
      System.exit(-1);
      return null;
    }

    for (SmallMolMetaData meta : metas) {
      DBObject metaDBObject = meta.getDBObject();
      metaDBObject.put("id", id);
      metaData.put(metaDBObject);
    }
    return metaData;
  }

  // Extract the conversion from a Catalysis object, and use the Catalysis + Conversion to construct a reaction.
  private Reaction constructReaction(Catalysis c, HashMap<String, Long> toDBID, StepDirection pathwayStepDirection) {
    Conversion catalyzed = getConversion(c);
    Map<Resource, Stoichiometry> stoichiometry = catalyzed.getRawStoichiometry(this.src);

    List<Pair<Long, Integer>> substratesPair = getReactants(c, toDBID, true, stoichiometry);
    List<Pair<Long, Integer>> productsPair = getReactants(c, toDBID, false, stoichiometry);
    List<Pair<Long, Integer>> cofactorsPair = getCofactors(c, toDBID, stoichiometry);
    return constructReactionHelper(catalyzed, toDBID,
        substratesPair, productsPair, cofactorsPair, pathwayStepDirection);
  }

  // If no Catalysis is available, extract the substrates/products/cofactors from a raw Conversion.
  private Reaction constructReaction(Conversion c, HashMap<String, Long> toDBID, StepDirection pathwayStepDirection) {
    Map<Resource, Stoichiometry> stoichiometry = c.getRawStoichiometry(this.src);

    List<Pair<Long, Integer>> substratesPair = getReactants(c, toDBID, true, stoichiometry);
    List<Pair<Long, Integer>> productsPair = getReactants(c, toDBID, false, stoichiometry);
    List<Pair<Long, Integer>> cofactorsPair = getCofactors(c, toDBID, stoichiometry);
    return constructReactionHelper(c, toDBID, substratesPair, productsPair, cofactorsPair, pathwayStepDirection);
  }

  private Reaction constructReactionHelper(Conversion catalyzed, HashMap<String, Long> toDBID,
                                           List<Pair<Long, Integer>> substratesPair,
                                           List<Pair<Long, Integer>> productsPair,
                                           List<Pair<Long, Integer>> cofactorsPair,
                                           StepDirection pathwayStepDirection) {
    Long[] substrates, products, cofactors;
    String ec, readable, dir, spont, typ;

    String metacycURL = getMetaCycURL(catalyzed);
    Boolean isSpontaneous = catalyzed.getSpontaneous();
    Object dirO = catalyzed.getDir();
    Object typO = catalyzed.getTyp();
    ec = singletonSet2Str(catalyzed.getEc(), metacycURL);
    spont = isSpontaneous == null ? "" : (isSpontaneous ? "Spontaneous" : "");
    dir = dirO == null ? "" : dirO.toString(); // L->R, L<->R, or L<-R
    typ = typO == null ? "" : typO.toString(); // bioc_rxn, transport, or transport+bioc

    cofactors = getLefts(cofactorsPair);

    // for now just write out the source RDFId as the identifier,
    // later, we can additionally get the names of reactants and products
    // and make a s1 + s2 -> p1 string (c.controlled.left.ref
    readable = rmHTML(catalyzed.getStandardName());
    readable += " (" + catalyzed.getID().getLocal() + ": " + ec + " " + spont + " " + dir + " " + typ + " cofactors:" +
        Arrays.asList(cofactors).toString() + " stoichiometry:" + catalyzed.getStoichiometry(this.src) + ")";

    substrates = getLefts(substratesPair);
    products = getLefts(productsPair);

    Reaction rxn = new Reaction(-1L, substrates, products, ec, catalyzed.getDir(), pathwayStepDirection, readable);

    for (int i = 0; i < substratesPair.size(); i++) {
      Pair<Long, Integer> s = substratesPair.get(i);
      rxn.setSubstrateCoefficient(s.getLeft(), s.getRight());
    }
    for (int i = 0; i < productsPair.size(); i++) {
      Pair<Long, Integer> p = productsPair.get(i);
      rxn.setProductCoefficient(p.getLeft(), p.getRight());
    }

    rxn.addReference(Reaction.RefDataSource.METACYC, this.originDB + " " + this.originDBSubID);
    rxn.addReference(Reaction.RefDataSource.METACYC, metacycURL);

    return rxn;
  }

  private Long[] getLefts(List<Pair<Long, Integer>> pairs) {
    Long[] lefts = new Long[pairs.size()];
    for (int i = 0; i<pairs.size(); i++) {
      lefts[i] = pairs.get(i).getLeft();
    }
    return lefts;
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

  List<Pair<Long, Integer>> getCofactors(Catalysis c, HashMap<String, Long> toDBID, Map<Resource, Stoichiometry> stoichiometry) {
    // cofactors = c.cofactors.smallmoleculeref.structure
    // but we retrieve it in two steps:
    //    1) get the small molecule,
    //    2) get the structure associated with the small molecule
    // this is because from `1)` we can also lookup the stoichiometry

    // here is the path to the small molecule reference:
    List<NXT> smmol_path = Arrays.asList(
        NXT.cofactors // get the SmallMolecule
    );

    // here is the path to the chemical structure within that small molecule:
    List<NXT> struct_path = Arrays.asList(
        NXT.ref, // get the SmallMoleculeRef
        NXT.structure // get the ChemicalStructure
    );

    List<Pair<Long, Integer>> cofactors = getMappedChems(c, smmol_path, struct_path, toDBID, stoichiometry, false);

    return cofactors;
  }

  /* Get cofactors for a stand-alone Conversion when a Catalysis object is not available.  Raw conversions don't
   * reference cofactors, so this is always an empty list.  `unmodifiableList` ensures this list is always empty. */
  private static final List<Pair<Long, Integer>> EMPTY_COFACTORS = Collections.unmodifiableList(new ArrayList<>(0));
  List<Pair<Long, Integer>> getCofactors(Conversion c, HashMap<String, Long> toDBID, Map<Resource, Stoichiometry> stoichiometry) {
    return EMPTY_COFACTORS;
  }

  private static final List<NXT> STRUCT_PATH = Collections.unmodifiableList(Arrays.asList(
      NXT.ref, // get the SmallMoleculeRef
      NXT.structure
  ));
  private static final List<NXT> STRUCT_PATH_ALT = Collections.unmodifiableList(Arrays.asList(
      NXT.ref, // get the SmallMoleculeRef
      NXT.members, // sometimes instead there are multiple members (e.g., in transports) instead of the small mol directly.
      NXT.structure
  ));
  List<Pair<Long, Integer>> getReactants(Catalysis c, HashMap<String, Long> toDBID, boolean left, Map<Resource, Stoichiometry> stoichiometry) {

    List<Pair<Long, Integer>> reactants = new ArrayList<Pair<Long, Integer>>();

    // default cases:
    // substrates/products = c.controlled.left.smallmolecule.smallmoleculeref.structure

    // but we retrieve it in two steps:
    //    1) get the small molecule,
    //    2) get the structure associated with the small molecule
    // this is because from `1)` we can also lookup the stoichiometry

    // here is the path to the small molecule reference:
    List<NXT> smmol_path = Arrays.asList(
        NXT.controlled, // get the controlled Conversion
        left ? NXT.left : NXT.right // get the left or right SmallMolecules
    );
    // here is the path to the chemical structure within that small molecule:
    List<NXT> struct_path = STRUCT_PATH;
    List<Pair<Long, Integer>> mappedChems = getMappedChems(c, smmol_path, struct_path, toDBID, stoichiometry, false);
    reactants.addAll(mappedChems);

    // we repeat something similar, but for cases where the small molecule ref
    // contains multiple members, e.g., in transports. This usually does
    // not lead to reactant elements, but in edge cases where it does
    // we add them to the reactants

    // here is the path to the small molecule reference:
    List <NXT> smmol_path_alt = Arrays.asList(
        NXT.controlled, // get the controlled Conversion
        left ? NXT.left : NXT.right // get the left or right SmallMolecules
    );
    // here is the path to the chemical structure within that small molecule:
    // (notice the difference from the above: this is ref.members.structure)
    List <NXT> struct_path_alt = STRUCT_PATH_ALT;
    mappedChems = getMappedChems(c, smmol_path_alt, struct_path_alt, toDBID, stoichiometry, true);
    reactants.addAll(mappedChems);

    return reactants;
  }

  List<Pair<Long, Integer>> getReactants(Conversion c, HashMap<String, Long> toDBID, boolean left, Map<Resource, Stoichiometry> stoichiometry) {
    // See getReactions(Catalysis c, ...) for documentation on this function's behavior.
    List<Pair<Long, Integer>> reactants = new ArrayList<Pair<Long, Integer>>();

    List<NXT> smmol_path = Collections.singletonList(
        // A raw Conversion doesn't have `controller`/`controlled` child nodes.
        left ? NXT.left : NXT.right // get the left or right SmallMolecules
    );
    // SmallMolecule lookup works the same within a Conversion.
    List<NXT> struct_path = STRUCT_PATH;
    List<Pair<Long, Integer>> mappedChems = getMappedChems(c, smmol_path, struct_path, toDBID, stoichiometry, false);
    reactants.addAll(mappedChems);

    // The smmol_path is the same in the alternative case: Conversions only have `left` and `right`.

    // The struct_path_alt is the same as Catalysis since we're looking at the left/right side of the conversion.
    List <NXT> struct_path_alt = STRUCT_PATH_ALT;
    mappedChems = getMappedChems(c, smmol_path, struct_path_alt, toDBID, stoichiometry, true);
    reactants.addAll(mappedChems);

    return reactants;
  }

  /**
   * Stoichiometry entries in raw Metacyc XML contain SmallMolecule objects that then contain ChemicalStructure objects.
   * Once the XML is parsed, stoichiometry coefficients are available via SmallMolecule ids.  The ChemicalStructure
   * objects, however, contain the chemical information we want to store in the DB.  In order to associate the
   * substrates and products in a reaction to their stoichiometric coefficients, we need to link the containing
   * SmallMolecule's id with its ChemicalStructure child.  The smmol_path allows us to traverse the Catalysis objects
   * (which represents the substrates and products of reactions) to find the SmallMolecules on one side of a reaction;
   * we then traverse those SmallMolecules to find their ChemicalStructures.  This gives us a mapping like:
   * <pre>Stoichiometry (with coefficient) <-> SmallMolecule <-> ChemicalStructure <-> DB ID.</pre>
   *
   * The output of this function is a list of the DB ids of the chemicals on whatever side of the reaction the specified
   * smmol_path represents, paired with their respective stoichiometric coefficients.
   *
   * @param catalysisOrConversion The Catalysis or Conversion (reaction) object whose substrates or products we're inspecting.
   * @param smmol_path A path to fetch the desired collection of small molecules from the reaction.
   * @param struct_path A path to fetch the chemical structures from the extracted small molecules.
   * @param toDBID A map from chemical structure id to DB id.
   * @param stoichiometry A map from small molecule id to Stoichiometry object that we'll use to extract coefficients.
   * @return A list of pairs of (DB id, stoichiometry coefficient) for the chemicals found via the specified path.
   */
  private List<Pair<Long, Integer>> getMappedChems(
      BPElement catalysisOrConversion, List<NXT> smmol_path, List<NXT> struct_path, HashMap<String, Long> toDBID,
      Map<Resource, Stoichiometry> stoichiometry, boolean expectedMultipleStructures) {
    /* TODO: since this is a private method, this check ought to be unnecessary (if we've written everything correctly).
     * Remove it once we're sure it's unnecessary. */
    if (!(catalysisOrConversion instanceof Catalysis || catalysisOrConversion instanceof Conversion)) {
      throw new RuntimeException(String.format(
          "getMappedChems passed unexpected BPElement subclass %s with id %s",
          catalysisOrConversion.getClass(), catalysisOrConversion.getID()));
    }

    List<Pair<Long, Integer>> chemids = new ArrayList<Pair<Long, Integer>>();

    Set<BPElement> smmols = this.src.traverse(catalysisOrConversion, smmol_path);
    for (BPElement smmol : smmols) {
      Resource smres = smmol.getID();
      Integer coeff = getStoichiometry(smres, stoichiometry);

      Set<BPElement> chems = this.src.traverse(smmol, struct_path);
      if (chems.size() > 1) {
        if (!expectedMultipleStructures) {
          /* Abort if we find an unexpected molecule with multiple chemical structures.  If we don't anticipate these
           * appearing and we ignore them, then we may be incorrectly ignoring good data. */
          throw new RuntimeException(String.format(
              "SEVERE WARNING: small molecule %s has multiple chemical structures " +
              "when only one is expected; ignoring.\n", smmol.getID())
          );
        } else {
          System.err.format("WARNING: small molecule %s has multiple chemical structures; ignoring.\n", smmol.getID());
        }
        ignoredMoleculesWithMultipleStructures++;
      } else {
        for (BPElement chem : chems) {
          // chem == null can happen if the path led to a smallmoleculeref
          // that is composed of other things and does not have a structure
          // of itself, we handle that by querying other paths later
          if (chem == null)
            continue;

          String id = chem.getID().getLocal();
          Long dbid = toDBID.get(id);
          if (dbid == null) {
            System.err.format("ERROR: Missing DB ID for %s\n", id);
          }
          chemids.add(Pair.of(dbid, coeff));
        }
      }
      totalSmallMolecules++;
    }

    return chemids;
  }

  private Map<Resource, Integer> tointvals(Map<Resource, Stoichiometry> st) {
    Map<Resource, Integer> intvals = new HashMap<Resource, Integer>();
    for (Resource r : st.keySet())
      intvals.put(r, st.get(r).getCoefficient().intValue());

    return intvals;
  }

  private Integer getStoichiometry(Resource res, Map<Resource, Stoichiometry> stoichiometry) {
    // lookup the stoichiometry in the global map
    Stoichiometry s = stoichiometry.get(res);

    if (s == null) {
      System.err.format("ERROR: missing stoichiometry entry for metacyc resource %s\n", res.getLocal());
      return null;
    }

    // pick out the integer coefficient with the stoichiometry object
    Integer coeff = s.getCoefficient().intValue();

    return coeff;
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
    List<NXT> path = Arrays.asList(NXT.controller, NXT.ref, NXT.organism);
    for (BPElement biosrc : this.src.traverse(c, path)) {
      if (biosrc == null) {
        System.err.format("WARNING: got null organism for %s\n", c.getID());
        continue;
      }
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
      seqs.add(getCatalyzingSequence(c, (ProteinRNARef) seqRef, rxn, rxnid));
    }
    // extract the sequences of proteins that make up complexes that control the rxn
    for (BPElement seqRef : this.src.traverse(c, complexPath)) {
      //  String seq = ((ProteinRNARef)seqRef).getSeq();
      //  if (seq == null) continue;
      seqs.add(getCatalyzingSequence(c, (ProteinRNARef) seqRef, rxn, rxnid));
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

    Long org_id = null;
    BPElement organism = this.src.resolve(org);
    /* `organism` might be null if the sequence doesn't have an organism reference or if that organism reference is
     * dangling.  In either case, only get/store the organism's id if we're able to find it in the source data.
     */
    if (organism != null) {
      org_id = getOrganismID(organism);
    } else {
      System.err.format("WARNING: catalysis %s does not have a valid organism reference (%s)\n", c.getID(), org);
    }

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

  public void writeStdout() {
    for (Resource id : smallmolecules.keySet()) {
      SmallMolecule sm = (SmallMolecule)smallmolecules.get(id);
      SmallMoleculeRef smref = (SmallMoleculeRef)this.src.resolve(sm.getSMRef());
      SmallMolMetaData meta = getSmallMoleculeMetaData(sm, smref);
      ChemicalStructure c = (ChemicalStructure)this.src.resolve(smref.getChemicalStructure());
      ChemStrs str = getChemStrsFromChemicalStructure(c);
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
    System.out.format("Chems: %d (fail inchi: %d)\n", smallmolecules.size(), fail_inchi);
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

    @Override
    public String toString() {
      return this.getDBObject().toString();
    }
  }

  private class ChemStrs {
    String inchi, smiles, inchikey;
    ChemStrs(String i, String ikey, String s) {
      this.inchi = i; this.inchikey = ikey; this.smiles = s;
    }
  }

  private String lookupInChIByXRefs(SmallMolecule sm) {
    Set<Resource> xrefs = sm.getXrefs();
    String firstInchi = null;
    if (xrefs == null) {
      throw new RuntimeException("No x-refs for " + sm.getID());
    }
    for (Resource xref : xrefs) {
      BPElement bpe = this.src.resolve(xref);
      if (bpe instanceof Relationship) {
        /* TODO: it's not clear how to link up the ontology name with the DB identifiers in these relationship objects.
         * For now we'll just look up by ID in the hash and hope that things work out okay. :-/
         */
        String id = ((Relationship) bpe).getRelnID();
        String db = ((Relationship) bpe).getRelnDB();
        String lookupResult = this.uniqueKeyToInChImap.get(id);
        if (lookupResult != null) {
          // Just store the first one and bail; we didn't see multiple InChIs for one molecule in testing.
          firstInchi = lookupResult;
          break;
        }
      }
    }

    return firstInchi;
  }

  private int fail_inchi = 0; // logging statistics

  private ChemStrs getChemStrsFromChemicalStructure(ChemicalStructure c) {
    String inc = null, smiles = null, incKey = null;

    /* Always prefer InChI over CML if available.  The Metacyc-defined InChIs are more precise than what we get from
     * parsing CML (which seems to lack stereochemistry details). */
    if (c.getInChI() != null) {
      // TODO: ditch InChI-Key and SMILES, as they're never really used.
      return new ChemStrs(c.getInChI(), incKey, smiles);
    }
    /* Note: this assumes the structure is always CML, but the ChemicalStructure class also expects SMILES.
     * Do we see both in practice? */

    String cml = c.getStructure().replaceAll("atomRefs","atomRefs2");
    // We can a CML description of the chemical structure.
    // Attempt to pass it through indigo to get the inchi
    // Then additionally pass it through consistentInChI
    // which in the integration step (as of the moment)
    // is a NOOP.
    try {
      IndigoObject mol = indigo.loadMolecule(cml);
      inc = indigoInchi.getInchi(mol);

      inc = CommandLineRun.consistentInChI(inc, "MetaCyc install");
    } catch (Exception e) {
      if (debugFails) System.out.format("Failed to get inchi for %s\n", c.getID());
      fail_inchi++;
      return null;
    }

    // TODO: later check if we need to compute the inchikey and
    // smiles or we can leave them null. It looks like leaving them
    // null does result in a right install output (CMLs are stuffed
    // into the SMILES field and inchikeys are computed downstream.
    // So it looks ok to leave them null.
    //
    // incKey = indigoInchi.getInchiKey(inc);
    // smiles = mol.canonicalSmiles();

    if (cml != null && inc == null) {
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
    // and we have sent these to the Indigo team.
  }

  private ChemStrs hackAllowingNonSmallMolecule(ChemicalStructure c) {
    String fakeinchi = "InChI=/FAKE/" + this.originDB + "/" + this.originDBSubID + "/" + c.getID().getLocal();
    String fakeinchikey = "FAKEKEY/" + fakeinchi;
    String fakesmiles = c.getStructure(); // install the CML inside SMILES
    return new ChemStrs(fakeinchi, fakeinchikey, fakesmiles);
  }

}

