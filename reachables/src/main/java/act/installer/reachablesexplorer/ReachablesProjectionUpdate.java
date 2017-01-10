package act.installer.reachablesexplorer;

import com.act.biointerpretation.l2expansion.sparkprojectors.utility.ProjectionResult;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import scala.collection.JavaConversions;

import java.util.Arrays;
import java.util.List;

public class ReachablesProjectionUpdate {

  public static final String PRECURSOR_KEY = "prediction_precursors";
  public static final String INCHI_KEY = "InChI";
  public static final String SUBSTRATES_KEY = "substrates";
  public static final String RO_KEY = "ro";

  private static final Boolean UPSERT = true;
  private static final Boolean NO_MULTI = false;

  @JsonProperty("substrates")
  private final List<String> substrates;

  @JsonProperty("products")
  private final List<String> products;

  @JsonProperty("ros")
  private final List<String> ros;

  @JsonCreator
  public ReachablesProjectionUpdate(
      @JsonProperty("substrates") List<String> substrates,
      @JsonProperty("products") List<String> products,
      @JsonProperty("ros") List<String> ros) {
    this.substrates = substrates;
    this.products = products;
    this.ros = ros;
  }

  public ReachablesProjectionUpdate(ProjectionResult projectionResult) {
    this.ros = Arrays.asList(projectionResult.ros());
    this.substrates = JavaConversions.asJavaList(projectionResult.substrates());
    this.products = JavaConversions.asJavaList(projectionResult.products());
  }

  public ReachablesProjectionUpdate(ReachableProjectionResult projectionResult) {
    this.ros = Arrays.asList(projectionResult.getRos());
    this.substrates = projectionResult.getSubstrates();
    this.products = projectionResult.getProducts();
  }


  public void updateDatabase(DBCollection reachables) {
    for (String product : products) {
      // The query object for this product
      BasicDBObject newProductQuery = new BasicDBObject().append(INCHI_KEY, product);

      // DB list of the substrates of this projection
      BasicDBList substrateList = new BasicDBList();
      substrateList.addAll(substrates);

      // DB list of the one RO associated with this projection
      BasicDBList roList = new BasicDBList();
      roList.addAll(ros);

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

  public void updateByLoader(Loader loader){
    loader.updateFromProjection(this);
  }

  /**
   * Get the products associated with this projection. This is especially needed since the Precursor data associated
   * with this ProjectionResult will need to be associated with every product of the projection.
   * @return The products of the projection.
   */
  public List<String> getProducts() {
    return products;
  }

  public List<String> getSubstrates() {
    return substrates;
  }

  public List<String> getRos() {
    return ros;
  }
}
