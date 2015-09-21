package act.server.Search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;

public class RankingMetrics {


  /**
   * Set of methods for getting metrics.
   */
  static private boolean initialized = false;
  static private MongoDB db;
  static private Set<Long> myCofactors;

  static public void init(MongoDB db) {
    if (initialized) return;
    initialized = true;
    RankingMetrics.db = db;
    List<Chemical> cofactorChemicals = db.getCofactorChemicals();
    myCofactors = new HashSet<Long>();
    for (Chemical chemical : cofactorChemicals) {
      myCofactors.add(chemical.getUuid());
    }

  }

  /**
   * Returns a count of each cofactor used.
   * @param reactionID
   * @param reversed
   * @return
   */
  static public Counter<Long> getCofactorUsage(Long reactionID, boolean reversed) {
    Reaction reaction = db.getReactionFromUUID(reactionID);
    Long[] substrateIDs = reversed ? reaction.getProducts() : reaction.getSubstrates();
    Long[] productIDs = reversed ? reaction.getSubstrates() : reaction.getProducts();
    Counter<Long> counts = new Counter<Long>();
    for (Long substrateID : substrateIDs) {
      counts.dec(substrateID);
    }
    for (Long productID : productIDs) {
      counts.inc(productID);
    }
    return counts;
  }
}
