package com.act.lcms.v2;

import com.act.lcms.LCMSNetCDFParser;
import com.act.lcms.LCMSSpectrum;
import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.utils.rocksdb.ColumnFamilyEnumeration;
import com.act.utils.rocksdb.DBUtil;
import com.act.utils.rocksdb.RocksDBAndHandles;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

public class TraceIndexExtractor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(TraceIndexExtractor.class);
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  /* TIMEPOINTS_KEY is a fixed key into a separate column family in the index that just holds a list of time points.
   * Within that column family, there is only one entry:
   *   "timepoints" -> serialized array of time point doubles
   * and we use this key to write/read those time points.  Since time points are shared across all traces, we can
   * maintain this one copy in the index and reconstruct the XZ pairs as we read trace intensity arrays. */
  private static final byte[] TIMEPOINTS_KEY = "timepoints".getBytes(UTF8);

  private static final Double WINDOW_WIDTH_FROM_CENTER = MS1.MS1_MZ_TOLERANCE_DEFAULT;

  // TODO: make this take a plate barcode and well coordinates instead of a scan file.
  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_SCAN_FILE = "i";
  public static final String OPTION_TARGET_MASSES = "m";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class extracts traces from an LCMS scan lcms for a list of target m/z values, ",
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
    add(Option.builder(OPTION_TARGET_MASSES)
        .argName("target mass file")
        .desc("A file containing m/z values for which to search")
        .hasArg().required()
        .longOpt("target-masses")
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
    TARGET_TO_WINDOW("target_mz_to_window_obj"),
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

  public TraceIndexExtractor() {
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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
      System.err.format("Cannot find input scan file at %s\n", inputFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (rocksDBFile.exists()) {
      System.err.format("Index file at %s already exists--remove and retry\n", rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(TraceIndexExtractor.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    List<Double> targetMZs = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(cl.getOptionValue(OPTION_TARGET_MASSES)))) {
      String line;
      while ((line = reader.readLine()) != null) {
        targetMZs.add(Double.valueOf(line));
      }
    }

    TraceIndexExtractor extractor = new TraceIndexExtractor();
    extractor.processScan(targetMZs, inputFile, rocksDBFile);
  }

  public void processScan(List<Double> targetMZs, File scanFile, File rocksDBFile)
      throws RocksDBException, ParserConfigurationException, XMLStreamException, IOException {
    LOGGER.info("Accessing scan file at %s", scanFile.getAbsolutePath());
    LCMSNetCDFParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> spectrumIterator = parser.getIterator(scanFile.getAbsolutePath());

    LOGGER.info("Opening index at %s", rocksDBFile.getAbsolutePath());
    RocksDB.loadLibrary();
    RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles = null;

    try {
      // TODO: add to existing DB instead of complaining if the DB already exists.  That'll enable one index per scan.
      dbAndHandles = DBUtil.createNewRocksDB(rocksDBFile, COLUMN_FAMILIES.values());

      // TODO: split targetMZs into batches of ~100k and extract incrementally to allow huge input sets.

      LOGGER.info("Extracting traces");
      IndexedTraces windowsTimesAndTraces = runSweepLine(targetMZs, spectrumIterator);

      LOGGER.info("Writing search targets to on-disk index");
      writeWindowsToDB(dbAndHandles, windowsTimesAndTraces.getWindows());

      LOGGER.info("Writing trace data to on-disk index");
      writeTracesToDB(dbAndHandles, windowsTimesAndTraces.getTimes(), windowsTimesAndTraces.getAllTraces());
    } finally {
      if (dbAndHandles != null) {
        dbAndHandles.getDb().close();
      }
    }

    LOGGER.info("Done");
  }

  // Make this public so it can be de/serialized
  public static class MZWindow implements Serializable {
    private static final long serialVersionUID = -3326765598920871504L;

    int index;
    Double targetMZ;
    double min;
    double max;

    public MZWindow(int index, Double targetMZ) {
      this.index = index;
      this.targetMZ = targetMZ;
      this.min = targetMZ - WINDOW_WIDTH_FROM_CENTER;
      this.max = targetMZ + WINDOW_WIDTH_FROM_CENTER;
    }

    public int getIndex() {
      return index;
    }

    public Double getTargetMZ() {
      return targetMZ;
    }

    public double getMin() {
      return min;
    }

    public double getMax() {
      return max;
    }
  }

  private static class IndexedTraces {
    /* IndexedTraces is a 2D array of aggregated intensity values over some <mz window, time> domains.  The organization
     * of this matrix works in conjunction with the list of windows and the list of times that we build in parallel.
     *
     * The three structures look like:
     * windows:
     *   <min_0, target_0, max_0>,
     *   <min_1, target_1, max_1>,
     *   <min_2, target_2, max_2>,
     *   ...
     *
     * times:
     *   t_0,
     *   t_1,
     *   t_2,
     *   ...
     *
     * allTraces (as i_{window_idx}_{time_idx}):
     *   i_0_0, i_0_1, i_0_2, ...
     *   i_1_0, i_1_1, i_1_2, ...
     *   i_2_0, i_2_1, i_2_2, ...
     *   ...
     *
     * So the aggregate intensity for all m/z values in the window <min_1, max_1> at time point 2 is i_1_2.
     *
     * We keep the window and time values separate for 1) efficiency and 2) ordering (i.e. no window -> array maps).
     *
     * When we want to create an iterator over the <time, intensity> traces (i.e. List<XZ>) for each window, we knit the
     * single time array together with the appropriate list of intensity values online, reducing the overhead of storing
     * several hundred million XZ objects (which turns out to be fairly expensive). */
    List<MZWindow> windows;
    List<Double> times;
    List<List<Double>> allTraces;

    public IndexedTraces(List<MZWindow> windows, List<Double> times, List<List<Double>> allTraces) {
      this.windows = windows;
      this.times = times;
      this.allTraces = allTraces;
    }

    public List<MZWindow> getWindows() {
      return windows;
    }

    public List<Double> getTimes() {
      return times;
    }

    public List<List<Double>> getAllTraces() {
      return allTraces;
    }
  }

  /**
   * Initiate a data feast of all traces within some window allocation.  OM NOM NOM.
   * @param iter An iterator over an LCMS data file.
   * @return The windows, time points, and per-window traces.
   */
  private IndexedTraces runSweepLine(List<Double> targetMZs, Iterator<LCMSSpectrum> iter)
      throws RocksDBException, IOException {
    // Create windows for sweep-linin'.
    List<MZWindow> windows = new ArrayList<MZWindow>() {{
      int i = 0;
      for (Double targetMZ : targetMZs) {
        add(new MZWindow(i, targetMZ));
        i++;
      }
    }};

    /* We *must* ensure the windows are sorted in m/z order for the sweep line to work.  However, we don't know anything
     * about the input targetMZs list, which may be immutable or may be in some order the client wants to preserve.
     * Rather than mess with that array, we'll sort the windows in our internal array and leave be he client's targets.
     */
    Collections.sort(windows, (a, b) -> a.getTargetMZ().compareTo(b.getTargetMZ()));

    List<Double> times = new ArrayList<>();

    List<List<Double>> allTraces = new ArrayList<List<Double>>(windows.size()) {{
      for (int i = 0; i < windows.size(); i++) {
        add(new ArrayList<>());
      }
    }};

    // Keep an array of accumulators around to reduce the overhead of accessing the trace matrix for accumulation.
    double[] sumIntensitiesInEachWindow = new double[windows.size()];

    int timepointCounter = 0;
    while (iter.hasNext()) {
      LCMSSpectrum spectrum = iter.next();
      Double time = spectrum.getTimeVal();

      // Store one list of the time values so we can knit times and intensity sums later to form XZs.
      times.add(time);

      for (int i = 0; i < sumIntensitiesInEachWindow.length; i++) {
        sumIntensitiesInEachWindow[i] = 0.0;
      }

      timepointCounter++;

      if (timepointCounter % 100 == 0) {
        LOGGER.info("Extracted %d timepoints (now at %.3fs)", timepointCounter, time);
      }

      /* We use a sweep-line approach to scanning through the m/z windows so that we can aggregate all intensities in
       * one pass over the current LCMSSpectrum (this saves us one inner loop in our extraction process).  The m/z
       * values in the LCMSSpectrum become our "critical" or "interesting points" over which we sweep our m/z ranges.
       * The next window in m/z order is guaranteed to be the next one we want to consider since we address the points
       * in m/z order as well.  As soon as we've passed out of the range of one of our windows, we discard it.  It is
       * valid for a window to be added to and discarded from the working queue in one application of the work loop. */
      LinkedList<MZWindow> workingQueue = new LinkedList<>();
      // TODO: can we reuse these instead of creating fresh?
      LinkedList<MZWindow> tbdQueue = new LinkedList<>(windows);

      // Assumption: these arrive in m/z order.
      for (Pair<Double, Double> mzIntensity : spectrum.getIntensities()) {
        Double mz = mzIntensity.getLeft();
        Double intensity = mzIntensity.getRight();

        // First, shift any applicable ranges onto the working queue based on their minimum mz.
        while (!tbdQueue.isEmpty() && tbdQueue.peekFirst().getMin() <= mz) {
          workingQueue.add(tbdQueue.pop());
        }

        // Next, remove any ranges we've passed.
        while (!workingQueue.isEmpty() && workingQueue.peekFirst().getMax() < mz) {
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

        /* Now add this intensity to accumulator value for each of the items in the working queue.
         * By the end of the outer loop, trace(t) = Sum(intensity) | win_min <= m/z <= win_max @ time point # t */
        for (MZWindow window : workingQueue) {
          // TODO: count the number of times we add intensities to each window's accumulator for MS1-style warnings.
          sumIntensitiesInEachWindow[window.getIndex()] += intensity;
        }
      }

      /* Extend allTraces to add a row of accumulated intensity values for this time point.  We build this incrementally
       * because the LCMSSpectrum iterator doesn't tell us how many time points to expect up front. */
      for (int i = 0; i < sumIntensitiesInEachWindow.length; i++) {
        allTraces.get(i).add(sumIntensitiesInEachWindow[i]);
      }
    }

    // Trace data has been devoured.  Might want to loosen the belt at this point...
    LOGGER.info("Done extracting %d traces", allTraces.size());

    return new IndexedTraces(windows, times, allTraces);
  }

  private void writeWindowsToDB(RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles, List<MZWindow> windows)
      throws RocksDBException, IOException {
    for (MZWindow window : windows) {
      byte[] keyBytes = serializeObject(window.getTargetMZ());
      byte[] valBytes = serializeObject(window);

      dbAndHandles.put(COLUMN_FAMILIES.TARGET_TO_WINDOW, keyBytes, valBytes);
    }

    dbAndHandles.getDb().flush(new FlushOptions());
    LOGGER.info("Done writing window data to index");
  }

  private void writeTracesToDB(RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles,
                               List<Double> times,
                               List<List<Double>> allTraces) throws RocksDBException, IOException {

    LOGGER.info("Writing timepoints to on-disk index (%d points)", times.size());
    dbAndHandles.put(COLUMN_FAMILIES.TIMEPOINTS, TIMEPOINTS_KEY, serializeDoubleList(times));
    for (int i = 0; i < allTraces.size(); i++) {
      byte[] keyBytes = serializeObject(i);
      byte[] valBytes = serializeDoubleList(allTraces.get(i));
      dbAndHandles.put(COLUMN_FAMILIES.ID_TO_TRACE, keyBytes, valBytes);
      if (i % 1000 == 0) {
        LOGGER.info("Finished writing %d traces", i);
      }

      // Drop this trace as soon as it's written so the GC can pick it up and hopefully reduce memory pressure.
      allTraces.set(i, Collections.emptyList());
    }

    dbAndHandles.getDb().flush(new FlushOptions());
    LOGGER.info("Done writing trace data to index");
  }

  public Iterator<Pair<Double, List<XZ>>> getIteratorOverTraces(File index)
      throws IOException, RocksDBException {
    RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles = DBUtil.openExistingRocksDB(index, COLUMN_FAMILIES.values());
    final RocksDBAndHandles.RocksDBIterator rangesIterator = dbAndHandles.newIterator(COLUMN_FAMILIES.TARGET_TO_WINDOW);

    rangesIterator.reset();

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

    return new Iterator<Pair<Double, List<XZ>>>() {
      int windowNum = 0;

      @Override
      public boolean hasNext() {
        return rangesIterator.isValid();
      }

      @Override
      public Pair<Double, List<XZ>> next() {
        byte[] valBytes = rangesIterator.value();
        MZWindow window;
        windowNum++;
        try {
          window = deserializeObject(valBytes);
        } catch (IOException e) {
          LOGGER.error("Caught IOException when iterating over mz windows (%d): %s", windowNum, e.getMessage());
          throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
          LOGGER.error("Caught ClassNotFoundException when iterating over mz windows (%d): %s",
              windowNum, e.getMessage());
          throw new RuntimeException(e);
        }

        byte[] traceKeyBytes;
        try {
          traceKeyBytes = serializeObject(window.getIndex());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }

        List<Double> trace;
        try {
          byte[] traceBytes = dbAndHandles.get(COLUMN_FAMILIES.ID_TO_TRACE, traceKeyBytes);
          if (traceBytes == null) {
            String msg = String.format("Got null byte array back for trace key %d (target: %.6f)",
                window.getIndex(), window.getTargetMZ());
            LOGGER.error(msg);
            throw new RuntimeException(msg);
          }
          trace = deserializeDoubleList(traceBytes);
        } catch (RocksDBException e) {
          LOGGER.error("Caught RocksDBException when trying to extract trace %d (%.6f): %s",
              window.getIndex(), window.getTargetMZ(), e.getMessage());
          throw new RuntimeException(e);
        } catch (IOException e) {
          LOGGER.error("Caught IOException when trying to extract trace %d (%.6f): %s",
              window.getIndex(), window.getTargetMZ(), e.getMessage());
          throw new UncheckedIOException(e);
        }

        if (trace.size() != times.size()) {
          LOGGER.error("Found mismatching trace and times size (%d vs. %d), continuing anyway",
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
        return Pair.of(window.getTargetMZ(), xzs);
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
