package act.shared.helpers;

import java.io.FileInputStream;
import java.util.Stack;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

public class XMLToImportantChemicals {
	private static final String strTag = "____txt_";
	private String xmlfile;
	private String rowTag;
	private String idTag;
	private String chemTag;
	private String DBS;

	public XMLToImportantChemicals(String f, String DBS, String rowTag, String idTag, String chemTag) {
		this.xmlfile = f;
		this.rowTag = rowTag;
		this.idTag = idTag;
		this.chemTag = chemTag;
		this.DBS = DBS;
	}

	public void process() {
		try {
			FileInputStream fileInputStream = new FileInputStream(this.xmlfile);
			XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(fileInputStream);
			process(xml);
		} catch (Exception ex) {
			ex.printStackTrace();
			System.out.println("XML Reading error:" + this.xmlfile);
			System.exit(-1);
		}
	}

	private void process(XMLStreamReader xml) throws XMLStreamException {  
        String tag;
        String root = null;
        Stack<DBObject> json = new Stack<DBObject>();
        DBObject js;
        while (xml.hasNext()) {
	        int eventType = xml.next();
	        while (xml.isWhiteSpace() || eventType == XMLEvent.SPACE)
	            eventType = xml.next();
	        
	        switch (eventType) {
	            case XMLEvent.START_ELEMENT:
	                tag = xml.getLocalName();
	                if (root == null) {
	                	root = tag;
	                } else {
	                	json.push(new BasicDBObject());
	                }
	                break;
	            case XMLEvent.END_ELEMENT:
	                tag = xml.getLocalName();
	                if (tag.equals(root)) {
	                	// will terminate in next iteration
	                } else {
	                	js = json.pop();
	            		if (json.size() == 0) {
	            			if (tag.equals(rowTag))
	            				printEntry(js);
	            			else
	            				printUnwantedEntry(js);
	            		} else {
		            		putListStrOrJSON(json.peek(), tag, js);
		                }
	                }
	                break;
	                
	            case XMLEvent.CHARACTERS:
	                String txt = xml.getText();
	                js = json.peek();
	                if (js.containsField(strTag)) {
	                	txt = js.get(strTag) + txt;
	                	js.removeField(strTag);
	                }
	                js.put(strTag, txt);
	                break;

	            case XMLEvent.START_DOCUMENT:
	                break;
	            case XMLEvent.END_DOCUMENT:
	                break;
	            case XMLEvent.COMMENT:
	            case XMLEvent.ENTITY_REFERENCE:
	            case XMLEvent.ATTRIBUTE:
	            case XMLEvent.PROCESSING_INSTRUCTION:
	            case XMLEvent.DTD:
	            case XMLEvent.CDATA:
	            case XMLEvent.SPACE:
	    	    	System.out.format("%s --\n", eventType);
	                break;
	        }
        }
	}

	private void putListStrOrJSON(DBObject json, String tag, DBObject toAdd) {
		// if it is a string add it unencapsulated
		if (toAdd.keySet().size() == 1 && toAdd.containsField(strTag))
			putElemOrList(json, tag, toAdd.get(strTag));
		else
			putElemOrList(json, tag, toAdd);
	}

	private void putElemOrList(DBObject json, String tag, Object add) {
		// if tag already present then make it a list
		if (json.containsField(tag)) {
			BasicDBList l;
			Object already = json.get(tag);
			if (already instanceof BasicDBList) {
				l = (BasicDBList) already;
			} else {
				l = new BasicDBList();
				l.add(already);
			}
			l.add(add);
			json.removeField(tag);
			json.put(tag, l);
			return;
		}
		
		// else, just add as is
		json.put(tag, add);
		return;
	}

	private void printEntry(DBObject json) {
		Object ido = json.get(idTag);
		Object caso = json.get(chemTag);
		String id = ido instanceof String ? (String) ido : ""; 
		String cas = caso instanceof String ? (String) caso : "";
		String inchi = getInchi(json);
		if (inchi != null)
			System.out.format("%s\t%s\t%s\t%s\n", this.DBS, id, inchi, JSON.serialize(json));
		else
			System.out.format("%s\t%s\t%s\t%s\n", this.DBS, id, cas, JSON.serialize(json));
	}

	private String getInchi(DBObject json) {
		// under calculated-properties.property.[{kind=InChI, value=<inchi>}]
		DBObject o; 
		if (json.containsField("calculated-properties")) {
			o = (DBObject) json.get("calculated-properties");
			if (o.containsField("property")) {
				o = (DBObject) o.get("property");
				if (o instanceof BasicDBList) {
					for (Object kv : (BasicDBList)o) {
						o = (DBObject) kv;
						if (o.containsField("kind") && o.get("kind").equals("InChI") && o.containsField("value")) {
							return (String)o.get("value");
						}
					}
				}
			}
		}
		return null;
	}

	private void printUnwantedEntry(DBObject json) {
		System.err.format("%s\t%s\t%s\t%s\n", "UNKNOWN", "ID?", "CAS?", JSON.serialize(json));
	}

}
