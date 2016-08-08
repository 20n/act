package act.server;

import act.shared.Chemical;
import act.shared.Cofactor;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.reachables.LoadAct;
import com.act.reachables.Network;
import com.mongodb.DBObject;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoSQLAPI {

  MongoDB readDB;
  MongoDB writeDB;

  public NoSQLAPI(String sourceDB, String destDB) {
    // This API is expected to interact and interface between two
    // versions of our knowledge graph (/ mongo dbs)
    //
    // Ref: https://github.com/20n/act/wiki/Naming-schemes
    // Lucille: KG after data integration
    // DrKnow: KG after data cleanup (bio-interpretation)
    //
    // Lucille is the readDB, expected to run on 27017
    // DrKnow is the writeDB, expected to run on 37017
    //
    // There are separate read and write function in this API
    // that transparently read and write to the right KG

    if (sourceDB.equals("lucille")) {
      sourceDB = "actv01";
    }

    this.readDB = new MongoDB("localhost", 27017, sourceDB);
    this.writeDB = new MongoDB("localhost", 27017, destDB);
  }

  public static void dropDB(String destDB) {
    MongoDB.dropDB("localhost", 27017, destDB);
  }

  public static void dropDB(String destDB, boolean force) {
    MongoDB.dropDB("localhost", 27017, destDB, force);
  }

  public NoSQLAPI() {
    this("lucille", "drknow");
  }

  // Thin wrapper around getIteratorOverReactions that returns actual Reaction objects.
  public Iterator<Reaction> readRxnsFromInKnowledgeGraph() {
    final DBIterator iter = this.readDB.getIteratorOverReactions(false);

    return new Iterator<Reaction>() {
      @Override
      public boolean hasNext() {
        boolean hasNext = iter.hasNext();
        if (!hasNext)
          iter.close();
        return hasNext;
      }

      @Override
      public Reaction next() {
        DBObject o = iter.next();
        return readDB.convertDBObjectToReaction(o);
      }
    };
  }

  public Iterator<Chemical> readChemsFromInKnowledgeGraph() {
    final DBIterator iter = this.readDB.getIteratorOverChemicals();

    return new Iterator<Chemical>() {
      @Override
      public boolean hasNext() {
        boolean hasNext = iter.hasNext();
        if (!hasNext)
          iter.close();
        return hasNext;
      }

      @Override
      public Chemical next() {
        DBObject o = iter.next();
        return readDB.convertDBObjectToChemical(o);
      }
    };
  }

  public Iterator<Seq> readSeqsFromInKnowledgeGraph() {
    final DBIterator iter = this.readDB.getDbIteratorOverSeq();

    return new Iterator<Seq>() {
      @Override
      public boolean hasNext() {
        boolean hasNext = iter.hasNext();
        if (!hasNext)
          iter.close();
        return hasNext;
      }

      @Override
      public Seq next() {
        DBObject o = iter.next();
        return readDB.convertDBObjectToSeq(o);
      }
    };
  }

  public Iterator<Organism> readOrgsFromInKnowledgeGraph() {
    final DBIterator iter = this.readDB.getDbIteratorOverOrgs();

    return new Iterator<Organism> () {
      @Override
      public boolean hasNext() {
        boolean hasNext = iter.hasNext();
        if (!hasNext)
          iter.close();
        return hasNext;
      }

      @Override
      public Organism next() {
        DBObject o = iter.next();
        return readDB.convertDBObjectToOrg(o);
      }

    };
  }

  public List<JSONObject> getObservations(Reaction rxn) {
    List<JSONObject> results = new ArrayList<>();
    for (JSONObject dbo : rxn.getProteinData()) {
      results.add(dbo);
    }
    return results;
  }

  public Reaction readReactionFromInKnowledgeGraph(Long id) {
    return this.readDB.getReactionFromUUID(id);
  }

  public Chemical readChemicalFromInKnowledgeGraph(Long id) {
    return this.readDB.getChemicalFromChemicalUUID(id);
  }

  public Chemical readChemicalFromInKnowledgeGraph(String inchi) {
    return this.readDB.getChemicalFromInChI(inchi);
  }

  public int writeToOutKnowlegeGraph(Reaction r) {
    // set the UUID of the reaction to `-1` otherwise
    // the write will fail. The mongo interface expects
    // a -1 value so that it can assign whatever is
    // the the next available dbid in the collection

    r.clearUUID();

    int writtenid = this.writeDB.submitToActReactionDB(r);

    return writtenid;
  }

  public long writeToOutKnowlegeGraph(Chemical c) {
    long installid = this.writeDB.getNextAvailableChemicalDBid();
    this.writeDB.submitToActChemicalDB(c, installid);
    return installid;
  }

  public Map<Long, String> reachables(Set<String> natives, Set<String> cofactors, boolean restrictToSeq) {

    Network l2tree = LoadAct.getReachablesTree(natives, cofactors, restrictToSeq);

    Map<Long, String> l2 = new HashMap<Long, String>();
    for (Long reachable_id : l2tree.nodesAndIds().values()) {
      l2.put(reachable_id, LoadAct.toInChI(reachable_id));
    }

    System.out.println("L2 Total size   = " + l2.size());
    System.out.println("L2 (Ids, InChI) = " + l2);

    return l2;
  }

  public long writeCofactorToOutKnowledgeGraph(Cofactor c) {
    long id = this.writeDB.getNextAvailableCofactorDBid();
    this.writeDB.submitToActCofactorsDB(c, id);
    return id;
  }

  public Iterator<Cofactor> readCofactorsFromInKnowledgeGraph() {
    final DBIterator iter = readDB.getIteratorOverCofactors();

    return new Iterator<Cofactor>() {
      @Override
      public boolean hasNext() {
        boolean hasNext = iter.hasNext();
        if (!hasNext) {
          iter.close();
        }
        return hasNext;
      }

      @Override
      public Cofactor next() {
        return readDB.getNextCofactor(iter);
      }
    };
  }

  public Cofactor getCofactorFromInKnowledgeGraphByUUID(Long uuid) {
    return this.readDB.getCofactorFromUUID(uuid);
  }

  public MongoDB getReadDB() {
    return this.readDB;
  }

  public MongoDB getWriteDB() {
    return this.writeDB;
  }
}
