package act.installer.brenda;

import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

import java.sql.SQLException;
import java.util.Iterator;

public class BrendaSQL {
	private MongoDB db;

  public BrendaSQL(MongoDB db) {
    this.db = db;
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
    if (id == -1) abortBrenda("Organism: " + organism);

    return id;
  }

  private Long[] splitAndGetCmpds(String cmpdsSet) {
    String[] cmpds = cmpdsSet.split(" + ");
    Long[] cids = new Long[cmpds.length];
    for (int i = 0; i < cmpds.length; i++) {
      cids[i] = db.getChemicalIDFromName(cmpds[i]);
      if (cids[i] == -1) abortBrenda("Chemical: " + cmpds[i]);
    }
    return cids;
  }

  private void abortBrenda(String whatfailed) {
    System.out.format("Brenda (%s) did not resolve. Abort!\n", whatfailed);
    System.exit(-1);
  }


}
