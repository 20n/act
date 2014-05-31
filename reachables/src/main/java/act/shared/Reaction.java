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

public class Reaction implements Serializable {
	private static final long serialVersionUID = 42L;
	Reaction() { /* default constructor for serialization */ }
	
	private int uuid;
  protected Long[] substrates, products;
  protected Map<Long, Integer> substrateCoefficients, productCoefficients;
  private Double estimatedEnergy;
  private String ecnum, rxnName;
  
  private List<EnzSeqData> organismData;
  private Long[] organismIDs;
  private List<String> references;
  
  private List<String> kmValues;
  private List<String> turnoverNumbers;
  private List<CloningExprData> cloningData;
  
  private ReactionType type = ReactionType.CONCRETE;
  
  public class EnzSeqData {
  	public Long orgID;
  	public String seqDataSrc;
  	public List<String> seqDataIDs;
  }
  
  public class CloningExprData {
  	public String reference;
  	public Long organism;
  	public String notes;
  }
  
  public Reaction(long uuid, Long[] substrates, Long[] products, String ecnum, String reaction_name_field, Long[] orgIDs, ReactionType type) {
    this(uuid, substrates, products, ecnum, reaction_name_field, orgIDs);
    this.type = type;
  }
  
  public Reaction(long uuid, Long[] substrates, Long[] products, String ecnum, String reaction_name_field, Long[] orgIDs) {
    this.uuid = (new Long(uuid)).intValue();
  	this.substrates = substrates;
    this.products = products;
    this.organismIDs = orgIDs;
    this.ecnum = ecnum;
    this.rxnName = reaction_name_field;
    references = new ArrayList<String>();
    organismData = new ArrayList<EnzSeqData>();
    this.substrateCoefficients = new HashMap<Long, Integer>();
    this.productCoefficients = new HashMap<Long, Integer>();
    
    kmValues = new ArrayList<String>();
    turnoverNumbers = new ArrayList<String>();
    cloningData = new ArrayList<CloningExprData>();
  }
  
  public Double getEstimatedEnergy() {
  	return estimatedEnergy;
  }
  
  public void setEstimatedEnergy(Double energy) {
  	estimatedEnergy = energy;
  }
  
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
  
  public void addNewSeqData(Long orgID, String seqDataSrc, List<String> seqDataIDs) {
  	EnzSeqData toAdd = new EnzSeqData();
  	toAdd.orgID = orgID;
  	toAdd.seqDataSrc = seqDataSrc;
  	toAdd.seqDataIDs = seqDataIDs;
  	organismData.add(toAdd);
  	
  }
  
  public void addReference(String ref) {
  	references.add(ref);
  }
  
  public List<String> getReferences() {
  	return references;
  }

  public void addKMValue(String kmValue) {
      kmValues.add(kmValue);
  }

  public List<String> getKMValues() {
      return kmValues;
  }

  public void addTurnoverNumber(String turnoverNumber) {
      turnoverNumbers.add(turnoverNumber);
  }

  public List<String> getTurnoverNumbers() {
      return turnoverNumbers;
  }
  
  public void addCloningData(Long organism, String notes, String reference) {
  	CloningExprData toAdd = new CloningExprData();
  	toAdd.notes = notes;
  	toAdd.organism = organism;
  	toAdd.reference = reference;
  	cloningData.add(toAdd);
  }
  
  public List<CloningExprData> getCloningData() {
  	return cloningData;
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
  public Long[] getOrganismIDs() { return organismIDs; } 
  public String getECNum() { return ecnum; }
  public String getReactionName() { return rxnName; }
  public ReactionType getType() { return type; }
  
  public List<Long> getOrganisms() { 
  	List<Long> orgIDs = new ArrayList<Long>();
  	for(EnzSeqData e: organismData) {
  		orgIDs.add(e.orgID);
  	}
  	return orgIDs;
  }
  
  public List<EnzSeqData> getOrganismData() { return organismData; }
  
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
    
	public List<String> getPreciseOrganismNames() {
		String desc = rxnName;
		// the organism name should be exactly as it is in the desc under {org1, org2, org3}, e.g., {Escherichia coli, Mycobacterium tuberculosis}
		int start = desc.indexOf('{');
		int end = desc.indexOf('}', start);
		String orgs = desc.substring(start + 1, end);
		List<String> names = Arrays.asList(orgs.split(", "));
		System.out.println("This reactions orgs:" + names + " -- desc: " + desc);
		return names;
	}
    
	public static String brenda_link(String ec, String org) {
		// Sample : http://brenda-enzymes.org/sequences/index.php4?sort=&restricted_to_organism_group=&f[TaxTree_ID_min]=0&f[TaxTree_ID_max]=0&f[stype_seq]=2&f[seq]=&f[limit_range]=10&f[stype_recom_name]=2&f[recom_name]=&f[stype_ec]=1&f[ec]=2.2.1.7&f[stype_accession_code]=2&f[accession_code]=&f[stype_organism]=1&f[organism]=Escherichia coli&f[stype_no_of_aa]=1&f[no_of_aa]=&f[stype_molecular_weight]=1&f[molecular_weight]=&f[stype_no_tmhs]=1&f[no_tmhs]=&Search=Search&f[limit_start]=0&f[nav]=&f[sort]=
		String url;
		try {
			url = "http://brenda-enzymes.org/sequences/index.php4?sort=" 
					+ "&" + "restricted_to_organism_group="
					+ "&" + "f[TaxTree_ID_min]=0" 
					+ "&" + "f[TaxTree_ID_max]=0"
					+ "&" + "f[stype_seq]=2"
					+ "&" + "f[seq]="
					+ "&" + "f[limit_range]=10"
					+ "&" + "f[stype_recom_name]=2"
					+ "&" + "f[recom_name]="
					+ "&" + "f[stype_ec]=1"
					+ "&" + "f[ec]=" + enc(ec)
					+ "&" + "f[stype_accession_code]=2"
					+ "&" + "f[accession_code]="
					+ "&" + "f[stype_organism]=1"
					+ "&" + "f[organism]=" + enc(org)
					+ "&" + "f[stype_no_of_aa]=1"
					+ "&" + "f[no_of_aa]="
					+ "&" + "f[stype_molecular_weight]=1"
					+ "&" + "f[molecular_weight]="
					+ "&" + "f[stype_no_tmhs]=1"
					+ "&" + "f[no_tmhs]="
					+ "&" + "Search=Search"
					+ "&" + "f[limit_start]=0"
					+ "&" + "f[nav]="
					+ "&" + "f[sort]=";
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			// just send the partial url back....
			url = "http://brenda-enzymes.org/sequences/index.php4?sort=" 
					+ "&" + "restricted_to_organism_group="
					+ "&" + "f[TaxTree_ID_min]=0"
					+ "&" + "f[TaxTree_ID_max]=0"
					+ "&" + "f[stype_seq]=2"
					+ "&" + "f[seq]="
					+ "&" + "f[limit_range]=10"
					+ "&" + "f[stype_recom_name]=2"
					+ "&" + "f[recom_name]="
					+ "&" + "f[stype_ec]=1"
					+ "&" + "f[ec]=" + ec
					+ "&" + "f[stype_accession_code]=2"
					+ "&" + "f[accession_code]="
					+ "&" + "f[stype_organism]=1";
		}
		return url;
		
	}

	private static String enc(String s) throws UnsupportedEncodingException {
		return URLEncoder.encode(s, "UTF-8");
	}
}
