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
import com.mongodb.DBObject;

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
  private Set<DBObject> proteinData;
  
  private Set<String> keywords;
  private Set<String> caseInsensitiveKeywords;
  
  // D private Long[] organismIDs;
  // D private List<Long> sequences;
  // D private List<EnzSeqData> organismData;
  // D private List<String> kmValues;
  // D private List<String> turnoverNumbers;
  // D private List<CloningExprData> cloningData;
  // D public class EnzSeqData {
  // D 	public Long orgID;
  // D 	public String seqDataSrc;
  // D 	public List<String> seqDataIDs;
  // D }
  // D 
  // D public class CloningExprData {
  // D 	public String reference;
  // D 	public Long organism;
  // D 	public String notes;
  // D }
  
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
    this.proteinData = new HashSet<DBObject>();
    this.keywords = new HashSet<String>();
    this.caseInsensitiveKeywords = new HashSet<String>();
    
    // D this.organismIDs = orgIDs;
    // D this.sequences = new ArrayList<Long>();
    // D this.organismData = new ArrayList<EnzSeqData>();
    // D kmValues = new ArrayList<String>();
    // D turnoverNumbers = new ArrayList<String>();
    // D cloningData = new ArrayList<CloningExprData>();
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

  public void addProteinData(DBObject proteinData) {
    this.proteinData.add(proteinData);
  }

  public Set<DBObject> getProteinData() {
    return this.proteinData;
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
    
  // D public void addNewSeqData(Long orgID, String seqDataSrc, List<String> seqDataIDs) {
  // D 	EnzSeqData toAdd = new EnzSeqData();
  // D 	toAdd.orgID = orgID;
  // D 	toAdd.seqDataSrc = seqDataSrc;
  // D 	toAdd.seqDataIDs = seqDataIDs;
  // D 	organismData.add(toAdd);
  // D 	
  // D }
  
  // D public void addSequence(Long seqid) {
  // D 	this.sequences.add(seqid);
  // D }
  // D public List<Long> getSequences() {
  // D 	return this.sequences;
  // D }
  // D public void addKMValue(String kmValue) {
  // D     kmValues.add(kmValue);
  // D }
  // D public List<String> getKMValues() {
  // D     return kmValues;
  // D }
  // D public void addTurnoverNumber(String turnoverNumber) {
  // D     turnoverNumbers.add(turnoverNumber);
  // D }
  // D public List<String> getTurnoverNumbers() {
  // D     return turnoverNumbers;
  // D }
  // D 
  // D public void addCloningData(Long organism, String notes, String reference) {
  // D 	CloningExprData toAdd = new CloningExprData();
  // D 	toAdd.notes = notes;
  // D 	toAdd.organism = organism;
  // D 	toAdd.reference = reference;
  // D 	cloningData.add(toAdd);
  // D }
  // D 
  // D public List<CloningExprData> getCloningData() {
  // D 	return cloningData;
  // D }
  // D public Long[] getOrganismIDs() { return organismIDs; } 
  // D public List<Long> getOrganisms() { 
  // D 	List<Long> orgIDs = new ArrayList<Long>();
  // D 	for(EnzSeqData e: organismData) {
  // D 		orgIDs.add(e.orgID);
  // D 	}
  // D 	return orgIDs;
  // D }
  // D public List<EnzSeqData> getOrganismData() { return organismData; }
  

	// D public List<String> getPreciseOrganismNamesDeprecated() {
	// D 	String desc = rxnName;
	// D 	// the organism name should be exactly as it is in the desc under {org1, org2, org3}, e.g., {Escherichia coli, Mycobacterium tuberculosis}
	// D 	int start = desc.indexOf('{');
	// D 	int end = desc.indexOf('}', start);
	// D 	String orgs = desc.substring(start + 1, end);
	// D 	List<String> names = Arrays.asList(orgs.split(", "));
	// D 	System.out.println("This reactions orgs:" + names + " -- desc: " + desc);
	// D 	return names;
	// D }
  // D   
	// D public static String brenda_linkDeprecated(String ec, String org) {
	// D 	// Sample : http://brenda-enzymes.org/sequences/index.php4?sort=&restricted_to_organism_group=&f[TaxTree_ID_min]=0&f[TaxTree_ID_max]=0&f[stype_seq]=2&f[seq]=&f[limit_range]=10&f[stype_recom_name]=2&f[recom_name]=&f[stype_ec]=1&f[ec]=2.2.1.7&f[stype_accession_code]=2&f[accession_code]=&f[stype_organism]=1&f[organism]=Escherichia coli&f[stype_no_of_aa]=1&f[no_of_aa]=&f[stype_molecular_weight]=1&f[molecular_weight]=&f[stype_no_tmhs]=1&f[no_tmhs]=&Search=Search&f[limit_start]=0&f[nav]=&f[sort]=
	// D 	String url;
	// D 	try {
	// D 		url = "http://brenda-enzymes.org/sequences/index.php4?sort=" 
	// D 				+ "&" + "restricted_to_organism_group="
	// D 				+ "&" + "f[TaxTree_ID_min]=0" 
	// D 				+ "&" + "f[TaxTree_ID_max]=0"
	// D 				+ "&" + "f[stype_seq]=2"
	// D 				+ "&" + "f[seq]="
	// D 				+ "&" + "f[limit_range]=10"
	// D 				+ "&" + "f[stype_recom_name]=2"
	// D 				+ "&" + "f[recom_name]="
	// D 				+ "&" + "f[stype_ec]=1"
	// D 				+ "&" + "f[ec]=" + enc(ec)
	// D 				+ "&" + "f[stype_accession_code]=2"
	// D 				+ "&" + "f[accession_code]="
	// D 				+ "&" + "f[stype_organism]=1"
	// D 				+ "&" + "f[organism]=" + enc(org)
	// D 				+ "&" + "f[stype_no_of_aa]=1"
	// D 				+ "&" + "f[no_of_aa]="
	// D 				+ "&" + "f[stype_molecular_weight]=1"
	// D 				+ "&" + "f[molecular_weight]="
	// D 				+ "&" + "f[stype_no_tmhs]=1"
	// D 				+ "&" + "f[no_tmhs]="
	// D 				+ "&" + "Search=Search"
	// D 				+ "&" + "f[limit_start]=0"
	// D 				+ "&" + "f[nav]="
	// D 				+ "&" + "f[sort]=";
	// D 	} catch (UnsupportedEncodingException e) {
	// D 		e.printStackTrace();
	// D 		// just send the partial url back....
	// D 		url = "http://brenda-enzymes.org/sequences/index.php4?sort=" 
	// D 				+ "&" + "restricted_to_organism_group="
	// D 				+ "&" + "f[TaxTree_ID_min]=0"
	// D 				+ "&" + "f[TaxTree_ID_max]=0"
	// D 				+ "&" + "f[stype_seq]=2"
	// D 				+ "&" + "f[seq]="
	// D 				+ "&" + "f[limit_range]=10"
	// D 				+ "&" + "f[stype_recom_name]=2"
	// D 				+ "&" + "f[recom_name]="
	// D 				+ "&" + "f[stype_ec]=1"
	// D 				+ "&" + "f[ec]=" + ec
	// D 				+ "&" + "f[stype_accession_code]=2"
	// D 				+ "&" + "f[accession_code]="
	// D 				+ "&" + "f[stype_organism]=1";
	// D 	}
	// D 	return url;
	// D 	
	// D }

	// D private static String enc(String s) throws UnsupportedEncodingException {
	// D 	return URLEncoder.encode(s, "UTF-8");
	// D }
}
