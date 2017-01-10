package act.installer.reachablesexplorer;


import java.util.List;

public class ReachableProjectionResult {
  private final List<String> substrates;
  private final List<String> products;
  private final String ros;

  public ReachableProjectionResult(List<String> substrates, List<String> products, String ros) {
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
