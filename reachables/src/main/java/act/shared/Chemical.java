package act.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;

import org.json.JSONArray;
import org.json.JSONObject;

public class Chemical implements Serializable {
  private static final long serialVersionUID = 42L;
  public Chemical() { /* default constructor for serialization */ }

  private Long uuid, pubchem_id;
  private String canon, smiles, inchi, inchiKey;
  private boolean isCofactor, isNative;
  public enum REFS { WIKIPEDIA, KEGG_DRUG, SIGMA, HSDB, DRUGBANK, WHO, SIGMA_POLYMER, PUBCHEM_TOX, TOXLINE, DEA, ALT_PUBCHEM, CHEBI, pubmed, genbank, KEGG, METACYC, BRENDA, CURATED }
  private HashMap<REFS, JSONObject> refs;
  private Double estimatedEnergy;

  private Map<String,String[]> names; //pubchem names (type,name)
  private List<String> synonyms; //more pubchem synonyms
  private List<String> brendaNames; //names used in brenda

  private Set<String> keywords;
  private Set<String> caseInsensitiveKeywords;
  private List<Integer> ros = new ArrayList<>();

  private Integer chemspider_id = -1;
  private Integer chemspider_num_unique_vendors = -1;
  private JSONArray chemspider_vendor_xrefs = new JSONArray();

  /*
   * If storing to db, this uuid will be ignored.
   */
  public Chemical(Long uuid) {
    this.uuid = uuid;
    this.isCofactor = false;
    this.refs = new HashMap<REFS, JSONObject>();
    this.names = new HashMap<String,String[]>();
    this.synonyms = new ArrayList<String>();
    this.brendaNames = new ArrayList<String>();
    this.keywords = new HashSet<String>();
    this.caseInsensitiveKeywords = new HashSet<String>();
  }

  public Chemical(String inchi) {
    this((long) -1);
    this.setInchi(inchi); // this also sets the inchiKey
    this.isCofactor = false;
    this.refs = new HashMap<REFS, JSONObject>();
    // deliberately do not map typ's to empty strings
    // null values should be checked for
    // for (REFS typ : REFS.values())
    //  this.refs.put(typ, "");
  }

  public Chemical(long uuid, Long pubchem_id, String canon, String smiles) {
    this(uuid);
    this.pubchem_id = pubchem_id;
    this.canon = canon;
    this.smiles = smiles;
    this.inchi = null;
    this.isCofactor = false;
    this.refs = new HashMap<REFS, JSONObject>();
  }

