package com.twentyn.patentSearch;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Searcher implements AutoCloseable {
  public static final Logger LOGGER = LogManager.getFormatterLogger(Searcher.class);

  private static final List<String> KEYWORDS = Collections.unmodifiableList(Arrays.asList(
      "yeast",
      "cerevisiae",
      "coli",
      "biosynthesis",
      "biogenesis",
      "anabolism",
      "catalysis",
      "ferment",
      "fermenter",
      "fermentor",
      "fermentation",
      "fermentive"
  ));
  private static final String CLAIMS_FIELD = "claims";
  private static final int MAX_RESULTS_PER_QUERY = 100;
  // Note: this score is likely dependent on the set of keywords above.  Adjust this if KEYWORDS change.
  private static final float DEFAULT_SCORE_THRESHOLD = 0.1f;

  private List<Pair<IndexReader, IndexSearcher>> indexReadersAndSearchers = new ArrayList<>();
  private float scoreThreshold = DEFAULT_SCORE_THRESHOLD;

  private Searcher() {

  }

  private Searcher(float scoreThreshold) {
    this.scoreThreshold = scoreThreshold;
  }

  private void init(List<File> indexDirectories) throws IOException {
    for (File indexDirectory : indexDirectories) {
      LOGGER.info("Opening index dir at %s", indexDirectory.getAbsolutePath());
      Directory indexDir = FSDirectory.open(indexDirectory.toPath());
      IndexReader indexReader = DirectoryReader.open(indexDir);
      IndexSearcher searcher = new IndexSearcher(indexReader);
      // Only add to the list if both of these calls work.
      indexReadersAndSearchers.add(Pair.of(indexReader, searcher));
    }
  }

  @Override
  public void close() throws IOException {
    for (IndexReader reader : indexReadersAndSearchers.stream().map(Pair::getLeft).collect(Collectors.toList())) {
      try {
        reader.close();
      } catch (IOException e) {
        LOGGER.error("Unable to close index reader, but continuing to try closing others: %s", e.getMessage());
      }
    }
  }

  public static class Factory {
    private static final Factory INSTANCE = new Factory();

    private Factory() {

    }

    public static Factory getInstance() {
      return INSTANCE;
    }

    public Searcher build(File indexTopDir, float scoreThreshold) throws IOException {
      Searcher s = new Searcher(scoreThreshold);
      runInit(indexTopDir, s);
      return s;
    }

    public Searcher build(File indexTopDir) throws IOException {
      Searcher s = new Searcher();
      runInit(indexTopDir, s);
      return s;
    }

    private void runInit(File indexTopDir, Searcher s) throws IOException {
      if (!indexTopDir.isDirectory()) {
        String msg = String.format("Top level directory at %s is not a directory", indexTopDir.getAbsolutePath());
        LOGGER.error(msg);
        throw new IOException(msg);
      }

      List<File> individualIndexes = Arrays.stream(indexTopDir.listFiles()).
          filter(f -> f.getName().endsWith(".index")).collect(Collectors.toList());
      if (individualIndexes.size() == 0) {
        String msg = String.format("Top level directory at %s contains no index sub-directories",
            indexTopDir.getAbsolutePath());
        LOGGER.error(msg);
        throw new IOException(msg);
      }

      s.init(individualIndexes);
    }
  }

  /**
   * Search for patents that contain any of the specified chemical synonyms, scored based on synonym and biosynthesis
   * keyword occurrence.  Results are filtered by score.
   * @param synonyms A list of chemical synonyms to use in the search.
   * @return A list of search results whose relevance scores are above the searcher's score threshold.
   * @throws IOException
   */
  public List<SearchResult> searchInClaims(List<String> synonyms) throws IOException {
    if (synonyms.size() == 0) {
      LOGGER.info("Not running search for no synonyms!");
      return Collections.emptyList();
    }

    // Make queries for all synonyms.
    final List<BooleanQuery> queries = makeQueries(synonyms, CLAIMS_FIELD).collect(Collectors.toList());

    // Reuse the compiled queries for all indices.
    try {
      Set<Triple<Float, String, String>> uniqueResults = indexReadersAndSearchers.stream().
          map(p -> runSearch(p, queries)). // Search to get per-query streams...
          flatMap(Function.identity()).    // combine all the streams into one...
          collect(Collectors.toSet());    // and collect the merged results in a list.

      /* Uniq-ify!  It is completely reasonable for a patent to appear for multiple queries.
       * TODO: we haven't seen results appear multiple times with different scores.  We should probably unique-ify
       * on id and take the result with the best score just to be safe. */
      List<Triple<Float, String, String>> results = new ArrayList<>(uniqueResults);
      Collections.sort(results);

      return results.stream().
          map(t -> new SearchResult(t.getMiddle(), t.getRight(), t.getLeft())).
          collect(Collectors.toList());
    } catch (UncheckedIOException e) {
      throw e.getCause(); // Promote back to a regular exception for handling by the caller.
    }
  }

  // Run a set of queries over a single reader + searcher.
  private Stream<Triple<Float, String, String>> runSearch(
      Pair<IndexReader, IndexSearcher> readerSearcher, List<BooleanQuery> queries) throws UncheckedIOException {

    // With hints from http://stackoverflow.com/questions/22382453/java-8-streams-flatmap-method-example
    return queries.stream().map(q -> executeQuery(readerSearcher, q)).flatMap(Collection::stream);
  }

  // Run a single query on a single reader + searcher.
  private List<Triple<Float, String, String>> executeQuery(
      Pair<IndexReader, IndexSearcher> readerSearcher, BooleanQuery query) throws UncheckedIOException {
    TopDocs topDocs;
    try {
      topDocs = readerSearcher.getRight().search(query, MAX_RESULTS_PER_QUERY);
    } catch (IOException e) {
      LOGGER.error("Caught IO exception when trying to run search for %s: %s", query, e.getMessage());
      /* Wrap `e` in an unchecked exception to allow it to escape our call stack.  The top level function with catch
       * and rethrow it as a normal IOException. */
      throw new UncheckedIOException(e);
    }

    ScoreDoc[] scoreDocs = topDocs.scoreDocs;
    if (scoreDocs.length == 0) {
      LOGGER.debug("Search returned no results.");
      return Collections.emptyList();
    }
    // ScoreDoc just contains a score and an id.  We need to retrieve the documents' content using that id.

    /* Crux of the next bit:
     * Filter by score and convert from scoreDocs to document features.
     * No need to use `limit` here since we already had Lucene cap the result set size. */
    return Arrays.stream(scoreDocs).
        filter(scoreDoc -> scoreDoc.score >= scoreThreshold).
        map(scoreDoc -> { //
          try {
            Pair<String, String> features = this.extractDocFeatures(readerSearcher.getLeft().document(scoreDoc.doc));
            // Put the score first so the natural sort order is based on score.
            return Triple.of(scoreDoc.score, features.getLeft(), features.getRight());
          } catch (IOException e) {
            // Yikes, this is v. bad.
            LOGGER.error("Caught IO exception when trying to read doc id %d: %s", scoreDoc.doc, e.getMessage());
            throw new UncheckedIOException(e); // Same as above.
          }
        }).collect(Collectors.toList());
  }

  // Just extract the id and title for now.  The id contains the patent number, and the title is enough for display.
  private Pair<String, String> extractDocFeatures(Document doc) {
    return Pair.of(doc.get("id"), doc.get("title"));
  }

  private Stream<BooleanQuery> makeQueries(List<String> synonyms, String field) {
    return synonyms.stream().
        filter(syn -> syn != null && !syn.isEmpty()).
        map(syn -> makeQuery(syn, field));
  }

  private BooleanQuery makeQuery(String synonym, String field) {
    BooleanQuery bq = new BooleanQuery();

    // Set the synonym as a required phrase query.  Phrase queries handle multi-word synonyms, but require construction.
    String queryString = synonym.trim().toLowerCase();
    String[] parts = queryString.split("\\s+");
    PhraseQuery query = new PhraseQuery();
    Arrays.stream(parts).forEach(p -> query.add(new Term(field, p)));
    bq.add(query, BooleanClause.Occur.MUST);

    // Append all keywords as optional clauses.  The more of these we find, the higher the score will be.
    KEYWORDS.forEach(term -> bq.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD));

    return bq;
  }

  public static class SearchResult {
    String id;
    String title;
    Float relevanceSecore;

    public SearchResult(String id, String title, Float relevanceSecore) {
      this.id = id;
      this.title = title;
      this.relevanceSecore = relevanceSecore;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public Float getRelevanceSecore() {
      return relevanceSecore;
    }
  }
}
