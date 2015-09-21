package act.installer.kegg;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;

import act.server.SQLInterface.MongoDB;
import act.server.Search.ConfidenceMetric;
import act.server.Search.InitialSetGenerator;
import act.shared.Chemical;


public class BrendaKeggComparison {
  /*
  public static void compareEcoliModels() {
    MongoDB db = new MongoDB();
    ConfidenceMetric.hardConstraints.add("balance");
    ConfidenceMetric.hardConstraints.add("reversibility");
    System.out.println("constraints set");

    Set<String> unknownKeggIDs = new HashSet<String>();
    Set<Long> brendaModel = InitialSetGenerator.ecoli(db);
    Set<String> keggIDs = InitialSetGenerator.getKeggIDsFromFile("data/keggEco01100.xml", db);
    Set<Long> keggModel = InitialSetGenerator.getChemicalIDsOfKeggChemicalsFromFile("data/keggEco01100.xml", db, unknownKeggIDs);

    Set<Long> keggOnly = new HashSet<Long>(keggModel);
    Set<Long> brendaOnly = new HashSet<Long>(brendaModel);

    keggOnly.removeAll(brendaModel);
    brendaOnly.removeAll(keggModel);
    Set<String> keggIDsWInchis = KeggParser.getAllKeggIDsWInchis("data/keggCompound.inchi", db);
    System.out.println("<html>");
    System.out.println("<head><title>KEGG BRENDA comparison</title></head>");
    System.out.println("<body>");
    System.out.println("<h4>Counts of chemicals in our database</h4>");
    System.out.println("KEGG Ecoli map: " + keggModel.size() + " (total " + (keggModel.size() + unknownKeggIDs.size()) + ")<br>");
    System.out.println("BRENDA Ecoli expanded: " + brendaModel.size() + "<br>");
    System.out.println("KEGG Ecoli map only: " + keggOnly.size() + "<br>");
    System.out.println("BRENDA Ecoli expanded only: " + brendaOnly.size() + "<br>");

    System.out.println("<h4>In KEGG Ecoli map and in our database but not in Ecoli expanded set</h4>");
    for (Long k : keggOnly) {
      Chemical chemical = db.getChemicalFromChemicalUUID(k);
      DBObject o = (DBObject) chemical.getRef(Chemical.REFS.KEGG);
      BasicDBList ids = (BasicDBList) o.get("id");
      for (Object id : ids) {
        String s = (String) id;
        if (keggIDs.contains(s))
          printKeggChemicalLink(s);
      }
    }

    System.out.println();
    System.out.println("<h4>In KEGG Ecoli map only (with inchi)</h4>");
    for (String s : unknownKeggIDs) {
      if (keggIDsWInchis.contains(s))
        printKeggChemicalLink(s);
    }

    System.out.println();
    System.out.println("<h4>In BRENDA Ecoli expanded only</h4>");
    for (Long id : brendaOnly) {
      System.out.println("<a href=\"http://act.berkeley.edu:28000/getChemImage?chemid=" + id + "\">" + db.getShortestName(id) + "</a>");
    }
    System.out.println("</body>");
    System.out.println("</html>");
  }

  private static void printKeggChemicalLink(String s) {
    if (s.contains("G"))
      System.out.println("<a href=\"http://www.kegg.jp/dbget-bin/www_bget?gl:" + s + "\">" + s + "</a>");
    else
      System.out.println("<a href=\"http://www.kegg.jp/dbget-bin/www_bget?cpd:" + s + "\">" + s + "</a>");
  }

  public static void main(String[] args) {
    compareEcoliModels();
  }
  */
}
