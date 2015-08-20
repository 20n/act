package act.shared;


import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import act.shared.helpers.P;
import org.json.JSONObject;

public class Reaction implements Serializable {
	private static final long serialVersionUID = 42L;
	Reaction() { /* default constructor for serialization */ }

  public enum RxnDataSource { BRENDA, KEGG, METACYC };
  public enum RefDataSource { PMID, BRENDA, KEGG, METACYC };
	
	private int uuid;
  private RxnDataSource dataSource;
  protected Long[] substrates, products;
  protected Map<Long, Integer> substrateCoefficients, productCoefficients;
  private Double estimatedEnergy;
  private String ecnum, rxnName;
  private ReactionType type = ReactionType.CONCRETE;
  
  private Set<P<RefDataSource, String>> references;
  private Set<JSONObject> proteinData;
  
  private Set<String> keywords;
  private Set<String> caseInsensitiveKeywords;

  public Reaction(long uuid, Long[] substrates, Long[] products, String ecnum, String reaction_name_field, ReactionType type) {
    this(uuid, substrates, products, ecnum, reaction_name_field);
    this.type = type;
  }
  
  public Reaction(long uuid, Long[] substrates, Long[] products, String ecnum, String reaction_name_field) {
    this.uuid = (new Long(uuid)).intValue();
  	this.substrates = substrates;
    this.products = products;
    this.ecnum = ecnum;
    this.rxnName = reaction_name_field;
    this.substrateCoefficients = new HashMap<Long, Integer>();
    this.productCoefficients = new HashMap<Long, Integer>();

    this.references = new HashSet<P<RefDataSource, String>>();
    this.proteinData = new HashSet<JSONObject>();
    this.keywords = new HashSet<String>();
    this.caseInsensitiveKeywords = new HashSet<String>();
  }

  public RxnDataSource getDataSource() {
    return this.dataSource;
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
    
  /**
   * Negative if irreversible, zero if uncertain, positive if reversible.
   * @return
   */
  public int isReversible() {
  	if (this.rxnName == null)
  		return 0;
  	if (this.rxnName.contains("<->"))
  		return 1;
  	else if (this.rxnName.contains("-?>"))
  		return 0;
  	else
  		return -1;
  }
  
  public String isReversibleString() {
  	if (this.rxnName == null)
  		return "Unspecified";
  	if (this.rxnName.contains("<->"))
  		return "Reversible";
  	else if (this.rxnName.contains("-?>"))
  		return "Unspecified";
  	else
  		return "Irreversible";
  }
  
  public void reverse() {
  	uuid = reverseID(uuid);
  	if (estimatedEnergy != null)
  		estimatedEnergy = -estimatedEnergy;
  	
  	Long[] compounds = substrates;
  	substrates = products;
  	products = compounds;
  	
  	Map<Long, Integer> coefficients;
  	coefficients = substrateCoefficients;
  	productCoefficients = substrateCoefficients;
  	substrateCoefficients = coefficients;
  }
  
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

  public void addProteinData(JSONObject proteinData) {
    this.proteinData.add(proteinData);
  }

  public Set<JSONObject> getProteinData() {
    return this.proteinData;
  }

  public Set<Long> getOrgRefs() {
    System.out.println("Need to pull organism refs out of proteins: for BRENDA; and install somewhere for metacyc.");
    System.exit(-1);
    return null;
  }

  public Set<Long> getSeqRefs() {
    System.out.println("For brenda reactions, db.seq needs to have, in rxn_refs, the reference to sequence (to be installed in db.seq instead of protein). For metacyc those references are already populated so just pull them from db.seq");
    System.exit(-1);
    return null;
  }

  public int getUUID() { return this.uuid; }
  public Long[] getSubstrates() { return substrates; }
  public Long[] getProducts() { return products; }
  public Set<Long> getSubstratesWCoefficients() { return substrateCoefficients.keySet(); }
  public Set<Long> getProductsWCoefficients() { return productCoefficients.keySet(); }
  public Integer getSubstrateCoefficient(Long s) { return substrateCoefficients.get(s); }
  public Integer getProductCoefficient(Long p) { return productCoefficients.get(p); }
  public void setSubstrateCoefficient(Long s, Integer c) { substrateCoefficients.put(s, c); }
  public void setProductCoefficient(Long p, Integer c) { productCoefficients.put(p, c); }
  public String getECNum() { return ecnum; }
  public String getReactionName() { return rxnName; }
  public ReactionType getType() { return type; }
  
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
