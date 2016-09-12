package act.installer.pubchem;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PubchemTTLMergerTest {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemTTLMergerTest.class);
  private static final String TEST_RDF_PATH = "rdf_synonyms";
  private static final String THIS_DIR = ".";
  private static final String PARENT_DIR = "..";

  private Path tempDirPath;

  @Before
  public void setUp() throws Exception {
    // Create a temporary directory where the RocksDB will live.
    tempDirPath = Files.createTempDirectory(PubchemTTLMergerTest.class.getName(), new FileAttribute[0]);
  }

  @After
  public void tearDown() throws Exception {
    // Clean up temp dir once the test is complete.  TODO: use mocks instead maybe?  But testing RocksDB helps too...
    /* With help from:
     * http://stackoverflow.com/questions/779519/delete-directories-recursively-in-java/27917071#27917071 */
    Files.walkFileTree(tempDirPath, new FileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // walkFileTree may ignore . and .., but I have never found it a /bad/ idea to check for these special names.
        if (!THIS_DIR.equals(file.toFile().getName()) && !PARENT_DIR.equals(file.toFile().getName())) {
          Files.delete(file);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        throw exc;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });

    // One last check to make sure the top level directory is removed.
    if (tempDirPath.toFile().exists()) {
      Files.delete(tempDirPath);
    }
  }

  public List<String> getValForKey(
      Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles,
      PubchemTTLMerger.COLUMN_FAMILIES columnFamily,
      String key
  ) throws Exception {
    RocksDB db = dbAndHandles.getLeft();
    String columnFamilyName = columnFamily.getName();
    ColumnFamilyHandle cfh = dbAndHandles.getRight().get(columnFamily);
    byte[] keyBytes = key.getBytes();
    byte[] valBytes = db.get(cfh, keyBytes);
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(valBytes))) {
      return (List<String>) ois.readObject();
    }
  }

  public PubchemSynonyms getPCSyonymsForKey(
      Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles,
      String key
  ) throws Exception {
    byte[] valBytes = dbAndHandles.getLeft().get(
        dbAndHandles.getRight().get(PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_SYNONYMS), key.getBytes());
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(valBytes))) {
      return (PubchemSynonyms) ois.readObject();
    }
  }

  private static String MD51 = "MD5_00000000000000000000000000000001";
  private static String MD52 = "MD5_00000000000000000000000000000002";
  private static String MD53 = "MD5_00000000000000000000000000000003";

  @Test
  public void testIndexConstructionAndMerge() throws Exception {
    PubchemTTLMerger merger = new PubchemTTLMerger();
    Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles =
        PubchemTTLMerger.createNewRocksDB(tempDirPath.toFile());

    // Alas, we can't swap this with a JAR-safe stream as we must list the lcms.
    File testSynonymFileDir = new File(this.getClass().getResource(TEST_RDF_PATH).getFile());
    List<File> testFiles = Arrays.asList(testSynonymFileDir.listFiles());
    Collections.sort(testFiles);

    Set<String> expectedValues, actualValues;

    merger.buildIndex(dbAndHandles, testFiles);

    dbAndHandles.getLeft().flush(new FlushOptions());

    // Check the hash-to-synonym index.
    expectedValues = new HashSet<>(Arrays.asList("test1"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_SYNONYMS, MD51));
    assertEquals("First hash-to-synonyms returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList("test2"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_SYNONYMS, MD52));
    assertEquals("Second hash-to-synonyms returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList("TEST3", "test3"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_SYNONYMS, MD53));
    assertEquals("Third hash-to-synonyms returns expected value(s)", expectedValues, actualValues);

    // Now check the MESH index.
    expectedValues = new HashSet<>(Arrays.asList("M01"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_MESH, MD51));
    assertEquals("First hash-to-synonyms returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList("M02"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_MESH, MD52));
    assertEquals("Second hash-to-synonyms returns expected value(s)", expectedValues, actualValues);

    // Finally (before merging) check the CID to hash index
    expectedValues = new HashSet<>(Arrays.asList(MD51));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_HASHES, "CID01"));
    assertEquals("First hash-to-synonyms returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList(MD52, MD53));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_HASHES, "CID02"));
    assertEquals("Second hash-to-synonyms returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList(MD53));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_HASHES, "CID03"));
    assertEquals("Third hash-to-synonyms returns expected value(s)", expectedValues, actualValues);

    merger.merge(dbAndHandles);

    PubchemSynonyms expectedSynonyms, actualSynonyms;

    expectedSynonyms = new PubchemSynonyms("CID01");
    expectedSynonyms.addMeSHId("M01");
    expectedSynonyms.addSynonym(PubchemTTLMerger.PC_SYNONYM_TYPES.TRIVIAL_NAME, "test1");
    actualSynonyms = getPCSyonymsForKey(dbAndHandles, "CID01");
    assertEquals("First CID-to-synonyms entry has expected PubchemSynonyms value", expectedSynonyms, actualSynonyms);
    expectedSynonyms = new PubchemSynonyms("CID02");
    expectedSynonyms.addMeSHId("M02");
    expectedSynonyms.addSynonyms(PubchemTTLMerger.PC_SYNONYM_TYPES.UNKNOWN, new HashSet<>(Arrays.asList("test2")));
    expectedSynonyms.addSynonyms(PubchemTTLMerger.PC_SYNONYM_TYPES.INTL_NONPROPRIETARY_NAME,
        new HashSet<>(Arrays.asList("test3", "TEST3")));
    actualSynonyms = getPCSyonymsForKey(dbAndHandles, "CID02");
    assertEquals("Second CID-to-synonyms entry has expected PubchemSynonyms value", expectedSynonyms, actualSynonyms);
    expectedSynonyms = new PubchemSynonyms("CID03");
    expectedSynonyms.addSynonyms(PubchemTTLMerger.PC_SYNONYM_TYPES.INTL_NONPROPRIETARY_NAME,
        new HashSet<>(Arrays.asList("test3", "TEST3")));
    actualSynonyms = getPCSyonymsForKey(dbAndHandles, "CID03");
    assertEquals("ThirdCID-to-synonyms entry has expected PubchemSynonyms value", expectedSynonyms, actualSynonyms);

    dbAndHandles.getLeft().flush(new FlushOptions());
    dbAndHandles.getLeft().close();
  }

  @Test
  public void testValuesAreReadableAfterIndexIsClosedAndReopened() throws Exception {
    PubchemTTLMerger merger = new PubchemTTLMerger();
    Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles =
        PubchemTTLMerger.createNewRocksDB(tempDirPath.toFile());

    // Alas, we can't swap this with a JAR-safe stream as we must list the lcms.
    File testSynonymFileDir = new File(this.getClass().getResource(TEST_RDF_PATH).getFile());
    List<File> testFiles = Arrays.asList(testSynonymFileDir.listFiles());
    Collections.sort(testFiles);

    merger.buildIndex(dbAndHandles, testFiles);
    merger.merge(dbAndHandles);
    dbAndHandles.getLeft().close();

    dbAndHandles = merger.openExistingRocksDB(tempDirPath.toFile());

    Map<String, PubchemSynonyms> expected = new HashMap<String, PubchemSynonyms>() {{
      put("CID01", new PubchemSynonyms("CID01", new HashMap<PubchemTTLMerger.PC_SYNONYM_TYPES, Set<String>>() {{
        put(PubchemTTLMerger.PC_SYNONYM_TYPES.TRIVIAL_NAME, new HashSet<>(Arrays.asList("test1")));
      }}, Arrays.asList("M01")));
      put("CID02", new PubchemSynonyms("CID02", new HashMap<PubchemTTLMerger.PC_SYNONYM_TYPES, Set<String>>() {{
        put(PubchemTTLMerger.PC_SYNONYM_TYPES.UNKNOWN, new HashSet<>(Arrays.asList("test2")));
        put(PubchemTTLMerger.PC_SYNONYM_TYPES.INTL_NONPROPRIETARY_NAME, new HashSet<>(Arrays.asList("TEST3", "test3")));
      }}, Arrays.asList("M02")));
      put("CID03", new PubchemSynonyms("CID03", new HashMap<PubchemTTLMerger.PC_SYNONYM_TYPES, Set<String>>() {{
        put(PubchemTTLMerger.PC_SYNONYM_TYPES.INTL_NONPROPRIETARY_NAME, new HashSet<>(Arrays.asList("TEST3", "test3")));
      }}, Collections.emptyList()));
    }};

    RocksIterator iterator = dbAndHandles.getLeft().newIterator(
        dbAndHandles.getRight().get(PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_SYNONYMS)
    );
    for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
      assertNotNull("Iterator key should never be null", iterator.key());
      assertNotNull("Iterator value should never be null", iterator.value());

      String key = new String(iterator.key());
      PubchemSynonyms synonyms;
      try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(iterator.value()))) {
        // We know all our values so far have been lists of strings, so this should be completely safe.
        synonyms = (PubchemSynonyms) ois.readObject();
      }
      assertEquals(String.format("Pubchem synonyms for %s match expected", key), expected.get(key), synonyms);
    }
  }
}
