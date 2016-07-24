package act.installer.pubchem;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * This class implements a parser for Pubchem's TTL (turtle) files.  These contain both the features available in the
 * full Pubchem compound corpus, as well as other features not available in that dataset.
 */
public class PubchemTTLMerger {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PubchemTTLMerger.class);
  private static final Charset UTF8 = Charset.forName("utf-8");

  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_RDF_DIRECTORY = "d";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class extracts Pubchem synonym data from RDF files into an on-disk index, then uses that index to join ",
      "the synonyms and MeSH ids with their corresponding pubchem ids."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index will be stored; must not already exist")
        .hasArg().required()
        .longOpt("index")
    );
    add(Option.builder(OPTION_RDF_DIRECTORY)
        .argName("RDF directory")
        .desc("A path to the directory of Pubchem RDF files")
        .hasArg()
        .longOpt("dir")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private enum PC_RDF_DATA_FILE_CONFIG {
    HASH_TO_SYNONYM("pc_synonym_value", COLUMN_FAMILIES.HASH_TO_SYNONYMS,
        PC_RDF_DATA_TYPES.SYNONYM, PC_RDF_DATA_TYPES.LITERAL, true, false),
    HASH_TO_CID("pc_synonym2compound", COLUMN_FAMILIES.CID_TO_HASHES,
        PC_RDF_DATA_TYPES.SYNONYM, PC_RDF_DATA_TYPES.COMPOUND, false, true),
    HASH_TO_MESH("pc_synonym_topic", COLUMN_FAMILIES.HASH_TO_MESH,
        PC_RDF_DATA_TYPES.SYNONYM, PC_RDF_DATA_TYPES.MeSH, true, false),
    ;

    private String filePrefix;
    private COLUMN_FAMILIES columnFamily;
    private PC_RDF_DATA_TYPES keyType;
    private PC_RDF_DATA_TYPES valType;
    private boolean expectUniqueKeys;
    private boolean reverseSubjectAndObject;

    PC_RDF_DATA_FILE_CONFIG(String filePrefix, COLUMN_FAMILIES columnFamily,
                            PC_RDF_DATA_TYPES keyType, PC_RDF_DATA_TYPES valType,
                            boolean expectUniqueKeys, boolean reverseSubjectAndObject) {
      this.filePrefix = filePrefix;
      this.columnFamily = columnFamily;
      this.keyType = keyType;
      this.valType = valType;
      this.expectUniqueKeys = expectUniqueKeys;
      this.reverseSubjectAndObject = reverseSubjectAndObject;
    }

    public static PC_RDF_DATA_FILE_CONFIG getDataTypeForFile(File file) {
      String name = file.getName();
      for (PC_RDF_DATA_FILE_CONFIG t : PC_RDF_DATA_FILE_CONFIG.values()) {
        if (name.startsWith(t.filePrefix)) {
          return t;
        }
      }
      return null;
    }

    public static AbstractRDFHandler makeHandlerForDataFile(
        Pair<RocksDB, Map<COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles, File file) {
      PC_RDF_DATA_FILE_CONFIG config = getDataTypeForFile(file);
      if (config == null) {
        LOGGER.info("No handler config found for file %s", file.getAbsolutePath());
        return null;
      }

      return new PCRDFHandler(
          dbAndHandles,
          config.columnFamily,
          config.keyType,
          config.valType,
          config.expectUniqueKeys,
          config.reverseSubjectAndObject
      );
    }
  }

  private enum PC_RDF_DATA_TYPES {
    SYNONYM("http://rdf.ncbi.nlm.nih.gov/pubchem/synonym/", PCRDFHandler.OBJECT_TYPE.IRI),
    MeSH("http://id.nlm.nih.gov/mesh/", PCRDFHandler.OBJECT_TYPE.IRI),
    COMPOUND("http://rdf.ncbi.nlm.nih.gov/pubchem/compound/", PCRDFHandler.OBJECT_TYPE.IRI),
    LITERAL("langString", PCRDFHandler.OBJECT_TYPE.LITERAL)
    ;


    private String urlOrDatatypeName;
    /* We only expect one kind of RDF value object at a time depending on the value's namespace, so constrain to that
     * to allow proper dispatch within the handler. */
    private PCRDFHandler.OBJECT_TYPE valueObjectType;

    PC_RDF_DATA_TYPES(String urlOrDatatypeName, PCRDFHandler.OBJECT_TYPE valueObjectType) {
      this.urlOrDatatypeName = urlOrDatatypeName;
      this.valueObjectType = valueObjectType;
    }

    public String getUrlOrDatatypeName() {
      return this.urlOrDatatypeName;
    }

    public PCRDFHandler.OBJECT_TYPE getValueObjectType() {
      return this.valueObjectType;
    }

  }

  private enum COLUMN_FAMILIES {
    HASH_TO_SYNONYMS("hash_to_synonym"),
    CID_TO_HASHES("cid_to_hashes"),
    CID_TO_SYNONYMS("cid_to_synonyms"),
    HASH_TO_MESH("hash_to_MeSH")
    ;

    private String name;

    COLUMN_FAMILIES(String name) {
      this.name = name;
    }

    public String getName() {
      return this.name;
    }
  }

  private static class PCRDFHandler extends AbstractRDFHandler {
    /* The Pubchem RDF corpus represents all subjects as SimpleIRIs, but objects can be IRIs or literals.  Let the child
     * class decide which one it wants to handle. */
    enum OBJECT_TYPE {
      IRI,
      LITERAL,
      ;
    }

    private RocksDB db;
    private ColumnFamilyHandle cfh;
    // Filter out RDF types (based on namespace) that we don't recognize or don't want to process.
    PC_RDF_DATA_TYPES keyType, valueType;
    boolean expectUniqueKeys;
    boolean reverseSubjectAndObject;

    PCRDFHandler(Pair<RocksDB, Map<COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles, COLUMN_FAMILIES columnFamily,
                               PC_RDF_DATA_TYPES keyType, PC_RDF_DATA_TYPES valueType,
                               boolean expectUniqueKeys, boolean reverseSubjectAndObject) {
      db = dbAndHandles.getLeft();
      cfh = dbAndHandles.getRight().get(columnFamily);
      this.keyType = keyType;
      this.valueType = valueType;
      this.expectUniqueKeys = expectUniqueKeys;
      this.reverseSubjectAndObject = reverseSubjectAndObject;
    }

    @Override
    public void handleStatement(Statement st) {
      if (!(st.getSubject() instanceof SimpleIRI)) {
        // If we can't even recognize the type of the subject, something is very wrong.
        String msg = String.format("Unknown type of subject: %s", st.getSubject().getClass().getCanonicalName());
        LOGGER.error(msg);
        throw new RuntimeIOException(msg);
      }

      SimpleIRI subjectIRI = (SimpleIRI) st.getSubject();
      // Filter out keys in namespaces we're not interested in.
      if (!(keyType.getUrlOrDatatypeName().equals(subjectIRI.getNamespace()))) {
        // If we don't recognize the namespace of the subject, then we probably can't handle this triple.
        LOGGER.warn("Unrecognized subject namespace: %s\n", subjectIRI.getLocalName());
        return;
      }

      String subject = subjectIRI.getLocalName();
      String object = null;
      // Let the subclasses tell us what
      if (this.valueType.getValueObjectType() == OBJECT_TYPE.IRI && st.getObject() instanceof SimpleIRI) {
        SimpleIRI objectIRI = (SimpleIRI) st.getObject();
        if (!valueType.getUrlOrDatatypeName().equals(objectIRI.getNamespace())) {
          // If we don't recognize the namespace of the subject, then we probably can't handle this triple.
          LOGGER.warn("Unrecognized object namespace: %s\n", objectIRI.getNamespace());
          return;
        }
        object = objectIRI.getLocalName();
      } else if (this.valueType.getValueObjectType() == OBJECT_TYPE.LITERAL &&
          st.getObject() instanceof SimpleLiteral) {
        SimpleLiteral objectLiteral = (SimpleLiteral) st.getObject();
        IRI datatype = objectLiteral.getDatatype();
        if (!valueType.getUrlOrDatatypeName().equals(datatype.getLocalName())) {
          // We're only expecting string values where we find literals.
          LOGGER.warn("Unrecognized simple literal datatype: %s\n", datatype.getLocalName());
          return;
        }
        object = objectLiteral.getLabel();
      } else {
        String msg = String.format("Unknown type of object: %s", st.getObject().getClass().getCanonicalName());
        LOGGER.error(msg);
        throw new RuntimeIOException(msg);
      }

      /* I considered modeling this decision using subclasses, but it made the configuration to much of a pain.  Maybe
       * we'll do something clever the next time this code needs modification... */
      Pair<String, String> kvPair;
      if (reverseSubjectAndObject) {
        // If the keys, like PC ids, are on the right, we need to swap them around before storing.
        kvPair = Pair.of(object, subject);
      } else {
        kvPair = Pair.of(subject, object);
      }

      // Store the key and value in the appropriate column fa family.
      appendValueToList(db, cfh, kvPair.getKey(), kvPair.getValue(), expectUniqueKeys);
    }

    private static void appendValueToList(RocksDB db, ColumnFamilyHandle cfh,
                                            String key, String val, boolean expectUniqueKeys) {
      StringBuffer buffer = new StringBuffer();
      List<String> storedObjects = null;
      byte[] keyBytes = key.getBytes(UTF8);
      // TODO: pull this out into a helper class or interface.  Alas, we can must extend the AbstractRDFHandler.
      try {
        if (db.keyMayExist(cfh, keyBytes, buffer)) {
          byte[] existingVal = db.get(cfh, keyBytes);
          if (existingVal != null) {
            // TODO: see if this is needed.  I don't think it is, but I'm curious if there are any hash collisions.
            if (expectUniqueKeys) {
              throw new RuntimeException(String.format("Found duplicate key %s when only one is expected", key));
            }
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(existingVal));
            storedObjects = (ArrayList<String>) oi.readObject(); // Note: assumes all values are lists.
          } else {
            storedObjects = new ArrayList<>(1);
          }
        } else {
          storedObjects = new ArrayList<>(1);
        }

        storedObjects.add(val);

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oo = new ObjectOutputStream(bos)) {
          oo.writeObject(storedObjects);
          oo.flush();

          db.put(cfh, keyBytes, bos.toByteArray());
        }
      } catch (RocksDBException e) {
        LOGGER.error("Caughted unexpected RocksDBException: %s", e.getMessage());
        throw new RuntimeException(e);
      } catch (IOException e) {
        LOGGER.error("Caughted unexpected IOException: %s", e.getMessage());
        throw new RuntimeException(e);
      } catch (ClassNotFoundException e) {
        LOGGER.error("Caughted unexpected ClassNotFoundEXception: %s", e.getMessage());
        throw new RuntimeException(e);
      }
    }
  }

  public static Pair<RocksDB, Map<COLUMN_FAMILIES, ColumnFamilyHandle>> createNewRocksDB(File pathToIndex)
      throws RocksDBException {
    RocksDB db = null; // Not auto-closable.
    Map<COLUMN_FAMILIES, ColumnFamilyHandle> columnFamilyHandles = new HashMap<>();

    Options options = new Options().setCreateIfMissing(true);
    System.out.println("Opening index at " + pathToIndex.getAbsolutePath());
    db = RocksDB.open(options, pathToIndex.getAbsolutePath());

    for (COLUMN_FAMILIES cf : COLUMN_FAMILIES.values()) {
      LOGGER.info("Creating column family %s", cf.getName());
      ColumnFamilyHandle cfh =
          db.createColumnFamily(new ColumnFamilyDescriptor(cf.getName().getBytes(UTF8)));
      columnFamilyHandles.put(cf, cfh);
    }

    return Pair.of(db, columnFamilyHandles);
  }

  public static class PubchemSynonyms implements Serializable {
    private static final long serialVersionUID = 2111293889592103961L;

    String pubchemId;
    List<String> synonyms = new ArrayList<>();
    List<String> meshIds = new ArrayList<>();

    public PubchemSynonyms(String pubchemId) {
      this.pubchemId = pubchemId;
    }

    public void addSynonym(String synonym) {
      synonyms.add(synonym);
    }

    public List<String> getSynonyms() {
      return synonyms;
    }

    public void addMeSHId(String id) {
      meshIds.add(id);
    }

    public List<String> getMeSHIds() {
      return meshIds;
    }
  }

  public static void merge(Pair<RocksDB, Map<COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles)
      throws RocksDBException, IOException, ClassNotFoundException {
    RocksDB db = dbAndHandles.getLeft();
    ColumnFamilyHandle pubchemIdCFH = dbAndHandles.getRight().get(COLUMN_FAMILIES.CID_TO_HASHES);
    ColumnFamilyHandle meshCFH = dbAndHandles.getRight().get(COLUMN_FAMILIES.HASH_TO_MESH);
    ColumnFamilyHandle synonymCFH = dbAndHandles.getRight().get(COLUMN_FAMILIES.HASH_TO_SYNONYMS);
    ColumnFamilyHandle mergeResultsCFH = dbAndHandles.getRight().get(COLUMN_FAMILIES.CID_TO_SYNONYMS);

    RocksIterator cidIterator = db.newIterator(pubchemIdCFH);
    // With help from https://github.com/facebook/rocksdb/wiki/Basic-Operations
    for (cidIterator.seekToFirst(); cidIterator.isValid(); cidIterator.next()) {
      byte[] key = cidIterator.key();
      byte[] val = cidIterator.value();
      String pubchemId = new String(key, UTF8);
      List<String> hashes;
      try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(val))) {
        // We know all our values so far have been lists of strings, so this should be completely safe.
        hashes = (List<String>) ois.readObject();
      }

      PubchemSynonyms pubchemSynonyms = new PubchemSynonyms(pubchemId);

      /* The hash keys are based on synonym value, which we can manually compute with:
       *   $ echo -n  'dimethyltin(iv)' | md5
       * This means that MeSH ids are linked to synonyms rather than pubchem ids.  We need to look up each cid-linked
       * hash in both the MeSH and synonym collections, as the key may legitimately exist in both (and serve to link
       * cid to synonym and cid to MeSH). */
      for (String hash : hashes) {
        // Check for existence before fetching.  IIRC doing otherwise might cause segfaults in the RocksDB JNI wrapper.
        StringBuffer stringBuffer = new StringBuffer();
        if (db.keyMayExist(meshCFH, hash.getBytes(), stringBuffer)) {
          byte[] meshIdBytes = db.get(meshCFH, hash.getBytes());
          pubchemSynonyms.addMeSHId(new String(meshIdBytes, UTF8));
        }

        // Might be paranoid, but create a new string buffer to avoid RocksDB flakiness.
        stringBuffer = new StringBuffer();
        if (db.keyMayExist(synonymCFH, hash.getBytes(), stringBuffer)) {
          byte[] synonymBytes = db.get(synonymCFH, hash.getBytes());
          pubchemSynonyms.addSynonym(new String(synonymBytes, UTF8));
        }
      }

      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
           ObjectOutputStream oo = new ObjectOutputStream(bos)) {
        oo.writeObject(pubchemSynonyms);
        oo.flush();

        db.put(mergeResultsCFH, key, bos.toByteArray());
      }
    }
  }

  public static void main(String[] args) throws Exception {
    org.apache.commons.cli.Options opts = new org.apache.commons.cli.Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(PubchemTTLMerger.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(PubchemTTLMerger.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }


    RDFParser parser = Rio.createParser(RDFFormat.TURTLE);

    File rdfDir = new File(cl.getOptionValue(OPTION_RDF_DIRECTORY));
    if (!rdfDir.isDirectory()) {
      System.err.format("Must specify a directory of RDF files to be parsed.\n");
      HELP_FORMATTER.printHelp(PubchemTTLMerger.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File[] filesInDirectoryArray = rdfDir.listFiles(new FilenameFilter() {
      private static final String TTL_GZ_SUFFIX = ".ttl.gz";
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(TTL_GZ_SUFFIX);
      }
    });

    if (filesInDirectoryArray == null || filesInDirectoryArray.length == 0) {
      System.err.format("Found zero compressed TTL files in directory at '%s'.\n", rdfDir.getAbsolutePath());
      HELP_FORMATTER.printHelp(PubchemTTLMerger.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Sort files for stability/sanity.
    List<File> filesInDirectory = Arrays.asList(filesInDirectoryArray);
    Collections.sort(filesInDirectory);

    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (rocksDBFile.exists()) {
      System.err.format("Index directory at '%s' already exists, delete before retrying.\n",
          rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(PubchemTTLMerger.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    Pair<RocksDB, Map<COLUMN_FAMILIES, ColumnFamilyHandle>> dbAndHandles = createNewRocksDB(rocksDBFile);

    for (File rdfFile : filesInDirectory) {
      LOGGER.info("Processing file %s", rdfFile.getAbsolutePath());
      AbstractRDFHandler handler = PC_RDF_DATA_FILE_CONFIG.makeHandlerForDataFile(dbAndHandles, rdfFile);
      if (handler == null) {
        LOGGER.info("Skipping file without defined handler: %s", rdfDir.getAbsolutePath());
        continue;
      }

      parser.setRDFHandler(handler);
      parser.parse(new GZIPInputStream(new FileInputStream(rdfFile)), "");
      LOGGER.info("Successfully parsed file at %s", rdfFile.getAbsolutePath());
    }

    LOGGER.info("Done reading files, merging data.");
    merge(dbAndHandles);


    LOGGER.info("Closing DB to complete merge.");
    dbAndHandles.getLeft().close();
  }
}
