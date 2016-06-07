package act.installer.bing;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.HashSet;

import com.mongodb.DBCollection;
import org.apache.logging.log4j.Logger;

public class MoleculeNames {

  private String inchi;
  private HashSet<String> brendaNames = new HashSet<>();
  private HashSet<String> metacycNames = new HashSet<>();
  private HashSet<String> drugbankNames = new HashSet<>();
  private HashSet<String> chebiNames = new HashSet<>();

  public MoleculeNames(String inchi) {
    this.inchi = inchi;
  }

  public String getInchi() {
    return inchi;
  }

  public HashSet<String> getBrendaNames() {
    return brendaNames;
  }

  public HashSet<String> getMetacycNames() {
    return metacycNames;
  }

  public HashSet<String> getDrugbankNames() {
    return drugbankNames;
  }

  public HashSet<String> getChebiNames() {
    return chebiNames;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  //public DBCursor fetchNamesFromCollection

  public void fetchNames(DBCollection mongoChemicalsCollection, Logger logger) {

    BasicDBObject whereQuery = new BasicDBObject();
    BasicDBObject fields = new BasicDBObject();
    fields.put("names.brenda", 1);
    fields.put("xref.CHEBI.metadata.Synonym", 1);
    fields.put("xref.DRUGBANK.metadata", 1);
    fields.put("xref.METACYC.meta", 1);

    whereQuery.put("InChI", inchi);
    DBCursor cursor = mongoChemicalsCollection.find(whereQuery, fields);

    if (cursor.hasNext()) {
      DBObject item = cursor.next();
      // BRENDA names
      BasicDBObject names = (BasicDBObject) item.get("names");
      BasicDBList brendaNamesList = (BasicDBList) names.get("brenda");
      if (brendaNamesList != null) {
        for(Object brendaName: brendaNamesList) {
          brendaNames.add((String) brendaName);
        }
      }
      // XREF
      BasicDBObject xref = (BasicDBObject) item.get("xref");
      if (xref != null) {
        // CHEBI
        BasicDBObject chebi = (BasicDBObject) xref.get("CHEBI");
        if (chebi != null) {
          BasicDBObject chebiMetadata = (BasicDBObject) chebi.get("metadata");
          BasicDBList chebiSynonymsList = (BasicDBList) chebiMetadata.get("Synonym");
          if (chebiSynonymsList != null) {
            for(Object chebiName: chebiSynonymsList) {
              chebiNames.add((String) chebiName);
            }
          }
        }
        // METACYC
        BasicDBObject metacyc = (BasicDBObject) xref.get("METACYC");
        if (metacyc != null) {
          BasicDBList metacycMetadata = (BasicDBList) metacyc.get("meta");
          if (metacycMetadata != null) {
            for(Object metaCycMeta : metacycMetadata) {
              BasicDBObject metaCycMetaDBObject = (BasicDBObject) metaCycMeta;
              metacycNames.add((String) metaCycMetaDBObject.get("sname"));
            }
          }
        }
        // DRUGBANK
        BasicDBObject drugbank = (BasicDBObject) xref.get("DRUGBANK");
        if (drugbank != null) {
          BasicDBObject drugbankMetadata = (BasicDBObject) drugbank.get("metadata");
          drugbankNames.add((String) drugbankMetadata.get("name"));
          BasicDBObject drugbankSynonyms = (BasicDBObject) drugbankMetadata.get("synonyms");
          BasicDBList drugbankSynonymsList = (BasicDBList) drugbankSynonyms.get("synonym");
          if (drugbankSynonymsList != null) {
            for(Object drugbankSynonym: drugbankSynonymsList) {
              drugbankNames.add((String) drugbankSynonym);
            }
          }
        }
      }
    }
    if (cursor.hasNext()) {
      logger.info("Duplicate entries have been found in the Installer DB corresponding to InChI: " + inchi);
      logger.info("Ignoring all but first one encountered");
    }
  }

  public void setBrendaNames(HashSet<String> brendaNames) {
    this.brendaNames = brendaNames;
  }

  public void setMetacycNames(HashSet<String> metacycNames) {
    this.metacycNames = metacycNames;
  }

  public void setDrugbankNames(HashSet<String> drugbankNames) {
    this.drugbankNames = drugbankNames;
  }

  public void setChebiNames(HashSet<String> chebiNames) {
    this.chebiNames = chebiNames;
  }
}