  public Chemical createNewByMerge(Chemical x) {
    /* x.uuid can be different from this.uuid as they are system generated */
    boolean consistent = (x.inchi != null && this.inchi != null && x.inchi.equals(this.inchi));
    if (!consistent)
      return null;

    // now create a copy
    System.err.format("-- new merged copy from %s/", this.uuid);
    System.err.format("%s/", this.pubchem_id);
    System.err.format("%s/", this.canon);
    System.err.format("%s/", this.smiles);
    System.err.format("%s/", this.inchi);
    System.err.println();
    Chemical c = new Chemical(this.uuid, this.pubchem_id, this.canon, this.smiles);
    c.setInchi(this.inchi);

    /*
     * merge the following fields:
     *
      isCofactor, isNative;
      HashMap<REFS, JSONObject> refs;

      Map<String,String[]> names;
      List<String> synonyms;
      List<String> brendaNames;

    */
    if (this.isCofactor || x.isCofactor) c.setAsCofactor();
    if (this.isNative || x.isNative) c.setAsNative();

    c.refs = new HashMap<REFS, JSONObject>(this.refs);
    for (REFS typ : x.refs.keySet())
      if (!c.refs.containsKey(typ))
        c.refs.put(typ, x.refs.get(typ));

    c.names = new HashMap<String,String[]>(this.names);
    c.synonyms = new ArrayList<String>(this.synonyms);
    c.brendaNames = new ArrayList<String>(this.brendaNames);

    for (String n : x.names.keySet())
      if (!c.names.containsKey(n))
        c.names.put(n, x.names.get(n));
    for (String n : x.synonyms)
      if (!c.synonyms.contains(n))
        c.synonyms.add(n);
    for (String n : x.brendaNames)
      if (!c.brendaNames.contains(n))
        c.brendaNames.add(n);

    for (String k : this.getKeywords())
      c.addKeyword(k);

    for (String k : this.getCaseInsensitiveKeywords())
      c.addCaseInsensitiveKeyword(k);

    c.setChemSpiderID(this.getChemSpiderID());
    c.setChemSpiderNumUniqueVendors(this.getChemSpiderNumUniqueVendors());
    c.setChemSpiderVendorXrefs(this.getChemSpiderVendorXrefs());

    // if canonical name and pubchem_id are different then add them as an ALT PUBCHEM
    boolean inchiKeySame = x.inchiKey != null && this.inchiKey != null && x.inchiKey.equals(this.inchiKey);
    boolean canonSame = x.canon != null && this.canon != null && x.canon.equals(this.canon);
    boolean smilesSame = x.smiles != null && this.smiles != null && x.smiles.equals(this.smiles);
    boolean pubchemSame = x.pubchem_id == this.pubchem_id;
    if (canonSame && pubchemSame && smilesSame && inchiKeySame)
      return c;

    JSONObject entry = new JSONObject();
    entry.put("canonical", x.canon);
    entry.put("pubchem", x.pubchem_id);
    entry.put("smiles", x.smiles);
    entry.put("inchiKey", x.inchiKey);
    JSONObject altPubchemList;
    if (c.refs.containsKey(REFS.ALT_PUBCHEM))
      altPubchemList = x.refs.get(REFS.ALT_PUBCHEM);
    else
      c.refs.put(REFS.ALT_PUBCHEM, altPubchemList = new JSONObject());
    altPubchemList.put("alt_pubchem", entry);

    System.err.format("ALT PUBCHEM on: %s\n", this.inchi);

    return c;
  }

  public Set<String> getKeywords() { return this.keywords; }
  public void addKeyword(String k) { this.keywords.add(k); }
  public Set<String> getCaseInsensitiveKeywords() { return this.caseInsensitiveKeywords; }
  public void addCaseInsensitiveKeyword(String k) { this.caseInsensitiveKeywords.add(k); }

  public void putRef(REFS typ, JSONObject entry) {
    this.refs.put(typ, entry);
  }

  /*
   * Add pubchem names. Pubchem categorizes names as:
   *   Allowed
   *  Preferred
   *  Traditional
   *  Systematic
   *  CAS-like Style
   * Arbitrarily using the first "Preferred" name as our canonical.
   */
  public void addNames(String type, String[]names) {
    if(type.equals("Preferred") && names.length > 0) {
      setCanon(names[0]);
    }
    this.names.put(type, names);
  }

