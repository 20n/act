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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;

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

public class LCMSIndexBuilder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(LCMSIndexBuilder.class);
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
      "This class extracts traces from an LCMS scan files for a list of target m/z values, ",
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
    TIMEPOINTS("timepoints"),
    ID_TO_TRIPLE("id_to_triple"),
    TIMEPOINT_ID_TO_TRIPLES("timepoints_to_triples"),
    WINDOW_ID_TO_TRIPLES("windows_to_triples"),
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

  public LCMSIndexBuilder() {
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
      HELP_FORMATTER.printHelp(LCMSIndexBuilder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LCMSIndexBuilder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
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
      HELP_FORMATTER.printHelp(LCMSIndexBuilder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File rocksDBFile = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (rocksDBFile.exists()) {
      System.err.format("Index file at %s already exists--remove and retry\n", rocksDBFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(LCMSIndexBuilder.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    List<Double> targetMZs = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(cl.getOptionValue(OPTION_TARGET_MASSES)))) {
      String line;
      while ((line = reader.readLine()) != null) {
        targetMZs.add(Double.valueOf(line));
      }
    }

    LCMSIndexBuilder extractor = new LCMSIndexBuilder();
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

      WriteOptions writeOptions = new WriteOptions();
      writeOptions.setDisableWAL(true);
      writeOptions.setSync(false);
      dbAndHandles.setWriteOptions(writeOptions);

      // TODO: split targetMZs into batches of ~100k and extract incrementally to allow huge input sets.

      LOGGER.info("Extracting traces");
      List<MZWindow> windows = targetsToWindows(targetMZs);
      extractTriples(dbAndHandles, spectrumIterator, windows);

      LOGGER.info("Writing search targets to on-disk index");
      writeWindowsToDB(dbAndHandles, windows);
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

  private List<MZWindow> targetsToWindows(List<Double> targetMZs) {
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

    return windows;
  }

  public static class TMzI { // (time, mass/charge, intensity) triple
    /* Note: we are cheating here.  We usually throw around Doubles for time and intensity.  To save (a whole bunch of)
     * of bytes, we pare down our time intensity values to floats, knowing they were actually floats to begin with
     * in the NetCDF file but were promoted to doubles to be compatible with the LCMS parser API.  */
    public static final int BYTES = Float.BYTES + Double.BYTES + Float.BYTES;

    // TODO: we might get better compression out of using an index for time.
    float time;
    double mz;
    float intensity;

    public TMzI(float time, double mz, float intensity) {
      this.time = time;
      this.mz = mz;
      this.intensity = intensity;
    }

    public float getTime() {
      return time;
    }

    public double getMz() {
      return mz;
    }

    public float getIntensity() {
      return intensity;
    }

    public void writeToByteBuffer(ByteBuffer buffer) {
      buffer.putFloat(time);
      buffer.putDouble(mz);
      buffer.putFloat(intensity);
    }

    public static void writeToByteBuffer(ByteBuffer buffer, float time, double mz, float intensity) {
      buffer.putFloat(time);
      buffer.putDouble(mz);
      buffer.putFloat(intensity);
    }

    public static TMzI readNextFromByteBuffer(ByteBuffer buffer) {
      float time = buffer.getFloat();
      double mz = buffer.getDouble();
      float intensity = buffer.getFloat();
      return new TMzI(time, mz, intensity);
    }
  }

  private static ByteBuffer appendOrResize(ByteBuffer dest, ByteBuffer toAppend) {
    /* Before reading this function, review this excellent explanation of the behaviors of ByteBuffers:
     * http://mindprod.com/jgloss/bytebuffer.html */

    // Assume toAppend is pre-flipped to avoid a double flip, which would be Very Bad.
    if (dest.capacity() - dest.position() < toAppend.limit()) {
      // Whoops, we have to reallocate!

      ByteBuffer newDest = ByteBuffer.allocate(dest.capacity() << 1); // TODO: make sure these are page-divisible sizes.
      dest.flip(); // Switch dest to reading mode.
      newDest.put(dest); // Write all of dest into the new buffer.
      dest = newDest;
    }
    dest.put(toAppend);
    return dest;
  }

  protected void extractTriples(
      RocksDBAndHandles<COLUMN_FAMILIES> dbAndHandles,
      Iterator<LCMSSpectrum> iter,
      List<MZWindow> windows)
      throws RocksDBException, IOException {

    ByteBuffer[] mzWindowTripleBuffers = new ByteBuffer[windows.size()];
    for (int i = 0; i < mzWindowTripleBuffers.length; i++) {
      // Validate that the windows' indexes are zero-based and contiguous.
      if (windows.get(i).getIndex() != i) {
        String msg = String.format("Assumption violation: window indeces must be zero-based and contiguous, %d != %d",
            i, windows.get(i).getIndex());
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }
      mzWindowTripleBuffers[i] = ByteBuffer.allocate(Long.BYTES * 4096); // Start with 4096 longs = 8 pages per window.
    }

    // Note: we could also write to an mmapped file and just track pointers, but then we might lose out on compression.
    long counter = -1; // We increment at the top of the loop.
    // We allocate all the buffers strictly here, as we expect
    ByteBuffer counterBuffer = ByteBuffer.allocate(Long.BYTES);
    ByteBuffer valBuffer = ByteBuffer.allocate(TMzI.BYTES);
    List<Float> timepoints = new ArrayList<>(2000); // We can be sloppy here, as the count is small.


    /* We use a sweep-line approach to scanning through the m/z windows so that we can aggregate all intensities in
     * one pass over the current LCMSSpectrum (this saves us one inner loop in our extraction process).  The m/z
     * values in the LCMSSpectrum become our "critical" or "interesting points" over which we sweep our m/z ranges.
     * The next window in m/z order is guaranteed to be the next one we want to consider since we address the points
     * in m/z order as well.  As soon as we've passed out of the range of one of our windows, we discard it.  It is
     * valid for a window to be added to and discarded from the working queue in one application of the work loop. */
    LinkedList<MZWindow> tbdQueueTemplate = new LinkedList<>(windows);

    int spectrumCounter = 0;
    while (iter.hasNext()) {
      LCMSSpectrum spectrum = iter.next();
      float time = spectrum.getTimeVal().floatValue();

      // This will record all the m/z + intensity readings that correspond to this timepoint.
      ByteBuffer triplesForThisTime = ByteBuffer.allocate(Long.BYTES * spectrum.getIntensities().size());

      // Batch up all the triple writes to reduce the number of times we hit the disk in this loop.
      RocksDBAndHandles.RocksDBWriteBatch<COLUMN_FAMILIES> writeBatch = dbAndHandles.makeWriteBatch();

      LinkedList<MZWindow> workingQueue = new LinkedList<>();
      LinkedList<MZWindow> tbdQueue = (LinkedList<MZWindow>) tbdQueueTemplate.clone(); // clone is in the docs, so okay!
      for (Pair<Double, Double> mzIntensity : spectrum.getIntensities()) {
        /* Very important: increment the counter for every triple.  It doesn't matter what the base value is so long
         * as it's used and incremented consistently. */
        counter++;

        Double mz = mzIntensity.getLeft();
        Double intensity = mzIntensity.getRight();

        // Reset the buffers so we end up re-using the few bytes we've allocated.
        counterBuffer.clear(); // Empty (virtually).
        counterBuffer.putLong(counter);
        counterBuffer.flip(); // Prep for reading.

        valBuffer.clear(); // Empty (virtually).
        TMzI.writeToByteBuffer(valBuffer, time, mz, intensity.floatValue());
        valBuffer.flip(); // Prep for reading.

        // First, shift any applicable ranges onto the working queue based on their minimum mz.
        while (!tbdQueue.isEmpty() && tbdQueue.peekFirst().getMin() <= mz) {
          workingQueue.add(tbdQueue.pop());
        }

        // Next, remove any ranges we've passed.
        while (!workingQueue.isEmpty() && workingQueue.peekFirst().getMax() < mz) {
          workingQueue.pop(); // TODO: add() this to a recovery queue which can then become the tbdQueue.  Edge cases!
        }
        /* In the old indexed trace extractor world, we could bail here if there were no target m/z's in our window set
         * that matched with the m/z of our current mzIntensity.  However, since we're now also recording the links
         * between timepoints and their (t, m/z, i) triples, we need to keep on keepin' on regardless of whether we have
         * any m/z windows in the working set right now. */

        // The working queue should now hold only ranges that include this m/z value.  Sweep line swept!

        /* Now add this intensity to the buffers of all the windows in the working queue.  Note that since we're only
         * storing the *index* of the triple, these buffers are going to consume less space than they would if we
         * stored everything together. */
        for (MZWindow window : workingQueue) {
          // TODO: count the number of times we add intensities to each window's accumulator for MS1-style warnings.
          counterBuffer.rewind(); // Already flipped.
          mzWindowTripleBuffers[window.getIndex()] = // Must assign when calling appendOrResize.
              appendOrResize(mzWindowTripleBuffers[window.getIndex()], counterBuffer);
        }

        // We flipped after reading, so we should be good to rewind (to be safe) and write here.
        counterBuffer.rewind();
        valBuffer.rewind();
        writeBatch.put(COLUMN_FAMILIES.ID_TO_TRIPLE, toCompactArray(counterBuffer), toCompactArray(valBuffer));

        // Rewind again for another read.
        counterBuffer.rewind();
        triplesForThisTime.put(counterBuffer);
      }

      writeBatch.write();

      // We should have used up every byte we had.  TODO: make this an assert() call.
      assert(triplesForThisTime.position() != triplesForThisTime.capacity());

      ByteBuffer timeBuffer = ByteBuffer.allocate(Float.BYTES).putFloat(time);
      timeBuffer.flip(); // Prep both bufers for reading so they can be written to the DB.
      triplesForThisTime.flip();
      dbAndHandles.put(COLUMN_FAMILIES.TIMEPOINT_ID_TO_TRIPLES,
          toCompactArray(timeBuffer), toCompactArray(triplesForThisTime));

      timepoints.add(time);

      spectrumCounter++;
      LOGGER.info("Extracted %d time spectra", spectrumCounter);
    }

    // Now write all the mzWindow to triple indexes.
    RocksDBAndHandles.RocksDBWriteBatch<COLUMN_FAMILIES> writeBatch = dbAndHandles.makeWriteBatch();
    ByteBuffer idBuffer = ByteBuffer.allocate(Integer.BYTES);
    for (int i = 0; i < mzWindowTripleBuffers.length; i++) {
      idBuffer.clear();
      idBuffer.putInt(windows.get(i).getIndex());
      idBuffer.flip();

      ByteBuffer triplesBuffer = mzWindowTripleBuffers[i];
      triplesBuffer.flip(); // Prep for read.

      writeBatch.put(COLUMN_FAMILIES.WINDOW_ID_TO_TRIPLES, toCompactArray(idBuffer), toCompactArray(triplesBuffer));
    }
    writeBatch.write();

    dbAndHandles.put(COLUMN_FAMILIES.TIMEPOINTS, TIMEPOINTS_KEY, serializeFloatList(timepoints));
    dbAndHandles.flush(true);
  }

  public static byte[] toCompactArray(ByteBuffer src) {
    assertReadyForFreshRead(src);
    if (src.limit() >= src.capacity()) { // No need to compact if limit covers the whole backing array.
      return src.array();
    }

    // Otherwise, we need to allocate and copy into a new array of the correct size.

    // TODO: is Arrays.copyOf any faster than this?  Ideally we want CoW semantics here...

    // Assume src is pre-flipped to avoid a double-flip.
    ByteBuffer dest = ByteBuffer.allocate(src.limit());
    assert(dest.capacity() == src.limit());
    dest.put(src);
    dest.flip();
    assert(dest.limit() == src.limit());
    byte[] asArray = dest.array();
    assert(asArray.length == src.limit());
    return asArray;
  }

  protected static void assertReadyForFreshRead(ByteBuffer b) {
    assert(b.limit() != 0); // A zero limit would cause an immediate buffer underflow.
    assert(b.position() == 0); // A non-zero position means either the buffer hasn't been flipped or has been read from.
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
      //dbAndHandles.put(COLUMN_FAMILIES.ID_TO_TRACE, keyBytes, valBytes);
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
    final RocksIterator rangesIterator = dbAndHandles.newIterator(COLUMN_FAMILIES.TARGET_TO_WINDOW);

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
          byte[] traceBytes = null; // dbAndHandles.get(COLUMN_FAMILIES.ID_TO_TRACE, traceKeyBytes);
          if (traceBytes == null) {
            String msg = String.format("Got null byte array back for trace key %d (target: %.6f)",
                window.getIndex(), window.getTargetMZ());
            LOGGER.error(msg);
            throw new RuntimeException(msg);
          }
          trace = deserializeDoubleList(traceBytes);
        /*} catch (RocksDBException e) {
          LOGGER.error("Caught RocksDBException when trying to extract trace %d (%.6f): %s",
              window.getIndex(), window.getTargetMZ(), e.getMessage());
          throw new RuntimeException(e);*/
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

  private static byte[] serializeFloatList(List<Float> vals) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(vals.size() * Float.BYTES);
    for (Float val : vals) {
      buffer.putFloat(val);
    }
    return buffer.array();
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
