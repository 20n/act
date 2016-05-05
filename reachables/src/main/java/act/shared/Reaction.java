package act.shared;

import act.shared.helpers.P;
import org.biopax.paxtools.model.level3.CatalysisDirectionType;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Reaction implements Serializable {
  private static final long serialVersionUID = 42L;
  Reaction() { /* default constructor for serialization */ }

  public enum RxnDataSource { BRENDA, KEGG, METACYC, MERGED }; // Note: MERGED should be last.
  public enum RefDataSource { PMID, BRENDA, KEGG, METACYC };
  public enum RxnDetailType { CONCRETE, ABSTRACT };

  private int uuid;
  private RxnDataSource dataSource;
  protected Long[] substrates, products;
  protected Long[] substrateCofactors, productCofactors;
  protected Long[] coenzymes;
  protected Map<Long, Integer> substrateCoefficients, productCoefficients;
  private Double estimatedEnergy;
  private String ecnum, rxnName;
  private RxnDetailType type = RxnDetailType.CONCRETE;

  private Set<P<RefDataSource, String>> references;
  private Set<JSONObject> proteinData;

  private Set<String> keywords;
  private Set<String> caseInsensitiveKeywords;

  private ConversionDirectionType conversionDirection;
  private StepDirection pathwayStepDirection;

  private JSONObject mechanisticValidatorResult;

  private int sourceReactionUuid; // The ID of the reaction from this object was derived, presumably via reversal.

  public Reaction(long uuid,
                  Long[] substrates, Long[] products,
                  Long[] substrateCofactors, Long[] productCofactors,
                  Long[] coenzymes,
                  String ecnum,
                  ConversionDirectionType conversionDirection, StepDirection pathwayStepDirection,
                  String reaction_name_field, RxnDetailType type) {
    this.type = type;
    this.uuid = Long.valueOf(uuid).intValue();
    this.substrates = substrates;
    this.products = products;
    this.substrateCofactors = substrateCofactors;
    this.productCofactors = productCofactors;
    this.coenzymes = coenzymes;
    this.ecnum = ecnum;
    this.rxnName = reaction_name_field;
    this.conversionDirection = conversionDirection;
    this.pathwayStepDirection = pathwayStepDirection;
    this.mechanisticValidatorResult = null;

    this.substrateCoefficients = new HashMap<Long, Integer>();
    this.productCoefficients = new HashMap<Long, Integer>();

    this.references = new HashSet<P<RefDataSource, String>>();
    this.proteinData = new HashSet<JSONObject>();
    this.keywords = new HashSet<String>();
    this.caseInsensitiveKeywords = new HashSet<String>();
  }

  private Reaction(int sourceReactionUuid, int uuid, 
      Long[] substrates, Long[] products, 
      Long[] substrateCofactors, Long[] productCofactors, 
      Long[] coenzymes,
      String ecnum,
      ConversionDirectionType conversionDirection, StepDirection pathwayStepDirection,
      String reaction_name_field, RxnDetailType type) {
    this(uuid, substrates, products, substrateCofactors, productCofactors, coenzymes, ecnum, 
        conversionDirection, pathwayStepDirection, reaction_name_field, type);
    this.sourceReactionUuid = sourceReactionUuid;
  }

  public RxnDataSource getDataSource() {
    return this.dataSource;
  }

  public JSONObject getMechanisticValidatorResult() {
    return mechanisticValidatorResult;
  }

  public void setMechanisticValidatorResult(JSONObject mechanisticValidatorResult) {
    this.mechanisticValidatorResult = mechanisticValidatorResult;
  }

  public void setDataSource(RxnDataSource src) {
    this.dataSource = src;
  }

  public Double getEstimatedEnergy() {
    return estimatedEnergy;
  }

  public void setEstimatedEnergy(Double energy) {
    estimatedEnergy = energy;
  }

  public Set<String> getKeywords() { return this.keywords; }
  public void addKeyword(String k) { this.keywords.add(k); }
  public Set<String> getCaseInsensitiveKeywords() { return this.caseInsensitiveKeywords; }
  public void addCaseInsensitiveKeyword(String k) { this.caseInsensitiveKeywords.add(k); }

  public static int reverseID(int id) {
    return -(id + 1);
  }

  public static long reverseID(long id) {
    return -(id + 1);
  }

  public static Set<Long> reverseAllIDs(Set<Long> ids) {
    Set<Long> result = new HashSet<Long>();
    for (Long id : ids) {
      result.add(reverseID(id));
    }
    return result;
  }

  public Reaction makeReversedReaction() {
    ConversionDirectionType reversedDirection = null;
    ConversionDirectionType conversionDirection = this.getConversionDirection();
    if (conversionDirection == null) {
      // Assume reactions are left-to-right by default.
      reversedDirection = ConversionDirectionType.RIGHT_TO_LEFT;
    } else {
      switch (this.getConversionDirection()) {
        case LEFT_TO_RIGHT:
          reversedDirection = ConversionDirectionType.RIGHT_TO_LEFT;
          break;
        case RIGHT_TO_LEFT:
          reversedDirection = ConversionDirectionType.LEFT_TO_RIGHT;
          break;
        case REVERSIBLE:
          reversedDirection = ConversionDirectionType.REVERSIBLE;
          break;
        default:
          // Assume reactions are left-to-right by default.
          reversedDirection = ConversionDirectionType.RIGHT_TO_LEFT;
          break;
      }
    }

    StepDirection reversedPathwayDirection = null;
    StepDirection pathwayDirection = this.getPathwayStepDirection();
    if (pathwayDirection != null) {
      switch (pathwayDirection) {
        case LEFT_TO_RIGHT:
          reversedPathwayDirection = StepDirection.RIGHT_TO_LEFT;
          break;
        case RIGHT_TO_LEFT:
          reversedPathwayDirection = StepDirection.LEFT_TO_RIGHT;
          break;
        default:
          // Do nothing if we don't recognize the pathway step direction.
          break;
      }
    }

    // TODO: should we copy the arrays?  That might eat a lot of unnecessary memory.
    // TODO: we don't want to use reverseID, but how else we will we guarantee no collisions?
    Reaction r =
        new Reaction(this.uuid, reverseID(this.getUUID()), 
            this.getProducts(), this.getSubstrates(), 
            this.getProductCofactors(), this.getSubstrateCofactors(), 
            this.getCoenzymes(),
            this.getECNum(),
            reversedDirection, reversedPathwayDirection, this.getReactionName(), this.getRxnDetailType());
    r.setDataSource(this.getDataSource());
    return r;
  }

  public Set<Reaction> correctForReactionDirection() {
    Set<Reaction> reactions = new HashSet<>(1); // Only expect one reaction in most cases.
    boolean addRightToLeft = false;
    boolean addLeftToRight = false;
    boolean foundConversionOrCatalysisDirection = false;
    ConversionDirectionType cd = this.getConversionDirection();
    if (cd != null) {
      foundConversionOrCatalysisDirection = true;
      switch (this.getConversionDirection()) {
        case LEFT_TO_RIGHT:
          addLeftToRight = true;
          break;
        case RIGHT_TO_LEFT:
          addRightToLeft = true;
          break;
        case REVERSIBLE:
          addLeftToRight = true;
          addRightToLeft = true;
          break;
        default:
          // Assume reactions are left-to-right by default.
          addLeftToRight = true;
          break;
      }
    }

    // TODO: partition proteins by direction and split them into respective reactions.
    // Note that currently each reaction has exactly one protein, so this TODO is not urgent.
    for (JSONObject protein : this.getProteinData()) {
      if (protein.has("catalysis_direction")) {
        String cds = protein.getString("catalysis_direction");
        if (cds != null) {
          switch (CatalysisDirectionType.valueOf(cds)) {
            case LEFT_TO_RIGHT:
              foundConversionOrCatalysisDirection = true;
              addLeftToRight = true;
              break;
            case RIGHT_TO_LEFT:
              foundConversionOrCatalysisDirection = true;
              addRightToLeft = true;
              break;
            default: // No other catalysis direction value adds evidence.
              break;
          }
        }
      }
    }

    // Fall back to pathway step direction if no conversion or catalysis directions were found.
    if (!foundConversionOrCatalysisDirection && this.getPathwayStepDirection() != null) {
      switch (this.getPathwayStepDirection()) {
        case LEFT_TO_RIGHT:
          addLeftToRight = true;
          break;
        case RIGHT_TO_LEFT:
          addRightToLeft = true;
          break;
        default: // No other pathway step direction value adds evidence.
          break;
      }
    }

    // Assume reaction is left-to-right if no evidence has been found to indicate a direction.
    if (!addLeftToRight && !addRightToLeft) {
      addLeftToRight = true;
    }

    if (addLeftToRight) {
      reactions.add(this);
    }
    if (addRightToLeft) {
      reactions.add(this.makeReversedReaction());
    }

    if (reactions.size() == 0) {
      // We never expect an empty result set here.
      throw new RuntimeException(
          String.format("ERROR: Unexpected empty direction-corrected reaction set for %d\n", this.getUUID()));
    }

    return reactions;
  }

  public void addReference(RefDataSource src, String ref) {
    this.references.add(new P<RefDataSource, String>(src, ref));
  }

  public Set<P<RefDataSource, String>> getReferences() {
    return this.references;
  }

  public Set<String> getReferences(Reaction.RefDataSource type) {
    Set<String> filtered = new HashSet<String>();
    for (P<Reaction.RefDataSource, String> ref : this.references)
      if (ref.fst() == type)
        filtered.add(ref.snd());
    return filtered;
  }

  public void removeAllProteinData() {
    this.proteinData.clear();
  }

  public void addProteinData(JSONObject proteinData) {
    this.proteinData.add(proteinData);
  }

  public Set<JSONObject> getProteinData() {
    return this.proteinData;
  }

  public boolean hasProteinSeq() {
    boolean hasSeq = false;
    for (JSONObject protein : this.proteinData) {
      boolean has = proteinDataHasSeq(protein);
      hasSeq |= has;
      if (has) break;
    }
    return hasSeq;
  }

  private boolean proteinDataHasSeq(JSONObject prt) {
    switch (this.dataSource) {
      case METACYC:
        return metacycProteinDataHasSeq(prt);
      case BRENDA:
        return brendaProteinDataHasSeq(prt);
      case KEGG:
        return false; // kegg entries dont map to sequences, AFAIK
      default:
        return false; // no seq
    }
  }

  private boolean metacycProteinDataHasSeq(JSONObject prt) {
    // Example of a protein field entry for a METACYC rxn:
    // *****************************************************
    // {
    //   "datasource" : "METACYC",
    //   "organisms" : [
    //     NumberLong(198094)
    //   ],
    //   "sequences" : [
    //     NumberLong(8033)
    //   ]
    // }
    // *****************************************************

    if (!prt.has("sequences"))
      return false;

    JSONArray seqs = prt.getJSONArray("sequences");
    for (int i = 0; i < seqs.length(); i++) {
      Long s = seqs.getLong(i);
      if (s != null)
        return true;
    }

    return false;
  }

  private boolean brendaProteinDataHasSeq(JSONObject prt) {
    // Example of a protein field entry for a BRENDA rxn:
    // *****************************************************
    // {
    //   "localization" : [ ],
    //   "km" : [ { "val" : 0.01, "comment" : "in 200 mM bicine, pH 6.0, at 60°C" }, ],
    //   "expression" : [ ],
    //   "organism" : NumberLong("4000006340"),
    //   "cofactor" : [ { "val" : "NAD+", "comment" : "dependent on" } ],
    //   "sequences" : [
    //     NumberLong(0)
    //   ],
    //   "kcat/km" : [ ],
    //   "subunits" : [ ],
    //   "recommended_name" : { "recommended_name" : "alcohol dehydrogenase", "go_num" : "GO:0004025" },
    //   ...
    // }
    // *****************************************************

    if (!prt.has("sequences"))
      return false;

    JSONArray seqs = prt.getJSONArray("sequences");

    return seqs != null && seqs.length() > 0;
  }

  public int getUUID() { return this.uuid; }
  public void clearUUID() { this.uuid = -1; }
  public Long[] getSubstrates() { return substrates; }
  public Long[] getProducts() { return products; }
  public Long[] getSubstrateCofactors() { return substrateCofactors; }
  public Long[] getProductCofactors() { return productCofactors; }
  public Long[] getCoenzymes() { return coenzymes; }
  public void setSubstrates(Long[] sUp) { this.substrates = sUp; }
  public void setProducts(Long[] pUp) { this.products = pUp; }
  public void setSubstrateCofactors(Long[] sUp) { this.substrateCofactors = sUp; }
  public void setProductCofactors(Long[] pUp) { this.productCofactors = pUp; }
  public Set<Long> getSubstrateIdsOfSubstrateCoefficients() { return substrateCoefficients.keySet(); }
  public Set<Long> getProductIdsOfProductCoefficients() { return productCoefficients.keySet(); }
  public Integer getSubstrateCoefficient(Long s) { return substrateCoefficients.get(s); }
  public Integer getProductCoefficient(Long p) { return productCoefficients.get(p); }
  public void setSubstrateCoefficient(Long s, Integer c) { substrateCoefficients.put(s, c); }
  public void setProductCoefficient(Long p, Integer c) { productCoefficients.put(p, c); }
  public void setAllProductCoefficients(Map<Long, Integer> inputIdToCoefficient) {
    productCoefficients = inputIdToCoefficient;
  }
  public void setAllSubstrateCoefficients(Map<Long, Integer> inputIdToCoefficient) {
    substrateCoefficients = inputIdToCoefficient;
  }
  public String getECNum() { return ecnum; }
  public String getReactionName() { return rxnName; }
  public RxnDetailType getRxnDetailType() { return type; }
  public ConversionDirectionType getConversionDirection() { return this.conversionDirection; }
  public StepDirection getPathwayStepDirection() { return this.pathwayStepDirection; }
  public int getSourceReactionUUID() { return this.sourceReactionUuid; }

  @Override
  public String toString() {
    return "uuid: " + uuid +
        "\n ec: " + ecnum +
        " \n rxnName: " + rxnName +
        " \n substrates: " + Arrays.toString(substrates) +
        " \n products: " + Arrays.toString(products);
  }

  public String toStringDetail() {
    return "uuid: " + uuid +
        "\n ec: " + ecnum +
        " \n rxnName: " + rxnName +
        " \n refs: " + references +
        " \n substrates: " + Arrays.toString(substrates) +
        " \n products: " + Arrays.toString(products);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Reaction r = (Reaction)o;
    if (this.uuid != r.uuid || !this.ecnum.equals(r.ecnum) || !this.rxnName.equals(r.rxnName))
      return false;
    if (this.substrates.length != r.substrates.length || this.products.length != r.products.length)
      return false;

    List<Long> ss = new ArrayList<Long>();
    List<Long> pp = new ArrayList<Long>();

    for (Long s : r.substrates) ss.add(s);
    for (Long p : r.products) pp.add(p);

    for (int i = 0 ;i <substrates.length; i++)
      if (!ss.contains(substrates[i]))
        return false;
    for (int i = 0 ;i <products.length; i++)
      if (!pp.contains(products[i]))
        return false;
    return true;
  }
}