  public void addSynonym(String syn) { synonyms.add(syn.toLowerCase()); }
  public void addBrendaNames(String name) { brendaNames.add(name); }
  public void addSubstructureRoId(Integer id) { ros.add(id); }
  public List<Integer> getSubstructureRoIds() { return ros; }
  public void setCanon(String canon) { this.canon = canon; }
  public void setPubchem(Long pubchem) { this.pubchem_id = pubchem; }
  public void setChemSpiderID(Integer csid) { this.chemspider_id = (csid == null ? -1 : csid); }
  public void setChemSpiderNumUniqueVendors(Integer n) { this.chemspider_num_unique_vendors = (n == null ? -1 : n); }
  public void setChemSpiderVendorXrefs(JSONArray v) { this.chemspider_vendor_xrefs = (v == null ? new JSONArray() : v); }
  public void setSmiles(String s) { smiles = s; }
  public void setInchi(String s) {
    this.inchi = s;

    // compute the inchikey and install it as well.
    // but make an exception for:
    // 1. big molecules and abstractions that have a fake inchi, (from metacyc and metacyc)
    // 2. corrupt inchis (from wikipedia mining)
    // 3. big molecules and abstraction with no inchi (from kegg)
    if (!s.startsWith("InChI=/FAKE/METACYC")   // 1.
        && !s.startsWith("InChI=/FAKE/BRENDA") // 1.
        && !s.startsWith("InChI'('")           // 2.
        && !s.startsWith("InChI1'('")          // 2.
        && !s.startsWith("none")               // 3.
        ) {
      try {
        String key = new IndigoInchi(new Indigo()).getInchiKey(inchi);
        this.inchiKey = key;
      } catch(Exception e) {
        System.out.println("Failed to compute InChIKey for: " + inchi);
      }
    }

  };
  // TODO: remove this when safe to do so since we can explicitly specify a value with setIsCofactor.
  public void setAsCofactor() { this.isCofactor = true; }
  public void setIsCofactor(boolean isCofactor) { this.isCofactor = isCofactor; }
  public void setAsNative() { this.isNative = true; }
  public void setEstimatedEnergy(Double e) { this.estimatedEnergy = e; }

  public Long getUuid() { return uuid; }

  /*
   * Should be null if no pubchem entry.
   */
  public Long getPubchemID() { return pubchem_id; }

  /*
   * Canonical name can be null.
   * Happens when no pubchem "Preferred" name or no pubchem entry.
   */
  public String getCanon() { return canon; }

  /*
   * Returns null only if bad InChI.
   */
  public String getSmiles() {
    return smiles;
  }

  public boolean isCofactor() {
    return this.isCofactor;
  }

  public boolean isNative() {
    return this.isNative;
  }

  public String getInChIKey() {
    return inchiKey;
  }

  public JSONObject getRef(REFS type) {
    return this.refs.get(type);
  }

  public Object getRef(REFS type, String[] xpath) {
    JSONObject o = this.refs.get(type);
    if (o == null)
      return null;
    for (int i = 0; i < xpath.length; i++) {
      if (!o.has(xpath[i]))
        return null;
      if (i == xpath.length - 1) {
        // need to return this object, irrespective of whether it is a Object or not
        return o.get(xpath[i]);
      } else {
        o = (JSONObject) o.get(xpath[i]);
        if (o == null)
          return null;
      }
    }
    return null; // unreachable
  }

  public Double getRefMetric(REFS typ) {
    if (typ == REFS.SIGMA) {
      // for sigma the metric we have is price...
      JSONObject d = (JSONObject)((JSONObject)this.refs.get(REFS.SIGMA)).get("metadata");
      if (d.has("price")) {
        Double price = null;
        try {
          price = Double.parseDouble((String)d.get("price"));
        } catch (NumberFormatException e) {
          // there is 1 entry that wierdly has "price" : "price" in the DB
          // xref.WIKIPEDIA.dbid: http://en.wikipedia.org/wiki/Castanospermine
          // InChI=1S/C8H15NO4/c10-4-1-2-9-3-5(11)7(12)8(13)6(4)9/h4-8,10-13H,1-3H2/t4-,5-,6+,7+,8+/m0/s1
          return null;
        }
        if (d.has("gramquant")) {
          Double perGramPrice = price / Double.parseDouble((String)d.get("gramquant"));
          return (double)Math.round(perGramPrice * 100) / 100;
        } else {
          // sometimes entries are liquid sizes, e.g., "quantity" : "1ML" , "cas" : "64-04-0" , "price" : "17.80"
          // and then they do not have the gramquant field associated with them.
          return price + 0.000009999; // tag these cases so that at least we can identify them in the UI
        }
      }
    } else if (typ == REFS.DRUGBANK) {
      JSONObject d = (JSONObject)((JSONObject)this.refs.get(REFS.DRUGBANK)).get("metadata");
      if (d.has("prices")) {
        JSONObject prices = (JSONObject)d.get("prices");
        if (prices.has("price")) {
          Object price = prices.get("price");
          if (price instanceof JSONArray) {
            // more than one price entry
            Double max = Double.NEGATIVE_INFINITY, cost;
            for (int i = 0; i < ((JSONArray)price).length(); i++) {
              JSONObject o = (JSONObject)((JSONArray)price).get(i);
              if (o.has("cost")) {
                cost = Double.parseDouble(o.getString("cost"));
                max = max < cost ? cost : max;
              }
            }
            if (max != Double.NEGATIVE_INFINITY)
              return max;
          } else if (((JSONObject)price).has("cost")) {
            // since entry and it contains cost field
            return Double.parseDouble((String)((JSONObject)price).get("cost"));
          }
        }
      }
    }
    return null; // no reasonable metric known
  }

