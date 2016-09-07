package com.act.lcms.db.analysis.untargeted;

import com.act.lcms.LCMSNetCDFParser;
import com.act.lcms.LCMSSpectrum;
import com.act.lcms.XZ;
import com.act.utils.rocksdb.ColumnFamilyEnumeration;
import com.act.utils.rocksdb.DBUtil;
import com.act.utils.rocksdb.RocksDBAndHandles;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class WindowingTraceExtractor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(WindowingTraceExtractor.class);
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final byte[] TIMEPOINTS_KEY = "timepoints".getBytes(UTF8);
  private static final Double MIN_MZ = 50.0;
  private static final Double MAX_MZ = 950.0;
  private static final Double STEP_SIZE = 0.005;
  private static final Double WINDOW_WIDTH_FROM_CENTER = 0.01;

  // TODO: make this take a plate barcode and well coordinates instead of a scan file.
  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_SCAN_FILE = "i";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class extracts windowed traces from an LCMS scan files, ",
      "and writes them to an on-disk index for later processing."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INDEX_PATH)
        .argName("index path")
        .desc("A path to the directory where the on-disk index will be stored; must not already exist")
        .hasArg().required()
        .longOpt("index")
    );
    add(Option.builder(OPTION_SCAN_FILE)
        .argName("scan file")
        .desc("A path to the LCMS NetCDF scan file to read")
        .hasArg().required()
        .longOpt("input")
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

  public enum COLUMN_FAMILIES implements ColumnFamilyEnumeration<COLUMN_FAMILIES> {
    RANGE_TO_ID("range_to_id"),
    ID_TO_TRACE("id_to_trace"),
    TIMEPOINTS("timepoints"),
    ;

    private static final Map<String, COLUMN_FAMILIES> reverseNameMap =
        new HashMap<String, COLUMN_FAMILIES>() {{
          for (COLUMN_FAMILIES cf : COLUMN_FAMILIES.values()) {
            put(cf.getName(), cf);
          }
        }};

    private String name;

    COLUMN_FAMILIES(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    public COLUMN_FAMILIES getFamilyByName(String name) {
      return reverseNameMap.get(name);
    }
  }

  public static final List<Triple<Double, Double, Integer>> WINDOWS_W_IDX = Collections.unmodifiableList(
      new ArrayList<Triple<Double, Double, Integer>>(){{
        int i = 0;
        for (double center = MIN_MZ; center <= MAX_MZ; center += STEP_SIZE, i++) {
          add(Triple.of(center - WINDOW_WIDTH_FROM_CENTER, center + WINDOW_WIDTH_FROM_CENTER, i));
        }
      }}
  );

  public WindowingTraceExtractor() {

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
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    // Not enough memory available?  We're gonna need a bigger heap.
    long maxMemory = Runtime.getRuntime().maxMemory();
    if (maxMemory < 1 << 34) {  // 16GB
      String msg = StringUtils.join(
          String.format("You have run this class with a maximum heap size of less than 16GB (%d to be exact). ",
              maxMemory),
          "There is no way this process will complete with that much space available. ",
          "Crank up your heap allocation with -Xmx and try again."
          , "");
      throw new RuntimeException(msg);
    }

    File inputFile = new File(cl.getOptionValue(OPTION_SCAN_FILE));
    if (!inputFile.exists()) {
      System.err.format("Cannot find input scan file at %s", inputFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (rocksDBFile.exists()) {
      System.err.format("Index file at %s already exists--remove and retry", rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(WindowingTraceExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    WindowingTraceExtractor extractor = new WindowingTraceExtractor();

    LOGGER.info("Accessing scan file at %s", inputFile.getAbsolutePath());
    LCMSNetCDFParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> spectrumIterator = parser.getIterator(inputFile.getAbsolutePath());

    LOGGER.info("Opening index at %s", rocksDBFile.getAbsolutePath());
    RocksDB.loadLibrary();
    RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles = null;
    try {
      dbAndHandles = DBUtil.createNewRocksDB(rocksDBFile, COLUMN_FAMILIES.values());

      LOGGER.info("Extracting traces");
      Pair<List<Double>, List<List<Double>>> timesAndTraces = extractor.extractTraces(spectrumIterator);

      LOGGER.info("Writing trace data to on-disk index");
      extractor.writeTracesToDB(dbAndHandles, timesAndTraces.getLeft(), timesAndTraces.getRight());

    } finally {
      if (dbAndHandles != null) {
        dbAndHandles.getDb().close();
      }
    }

    LOGGER.info("Done");
  }

  /**
   * Initiate a data feast of all traces within some window allocation.  OM NOM NOM.
   * @param iter An iterator over an LCMS data file.
   * @return Extracted time points and traces per window (organized by index).
   */
  private Pair<List<Double>, List<List<Double>>> extractTraces(Iterator<LCMSSpectrum> iter) {
    List<List<Double>> allTraces = new ArrayList<List<Double>>(WINDOWS_W_IDX.size()) {{
      for (int i = 0; i < WINDOWS_W_IDX.size(); i++) {
        add(new ArrayList<>());
      }
    }};
    List<Double> times = new ArrayList<>();

    int timepointCounter = 0;
    while (iter.hasNext()) {
      LCMSSpectrum spectrum = iter.next();
      Double time = spectrum.getTimeVal();

      // Store one list of the time values so we can knit times and intensity sums later to form XZs.
      times.add(time);

      // Make sure we have a time entry for this timepoint.  The one we're working on is always the last in the list.
      for (List<Double> trace : allTraces) {
        trace.add(0.0d);
      }
      timepointCounter++;

      if (timepointCounter % 100 == 0) {
        LOGGER.info("Extracted %d timepoints (now at %.3fs)", timepointCounter, time);
      }

      LinkedList<Triple<Double, Double, Integer>> workingQueue = new LinkedList<>();
      // TODO: can we reuse these instead of creating fresh?
      LinkedList<Triple<Double, Double, Integer>> tbdQueue = new LinkedList<>(WINDOWS_W_IDX);

      for (Pair<Double, Double> mzIntensity : spectrum.getIntensities()) {
        Double mz = mzIntensity.getLeft();
        Double intensity = mzIntensity.getRight();

        // First, shift any applicable ranges onto the working queue based on their minimum mz.
        while (!tbdQueue.isEmpty() && tbdQueue.peekFirst().getLeft() <= mz) {
          workingQueue.add(tbdQueue.pop());
        }

        // Next, remove any ranges we've passed.
        while (!workingQueue.isEmpty() && workingQueue.peekFirst().getMiddle() < mz) {
          workingQueue.pop();
        }

        if (workingQueue.isEmpty()) {
          if (tbdQueue.isEmpty()) {
            // If both queues are empty, there are no more windows to consider at all.  One to the next timepoint!
            break;
          }

          // If there's nothing that happens to fit in this range, skip it!
          continue;
        }

        // The working queue should now hold only ranges that include this m/z value.  Sweep line swept!

        // Now add this intensity to the most recent XZ value for each of the items in the working queue (max 2?).
        for (Triple<Double, Double, Integer> triple : workingQueue) {
          List<Double> trace = allTraces.get(triple.getRight());
          Double accumulator = trace.get(trace.size() - 1); // Get the current XZ, i.e. the one at this timepoint.
          trace.set(trace.size() - 1, accumulator + intensity);
        }
      }
    }

    // Trace data has been devoured.  Might want to loosen the belt at this point...
    LOGGER.info("Done extracting %d traces", allTraces.size());
    return Pair.of(times, allTraces);
  }

  private void writeTracesToDB(RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles,
                               List<Double> times,
                               List<List<Double>> allTraces) throws RocksDBException, IOException {
    for (Triple<Double, Double, Integer> triple : WINDOWS_W_IDX) {
      Pair<Double, Double> range = Pair.of(triple.getLeft(), triple.getMiddle());

      byte[] keyBytes = serializeObject(range);
      byte[] valBytes = serializeObject(triple.getRight());

      dbAndHandles.put(COLUMN_FAMILIES.RANGE_TO_ID, keyBytes, valBytes);
    }

    LOGGER.info("Writing timepoints to on-disk index (%d points)", times.size());
    dbAndHandles.put(COLUMN_FAMILIES.TIMEPOINTS, TIMEPOINTS_KEY, serializeDoubleList(times));

    for (int i = 0; i < allTraces.size(); i++) {
      byte[] keyBytes = serializeObject(i);
      byte[] valBytes = serializeDoubleList(allTraces.get(i));
      dbAndHandles.put(COLUMN_FAMILIES.ID_TO_TRACE, keyBytes, valBytes);
      if (i % 1000 == 0) {
        LOGGER.info("Finished writing %d traces", i);
      }
    }

    dbAndHandles.getDb().flush(new FlushOptions());
    LOGGER.info("Done writing trace data to index");
  }

  public Iterator<Pair<Pair<Double, Double>, List<XZ>>> getIteratorOverTraces(File index)
      throws IOException, RocksDBException {
    RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles = DBUtil.openExistingRocksDB(index, COLUMN_FAMILIES.values());
    final RocksIterator rangesIterator = dbAndHandles.newIterator(COLUMN_FAMILIES.RANGE_TO_ID);

    rangesIterator.seekToFirst();

    final List<Double> times;
    try {
      byte[] timeBytes = dbAndHandles.get(COLUMN_FAMILIES.TIMEPOINTS, TIMEPOINTS_KEY);
      times = deserializeDoubleList(timeBytes);
    } catch (RocksDBException e) {
      LOGGER.error("Caught RocksDBException when trying to fetch times: %s", e.getMessage());
      throw new RuntimeException(e);
    } catch (IOException e) {
      LOGGER.error("Caught IOException when trying to fetch timese %s", e.getMessage());
      throw new UncheckedIOException(e);
    }

    return new Iterator<Pair<Pair<Double, Double>, List<XZ>>>() {
      @Override
      public boolean hasNext() {
        return rangesIterator.isValid();
      }

      @Override
      public Pair<Pair<Double, Double>, List<XZ>> next() {
        byte[] keyBytes = rangesIterator.key();
        byte[] valBytes = rangesIterator.value();
        Pair<Double, Double> range;
        Integer traceIndex;
        try {
          range = deserializeObject(keyBytes);
          traceIndex = deserializeObject(valBytes);
        } catch (IOException e) {
          LOGGER.error("Caught IOException when iterating over window ranges: %s", e.getMessage());
          throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
          LOGGER.error("Caught ClassNotFoundException when iterating over window ranges: %s", e.getMessage());
          throw new RuntimeException(e);
        }

        List<Double> trace;
        try {
          byte[] traceBytes = dbAndHandles.get(COLUMN_FAMILIES.ID_TO_TRACE, valBytes);
          if (traceBytes == null) {
            String msg = String.format("Got null byte array back for trace key %d (%.3f - %.3f)",
                traceIndex, range.getLeft(), range.getRight());
            LOGGER.error(msg);
            throw new RuntimeException(msg);
          }
          trace = deserializeDoubleList(traceBytes);
        } catch (RocksDBException e) {
          LOGGER.error("Caught RocksDBException when trying to extract range %d (%f - %f): %s",
              traceIndex, range.getLeft(), range.getRight(), e.getMessage());
          throw new RuntimeException(e);
        } catch (IOException e) {
          LOGGER.error("Caught IOException when trying to extract range %d (%f - %f): %s",
              traceIndex, range.getLeft(), range.getRight(), e.getMessage());
          throw new UncheckedIOException(e);
        }

        if (trace.size() != times.size()) {
          LOGGER.error("Found mistmatching trace and times size (%d vs. %d), continuing anyway",
              trace.size(), times.size());
        }

        List<XZ> xzs = new ArrayList<>(times.size());
        for (int i = 0; i < trace.size() && i < times.size(); i++) {
          xzs.add(new XZ(times.get(i), trace.get(i)));
        }

        /* The Rocks iterator pattern is a bit backwards from the Java model, as we don't need an initial next() call
         * to prime the iterator, and `isValid` indicates whether we've gone past the end of the iterator.  We thus
         * advance only after we've read the current value, which means the next hasNext call after we've walked off the
         * edge will return false. */
        rangesIterator.next();
        return Pair.of(range, xzs);
      }
    };
  }

  private static <T> byte[] serializeObject(T obj) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oo = new ObjectOutputStream(bos)) {
      oo.writeObject(obj);
      oo.flush();
      return bos.toByteArray();
    }
  }

  private static <T> T deserializeObject(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      // Assumes you know what you're getting into when deserializing.  Don't use this blindly.
      return (T) ois.readObject();
    }
  }

  private static byte[] serializeDoubleList(List<Double> vals) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream(vals.size() * Double.BYTES)) {
      byte[] bytes = new byte[Double.BYTES];
      for (Double val : vals) {
        bos.write(ByteBuffer.wrap(bytes).putDouble(val).array());
      }
      return bos.toByteArray();
    }
  }

  private static List<Double> deserializeDoubleList(byte[] byteStream) throws IOException {
    List<Double> results = new ArrayList<>(byteStream.length / Double.BYTES);
    try (ByteArrayInputStream is = new ByteArrayInputStream(byteStream)) {
      byte[] bytes = new byte[Double.BYTES];
      while (is.available() > 0) {
        int readBytes = is.read(bytes); // Same as read(bytes, 0, bytes.length)
        if (readBytes != bytes.length) {
          throw new RuntimeException(String.format("Couldn't read a whole double at a time: %d", readBytes));
        }
        results.add(ByteBuffer.wrap(bytes).getDouble());
      }
    }
    return results;
  }

}
