package org.twentyn.proteintodna;

import com.act.reachables.Cascade;
import com.act.reachables.ReactionPath;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;

import java.util.ArrayList;
import java.util.List;

public class ProteinToDNADriver {


  public static void main(String[] args) throws Exception {

    MongoClient client = new MongoClient(new ServerAddress("localhost", 27017));
    DB db = client.getDB("wiki_reachables");
    String collectionName = "pathways_vijay";

    JacksonDBCollection collection =
        JacksonDBCollection.wrap(db.getCollection(collectionName), ReactionPath.class, String.class);

    DBCursor cursor = collection.find();


    List<Long> reactions = new ArrayList<Long>();
    while (cursor.hasNext()) {
      ReactionPath reactionPath = (ReactionPath) cursor.next();
      int j = 0;
    }
  }
}
