package com.act.biointerpretation.l2expansion;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import java.net.UnknownHostException;

public class CreateTestWikiReachables {

  private static final String REACHABLES_COLLECTION = "reachables";
  private static final String INCHI_KEY = "InChI";
  private static final String ONLY_INCHI = "InChI=1S/C2H6O/c1-2-3/h3H,2H2,1H3";

  public static void main(String[] args) throws UnknownHostException {
    MongoClient mongoClient = new MongoClient("localhost" , 27017 );
    DB db = mongoClient.getDB("gil_wiki_reachables");
    db.getCollection(REACHABLES_COLLECTION).drop();
    db.createCollection(REACHABLES_COLLECTION, null);
    DBCollection reachables = db.getCollection(REACHABLES_COLLECTION);

    DBObject onlyDoc = new BasicDBObject().append(INCHI_KEY, ONLY_INCHI);
    reachables.insert(onlyDoc);

    DBObject keysToIndex = new BasicDBObject().append(INCHI_KEY, 1);
    reachables.createIndex(keysToIndex, new BasicDBObject("unique", true));
  }
}
