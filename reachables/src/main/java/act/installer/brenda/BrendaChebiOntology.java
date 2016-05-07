package act.installer.brenda;

import org.apache.commons.lang3.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class BrendaChebiOntology {

  public static class ChebiRelationship {
    public static final String QUERY = StringUtils.join(new String[]{
        "select",
        " id_go,",
        " rel_id_go,",
        " type",
        "from ontology_chebi_Relations",
        "where type = ?"
    }, " ");

    private String chebiId;
    private String parentChebiId;

    public ChebiRelationship(String chebiId, String parentChebiId) {
      this.chebiId = chebiId;
      this.parentChebiId = parentChebiId;
    }

    public String getChebiId() {
      return chebiId;
    }

    public String getParentChebiId() {
      return parentChebiId;
    }


    public static ChebiRelationship fromResultSet(ResultSet resultSet) throws SQLException {
      return new ChebiRelationship(
          resultSet.getString(1),
          resultSet.getString(2));
    }
  }

  public static class ChebiOntology {
    public static final String QUERY = StringUtils.join(new String[]{
        "select",
        "  t.id_go,",
        "  t.term,",
        "  d.definition",
        "from ontology_chebi_Terms t, ontology_chebi_Definitions d",
        "where t.id_go == d.ig_go"
    }, " ");

    private String chebiId;
    private String term;
    private String definition;

    public ChebiOntology(String chebiId, String term, String definition) {
      this.chebiId = chebiId;
      this.term = term;
      this.definition = definition;
    }

    public String getChebiId() {
      return this.chebiId;
    }

    public String getTerm() {
      return this.term;
    }

    public String getDefinition() {
      return this.definition;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ChebiOntology that = (ChebiOntology) o;
      return (chebiId != null) ? chebiId.equals(that.chebiId) : (that.chebiId == null);
    }

    @Override
    public int hashCode() {
      int result = chebiId.hashCode();
      return result;
    }

    public static ChebiOntology fromResultSet(ResultSet resultSet) throws SQLException {
      return new ChebiOntology(
          resultSet.getString(1),
          resultSet.getString(2),
          resultSet.getString(3));
    }
  }

  public static class ChebiApplicationSet {

    private HashSet<ChebiOntology> directApplications;
    private HashSet<ChebiOntology> mainApplications;

    public ChebiApplicationSet(HashSet<ChebiOntology> directApplications,
                               HashSet<ChebiOntology> mainApplications) {
      this.directApplications = directApplications;
      this.mainApplications = mainApplications;
    }

    public HashSet<ChebiOntology> getMainApplications() {
      return mainApplications;
    }

    public HashSet<ChebiOntology> getDirectApplications() {
      return directApplications;
    }
  }

  public static HashMap<String, ChebiOntology> fetchOntologyMap(SQLConnection brendaDB) throws SQLException {
    int ontologiesProcessed = 0;

    HashMap<String, ChebiOntology> ontologyMap = new HashMap<String, ChebiOntology>();

    Iterator<ChebiOntology> ontologies = brendaDB.getChebiOntologies();

    while (ontologies.hasNext()) {
      ChebiOntology ontology = ontologies.next();
      ontologyMap.put(ontology.getChebiId(), ontology);
      ontologiesProcessed++;
    }
    System.out.println("Done processing ontologies");
    System.out.println("Found " + ontologiesProcessed + "relations");

    return ontologyMap;
  }


  public static HashMap<ChebiOntology, HashSet<ChebiOntology>> fetchIsSubtypeOfRelationships(
      SQLConnection brendaDB,
      HashMap<String, ChebiOntology> ontologyMap)
      throws SQLException {

    int relationshipsProcessed = 0;

    HashMap<ChebiOntology, HashSet<ChebiOntology>> isSubtypeOfRelationships = new HashMap<>();

    Iterator<ChebiRelationship> relationships = brendaDB.getChebiRelationships(1);

    while (relationships.hasNext()) {
      ChebiRelationship relationship = relationships.next();
      HashSet<ChebiOntology> ontologySet = isSubtypeOfRelationships.get(ontologyMap.get(relationship.getParentChebiId()));
      if (ontologySet == null) {
        ontologySet = new HashSet<>();
        isSubtypeOfRelationships.put(ontologyMap.get(relationship.getParentChebiId()), ontologySet);
      }
      ontologySet.add(ontologyMap.get(relationship.getChebiId()));
      relationshipsProcessed++;
    }
    System.out.println("Done processing relationships");
    System.out.println("Found " + relationshipsProcessed + "relationships");

    return isSubtypeOfRelationships;

  }

  public static HashMap<ChebiOntology, HashSet<ChebiOntology>> fetchHasRoleRelationships(
      SQLConnection brendaDB,
      HashMap<String, ChebiOntology> ontologyMap)
      throws SQLException {

    int relationshipsProcessed = 0;

    HashMap<ChebiOntology, HashSet<ChebiOntology>> hasRoleRelationships = new HashMap<>();

    Iterator<ChebiRelationship> relationships = brendaDB.getChebiRelationships(12);

    while (relationships.hasNext()) {
      ChebiRelationship relationship = relationships.next();
      HashSet<ChebiOntology> ontologySet = hasRoleRelationships.get(ontologyMap.get(relationship.getChebiId()));
      if (ontologySet == null) {
        ontologySet = new HashSet<>();
        hasRoleRelationships.put(ontologyMap.get(relationship.getChebiId()), ontologySet);
      }
      ontologySet.add(ontologyMap.get(relationship.getParentChebiId()));
      relationshipsProcessed++;
    }
    System.out.println("Done processing relations");
    System.out.println("Found " + relationshipsProcessed + "relations");

    return hasRoleRelationships;
  }

  public static HashMap<ChebiOntology, ChebiApplicationSet> getApplications(
      SQLConnection brendaDB,
      HashMap<String, ChebiOntology> ontologyMap) throws SQLException {

    final String APPLICATION_CHEBI_ID = "CHEBI:33232";
    final ChebiOntology APPLICATION_ONTOLOGY = ontologyMap.get(APPLICATION_CHEBI_ID);


    // Compute set of main app
    HashMap<ChebiOntology, HashSet<ChebiOntology>> isSubtypeOfRelationships =
        fetchIsSubtypeOfRelationships(brendaDB, ontologyMap);
    final HashSet<ChebiOntology> MAIN_APPLICATIONS_ONTOLOGIES = isSubtypeOfRelationships.get(APPLICATION_ONTOLOGY);

    // Compute rel app -> {main app}
    HashMap<ChebiOntology, HashSet<ChebiOntology>> applicationToMainApplicationsMap = new HashMap<>();

    ArrayList<ChebiOntology> applicationsToVisit = new ArrayList<>(MAIN_APPLICATIONS_ONTOLOGIES);
    for (ChebiOntology applicationToVisit: applicationsToVisit) {
      HashSet<ChebiOntology> mainApplicationsSet = new HashSet<>();
      mainApplicationsSet.add(applicationToVisit);
      applicationToMainApplicationsMap.put(applicationToVisit, mainApplicationsSet);
    }

    while (!applicationsToVisit.isEmpty()) {
      ChebiOntology currentApplication = applicationsToVisit.get(0);
      applicationsToVisit.remove(0);
      HashSet<ChebiOntology> subApplications = isSubtypeOfRelationships.get(currentApplication);
      applicationsToVisit.addAll(subApplications);
      for (ChebiOntology subApplication : subApplications) {
        HashSet<ChebiOntology> mainApplicationsSet = applicationToMainApplicationsMap.get(subApplication);
        if (mainApplicationsSet == null) {
          mainApplicationsSet = new HashSet<>();
          applicationToMainApplicationsMap.put(subApplication, mainApplicationsSet);
        }
        mainApplicationsSet.addAll(applicationToMainApplicationsMap.get(currentApplication));
      }
    }

    // Compute rel mol -> {direct apps}
    HashMap<ChebiOntology, HashSet<ChebiOntology>> directApplicationMap =
        fetchHasRoleRelationships(brendaDB, ontologyMap);

    // Compute rel mol -> {main apps}
    HashMap<ChebiOntology, HashSet<ChebiOntology>> chemicalEntityToMainApplicationMap = new HashMap<>();
    for (ChebiOntology chemicalEntity: directApplicationMap.keySet()) {
      HashSet<ChebiOntology> mainApplicationsSet = chemicalEntityToMainApplicationMap.get(chemicalEntity);
      if (mainApplicationsSet == null) {
        mainApplicationsSet = new HashSet<>();
        chemicalEntityToMainApplicationMap.put(chemicalEntity, mainApplicationsSet);
      }
      for (ChebiOntology parentApplication: directApplicationMap.get(chemicalEntity)) {
        mainApplicationsSet.addAll(applicationToMainApplicationsMap.get(parentApplication));
      }
    }

    HashMap<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = new HashMap<>();
    for (ChebiOntology chemicalEntity: directApplicationMap.keySet()) {
      ChebiApplicationSet applications = new ChebiApplicationSet(
          directApplicationMap.get(chemicalEntity),
          chemicalEntityToMainApplicationMap.get(chemicalEntity));
      chemicalEntityToApplicationsMap.put(chemicalEntity, applications);
    }

    return chemicalEntityToApplicationsMap;
  }

  public static HashMap<String, HashMap<String, ArrayList<String>>> getApplicationsSimple(
      HashMap<ChebiOntology, ChebiApplicationSet> applicationMap) throws SQLException {
    HashMap<String, HashMap<String, ArrayList<String>>> applicationMapSimple = new HashMap<>();
    for (ChebiOntology ontology: applicationMap.keySet()) {
      ArrayList<String> directApplications = new ArrayList<>();
      for (ChebiOntology directApplication: applicationMap.get(ontology).getDirectApplications()) {
        directApplications.add(directApplication.getTerm());
      }
      ArrayList<String> mainApplications = new ArrayList<>();
      for (ChebiOntology mainApplication: applicationMap.get(ontology).getMainApplications()) {
        mainApplications.add(mainApplication.getTerm());
      }
      HashMap<String, ArrayList<String>> applications = new HashMap<>();
      applications.put("directApplications", directApplications);
      applications.put("mainApplications", mainApplications);
      applicationMapSimple.put(ontology.getChebiId(), applications);
    }
    return applicationMapSimple;
  }

  public static void main(String[] args) throws SQLException {
    SQLConnection brendaDB = new SQLConnection();
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");
    HashMap<String, ChebiOntology> ontologyMap = fetchOntologyMap(brendaDB);
    HashMap<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = getApplications(
        brendaDB,
        ontologyMap);
    HashMap<String, HashMap<String, ArrayList<String>>> applicationMapSimple = getApplicationsSimple(
        chemicalEntityToApplicationsMap);

    System.out.println(applicationMapSimple.get("CHEBI:46195"));

    brendaDB.disconnect();
  }
}
