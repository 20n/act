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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
    LOGGER.info("Temp dir is %s", tempDirPath);
    // Clean up temp dir once the test is complete.  TODO: use mocks instead maybe?  But testing RocksDB helps too...
    /* With help from:
     * http://stackoverflow.com/questions/779519/delete-directories-recursively-in-java/27917071#27917071 */
    // Don't follow symlinks so as not to leave the tree.
    /*
    Files.walkFileTree(tempDirPath, new FileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
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

    if (tempDirPath.toFile().exists()) {
      Files.delete(tempDirPath);
    }*/
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

  public PubchemTTLMerger.PubchemSynonyms getPCSyonymsForKey(
      Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles,
      String key
  ) throws Exception {
    byte[] valBytes = dbAndHandles.getLeft().get(
        dbAndHandles.getRight().get(PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_SYNONYMS), key.getBytes());
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(valBytes))) {
      return (PubchemTTLMerger.PubchemSynonyms) ois.readObject();
    }
  }

  private static String MD51 = "MD5_00000000000000000000000000000001";
  private static String MD52 = "MD5_00000000000000000000000000000002";
  private static String MD53 = "MD5_00000000000000000000000000000003";

  @Test
  public void testMerge() throws Exception {
    PubchemTTLMerger merger = new PubchemTTLMerger();
    Pair<RocksDB, Map<PubchemTTLMerger.COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles =
        PubchemTTLMerger.createNewRocksDB(tempDirPath.toFile());

    // Alas, we can't swap this with a JAR-safe stream as we must list the files.
    File testSynonymFileDir = new File(this.getClass().getResource(TEST_RDF_PATH).getFile());
    List<File> testFiles = Arrays.asList(testSynonymFileDir.listFiles());
    Collections.sort(testFiles);

    Set<String> expectedValues, actualValues;

    merger.buildIndex(dbAndHandles, testFiles);

    dbAndHandles.getLeft().flush(new FlushOptions());

    // Check the hash-to-synonym index.
    expectedValues = new HashSet<>(Arrays.asList("test1"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_SYNONYMS, MD51));
    assertEquals("First hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList("test2"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_SYNONYMS, MD52));
    assertEquals("Second hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList("TEST3", "test3"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_SYNONYMS, MD53));
    assertEquals("Third hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);

    // Now check the MESH index.
    expectedValues = new HashSet<>(Arrays.asList("M01"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_MESH, MD51));
    assertEquals("First hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList("M02"));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.HASH_TO_MESH, MD52));
    assertEquals("Second hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);

    // Finally (before merging) check the CID to hash index
    expectedValues = new HashSet<>(Arrays.asList(MD51));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_HASHES, "CID01"));
    assertEquals("First hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList(MD52, MD53));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_HASHES, "CID02"));
    assertEquals("Second hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);
    expectedValues = new HashSet<>(Arrays.asList(MD53));
    actualValues = new HashSet<>(getValForKey(dbAndHandles, PubchemTTLMerger.COLUMN_FAMILIES.CID_TO_HASHES, "CID03"));
    assertEquals("Third hash-to-synonyms has returns expected value(s)", expectedValues, actualValues);

    merger.merge(dbAndHandles);

    PubchemTTLMerger.PubchemSynonyms expectedSynonyms, actualSynonyms;

    expectedSynonyms = new PubchemTTLMerger.PubchemSynonyms("CID01");
    expectedSynonyms.addMeSHId("M01");
    expectedSynonyms.addSynonym("test1");
    actualSynonyms = getPCSyonymsForKey(dbAndHandles, "CID01");
    assertEquals("First CID-to-synonyms entry has expected PubchemSynonyms value", expectedSynonyms, actualSynonyms);
    expectedSynonyms = new PubchemTTLMerger.PubchemSynonyms("CID02");
    expectedSynonyms.addMeSHId("M02");
    expectedSynonyms.addSynonyms(Arrays.asList("test2", "test3", "TEST3"));
    actualSynonyms = getPCSyonymsForKey(dbAndHandles, "CID02");
    assertEquals("Second CID-to-synonyms entry has expected PubchemSynonyms value", expectedSynonyms, actualSynonyms);
    expectedSynonyms = new PubchemTTLMerger.PubchemSynonyms("CID03");
    expectedSynonyms.addSynonyms(Arrays.asList("test3", "TEST3"));
    actualSynonyms = getPCSyonymsForKey(dbAndHandles, "CID03");
    assertEquals("ThirdCID-to-synonyms entry has expected PubchemSynonyms value", expectedSynonyms, actualSynonyms);
  }
}
