package act.installer.brenda;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    public BasicDBObject toBasicDBObject() {
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
    private Set<ChebiOntology> directApplications;

    @JsonProperty("main_applications")
    private Set<ChebiOntology> mainApplications;

    public ChebiApplicationSet(Set<ChebiOntology> directApplications,
                               Set<ChebiOntology> mainApplications) {
      this.directApplications = directApplications;
      this.mainApplications = mainApplications;
    }

    public Set<ChebiOntology> getMainApplications() {
      return mainApplications;
    }

    public Set<ChebiOntology> getDirectApplications() {
      return directApplications;
    }

    public BasicDBObject toBasicDBObject() {

      BasicDBList directApplications = new BasicDBList();
      BasicDBList mainApplications = new BasicDBList();

      getDirectApplications().forEach(directApplication -> directApplications.add(directApplication.toBasicDBObject()));
      getMainApplications().forEach(mainApplication -> mainApplications.add(mainApplication.toBasicDBObject()));

      return new BasicDBObject()
          .append("direct_applications", directApplications)
          .append("main_applications", mainApplications);
    }
  }

  /**
   * This function fetches an ontology map (ChebiId -> ChebiOntology) given a connexion to the BRENDA DB.
   * @param brendaDB A SQLConnexion object to the BRENDA DB
   * @return a map from ChebiId to ChebiOntology objects
   * @throws SQLException
   */
  public static Map<String, ChebiOntology> fetchOntologyMap(SQLConnection brendaDB) throws SQLException {
    int ontologiesProcessed = 0;

    Map<String, ChebiOntology> ontologyMap = new HashMap<>();

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
   * @return a map from ChebiOntology objects to a set of ChebiOntology objects (its subtypes).
   * @throws SQLException
   */
  public static Map<String, Set<String>> fetchIsSubtypeOfRelationships(SQLConnection brendaDB)
      throws SQLException {

    // Initializations
    int relationshipsProcessed = 0;
    Map<String, Set<String>> isSubtypeOfRelationships = new HashMap<>();

    // Get an iterator over all Chebi relationships of type "is subtype of".
    Iterator<ChebiRelationship> relationships = brendaDB.getChebiRelationships(IS_SUBTYPE_OF_RELATIONSHIP_TYPE);

    while (relationships.hasNext()) {
      ChebiRelationship relationship = relationships.next();
      // Note that we use the 'parent' as the key here and insert the 'child' in the value set.
      // Hence we get a map of ontologies to their subtype.

      // Get child and parent ontologies
      String parentChebiId = relationship.getParentChebiId();
      String childChebiId = relationship.getChebiId();

      // Add child to the set of existing child ontologies
      Set<String> childchebiIds = isSubtypeOfRelationships.get(parentChebiId);
      if (childchebiIds == null) {
        childchebiIds = new HashSet<>();
        isSubtypeOfRelationships.put(parentChebiId, childchebiIds);
      }
      childchebiIds.add(childChebiId);
      relationshipsProcessed++;
    }

    LOGGER.debug("Done processing 'is subtype of' relationships");
    LOGGER.debug("Found %d 'is subtype of' relationships", relationshipsProcessed);

    return isSubtypeOfRelationships;
  }


  /**
   * This function fetches relationships of type 'hasRole' between ChebiOntology objects given a connexion to the
   * BRENDA DB. The output is a map from a ChebiOntology object to all its roles.
   * @param brendaDB a SQLConnexion object to the BRENDA DB
   * @return a map from ChebiOntology objects to a set of ChebiOntology objects (its roles).
   * @throws SQLException
   */
  public static Map<String, Set<String>> fetchHasRoleRelationships(SQLConnection brendaDB)
      throws SQLException {

    // Initializations
    int relationshipsProcessed = 0;
    Map<String, Set<String>> hasRoleRelationships = new HashMap<>();

    // Get an iterator over all Chebi relationships of type "has role".
    Iterator<ChebiRelationship> relationships = brendaDB.getChebiRelationships(HAS_ROLE_RELATIONSHIP_TYPE);

    while (relationships.hasNext()) {
      // For each relationship "has role", we have a child and a parent ontology.
      // We call the child the "base ontology" and the parent the "role ontology"
      ChebiRelationship relationship = relationships.next();

      // Find the role ontology. This is the parent ontology in the relationship.
      String roleChebiId = relationship.getParentChebiId();
      // Find the base ontology. This is the child ontology in the relationship.
      String baseChebiId = relationship.getChebiId();

      // Get the existing set of roles for the ontology of interest
      Set<String> roles = hasRoleRelationships.get(baseChebiId);
      if (roles == null) {
        roles = new HashSet<>();
        hasRoleRelationships.put(baseChebiId, roles);
      }
      // Add the role the existing set
      roles.add(roleChebiId);
      relationshipsProcessed++;
    }

    LOGGER.debug("Done processing 'has role' relationships");
    LOGGER.debug("Found %s 'has role' relationships", relationshipsProcessed);

    return hasRoleRelationships;
  }


  public static Map<String, Set<String>> getApplicationToMainApplicationsMap(
      Map<String, Set<String>> isSubtypeOfRelationships, String applicationChebiId) {

    /*
     * Compute the set of main applications. These are the ontologies that are subtypes of the ontology 'application'.
     */
    final Set<String> mainApplicationsChebiId = isSubtypeOfRelationships.get(applicationChebiId);

    /*
     * For each application, compute its set of main applications (subset of direct or indirect parents that are in
     * MAIN_APPLICATIONS_ONTOLOGIES) and store it in a hashmap applicationToMainApplicationsMap
     */
    Map<String, Set<String>> applicationToMainApplicationsMap = new HashMap<>();

    // Compute the initial list of applications to visit from the set of main applications.
    ArrayList<String> applicationsToVisit = new ArrayList<>(mainApplicationsChebiId);

    // For each main application, define the mainApplicationSet as a set containing only itself.
    applicationsToVisit.stream().forEach(application ->
        applicationToMainApplicationsMap.put(application, Collections.singleton(application)));

    // Then visit all applications in a BFS fashion, appending new applications to visit to the applicationsToVisit
    // and propagating/merging the set of main applications as we progress down the relationship graph.
    int currentIndex = 0;
    while (currentIndex < applicationsToVisit.size()) {
      LOGGER.debug("current index is at %d", currentIndex);
      LOGGER.debug("%d applications in the applications to visit set", applicationsToVisit.size());
      String currentApplication = applicationsToVisit.get(currentIndex);
      LOGGER.debug("current application is %s", currentApplication);
      Set<String> subApplications = isSubtypeOfRelationships.get(currentApplication);

      if (subApplications != null) {
        LOGGER.debug("Found %d existing sub-applicaitons", subApplications.size());

        // add all sub-applications to the set of applications to visit
        applicationsToVisit.addAll(subApplications);
        for (String subApplication : subApplications) {
          Set<String> mainApplicationsSet = applicationToMainApplicationsMap.get(subApplication);
          if (mainApplicationsSet == null) {
            mainApplicationsSet = new HashSet<>();
            applicationToMainApplicationsMap.put(subApplication, mainApplicationsSet);
          }
          mainApplicationsSet.addAll(applicationToMainApplicationsMap.get(currentApplication));
        }

      }
      currentIndex++;
    }

    return applicationToMainApplicationsMap;
  }

  /**
   * This function fetches and construct the set of main and direct applications for each ontology that has a role.
   * @return a map from ChebiOntology objects to a ChebiApplicationSet object
   * @throws SQLException
   */
  public static Map<ChebiOntology, ChebiApplicationSet> getApplications(
      Map<String, ChebiOntology> ontologyMap,
      Map<String, Set<String>> isSubtypeOfRelationships,
      Map<String, Set<String>> hasRoleRelationships) {


    Map<String, Set<String>> applicationToMainApplicationsMap = getApplicationToMainApplicationsMap(
        isSubtypeOfRelationships, APPLICATION_CHEBI_ID);

    // Filter out the roles that are not applications
    Map<String, Set<String>> directApplicationMap = new HashMap<>();
    hasRoleRelationships.forEach((key, value) -> directApplicationMap.put(
        key, value
            .stream()
            .filter(ontology -> applicationToMainApplicationsMap.keySet().contains(ontology))
            .collect(Collectors.toSet())));

    // directApplicationMap now maps each ontology to its direct applications

    /*
     * Compute the set of main applications for each ontology that has a role (aka is a chemical entity).
     */
    Map<ChebiOntology, Set<ChebiOntology>> chemicalEntityToMainApplicationMap = new HashMap<>();
    for (String chemicalEntity : directApplicationMap.keySet()) {

      Set<ChebiOntology> mainApplicationsSet = chemicalEntityToMainApplicationMap.get(ontologyMap.get(chemicalEntity));
      if (mainApplicationsSet == null) {
        mainApplicationsSet = new HashSet<>();
        chemicalEntityToMainApplicationMap.put(ontologyMap.get(chemicalEntity), mainApplicationsSet);
      }
      for (String parentApplication : directApplicationMap.get(chemicalEntity)) {
        Set<String> mainApplications = applicationToMainApplicationsMap.get(parentApplication);
        if (mainApplications != null) {
          mainApplicationsSet.addAll(mainApplications.stream().map(ontologyMap::get).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
      }
    }

    LOGGER.info("Done computing main applications for ontologies having a role.");

    /*
     * Finally, construct a ChebiApplicationSet object containing direct and main applications for the molecules.
     */
    Map<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = new HashMap<>();
    for (String chemicalEntity : directApplicationMap.keySet()) {
      Set<ChebiOntology> directApplications = directApplicationMap.get(chemicalEntity).stream().map(ontologyMap::get).collect(Collectors.toSet());
      Set<ChebiOntology> mainApplications = chemicalEntityToMainApplicationMap.get(ontologyMap.get(chemicalEntity));
      if (directApplications.size() > 0 || mainApplications.size() > 0) {
        ChebiApplicationSet applications = new ChebiApplicationSet(directApplications, mainApplications);
        chemicalEntityToApplicationsMap.put(ontologyMap.get(chemicalEntity), applications);
      }
    }

    LOGGER.info("Done computing each ontology application set.");

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

    // Get the ontology map (ChebiId -> ChebiOntology object)
    Map<String, ChebiOntology> ontologyMap = fetchOntologyMap(brendaDB);
    LOGGER.info("Done fetching ontology map: ChEBI ID -> ontology object (id, term, definition)");

    // Get relationships of type 'isSubtypeOf'
    Map<String, Set<String>> isSubtypeOfRelationships = fetchIsSubtypeOfRelationships(brendaDB);
    LOGGER.info("Done fetching 'is subtype of' relationships");

    // Get relationships of type 'hasRole'
    Map<String, Set<String>> hasRoleRelationships = fetchHasRoleRelationships(brendaDB);
    LOGGER.info("Done fetching 'has role' relationships");

    // Get the applications for all chemical entities
    Map<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = getApplications(
        ontologyMap, isSubtypeOfRelationships, hasRoleRelationships);
    LOGGER.info("Done computing applications");

    DBIterator chemicalsIterator = db.getIteratorOverChemicals();
    // Iterate over all chemicals
    while (chemicalsIterator.hasNext()) {
      Chemical chemical = db.getNextChemical(chemicalsIterator);
      String inchi = chemical.getInChI();
      String chebiId = db.getChebiIDFromInchi(inchi);

      if (chebiId == null || chebiId.equals("")) {
        continue;
      }

      LOGGER.info("Processing Chemical with InChI: %s and ChEBI ID: %s", inchi, chebiId);
      ChebiOntology ontology = ontologyMap.get(chebiId);
      ChebiApplicationSet applicationSet = chemicalEntityToApplicationsMap.get(ontology);
      if (applicationSet == null) {
        LOGGER.debug("Found no applications for %s. Skipping database update for this chemical.", chebiId);
        continue;
      }
      LOGGER.info(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(applicationSet));
      db.updateChemicalWithChebiApplications(chebiId, applicationSet);
    }
  }

  public static void main(String[] args) throws Exception {
    // We provide a proof of concept in this main function. This should later be moved to either a test or removed.

    // Connect to the BRENDA DB
    SQLConnection brendaDB = new SQLConnection();
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");

    // Get the ontology map (ChebiId -> ChebiOntology object)
    Map<String, ChebiOntology> ontologyMap = fetchOntologyMap(brendaDB);

    // Get "is subtype of" relationships
    Map<String, Set<String>> isSubTypeOfRelationships = fetchIsSubtypeOfRelationships(brendaDB);

    // Get "has role" relationships
    Map<String, Set<String>> hasRoleRelationships = fetchHasRoleRelationships(brendaDB);

    // Get the applications for all chemical entities
    Map<ChebiOntology, ChebiApplicationSet> chemicalEntityToApplicationsMap = getApplications(
        ontologyMap, isSubTypeOfRelationships, hasRoleRelationships);

    ChebiOntology applicationOntology = ontologyMap.get("CHEBI:46195");

    // Convert ChebiApplicationSet to JSON string and pretty print
    String chebiApplicationSetString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        chemicalEntityToApplicationsMap.get(applicationOntology));

    System.out.println(chebiApplicationSetString);

    // Disconnect from the BRENDA DB
    brendaDB.disconnect();
  }
}
