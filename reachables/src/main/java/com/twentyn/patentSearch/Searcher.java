package com.twentyn.patentSearch;

import org.apache.commons.lang3.tuple.Pair;
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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Searcher implements AutoCloseable {
  public static final Logger LOGGER = LogManager.getLogger(Searcher.class);

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
  private static final float SCORE_THRESHOLD = 0.1f;

  private List<Pair<IndexReader, IndexSearcher>> indexReadersAndSearchers = new ArrayList<>();

  private Searcher() {

  }

  private void init(List<File> indexDirectories) throws IOException {
    for (File indexDirectory : indexDirectories) {
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
      reader.close();
    }
  }

  public static class Factory {
    public Searcher build(File indexTopDir) throws IOException {
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

      Searcher s = new Searcher();
      s.init(individualIndexes);
      return s;
    }
  }

  public List<Pair<String, String>> searchInClaims(List<String> synonyms) throws IOException {
    final Stream<BooleanQuery> queries = makeQueries(synonyms, CLAIMS_FIELD);
    // Reuse the queries for all indices.
    try {
      return indexReadersAndSearchers.stream().
          map(p -> runSearch(p.getLeft(), p.getRight(), queries)).
          flatMap(Function.identity()).
          collect(Collectors.toList());
    } catch (UncheckedIOException e) {
      throw e.getCause(); // ...and promote back to a regular exception.
    }
  }

  private Stream<Pair<String, String>> runSearch(
      IndexReader reader, IndexSearcher searcher, Stream<BooleanQuery> queries) throws UncheckedIOException {

    // With hints from http://stackoverflow.com/questions/22382453/java-8-streams-flatmap-method-example
    return queries.map(q -> executeQuery(reader, searcher, q)).flatMap(Collection::stream);
  }

  private List<Pair<String, String>> executeQuery(IndexReader reader, IndexSearcher searcher, BooleanQuery query)
      throws UncheckedIOException {
    TopDocs topDocs;
    try {
      topDocs = searcher.search(query, MAX_RESULTS_PER_QUERY);
    } catch (IOException e) {
      LOGGER.error("Caught IO exception when trying to run search for %s: %s", query, e.getMessage());
      throw new UncheckedIOException(e);
    }
    ScoreDoc[] scoreDocs = topDocs.scoreDocs;
    if (scoreDocs.length == 0) {
      LOGGER.debug("Search returned no results.");
      return Collections.emptyList();
    }

    return Arrays.stream(scoreDocs). // No need to use `limit` here since we already had Lucene cap the result set size.
        map(scoreDoc -> scoreDoc.score >= SCORE_THRESHOLD ? scoreDoc : null).
        filter(scoreDoc -> scoreDoc != null).
        map(scoreDoc -> {
          try {
            return reader.document(scoreDoc.doc);
          } catch (IOException e) {
            // Yikes, this is v. bad.
            LOGGER.error("Caught IO exception when trying to read doc id %d: %s", scoreDoc.doc, e.getMessage());
            throw new UncheckedIOException(e);
          }
        }).map(this::extractDocFeatures).collect(Collectors.toList());
  }

  // Just extract the id and title for now.  The id contains the patent number, and the title is enough for display.
  private Pair<String, String> extractDocFeatures(Document doc) {
    return Pair.of(doc.get("id"), doc.get("title"));
  }

  private Stream<BooleanQuery> makeQueries(List<String> synonyms, String field) {
    return synonyms.stream().
        filter(syn -> syn != null && syn.isEmpty()).
        map(syn -> makeQuery(syn, field));
  }

  private BooleanQuery makeQuery(String synonym, String field) {
    String queryString = synonym.trim().toLowerCase();
    String[] parts = queryString.split("\\s+");
    PhraseQuery query = new PhraseQuery();
    for (String p : parts) {
      query.add(new Term(field, p));
    }

    BooleanQuery bq = new BooleanQuery();
    bq.add(query, BooleanClause.Occur.MUST);
    KEYWORDS.forEach(term -> bq.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD));

    return bq;
  }
}
