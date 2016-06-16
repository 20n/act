package act.installer.brenda;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class BrendaChebiOntology {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BrendaChebiOntology.class);
  static final int IS_SUBTYPE_OF_RELATIONSHIP_TYPE = 1;
  static final int HAS_ROLE_RELATIONSHIP_TYPE = 12;

  private static ObjectMapper mapper = new ObjectMapper();

  // This ChEBI ID corresponds to the ontology 'Application' which is a top-level role.
  // The method getApplications then traverses the ontologies down from this ontology.
  // The effect is to consider only roles that are applications, defined in the user manual as 'classifying [entities]
  // on the basis of their intended use by humans'.
  static final String APPLICATION_CHEBI_ID = "CHEBI:33232";

  public static class ChebiOntology {

    // The following query allows to retrieve the terms (basic string defining an ontology) and definitions
    // (when it exists) corresponding to a ChEBI id (ex: "CHEBI:46195") to create ChebiOntology objects.
    public static final String QUERY = StringUtils.join(new String[]{
        "SELECT",
        "  terms.id_go,",
        "  terms.term,",
        "  definitions.definition",
        "FROM ontology_chebi_Terms terms",
        "LEFT JOIN ontology_chebi_Definitions definitions",
        "ON terms.id_go = definitions.id_go"
    }, " ");

    @JsonProperty("chebi_id")
    private String chebiId;

    @JsonProperty("term")
    private String term;

    @JsonProperty("definition")
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

    // We override the equals and hashCode methods to make a ChebiOntology object hashable and allow construction of
    // HashSet and HashMap of ChebiOntology objects.
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

    /* This function creates a ChebiOntology object from a ResultSet resulting from a SQL query.
     * It pulls the 3 first fields from the query, assuming the order:
     * ChebiId,
     * Term,
     * Definition
     */
    public static ChebiOntology fromResultSet(ResultSet resultSet) throws SQLException {
      return new ChebiOntology(
          resultSet.getString(1),
          resultSet.getString(2),
          resultSet.getString(3));
    }

    public BasicDBObject getBasicDBObject() {
      BasicDBObject o = new BasicDBObject();
      o.put("chebi_id", getChebiId());
      o.put("term", getTerm());
      o.put("definition", getDefinition());
      return o;
    }

  }

  public static class ChebiRelationship {

    // The following query allows to retrieve the relations of a given type, passed as argument.
    // It is restricted to ids starting with the string 'CHEBI:'
    public static final String QUERY = StringUtils.join(new String[]{
        "SELECT",
        " id_go,",
        " rel_id_go",
        "FROM ontology_chebi_Relations",
        "WHERE type = ?",
        "AND id_go like 'CHEBI:%'",
        "AND rel_id_go like 'CHEBI:%'"
    }, " ");

    public static void bindType(PreparedStatement stmt, Integer relationshipType) throws SQLException {
      stmt.setInt(1, relationshipType);
    }

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


    /* This function creates a ChebiOntology object from a ResultSet resulting from a SQL query.
     * It pulls the 3 first fields from the query, assuming the order:
     * chebiId,
     * parentChebiId
     * if type = 1, chebiId refers to a subtype of the ontology parentChebiId
     * if type = 12, parentChebiId refers to a role of the ontology chebiId
     */
    public static ChebiRelationship fromResultSet(ResultSet resultSet) throws SQLException {
      return new ChebiRelationship(
          resultSet.getString(1),
          resultSet.getString(2));
    }
  }

  public static class ChebiApplicationSet {

    @JsonProperty("direct_applications")
    private HashSet<ChebiOntology> directApplications;

    @JsonProperty("main_applications")
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

    public BasicDBObject getBasicDBObject() {
      BasicDBObject o = new BasicDBObject();
      BasicDBList directApplications = new BasicDBList();
      BasicDBList mainApplications = new BasicDBList();
      for (BrendaChebiOntology.ChebiOntology directApplication : getDirectApplications()) {
        directApplications.add(directApplication.getBasicDBObject());
      }
      for (BrendaChebiOntology.ChebiOntology mainApplication : getMainApplications()) {
        mainApplications.add(mainApplication.getBasicDBObject());
      }
      o.put("direct_applications", directApplications);
      o.put("main_applications", mainApplications);
      return o;
    }
  }

  /**
   * This function fetches an ontology map (ChebiId -> ChebiOntology) given a connexion to the BRENDA DB.
   * @param brendaDB A SQLConnexion object to the BRENDA DB
   * @return a map from ChebiId to ChebiOntology objects
   * @throws SQLException
   */
  public static HashMap<String, ChebiOntology> fetchOntologyMap(SQLConnection brendaDB) throws SQLException {
    int ontologiesProcessed = 0;

    HashMap<String, ChebiOntology> ontologyMap = new HashMap<>();

    Iterator<ChebiOntology> ontologies = brendaDB.getChebiOntologies();

    while (ontologies.hasNext()) {
      ChebiOntology ontology = ontologies.next();
      // We should not see collisions with the ChEBI ID as key.
      // The number of distinct ChEBI ID in the DB is the same as the number of rows.
      ontologyMap.put(ontology.getChebiId(), ontology);
      ontologiesProcessed++;
    }
    LOGGER.debug("Done processing ontologies");
    LOGGER.debug("Found %d ontologies", ontologiesProcessed);

    return ontologyMap;
  }

  /**
   * This function fetches relationships of type 'isSubTypeOf' between ChebiOntology objects given a connexion to the
   * BRENDA DB. The output is a map from a ChebiOntology object to all its subtypes ontologies.
   * @param brendaDB a SQLConnexion object to the BRENDA DB
   * @param ontologyMap a map from ChebiId to ChebiOntology objects
   * @return a map from ChebiOntology objects to a set of ChebiOntology objects (its subtypes).
   * @throws SQLException
   */
  public static HashMap<ChebiOntology, HashSet<ChebiOntology>> fetchIsSubtypeOfRelationships(
      SQLConnection brendaDB,
      HashMap<String, ChebiOntology> ontologyMap)
      throws SQLException {

    int relationshipsProcessed = 0;

    HashMap<ChebiOntology, HashSet<ChebiOntology>> isSubtypeOfRelationships = new HashMap<>();

    Iterator<ChebiRelationship> relationships = brendaDB.getChebiRelationships(IS_SUBTYPE_OF_RELATIONSHIP_TYPE);

    while (relationships.hasNext()) {
      ChebiRelationship relationship = relationships.next();
      // Note that we use the 'parent' as the key here and insert the 'child' in the value set.
      // Hence we get a map of ontologies to their subtype.
      HashSet<ChebiOntology> ontologySet = isSubtypeOfRelationships.get(ontologyMap.get(relationship.getParentChebiId()));
      if (ontologySet == null) {
        ontologySet = new HashSet<>();
        isSubtypeOfRelationships.put(ontologyMap.get(relationship.getParentChebiId()), ontologySet);
      }
      ontologySet.add(ontologyMap.get(relationship.getChebiId()));
      relationshipsProcessed++;
    }

    LOGGER.debug("Done processing 'is subtype of' relationships");
    LOGGER.debug("Found %d 'is subtype of' relationships", relationshipsProcessed);

    return isSubtypeOfRelationships;
  }

  /**
   * This function fetches relationships of type 'hasRole' between ChebiOntology objects given a connexion to the
   * BRENDA DB. The output is a map from a ChebiOntology object to all its roles that are 'applications'.
   * @param brendaDB a SQLConnexion object to the BRENDA DB
   * @param ontologyMap a map from ChebiId to ChebiOntology objects
   * @param allApplications the set of all ontologies that have the ontology 'application' as a parent
   * @return a map from ChebiOntology objects to a set of ChebiOntology objects (its roles).
   * @throws SQLException
   */
  public static HashMap<ChebiOntology, HashSet<ChebiOntology>> fetchHasRoleRelationships(
      SQLConnection brendaDB,
      HashMap<String, ChebiOntology> ontologyMap,
      ArrayList<ChebiOntology> allApplications)
      throws SQLException {

    int relationshipsProcessed = 0;
    HashSet<ChebiOntology> allApplicationsSet = new HashSet<>(allApplications);
    HashMap<ChebiOntology, HashSet<ChebiOntology>> hasRoleRelationships = new HashMap<>();

    Iterator<ChebiRelationship> relationships = brendaDB.getChebiRelationships(HAS_ROLE_RELATIONSHIP_TYPE);

    while (relationships.hasNext()) {

      ChebiRelationship relationship = relationships.next();
      ChebiOntology ontology = ontologyMap.get(relationship.getParentChebiId());

      if (allApplicationsSet.contains(ontology)) {
        HashSet<ChebiOntology> ontologySet = hasRoleRelationships.get(ontologyMap.get(relationship.getChebiId()));
        if (ontologySet == null) {
          ontologySet = new HashSet<>();
          hasRoleRelationships.put(ontologyMap.get(relationship.getChebiId()), ontologySet);
        }
        ontologySet.add(ontology);
        relationshipsProcessed++;
      }
    }

    LOGGER.debug("Done processing 'has role' relationships");
    LOGGER.debug("Found %s 'has role' relationships", relationshipsProcessed);

    return hasRoleRelationships;
  }

  /**
   * This function fetches and construct the set of main and direct applications for each ontology that has a role.
   * @param brendaDB a SQLConnexion object to the BRENDA DB
   * @param ontologyMap a map from ChebiId to ChebiOntology objects
   * @return a map from ChebiOntology objects to a ChebiApplicationSet object
   * @throws SQLException
   */
  public static HashMap<ChebiOntology, ChebiApplicationSet> getApplications(
      SQLConnection brendaDB,
      HashMap<String, ChebiOntology> ontologyMap) throws SQLException {

    final ChebiOntology APPLICATION_ONTOLOGY = ontologyMap.get(APPLICATION_CHEBI_ID);

    /*
     * Compute the set of main applications. These are the ontologies that are subtypes of the ontology 'application'.
     */
    HashMap<ChebiOntology, HashSet<ChebiOntology>> isSubtypeOfRelationships =
        fetchIsSubtypeOfRelationships(brendaDB, ontologyMap);
    final HashSet<ChebiOntology> MAIN_APPLICATIONS_ONTOLOGIES = isSubtypeOfRelationships.get(APPLICATION_ONTOLOGY);

    /*
     * For each application, compute its set of main applications (subset of direct or indirect parents that are in
     * MAIN_APPLICATIONS_ONTOLOGIES) and store it in a hashmap applicationToMainApplicationsMap
     */
    HashMap<ChebiOntology, HashSet<ChebiOntology>> applicationToMainApplicationsMap = new HashMap<>();

    // Compute the initial list of applications to visit from the set of main applications.
    ArrayList<ChebiOntology> applicationsToVisit = new ArrayList<>(MAIN_APPLICATIONS_ONTOLOGIES);

    // For each main application, define the mainApplicationSet as a set containing only itself.
    for (ChebiOntology applicationToVisit : applicationsToVisit) {
      HashSet<ChebiOntology> mainApplicationsSet = new HashSet<>();
      mainApplicationsSet.add(applicationToVisit);
      applicationToMainApplicationsMap.put(applicationToVisit, mainApplicationsSet);
    }

    // Then visit all applications in a BFS fashion, appending new applications to visit to the applicationsToVisit
    // and propagating/merging the set of main applications as we progress down the relationship graph.
    int currentIndex = 0;
    while (currentIndex < applicationsToVisit.size()) {
      ChebiOntology currentApplication = applicationsToVisit.get(currentIndex);
      HashSet<ChebiOntology> subApplications = isSubtypeOfRelationships.get(currentApplication);
      if (subApplications != null) {
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
      currentIndex++;
    }
    /*
     * Fetch the set of direct applications for each ontology that has a role (aka is a chemical entity).
     */
    HashMap<ChebiOntology, HashSet<ChebiOntology>> directApplicationMap =
        fetchHasRoleRelationships(brendaDB, ontologyMap, applicationsToVisit);

    /*
     * Compute the set of main applications for each ontology that has a role (aka is a chemical entity).
     */
    HashMap<ChebiOntology, HashSet<ChebiOntology>> chemicalEntityToMainApplicationMap = new HashMap<>();
    for (ChebiOntology chemicalEntity : directApplicationMap.keySet()) {
      HashSet<ChebiOntology> mainApplicationsSet = chemicalEntityToMainApplicationMap.get(chemicalEntity);
      if (mainApplicationsSet == null) {
        mainApplicationsSet = new HashSet<>();
        chemicalEntityToMainApplicationMap.put(chemicalEntity, mainApplicationsSet);
      }
      for (ChebiOntology parentApplication : directApplicationMap.get(chemicalEntity)) {
        HashSet<ChebiOntology> mainApplications = applicationToMainApplicationsMap.get(parentApplication);
        if (mainApplications != null) {
          mainApplicationsSet.addAll(mainApplications);
        }
      }
    }

    /*
     * Finally, construct a ChebiApplicationSet object containing direct and main applications for the molecules.
     */
    HashMap<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = new HashMap<>();
    for (ChebiOntology chemicalEntity : directApplicationMap.keySet()) {
      ChebiApplicationSet applications = new ChebiApplicationSet(
          directApplicationMap.get(chemicalEntity),
          chemicalEntityToMainApplicationMap.get(chemicalEntity));
      chemicalEntityToApplicationsMap.put(chemicalEntity, applications);
    }

    return chemicalEntityToApplicationsMap;
  }

  /**
   * This function contains the main logic for adding ChEBI applications to the Installer database.
   * Provided with a connexion to both the Mongo instance on which the database "actv01" lives and a SQL connexion to
   * Brenda to retrieve the application sets corresponding to each ChEBI chemical.
   * @param db a MongoDB object representing the connexion to the main MongoDB instance
   * @param brendaDB a SQLConnexion to the Brenda database, on which to find the ChEBI ontologies and relationships
   * @throws SQLException
   * @throws IOException
   */
  public void addChebiApplications(MongoDB db, SQLConnection brendaDB) throws SQLException, IOException {
    Map<String, Long> all_db_chems = db.constructAllInChIs();

    // Get the ontology map (ChebiId -> ChebiOntology object)
    HashMap<String, ChebiOntology> ontologyMap = fetchOntologyMap(brendaDB);

    // Get the applications for all chemical entities
    HashMap<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = getApplications(
        brendaDB,
        ontologyMap);

    DBIterator chemicalsIterator = db.getIteratorOverChemicals();
    // Iterate over all chemicals
    while (chemicalsIterator.hasNext()) {
      Chemical chemical = db.getNextChemical(chemicalsIterator);
      String inchi = chemical.getInChI();
      String chebiId = db.getChebiIDFromInchi(inchi);

      if (chebiId == null || chebiId.equals("")) { continue;}

      LOGGER.debug("Processing Chemical with InChI: %s and ChEBI ID: %s", inchi, chebiId);
      ChebiOntology ontology = ontologyMap.get(chebiId);
      ChebiApplicationSet applicationSet = chemicalEntityToApplicationsMap.get(ontology);
      if (applicationSet == null) {
        LOGGER.debug("Application set for %s was found null. Skipping update.", chebiId);
        continue;
      }
      db.updateChemicalWithChebiApplications(chebiId, applicationSet);
    }
  }

  public static void main(String[] args) throws SQLException, IOException {
    // We provide a proof of concept in this main function. This should later be moved to either a test or removed.

    // Connect to the BRENDA DB
    SQLConnection brendaDB = new SQLConnection();
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");

    // Get the ontology map (ChebiId -> ChebiOntology object)
    HashMap<String, ChebiOntology> ontologyMap = fetchOntologyMap(brendaDB);

    // Get the applications for all chemical entities
    HashMap<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = getApplications(
        brendaDB,
        ontologyMap);

    ChebiOntology applicationOntology = ontologyMap.get("CHEBI:46195");

    // Convert ChebiApplicationSet to JSON string and pretty print
    String chebiApplicationSetString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        chemicalEntityToApplicationsMap.get(applicationOntology));

    System.out.println(chebiApplicationSetString);

    // Disconnect from the BRENDA DB
    brendaDB.disconnect();
  }
}
