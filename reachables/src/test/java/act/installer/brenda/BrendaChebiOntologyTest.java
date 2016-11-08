package act.installer.brenda;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


import static org.junit.Assert.assertEquals;


public class BrendaChebiOntologyTest {


  private BrendaChebiOntology.ChebiOntology buildTestCase(String id) {
    String chebiId = String.format("CHEBI:%s", id);
    String term  = String.format("term%s", id);
    String definition = String.format("definition%s", id);
    return new BrendaChebiOntology.ChebiOntology(chebiId, term, definition);
  }

  @Test
  public void testApplicationToMainApplicationMapping() {

    List<Integer> range = IntStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
    List<BrendaChebiOntology.ChebiOntology> ontologies = range.stream().map(id -> buildTestCase(id.toString())).collect(Collectors.toList());
    Map<String, BrendaChebiOntology.ChebiOntology> ontologyMap = new HashMap<>();
    ontologies.forEach(o -> ontologyMap.put(o.getChebiId(), o));

    Map<String, Set<String>> isSubtypeOfRelationships = new HashMap<>();

    //         1 -> main application ontology
    //        / \
    //       2   3 -> these are main applications
    //      /     \
    //     4       5 -> these are sub applications
    //      \     / \
    //       \   /   6 -> another level of sub-applications
    //        \ /     \
    //         7       8 -> chemicals
    // Question: can there be nested chemicals?

    // 2 and 3 are main applications
    Set<String> mainApplications = new HashSet<>(Arrays.asList("2", "3"));
    // 1 is the main application id
    isSubtypeOfRelationships.put("1", mainApplications);

    // 4, 5 and 6 are sub applications. 4 is sub-application of 2 and 5 is sub-application of 3.
    isSubtypeOfRelationships.put("2", Collections.singleton("4"));
    isSubtypeOfRelationships.put("3", Collections.singleton("5"));
    isSubtypeOfRelationships.put("5", new HashSet<String>() {{
      add("6");
      add("7");
    }});

    // 7 and 8 are chemicals with respective applications 4 and 6
    isSubtypeOfRelationships.put("4", Collections.singleton("7"));
    isSubtypeOfRelationships.put("6", Collections.singleton("8"));

    // Expected map is:
    Map<String, Set<String>> applicationToMainApplicationMap = new HashMap<>();
    applicationToMainApplicationMap.put("2", Collections.singleton("2"));
    applicationToMainApplicationMap.put("3", Collections.singleton("3"));
    applicationToMainApplicationMap.put("4", Collections.singleton("2"));
    applicationToMainApplicationMap.put("5", Collections.singleton("3"));
    applicationToMainApplicationMap.put("6", Collections.singleton("3"));
    applicationToMainApplicationMap.put("7", new HashSet<String>() {{
      add("2");
      add("3");
    }});
    applicationToMainApplicationMap.put("8", Collections.singleton("3"));

    assertEquals(applicationToMainApplicationMap, BrendaChebiOntology.getApplicationToMainApplicationsMap(isSubtypeOfRelationships, "1"));
  }
}
