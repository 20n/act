package act.server.AbstractPath;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;

public class OperatorHierarchySimple implements OperatorHierarchy {

  private static OperatorHierarchySimple instance;

  private OperatorHierarchySimple(MongoDB db) {
    // TODO Auto-generated constructor stub
  }

  public static OperatorHierarchy getInstance(MongoDB db) {
    if (instance != null)
      return instance;
    instance = new OperatorHierarchySimple(db);
    return instance;
  }

  /*
   * See comments in:
   * @see act.server.OperatorHierarchy#Transform(Chemical, boolean, int)
   */
  @Override
  public Applier Transform(Chemical start, boolean fwd, int nth) {
    // TODO Auto-generated method stub
    return null;
  }

}