  /*
   * If reading from db, this should never return null.
   */
  public String getInChI() { return inchi; }

  public Integer getChemSpiderID() { return this.chemspider_id; }
  public Integer getChemSpiderNumUniqueVendors() { return this.chemspider_num_unique_vendors; }
  public JSONArray getChemSpiderVendorXrefs() { return this.chemspider_vendor_xrefs; }

  public List<String> getSynonyms() { return synonyms; }
  public List<String> getBrendaNames() { return brendaNames; }

  public Map<String, String[]> getPubchemNames() {
    return names;
  }

  public String[] getPubchemNames(String type) {
    return names.get(type);
  }
  public Set<String> getPubchemNameTypes() {
    return names.keySet();
  }

  public String getShortestName() {
    String shortest = canon;
    for (String s : synonyms) {
      if (shortest == null || s.length() < shortest.length()) {
        shortest = s;
      }
    }
    for(String s : brendaNames) {
      if (shortest == null || s.length() < shortest.length()) {
        shortest = s;
      }
    }
    return shortest;
  }

  public String getShortestBRENDAName() {
    String shortest = canon;
    for(String s : brendaNames) {
      if (shortest == null || s.length() < shortest.length()) {
        shortest = s;
      }
    }
    return shortest;
  }

  public String getFirstName() {
    String first = "no_name";
    if (brendaNames.size() != 0)
      first = brendaNames.get(0);
    else if (synonyms.size() != 0)
      first = synonyms.get(0);
    return first;
  }

  //TODO: incomplete
  public String getFewestAlphaName() {
    String shortest = canon;
    String shortestAlpha = new String(canon);
    shortestAlpha.replaceAll("[0-9]", "");
    for (String s : synonyms) {
      if (shortest == null || s.length() < shortest.length()) {
        shortest = s;
      }
    }
    for(String s : brendaNames) {
      if (shortest == null || s.length() < shortest.length()) {
        shortest = s;
      }
    }
    return shortest;
  }

  public Double getEstimatedEnergy() { return estimatedEnergy; }


  @Override
  public String toString() {
    return "UUID: " + uuid +
        " \n PubchemID: " + pubchem_id +
        " \n Canon: " + canon +
        " \n Smiles: " + getSmiles() +
        " \n InChI: " + inchi +
        " \n InChIKey: " + getInChIKey();
  }

  public String toStringDetail() {
    return "ID: " + uuid +
        " \n PubchemID: " + pubchem_id +
        " \n Canon: " + canon +
        " \n Smiles: " + getSmiles() +
        " \n InChI: " + inchi +
        " \n InChIKey: " + getInChIKey() +
        " \n Names: " + names + "; " + synonyms + "; " + brendaNames +
        " \n Refs: " + refs +
        " \n IsCofactor, IsNative: " + isCofactor + ", " + isNative +
        " \n EstimatedEnergy: " + estimatedEnergy;
  }

  public static Set<Long> getChemicalIDs(Collection<Chemical> chemicals) {
    Set<Long> result = new HashSet<Long>();
    for (Chemical chemical : chemicals) {
      result.add(chemical.getUuid());
    }
    return result;
  }
}
