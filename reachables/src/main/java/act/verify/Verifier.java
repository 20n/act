package act.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

public class Verifier {
  private MongoDB mongo;

  public Verifier(String host, int port, String db) {
    mongo = new MongoDB(host, port, db);
  }

  public Verifier(MongoDB mongo) {
    this.mongo = mongo;
  }

  public VerifierResult verifyReaction(List<Long> substrates, String ecnum, Long orgID) {
    List<Long> enzymeCheck = verifyEnzyme(ecnum, orgID, substrates);
    List<String> substrateCheck = verifySubstrates(substrates, orgID, ecnum);
    VerifierResult result;
    if (enzymeCheck.size() == 0 && substrateCheck.size() == 0) {
      result = new VerifierResult(true, null, null, null);
    }else if (enzymeCheck.size() != 0 && substrateCheck.size() != 0) {
      result = new VerifierResult(false, errorType.BOTH, enzymeCheck, substrateCheck);
    }else if (enzymeCheck.size() != 0) {
      result = new VerifierResult(false, errorType.ENZYMEINTERACTION, enzymeCheck, null);
    }else {
      result = new VerifierResult(false, errorType.SUBSTRATEINTERACTION, null, substrateCheck);
    }
    return result;
  }

  public class VerifierResult {
    boolean result;
    errorType error;
    List<Long> substrates;
    List<String> enzymes;
    public VerifierResult(boolean result, errorType error, List<Long> substrates, List<String> enzymes) {
      this.result = result;
      this.error = error;
      substrates = new ArrayList<Long>();
      enzymes = new ArrayList<String>();
      this.substrates = substrates;
      this.enzymes = enzymes;
    }

    public boolean getResult() {
      return this.result;
    }
    public List<Long> getSubstrates() {
      return this.substrates;
    }
    public List<String> getEnzymes() {
      return this.enzymes;
    }

    public Map<String,Object> getProperties() {
      Map<String,Object> props = new HashMap<String,Object>();
      props.put("result", result);
      props.put("error", error);
      props.put("substrates", substrates);
      props.put("enzymes", enzymes);
      return props;
    }
  }

  public enum errorType {
    ENZYMEINTERACTION,
    SUBSTRATEINTERACTION,
    BOTH;
  }

  private List<Long> verifyEnzyme(String ecnum, Long orgID, List<Long> substrates) {
    List<Long> reactionList = mongo.getRxnsWithEnzyme(ecnum, orgID, substrates);
    List<Long> result = new ArrayList<Long>();
    if (reactionList.size() != 0) {
      for (Long reaction : reactionList) {
        Reaction react = mongo.getReactionFromUUID(reaction);
        Long[] subList = react.getSubstrates();
        Long[] prodList = react.getProducts();
        for ( Long chem : subList ) {
          result.add(chem);
        }
        for ( Long chem : prodList ) {
          result.add(chem);
        }
      }
    }
    return result;
  }

  private List<String> verifySubstrates(List<Long> substrates, Long orgID, String ecnum) {
    List<Long> reactionList = mongo.getRxnsWithSubstrate(ecnum, orgID, substrates);
    List<String> result = new ArrayList<String>();
    if (reactionList.size() != 0) {
      for (Long reaction : reactionList) {
        Reaction react = mongo.getReactionFromUUID(reaction);
        result.add(react.getECNum());
      }
    }
    return result;
  }
}
