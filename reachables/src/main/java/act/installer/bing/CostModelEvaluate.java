package act.installer.bing;

public class CostModelEvaluate {
  public static void main(String args[]) {
    costmodel model = new costmodel("testmodel");
    Double cost = model.getPerTonCost(31.9, 84);
    System.out.println("Cost = " + cost);
  }
}
