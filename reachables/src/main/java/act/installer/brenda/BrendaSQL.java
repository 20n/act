package act.installer.brenda;

import act.client.CommandLineRun;
import act.server.SQLInterface.MongoDB;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Chemical;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;

import com.mongodb.DBObject;
import com.mongodb.BasicDBObject;
import org.bson.types.Binary;

public class BrendaSQL {
  public static final long BRENDA_ORGANISMS_ID_OFFSET = 4000000000l;
  public static final long BRENDA_NO_NCBI_ID = -1;

	private MongoDB db;

  public BrendaSQL(MongoDB db) {
    this.db = db;
  }


  public void installChemicals(List<String> tagCofactors) throws SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 10000, "brenda_user", "micv395-pastille");

    long installid = db.getNextAvailableChemicalDBid();
    Iterator<BrendaSupportingEntries.Ligand> ligands = brendaDB.getLigands();
    while (ligands.hasNext()) {
      // this ligand iterator will not give us unique chemical
      // inchis. so we have to lookup what inchis we have already seen
      // and if a repeat shows up, we only add the name/other metadata...

      BrendaSupportingEntries.Ligand ligand = ligands.next();
      Chemical c = createActChemical(ligand);
      if (tagCofactors.contains(c.getSmiles()))
        c.setAsCofactor();
      if (c.getUuid() == -1) {
        // indeed a new chemical inchi => install new

        /* 
           This use of a locally incremented installid counter 
           will not be safe if multiple processes are
           writing to the DB. E.g., if we distribute the installer

           If that is the case, then use some locks and
           long installid = db.getNextAvailableChemicalDBid();
           to pick the next available id to install this chem to
        */
        db.submitToActChemicalDB(c, installid);
        installid++;
        numEntriesAdded++;
      } else {
        // chemical already seen, just merge with existing in db
        // submitToActChemicalDB checks pre-existing, and if yes
        // ignores the provided installid, and just merges with existing
        db.submitToActChemicalDB(c, (long) -1);
      }
    }

    brendaDB.disconnect();
    System.out.format("Main.addChemicals: Num added %d\n", numEntriesAdded);
  }

  private Chemical createActChemical(BrendaSupportingEntries.Ligand ligand) {
    // read all fields from the BRENDA SQL ligand table
    String brenda_inchi = ligand.getInchi();
    String name = ligand.getLigand().toLowerCase();
    byte[] molfile = ligand.getMolfile();
    Integer group_id_synonyms = ligand.getGroupId();
    Integer brenda_id = ligand.getLigandId();

    if (brenda_inchi == null || brenda_inchi.equals("")) {
      brenda_inchi = "InChI=/FAKE/BRENDA/" + brenda_id;
    }

    // System.out.format("BRENDA Ligand: \n\t%s\n\t%s\n\t%d\n\t%d\n", brenda_inchi, name, brenda_id, group_id_synonyms);
    String inchi = CommandLineRun.consistentInChI(brenda_inchi, "BRENDA SQL install");

    // check if this inchi has already been installed as a db chemical
    Chemical exists = db.getChemicalFromInChI(inchi);

    if (exists != null) {
      // chemical already exists in db, return as is.
      setMetaData(exists, name, molfile, brenda_id, group_id_synonyms);
      return exists;
    }

    // this is the first time we are seeing a ligand with this inchi
    // create a chemical with this new inchi
    Chemical c = new Chemical((long) -1); // id we set here ignored on install
    c.setInchi(inchi);
    c.setSmiles(inchi2smiles(inchi));
    setMetaData(c, name, molfile, brenda_id, group_id_synonyms);

    return c;
  }

  private void setMetaData(Chemical c, String name, byte[] molfile, Integer bid, Integer group_id_synonyms) {
    // we add to Brenda Names because getChemicalIDFromExactBrendaName
    // looks up in that field of the chemical for a match.
    c.addBrendaNames(name);

    // set molfile, bid, and group_id_synonyms in brenda.xref
    DBObject brendaMetadata = new BasicDBObject();
    brendaMetadata.put("brenda_id", bid);
    brendaMetadata.put("group_id_synonyms", group_id_synonyms);
    if (molfile != null) 
      brendaMetadata.put("molfile", new Binary(molfile));
    c.putRef(Chemical.REFS.BRENDA, brendaMetadata);
  }

  private String inchi2smiles(String inchi) {
    Indigo ind = new Indigo();
    IndigoInchi ic = new IndigoInchi(ind);
    try {
      return ic.loadMolecule(inchi).canonicalSmiles();
    } catch (Exception e) {
      return null;
    }
  }

  public void install() throws SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, like the one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 10000, "brenda_user", "micv395-pastille");

    Iterator<BrendaRxnEntry> rxns = brendaDB.getRxns();
    while (rxns.hasNext()) {
      BrendaRxnEntry brendaTblEntry = rxns.next();
      Reaction r = createActReaction(brendaTblEntry);
      db.submitToActReactionDB(r);
      numEntriesAdded++;
    }

    rxns = brendaDB.getNaturalRxns();
    while (rxns.hasNext()) {
      BrendaRxnEntry brendaTblEntry = rxns.next();
      Reaction r = createActReaction(brendaTblEntry);
      db.submitToActReactionDB(r);
      numEntriesAdded++;
    }

    brendaDB.disconnect();
    System.out.format("Main.addBrendaReactionsFromSQL: Num entries added %d\n", numEntriesAdded);
  }

  public void installOrganisms() throws SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, like the one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 10000, "brenda_user", "micv395-pastille");

    Iterator<BrendaSupportingEntries.Organism> organisms = brendaDB.getOrganisms();
    while (organisms.hasNext()) {
      BrendaSupportingEntries.Organism organism = organisms.next();
      numEntriesAdded++;
      // TODO: continue here.
      // TODO: what is the space of organism ids we get from other sources, and how can we avoid collisions?
      Organism o = new Organism(organism.getOrganismId().longValue() + BRENDA_ORGANISMS_ID_OFFSET,
          BRENDA_NO_NCBI_ID,
          organism.getOrganism()
      );
      db.submitToActOrganismNameDB(o);
    }

    brendaDB.disconnect();
    System.out.format("Main.addBrendaReactionsFromSQL: Num entries added %d\n", numEntriesAdded);
  }

  private Reaction createActReaction(BrendaRxnEntry entry) {
    String org = entry.getOrganism();
    String rev = entry.getReversibility();
    String sub = entry.getSubstrateStr();
    String prd = entry.getProductStr();
    String ecnum = entry.getEC();
    String litref = entry.getLiteratureRef();
    String brendaID = entry.getBrendaID();

    Long[] substrates = splitAndGetCmpds(sub);
    Long[] products = splitAndGetCmpds(prd);

    String readable = constructReadable(org, sub, prd, REVERSIBILITY.brendaCode(rev));

    Reaction rxn = new Reaction(-1L, 
    		substrates, 
    		products, 
    		ecnum, 
    		readable, 
    		new Long[] { getOrgID(org) });
    rxn.addReference(litref);

    rxn.addReference("BRENDA " + brendaID);
    rxn.setDataSource(Reaction.RxnDataSource.BRENDA);

    return rxn;
  }

  private String constructReadable(String o, String s, String p, REVERSIBILITY r) {
    return " {" + o + "} " + s + " " + r + " " + p;

  }

  enum REVERSIBILITY { 
    R("<->"),
    IR("->"),
    UNK("-?>");

    private String inBetweenRxn;

    private REVERSIBILITY(String inBtwn) {
      this.inBetweenRxn = inBtwn;
    }

    public static REVERSIBILITY brendaCode(String revCode) {
      if (revCode.equals("r")) {
        return REVERSIBILITY.R;
      } else if (revCode.equals("ir")) {
        return REVERSIBILITY.IR;
      } else if (revCode.equals("?")) {
        return REVERSIBILITY.UNK;
      }

      // default
      // when junk entries populate the reversibility field
      return REVERSIBILITY.UNK;
    }

    @Override
    public String toString() {
      return this.inBetweenRxn;
    }
  };

  private Long getOrgID(String organism) {
    Long id = db.getOrganismId(organism);
    if (id == -1) logMsgBrenda("Organism: " + organism);

    return id;
  }

  private Long[] splitAndGetCmpds(String cmpdsSet) {
    String[] cmpds = cmpdsSet.split(" \\+ ");
    Long[] cids = new Long[cmpds.length];
    for (int i = 0; i < cmpds.length; i++) {
      String name = cmpds[i].trim();
      cids[i] = db.getChemicalIDFromExactBrendaName(name);
      // if (cids[i] == -1 && !name.equals("H") && !name.equals("NAD") && !name.equals("?")) logMsgBrenda("Chemical: " + name);
      if (cids[i] == -1 && !name.equals("?") && !name.equals("more")) logMsgBrenda("Chemical: " + name);
    }
    return cids;
  }

  private void logMsgBrenda(String whatfailed) {
    System.out.format("Brenda (%s) did not resolve. Abort!\n", whatfailed);
  }

  private void abortBrenda(String whatfailed) {
    System.out.format("Brenda (%s) did not resolve. Abort!\n", whatfailed);
    System.exit(-1);
  }


}
