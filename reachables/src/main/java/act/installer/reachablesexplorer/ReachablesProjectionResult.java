package act.installer.reachablesexplorer;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// The original ProjectionResult is a Scala case class, which Jackson doesn't handle well.
// This class is the Java equivalent, used for the purpose of Jackson de-serialization.
// TODO: ProjectionResult.scala and this class should be eventually merged together

public class ReachablesProjectionResult {

  @JsonProperty("substrates")
  private final List<String> substrates;

  @JsonProperty("products")
  private final List<String> products;

  @JsonProperty("ros")
  private final String ros;

  @JsonCreator
  public ReachablesProjectionResult(
      @JsonProperty("substrates") List<String> substrates,
      @JsonProperty("products") List<String> products,
      @JsonProperty("ros") String ros) {
    this.substrates = substrates;
    this.products = products;
    this.ros = ros;
  }

  public List<String> getSubstrates() {
    return substrates;
  }

  public List<String> getProducts() {
    return products;
  }

  public String getRos() {
    return ros;
  }
}
