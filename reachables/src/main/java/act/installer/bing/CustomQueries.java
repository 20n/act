package act.installer.bing;

import act.server.MongoDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.act.utils.TSVWriter;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;



/**
 * Created by tom on 6/6/16.
 */
public class CustomQueries {

  private static ObjectMapper mapper = new ObjectMapper();
  static final private String SHIKIMATE_CLADE_INCHIS = "src/main/resources/Inchis_shikimate.txt";

  public String parseInchi(BasicDBObject c) {
    String inchi = (String) c.get("InChI");
    return inchi;
  }

  public Long parseCount(BasicDBObject c) {
    Long totalCountSearchResults = (Long) c.get("total_count_search_results");
    return totalCountSearchResults;
  }

  public String parseName(BasicDBObject c) {
    String bestName = (String) c.get("best_name");
    return bestName;
  }

  public HashSet<String> parseNames(BasicDBObject c) {
    BasicDBObject names = (BasicDBObject) c.get("names");
    BasicDBList brendaNamesList = (BasicDBList) names.get("brenda");
    HashSet<String> allNames = new HashSet<>();
    if (brendaNamesList != null) {
      for(Object brendaName: brendaNamesList) {
        allNames.add((String) brendaName);
      }
    }
    // XREF
    BasicDBObject xref = (BasicDBObject) c.get("xref");
    if (xref != null) {
      // CHEBI
      BasicDBObject chebi = (BasicDBObject) xref.get("CHEBI");
      if (chebi != null) {
        BasicDBObject chebiMetadata = (BasicDBObject) chebi.get("metadata");
        BasicDBList chebiSynonymsList = (BasicDBList) chebiMetadata.get("Synonym");
        if (chebiSynonymsList != null) {
          for (Object chebiName : chebiSynonymsList) {
            allNames.add((String) chebiName);
          }
        }
      }
      // METACYC
      BasicDBObject metacyc = (BasicDBObject) xref.get("METACYC");
      if (metacyc != null) {
        BasicDBList metacycMetadata = (BasicDBList) metacyc.get("meta");
        if (metacycMetadata != null) {
          for (Object metaCycMeta : metacycMetadata) {
            BasicDBObject metaCycMetaDBObject = (BasicDBObject) metaCycMeta;
            allNames.add((String) metaCycMetaDBObject.get("sname"));
          }
        }
      }
      // DRUGBANK
      BasicDBObject drugbank = (BasicDBObject) xref.get("DRUGBANK");
      if (drugbank != null) {
        BasicDBObject drugbankMetadata = (BasicDBObject) drugbank.get("metadata");
        allNames.add((String) drugbankMetadata.get("name"));
        BasicDBObject drugbankSynonyms = (BasicDBObject) drugbankMetadata.get("synonyms");
        if (drugbankSynonyms.get("synonym") instanceof String) {
          allNames.add((String) drugbankSynonyms.get("synonym"));
        } else {
          BasicDBList drugbankSynonymsList = (BasicDBList) drugbankSynonyms.get("synonym");
          if (drugbankSynonymsList != null) {
            for (Object drugbankSynonym : drugbankSynonymsList) {
              allNames.add((String) drugbankSynonym);
            }
          }
        }
      }
    }
    return allNames;
  }


  public static void main(final String[] args) throws IOException {

    MongoDB db = new MongoDB("localhost", 27717, "actv01");
    CustomQueries customQueries = new CustomQueries();

    BingSearcher bingSearcher = new BingSearcher();
    DBCursor cursor = db.fetchNamesAndBingInformation();

    List<String> headers = new ArrayList<>();
    headers.add("inchi");
    headers.add("best_name");
    headers.add("total_count_search_results");
    headers.add("names_list");

    TSVWriter tsvWriter = new TSVWriter(headers);
    tsvWriter.open(new File("src/ranker_shikimate_molecules.tsv"));

    HashSet<String> shikimateCladeInchis = bingSearcher.getStringSetFromFile(SHIKIMATE_CLADE_INCHIS);

    while (cursor.hasNext()) {
      BasicDBObject o = (BasicDBObject) cursor.next();
      String inchi = customQueries.parseInchi(o);
      if (shikimateCladeInchis.contains(inchi)) {
        Map<String, String> row = new HashMap<>();
        System.out.println(customQueries.parseInchi(o));
        row.put("inchi", inchi);
        BasicDBObject g = (BasicDBObject) o.get("xref");
        BasicDBObject h = (BasicDBObject) g.get("BING");
        BasicDBObject j = (BasicDBObject) h.get("metadata");
        row.put("best_name", customQueries.parseName(j));
        row.put("total_count_search_results", customQueries.parseCount(j).toString());
        row.put("names_list", mapper.writeValueAsString(customQueries.parseNames(o)));
        tsvWriter.append(row);
      }
    }

  }
}
