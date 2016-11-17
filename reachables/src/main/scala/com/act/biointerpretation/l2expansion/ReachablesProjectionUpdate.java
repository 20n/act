package com.act.biointerpretation.l2expansion;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;

import scala.collection.JavaConversions;

import java.util.Collection;

public class ReachablesProjectionUpdate {

  public static final String PRECURSOR_KEY = "prediction_precursors";
  public static final String INCHI_KEY = "InChI";
  public static final String SUBSTRATES_KEY = "substrates";
  public static final String RO_KEY = "ro";

  private static final Boolean UPSERT = true;
  private static final Boolean NO_MULTI = false;

  private final ProjectionResult projectionResult;

  public ReachablesProjectionUpdate(ProjectionResult projectionResult) {
    this.projectionResult = projectionResult;
  }

  public void updateReachables(DBCollection reachables) {
    for (String product : JavaConversions.asJavaCollection(projectionResult.products())) {
      // The query object for this product
      BasicDBObject newProductQuery = new BasicDBObject().append(INCHI_KEY, product);

      // DB list of the substrates of this projection
      Collection<String> substrates = JavaConversions.asJavaCollection(projectionResult.substrates());
      BasicDBList substrateList = new BasicDBList();
      substrateList.addAll(substrates);

      // DB list of the one RO associated with this projection
      BasicDBList roList = new BasicDBList();
      roList.add(projectionResult.ros());

      // The full entry to be added to the product's precursor list
      BasicDBObject precursorEntry = new BasicDBObject()
          .append(SUBSTRATES_KEY, substrateList)
          .append(RO_KEY, roList);

      // The command to push the precursor entry onto the precursor list
      BasicDBObject precursors = new BasicDBObject();
      precursors.append("$push", new BasicDBObject(PRECURSOR_KEY, precursorEntry));

      // Do the update!
      reachables.update(newProductQuery, precursors, UPSERT, NO_MULTI);
    }
  }
}
