package act.installer.reachablesexplorer;

import act.installer.pubchem.PubchemSynonymType;
import com.mongodb.BasicDBObject;
import com.twentyn.patentSearch.Searcher;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mongojack.DBCursor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatentFinder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PatentFinder.class);

  private static final List<PubchemSynonymType> SYNONYM_TYPE_PREFERENCE = Collections.unmodifiableList(Arrays.asList(
      PubchemSynonymType.TRIVIAL_NAME,
      PubchemSynonymType.INTL_NONPROPRIETARY_NAME,
      PubchemSynonymType.DEPOSITORY_NAME, // Beware: this list can be huge.
      PubchemSynonymType.DRUG_TRADE_NAME,
      PubchemSynonymType.IUPAC_NAME
  ));

  public static void main(String[] args) throws Exception {

    File indexesTopDir = new File(args[0]);
    LOGGER.info("Using index top level dir: %s", indexesTopDir.getAbsolutePath());

    PatentFinder finder = new PatentFinder();
    try (Searcher searcher = Searcher.Factory.getInstance().build(indexesTopDir)) {
      finder.run(searcher);
    }
  }

  private void run(Searcher searcher) throws IOException {
    Loader loader = new Loader("localhost", 27017, "wiki_reachables", "reachablesv6_test_thomas", "sequencesv6_test_thomas", "/tmp");

    DBCursor<Reachable> reachableDBCursor = loader.getJacksonReachablesCollection().find(new BasicDBObject("names", "glycerol"));

    while (reachableDBCursor.hasNext()) {
      Reachable reachable = reachableDBCursor.next();

      SynonymData synonyms = reachable.getSynonyms();
      Set<String> preferredSynonyms = null;
      if (synonyms != null) {
        Map<PubchemSynonymType, Set<String>> pubchemSynonyms = synonyms.getPubchemSynonyms();
        for (PubchemSynonymType type : SYNONYM_TYPE_PREFERENCE) {
          if (pubchemSynonyms.containsKey(type)) {
            preferredSynonyms = pubchemSynonyms.get(type);
            break;
          }
        }
      }

      if (preferredSynonyms == null) {
        LOGGER.warn("No synonyms for molecule %s", reachable.getInchi());
        preferredSynonyms = Collections.emptySet();
      }

      List<String> allNames = new ArrayList<>(reachable.getNames());
      allNames.addAll(preferredSynonyms);

      allNames.removeIf(s -> s == null || s.length() < 3); // Eliminate potential garbage rankings for short names.
      // Note: stop words should not appear in the index, so no need to filter on terms.
      Collections.sort(allNames);

      LOGGER.info("Running query with terms: %s", StringUtils.join(allNames, ", "));

      List<Triple<Float, String, String>> results = searcher.searchInClaims(allNames);

      LOGGER.info("Results (%d) for %s:", results.size(), reachable.getPageName());
      for (Triple<Float, String, String> triple : results) {
        LOGGER.info("(%.3f) %s: %s", triple.getLeft(), triple.getMiddle(), triple.getRight());
      }
    }
  }
}
