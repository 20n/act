package act.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.shared.Chemical.REFS;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class Chemical implements Serializable {
	private static final long serialVersionUID = 42L;
	public Chemical() { /* default constructor for serialization */ }
	
	private Long uuid, pubchem_id;
    private String canon, smiles, inchi, inchiKey;
    private boolean isCofactor, isNative;
    public enum REFS { WIKIPEDIA, KEGG_DRUG, SIGMA, HSDB, DRUGBANK, WHO, SIGMA_POLYMER, PUBCHEM_TOX, TOXLINE, DEA, ALT_PUBCHEM, pubmed, genbank, KEGG}
    private HashMap<REFS, DBObject> refs;
    private Double estimatedEnergy;
    
    private Map<String,String[]> names; //pubchem names (type,name)
    private List<String> synonyms; //more pubchem synonyms
    private List<String> brendaNames; //names used in brenda
    
    /*
     * If storing to db, this uuid will be ignored. 
     */
    public Chemical(Long uuid) {
    	this.uuid = uuid;
        this.isCofactor = false;
        this.refs = new HashMap<REFS, DBObject>();
    	names = new HashMap<String,String[]>();
    	synonyms = new ArrayList<String>();
    	brendaNames = new ArrayList<String>();
    }
    
    public Chemical(String inchi) {
    	this((long) -1);
    	this.setInchi(inchi);
        this.isCofactor = false;
        this.refs = new HashMap<REFS, DBObject>();
        // deliberately do not map typ's to empty strings
        // null values should be checked for
        //  for (REFS typ : REFS.values())
        // 	this.refs.put(typ, "");
    }
    
    public Chemical(long uuid, Long pubchem_id, String canon, String smiles) {
    	this(uuid);
        this.pubchem_id = pubchem_id;
        this.canon = canon;
        this.smiles = smiles;
        this.inchi = null;
        this.isCofactor = false;
        this.refs = new HashMap<REFS, DBObject>();
        // deliberately do not map typ's to empty strings
        // null values should be checked for
        // for (REFS typ : REFS.values())
        //	this.refs.put(typ, new BasicDBObject());
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
		System.err.println();
		Chemical c = new Chemical(this.uuid, this.pubchem_id, this.canon, this.smiles);
		c.setInchi(this.inchi);
		c.setInchiKey(this.inchiKey);
		
		/*
		 * merge the following fields:
		 * 
	    isCofactor, isNative;
	    HashMap<REFS, DBObject> refs;
	    
	    Map<String,String[]> names;
	    List<String> synonyms;
	    List<String> brendaNames;
		
		*/
		if (this.isCofactor || x.isCofactor) c.setAsCofactor();
		if (this.isNative || x.isNative) c.setAsNative();

    	c.refs = new HashMap<REFS, DBObject>(this.refs);
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
		
    	// if canonical name and pubchem_id are different then add them as an ALT PUBCHEM
    	boolean inchiKeySame = x.inchiKey != null && this.inchiKey != null && x.inchiKey.equals(this.inchiKey);
		boolean canonSame = x.canon != null && this.canon != null && x.canon.equals(this.canon);
		boolean smilesSame = x.smiles != null && this.smiles != null && x.smiles.equals(this.smiles);
		boolean pubchemSame = x.pubchem_id == this.pubchem_id;
		if (canonSame && pubchemSame && smilesSame && inchiKeySame)
			return c;
		
		DBObject entry = new BasicDBObject();
		entry.put("canonical", x.canon);
		entry.put("pubchem", x.pubchem_id);
		entry.put("smiles", x.smiles);
		entry.put("inchiKey", x.inchiKey);
		BasicDBList altPubchemList;
		if (c.refs.containsKey(REFS.ALT_PUBCHEM))
			altPubchemList = (BasicDBList)x.refs.get(REFS.ALT_PUBCHEM);
		else
			c.refs.put(REFS.ALT_PUBCHEM, altPubchemList = new BasicDBList());
		altPubchemList.add(entry);
		
		System.err.format("\n\n\n\n\n\n ALT PUBCHEM on: %s \n\n\n\n\n\n", this.inchi);
    	
		return c;
	}
    
    public void putRef(REFS typ, DBObject entry) {
    	this.refs.put(typ, entry);
    }
    
    /*
     * Add pubchem names. Pubchem categorizes names as:
     * 	Allowed
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
    
    public void addSynonym(String syn) { synonyms.add(syn); }
    public void addBrendaNames(String name) { brendaNames.add(name); }
    public void setCanon(String canon) { this.canon = canon; }
    public void setPubchem(Long pubchem) { this.pubchem_id = pubchem; }
    public void setSmiles(String s) { smiles = s; }
    public void setInchi(String s) { inchi = s; };
    public void setInchiKey(String s) { inchiKey = s; }
    public void setAsCofactor() { this.isCofactor = true; }
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

	public Object getRef(REFS type) {
		return this.refs.get(type);
	}

	public Object getRef(REFS type, String[] xpath) {
		DBObject o = this.refs.get(type);
		if (o == null)
			return null;
		for (int i = 0; i < xpath.length; i++) {
			if (!o.containsField(xpath[i]))
				return null;
			if (i == xpath.length - 1) {
				// need to return this object, irrespective of whether it is a DBObject or not
				return o.get(xpath[i]); 
			} else {
				o = (DBObject) o.get(xpath[i]);
				if (o == null)
					return null;
			}
		}
		return null; // unreachable
	}

	public Double getRefMetric(REFS typ) {
		if (typ == REFS.SIGMA) {
			// for sigma the metric we have is price...
			DBObject d = (DBObject)((DBObject)this.refs.get(REFS.SIGMA)).get("metadata");
			if (d.containsField("price")) {
				Double price = Double.parseDouble((String)d.get("price"));
				if (d.containsField("gramquant")) {
					Double perGramPrice = price / Double.parseDouble((String)d.get("gramquant"));
					return (double)Math.round(perGramPrice * 100) / 100; 
				} else {
					// sometimes entries are liquid sizes, e.g., "quantity" : "1ML" , "cas" : "64-04-0" , "price" : "17.80"
					// and then they do not have the gramquant field associated with them.
					return price + 0.000009999; // tag these cases so that at least we can identify them in the UI 
				}
			}
		} else if (typ == REFS.DRUGBANK) {
			DBObject d = (DBObject)((DBObject)this.refs.get(REFS.DRUGBANK)).get("metadata");
			if (d.containsField("prices")) {
				d = (DBObject)d.get("prices");
				if (d.containsField("price")) {
					d = (DBObject)d.get("price");
					if (d instanceof BasicDBList) {
						// more than one price entry
						Double max = Double.NEGATIVE_INFINITY, cost;
						for (Object o : (BasicDBList)d) {
							if (((DBObject)o).containsField("cost")) {
								cost = Double.parseDouble((String)((DBObject)o).get("cost"));
								max = max < cost ? cost : max;
							}
						}
						if (max != Double.NEGATIVE_INFINITY)
							return max;
					} else if (d.containsField("cost")) {
						// since entry and it contains cost field
						return Double.parseDouble((String)d.get("cost"));
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
    
	public static Set<Long> getChemicalIDs(Collection<Chemical> chemicals) {
		Set<Long> result = new HashSet<Long>();
		for (Chemical chemical : chemicals) {
			result.add(chemical.getUuid());
		}
		return result;
	}
}
