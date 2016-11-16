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

  private final ProjectionResult projectionResult;

  public ReachablesProjectionUpdate(ProjectionResult projectionResult) {
    this.projectionResult = projectionResult;
  }

  public void updateReachables(DBCollection reachables) {
    for (String product : JavaConversions.asJavaCollection(projectionResult.products())) {
      BasicDBObject newProduct = new BasicDBObject().append(INCHI_KEY, product);

      Collection<String> substrates = JavaConversions.asJavaCollection(projectionResult.substrates());
      BasicDBList substrateList = new BasicDBList();
      substrateList.addAll(substrates);

      BasicDBList roList = new BasicDBList();
      roList.add(projectionResult.ros());

      BasicDBObject precursorEntry = new BasicDBObject()
          .append(SUBSTRATES_KEY, substrateList)
          .append(RO_KEY, roList);

      BasicDBObject precursors = new BasicDBObject();
      precursors.append("$push", new BasicDBObject(PRECURSOR_KEY, precursorEntry));

      reachables.update(newProduct, precursors, true, false);
    }
  }
}
