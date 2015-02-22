package act.installer;


import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import act.client.CommandLineRun;
import act.shared.Chemical;
import act.shared.Chemical.REFS;
import act.shared.helpers.InchiMapKey;
import act.shared.helpers.LargeMap;
import act.shared.helpers.LargeMapKey;
import act.shared.helpers.NotSoLargeMap;

class Xref implements Serializable {
	private static final long serialVersionUID = 1L;
	HashMap<REFS, DBObject> json;
	
	Xref() {
		this.json = new HashMap<REFS, DBObject>();
	}

	public void add(REFS typ, DBObject doc) {
		this.json.put(typ, doc);
	}

	public boolean containsType(REFS typ) {
		return this.json.containsKey(typ);
	}

	public DBObject get(REFS typ) {
		return this.json.get(typ);
	}
}

public class ImportantChemicals {
	LargeMap<Xref> chems;
	Set<String> all_inchis;
	Set<String> done;
	
	ImportantChemicals() {
		this.done = new HashSet<String>();
		this.chems = new LargeMap<Xref>();
		this.all_inchis = new HashSet<String>();
	}

	public void parseAndAdd(String row) throws Exception {
		// We assume that the format is "DB_SRC\tDB_SPECIFIC_XREF\tInChI\tJSON_METADATA" 
		// DB_SRC has type Chemical.REFS
		
		String[] entry = row.split("\t", -2); // the neg limit means that it keeps the trailing empty strings
		REFS typ = null;
		String typ_str = entry[0], dbid = entry[1], inchi = entry[2], meta = entry[3];

		inchi = CommandLineRun.consistentInChI(inchi, "Important Chemicals"); // round trip inchi to make it consistent with the rest of the system
		DBObject doc = new BasicDBObject();
		
		try {
			typ = REFS.valueOf(typ_str);
		} catch (Exception e) {
			System.err.println("Invalid important chemicals row: " + row);
		}
		doc.put("dbid", dbid);
		doc.put("metadata", JSON.parse(meta));

		InchiMapKey large_inchi = new InchiMapKey(inchi);
		if (this.chems.containsKey(large_inchi)) {
			Xref xref = this.chems.get(large_inchi);
			if (xref.containsType(typ)) {
				// duplicate mapping... hmm... one needs to be ignored or they are redundant.
				// the only thing to check are dbid and metadata as the others are identical by construction
				DBObject o = xref.get(typ);
				if (o.get("dbid") != dbid || o.get("metadata") != meta) {
					// conflicting entry.. error message
					System.err.println("ImportantChemicals: conflicting entry! leaving the old one in there.");
				} else {
					// redundant entry.. do nothing
				}
			} else {
				xref.add(typ, doc); // does not already have a mapping. add indiscrimately
			}
		} else {
			Xref xref = new Xref();
			xref.add(typ, doc);
			this.chems.put(large_inchi, xref);
			this.all_inchis.add(inchi);
			System.out.format("Mapped %s to %s\n", inchi, xref.json);
		}
	}

	public void setRefs(Chemical c) throws Exception {
		String index = c.getInChI();
		InchiMapKey inchi = new InchiMapKey(index);
		if (this.chems.containsKey(inchi)) {
			// we have data on this node, so add that to the chem
			Xref ref = this.chems.get(inchi);
			for (REFS typ : ref.json.keySet()) 
				c.putRef(typ, ref.json.get(typ));
			System.out.println("Added ref to " + index);
		}
		this.done.add(index);
		return;
	}

	public Set<Chemical> remaining() throws Exception {
		// we keep a flag about which chemicals have been called setRefs on and which are not
		// For the ones that are not, we need to create a chemical using its 
		
		Set<Chemical> rem = new HashSet<Chemical>();
		for (String inchi : all_inchis) {
			if (done.contains(inchi))
				continue;
			Chemical c = new Chemical(inchi);
			setRefs(c);
			// the UUID here does not matter, since db.submitActChemicalToDB computes the next available uuid
			rem.add(c);
			System.out.println("Remaining " + inchi);
		}
		return rem;
	}

}
