package com.act.lcms.v2;

import com.act.utils.CLIUtil;
import com.act.utils.rocksdb.DBUtil;
import com.act.utils.rocksdb.RocksDBAndHandles;
import io.netty.buffer.ByteBuf;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is the conjoined twin of LCMSIndexBuilder.  If IndexBuilder changes in a material way, this class should also.
 */
public class LCMSIndexSearcher {
  private static final Logger LOGGER = LogManager.getFormatterLogger(LCMSIndexSearcher.class);
  private static final Character RANGE_SEPARATOR = ':';
  private static final String OUTPUT_HEADER = StringUtils.join(new String[] {
      "id", "time", "m/z", "intensity"
  }, "\t");

  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_MZ_RANGE   = "m";
  public static final String OPTION_TIME_RANGE = "t";
  public static final String OPTION_OUTPUT_FILE = "o";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "Queries a triple index constructed by LCMSIndexBuilder for readings in some m/z and time window.",
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index will be stored; must not already exist")
        .hasArg().required()
        .longOpt("index")
    );
    add(Option.builder(OPTION_MZ_RANGE)
        .argName("m/z range")
        .desc("An m/z range to query separated by a colon, like 151.0:152.0")
        .hasArg()
        .longOpt("mz-range")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output file")
        .desc("A destination at which to write the found triples as a TSV (default is stdout)")
        .hasArg()
        .longOpt("output")
    );
    add(Option.builder(OPTION_TIME_RANGE)
        .argName("time range")
        .desc("An time range to query separated by a colon, like 45.0:50.0")
        .hasArg()
        .longOpt("time-range")
    );
  }};

  public static void main(String args[]) throws Exception {
    CLIUtil cliUtil = new CLIUtil(LCMSIndexSearcher.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    File indexDir = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (!indexDir.exists() || !indexDir.isDirectory()) {
      cliUtil.failWithMessage("Unable to read index directory at %s", indexDir.getAbsolutePath());
    }

    if (!cl.hasOption(OPTION_MZ_RANGE) && !cl.hasOption(OPTION_TIME_RANGE)) {
      cliUtil.failWithMessage("Extracting all readings is not currently supported; specify an m/z or time range");
    }

    Pair<Double, Double> mzRange = extractRange(cl.getOptionValue(OPTION_MZ_RANGE));
    Pair<Double, Double> timeRange = extractRange(cl.getOptionValue(OPTION_TIME_RANGE));

    LCMSIndexSearcher searcher = Factory.makeLCMSIndexSearcher(indexDir);
    List<LCMSIndexBuilder.TMzI> results = searcher.searchIndexInRange(mzRange, timeRange);

    if (cl.hasOption(OPTION_OUTPUT_FILE)) {
      try (PrintWriter writer = new PrintWriter(new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE)))) {
        searcher.writeOutput(writer, results);
      }
    } else {
      // Don't close the print writer if we're writing to stdout.
      searcher.writeOutput(new PrintWriter(new OutputStreamWriter(System.out)), results);
    }

    LOGGER.info("Done");
  }

  private void writeOutput(PrintWriter writer, List<LCMSIndexBuilder.TMzI> results) throws IOException {
    int counter = 0;
    writer.println(OUTPUT_HEADER);
    for (LCMSIndexBuilder.TMzI triple : results) {
      writer.format("%d\t%.6f\t%.6f\t%.6f\n", counter, triple.getTime(), triple.getMz(), triple.getIntensity());
      counter++;
    }
    writer.flush();
  }

  public static Pair<Double, Double> extractRange(String rangeStr) {
    String[] parts = StringUtils.split(rangeStr, RANGE_SEPARATOR);
    if (parts.length == 1) {
      LOGGER.info("Found only one value in ranged '%s', returning closed range (for exact extraction)", rangeStr);
      Double exactVal = Double.valueOf(parts[0]);
      return Pair.of(exactVal, exactVal);
    } else if (parts.length == 2) {
      Double lowerBound = Double.valueOf(parts[0]);
      Double upperBound = Double.valueOf(parts[1]);
      if (upperBound < lowerBound) {
        String msg = String.format(
            "Lower bound %.6f exceeds upper bound %.6f.  Cowardly refusing to search for an empty range",
            lowerBound, upperBound);
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }
      return Pair.of(lowerBound, upperBound);
    } else {
      String msg = String.format(
          "Unable to parse range string '%s'; did you use the correct separator ('%c')?", RANGE_SEPARATOR);
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  private static <T> T deserializeObject(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      // Assumes you know what you're getting into when deserializing.  Don't use this blindly.
      return (T) ois.readObject();
    }
  }

  public static class Factory {
    public static LCMSIndexSearcher makeLCMSIndexSearcher(File indexDir)
        throws RocksDBException, ClassNotFoundException, IOException {
      LCMSIndexSearcher searcher = new LCMSIndexSearcher(indexDir);
      searcher.init();
      return searcher;
    }
  }

  private File indexDir;
  private RocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> dbAndHandles;
  private List<LCMSIndexBuilder.MZWindow> mzWindows;
  private List<Float> timepoints;

  LCMSIndexSearcher(File indexDir) throws RocksDBException, ClassNotFoundException, IOException {
    this.indexDir = indexDir;
  }

  public void init() throws RocksDBException, ClassNotFoundException, IOException {
    dbAndHandles = DBUtil.openExistingRocksDB(indexDir, LCMSIndexBuilder.COLUMN_FAMILIES.values());
    LOGGER.info("Initializing DB");

    // TODO: hold onto the byte representation of the timepoints so we can use them as keys more easily.
    timepoints = LCMSIndexBuilder.byteArrayToFloatList(
        dbAndHandles.get(LCMSIndexBuilder.COLUMN_FAMILIES.TIMEPOINTS, LCMSIndexBuilder.TIMEPOINTS_KEY)
    );
    LOGGER.info("Loaded %d timepoints", timepoints.size());
    // Assumes timepoints are sorted.  TODO: check!

    mzWindows = new ArrayList<>();
    RocksIterator mzIter = dbAndHandles.newIterator(LCMSIndexBuilder.COLUMN_FAMILIES.TARGET_TO_WINDOW);
    mzIter.seekToFirst();
    while (mzIter.isValid()) {
      // The keys are the target m/z's, so we can ignore them.
      mzWindows.add(deserializeObject(mzIter.value()));
      mzIter.next();
    }

    // Sort windows so we can easily search through them
    Collections.sort(mzWindows, (a, b) -> a.getTargetMZ().compareTo(b.getTargetMZ()));

    LOGGER.info("Loaded %d m/z windows", mzWindows.size());
  }

  public List<LCMSIndexBuilder.TMzI> searchIndexInRange(
      Pair<Double, Double> mzRange,
      Pair<Double, Double> timeRange)
      throws RocksDBException, ClassNotFoundException, IOException {
    DateTime start = DateTime.now();
    // Demote the time range to floats, as we know that that's how we stored times in the DB.
    Pair<Float, Float> tRangeF = // My kingdom for a functor!
        Pair.of(timeRange.getLeft().floatValue(), timeRange.getRight().floatValue());

    LOGGER.info("Running search for %.6f <= t <= %.6f, %.6f <= m/z <= %.6f",
        tRangeF.getLeft(), tRangeF.getRight(), mzRange.getLeft(), mzRange.getRight()
    );

    // TODO: short circuit these filters.  The first failure after success => no more possible hits.
    List<Float> timesInRange = new ArrayList<>( // Use an array list as we'll be accessing by index.
        timepoints.stream().filter(x -> x >= tRangeF.getLeft() && x <= tRangeF.getRight()).collect(Collectors.toList())
    );
    if (timesInRange.size() == 0) {
      LOGGER.warn("Found zero times in range %.6f - %.6f, aborting", tRangeF.getLeft(), tRangeF.getRight());
      return null;
    }

    List<LCMSIndexBuilder.MZWindow> mzWindowsInRange = new ArrayList<>( // Same here--access by index.
        mzWindows.stream().filter(x -> rangesOverlap(mzRange.getLeft(), mzRange.getRight(), x.getMin(), x.getMax())).
            collect(Collectors.toList())
    );
    if (mzWindowsInRange.size() == 0) {
      LOGGER.warn("Found zero m/z windows in range %.6f - %.6f, aborting", mzRange.getLeft(), mzRange.getRight());
      return null;
    }

    LOGGER.info("Found %d matching time ranges, %d matching m/z ranges", timesInRange.size(), mzWindowsInRange.size());

    byte[][] timeIndexBytes = new byte[timesInRange.size()][];
    ByteBuffer timeBuffer = ByteBuffer.allocate(Float.BYTES);
    for (int i = 0; i < timesInRange.size(); i++) {
      Float t = timesInRange.get(i);
      timeBuffer.clear();
      timeBuffer.putFloat(t).flip();
      // TODO: try compacting the timeBuffer array to be safe?
      timeIndexBytes[i] = dbAndHandles.get(LCMSIndexBuilder.COLUMN_FAMILIES.TIMEPOINT_TO_TRIPLES, timeBuffer.array());
      assert(timeIndexBytes[i] != null);
    }
    // TODO: bail if all the timeIndexBytes lengths are zero.

    byte[][] mzIndexBytes = new byte[mzWindowsInRange.size()][];
    ByteBuffer mzIndexBuffer = ByteBuffer.allocate(Integer.BYTES);
    for (int i = 0; i < mzWindowsInRange.size(); i++) {
      LCMSIndexBuilder.MZWindow mz = mzWindowsInRange.get(i);
      mzIndexBuffer.clear();
      mzIndexBuffer.putInt(mz.getIndex()).flip();
      // TODO: try compacting the mzIndexBuffer array to be safe?
      mzIndexBytes[i] =
          dbAndHandles.get(LCMSIndexBuilder.COLUMN_FAMILIES.WINDOW_ID_TO_TRIPLES, mzIndexBuffer.array());
      assert(mzIndexBytes[i] != null);
    }
    // TODO: bail if all the mzIndexBytes are zero.

    Set<Long> unionTimeIds = unionIdBuffers(timeIndexBytes);
    Set<Long> unionMzIds = unionIdBuffers(mzIndexBytes);
    // TODO: handle the case where one of the sets is empty specially.  Either keep all in the other set or drop all.
    // TODO: we might be able to do this faster by intersecting two sorted lists.
    Set<Long> intersectionIds = new HashSet<>(unionTimeIds);
    intersectionIds.retainAll(unionMzIds);
    LOGGER.info("Id intersection results: t = %d, mz = %d, t ^ mz = %d",
        unionTimeIds.size(), unionMzIds.size(), intersectionIds.size());

    List<Long> idsToFetch = new ArrayList<>(intersectionIds);
    Collections.sort(idsToFetch); // Sort ids so we retrieve them in an order that exploits index locality.

    LOGGER.info("Collecting TMzI triples");
    // Collect all the triples for the ids we extracted.
    List<LCMSIndexBuilder.TMzI> results = new ArrayList<>(idsToFetch.size());
    ByteBuffer idBuffer = ByteBuffer.allocate(Long.BYTES);
    for (Long id : idsToFetch) {
      idBuffer.clear();
      idBuffer.putLong(id).flip();
      byte[] tmziBytes = dbAndHandles.get(LCMSIndexBuilder.COLUMN_FAMILIES.ID_TO_TRIPLE, idBuffer.array());
      results.add(LCMSIndexBuilder.TMzI.readNextFromByteBuffer(ByteBuffer.wrap(tmziBytes)));
    }
    LOGGER.info("Performing final filtering");
    int preFilterTMzICount = results.size();
    results = results.stream().filter(tmzi ->
        tmzi.getTime() >= tRangeF.getLeft() && tmzi.getTime() <= tRangeF.getRight() &&
        tmzi.getMz() >= mzRange.getLeft() && tmzi.getMz() <= mzRange.getRight()
    ).collect(Collectors.toList());
    LOGGER.info("Precise filtering results: %d -> %d", preFilterTMzICount, results.size());

    DateTime end = DateTime.now();
    LOGGER.info("Search complete in %dms", end.getMillis() - start.getMillis());

    return results;
  }

  private static boolean rangesOverlap(double aMin, double aMax, double bMin, double bMax) {
    /* You can push this through negation and De Morgan's Law to get
     * !(aMax < bMin || bMax < aMin) -> !(A to the left of B || B to the left of A) = intersection */
    return aMax >= bMin && bMax >= aMin;
  }

  private static Set<Long> unionIdBuffers(byte[][] idBytes) {
    /* Note: this doesn't take advantage of the fact that all of the ids are in sorted order in every idBytes sub-array.
     * We should be able to exploit that.  For now, we'll just start by hashing the ids. */
    Set<Long> uniqueIds = new HashSet<>();
    for (int i = 0; i < idBytes.length; i++) {
      assert(idBytes[i] != null);
      ByteBuffer idsBuffer = ByteBuffer.wrap(idBytes[i]);
      while (idsBuffer.hasRemaining()) {
        uniqueIds.add(idsBuffer.getLong());
      }
    }
    return uniqueIds;
  }
}
