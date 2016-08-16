package act.installer.bing;

public class CostModelEvaluate {
  public static void main(String args[]) {
    CostModel model = new CostModel("testmodel");
    Double cost = model.getPerTonCost(31.9, 84);
    System.out.println("Cost = " + cost);

    InvestModel moneymodel = new InvestModel();
    System.out.println("Investment = " + moneymodel.getInvestment());
  }
}
