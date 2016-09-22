package com.act.lcms.v2.fullindex;

import com.act.lcms.LCMSNetCDFParser;
import com.act.lcms.LCMSSpectrum;
import com.act.lcms.MS1;
import com.act.utils.CLIUtil;
import com.act.utils.rocksdb.DBUtil;
import com.act.utils.rocksdb.RocksDBAndHandles;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Builder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Builder.class);
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  /* TIMEPOINTS_KEY is a fixed key into a separate column family in the index that just holds a list of time points.
   * Within that column family, there is only one entry:
   *   "timepoints" -> serialized array of time point doubles
   * and we use this key to write/read those time points.  Since time points are shared across all traces, we can
   * maintain this one copy in the index and reconstruct the XZ pairs as we read trace intensity arrays. */
  // All of these are intentionally package private.
  static final byte[] TIMEPOINTS_KEY = "timepoints".getBytes(UTF8);

  static final Double WINDOW_WIDTH_FROM_CENTER = MS1.MS1_MZ_TOLERANCE_DEFAULT;
  /* This step size should make it impossible for us to miss any readings in the index due to FP error.
   * We could make the index more compact by spacing these windows out a bit, but I'll leave that as a TODO. */
  static final Double MZ_STEP_SIZE = MS1.MS1_MZ_TOLERANCE_DEFAULT / 2.0;
  static final Double MIN_MZ = 50.0;
  static final Double MAX_MZ = 950.0;

  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_SCAN_FILE = "i";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class extracts and indexes readings from an LCMS scan files, ",
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

  public static class Factory {
    public static Builder makeBuilder(File indexDir) throws RocksDBException{
      RocksDB.loadLibrary();
      LOGGER.info("Creating index at %s", indexDir.getAbsolutePath());
      RocksDBAndHandles<ColumnFamilies> dbAndHandles = DBUtil.createNewRocksDB(indexDir, ColumnFamilies.values());
      return new Builder(dbAndHandles);
    }
  }

  private RocksDBAndHandles<ColumnFamilies> dbAndHandles;

  Builder(RocksDBAndHandles<ColumnFamilies> dbAndHandles) {
    this.dbAndHandles = dbAndHandles;
  }

  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(Builder.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    File inputFile = new File(cl.getOptionValue(OPTION_SCAN_FILE));
    if (!inputFile.exists()) {
      cliUtil.failWithMessage("Cannot find input scan file at %s", inputFile.getAbsolutePath());
    }

    File indexDir = new File(cl.getOptionValue(OPTION_INDEX_PATH));
    if (indexDir.exists()) {
      cliUtil.failWithMessage("Index file at %s already exists--remove and retry", indexDir.getAbsolutePath());
    }

    Builder indexBuilder = Factory.makeBuilder(indexDir);
    try {
      indexBuilder.processScan(indexBuilder.makeTargetMasses(), inputFile);
    } finally {
      if (indexBuilder != null) {
        indexBuilder.close();
      }
    }

    LOGGER.info("Done");
  }

  public void close() throws RocksDBException {
    dbAndHandles.close();
  }

  private List<Double> makeTargetMasses() {
    List<Double> targets = new ArrayList<>();
    for (double m = MIN_MZ - MZ_STEP_SIZE; m <= MAX_MZ + MZ_STEP_SIZE; m += MZ_STEP_SIZE) {
      targets.add(m);
    }
    return targets;
  }

  public void processScan(List<Double> targetMZs, File scanFile)
      throws RocksDBException, ParserConfigurationException, XMLStreamException, IOException {
    DateTime start = DateTime.now();
    LOGGER.info("Accessing scan file at %s", scanFile.getAbsolutePath());
    LCMSNetCDFParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> spectrumIterator = parser.getIterator(scanFile.getAbsolutePath());

    WriteOptions writeOptions = new WriteOptions();
    writeOptions.setDisableWAL(true);
    writeOptions.setSync(false);
    dbAndHandles.setWriteOptions(writeOptions);

    // TODO: split targetMZs into batches of ~100k and extract incrementally to allow huge input sets.

    LOGGER.info("Extracting traces");
    List<MZWindow> windows = targetsToWindows(targetMZs);
    extractTriples(spectrumIterator, windows);

    LOGGER.info("Writing search targets to on-disk index");
    writeWindowsToDB(windows);

    DateTime end = DateTime.now();
    LOGGER.info("Index construction completed in %dms", end.getMillis() - start.getMillis());
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

  protected void extractTriples(
      Iterator<LCMSSpectrum> iter,
      List<MZWindow> windows)
      throws RocksDBException, IOException {
    /* Warning: this method makes heavy use of ByteBuffers to perform memory efficient collection of values and
     * conversion of those values into byte arrays that RocksDB can consume.  If you haven't already, go read this
     * tutorial on ByteBuffers: http://mindprod.com/jgloss/bytebuffer.html
     *
     * ByteBuffers are quite low-level structures, and they use some terms you need to watch out for:
     *   capacity: The total number of bytes in the array backing the buffer.  Don't write more than this.
     *   position: The next index in the buffer to read or write a byte.  Moves with each read or write op.
     *   limit:    A mark of where the final byte in the buffer was written.  Don't read past this.
     *             The remaining() call is affected by the limit.
     *   mark:     Ignore this for now, we don't use it.  (We'll always, always read buffers from 0.)
     *
     * And here are some methods that we'll use often:
     *   clear:     Set position = 0, limit = 0.  Pretend the buffer is empty, and is ready for more writes.
     *   flip:      Set limit = position, then position = 0.  This remembers how many bytes were written to the buffer
     *              (as the current position), and then puts the position at the beginning.
     *              Always call this after the write before a read.
     *   rewind:    Set position = 0.  Buffer is ready for reading, but unless the limit was set we might now know how
     *              many bytes there are to read.  Always call flip() before rewind().  Can rewind many times to re-read
     *              the buffer repeatedly.
     *   remaining: How many bytes do we have left to read?  Requires an accurate limit value to avoid garbage bytes.
     *   reset:     Don't use this.  It uses the mark, which we don't need currently.
     *
     * Write/read patterns look like:
     *   buffer.reset(); // Clear out anything already in the buffer.
     *   buffer.put(thing1).put(thing2)... // write a bunch of stuff
     *   buffer.flip(); // Prep for reading.  Call *once*!
     *
     *   while (buffer.hasRemaining()) { buffer.get(); } // Read a bunch of stuff.
     *   buffer.rewind(); // Ready for reading again!
     *   while (buffer.hasRemaining()) { buffer.get(); } // Etc.
     *   buffer.reset(); // Forget what was written previously, buffer is ready for reuse.
     *
     * We use byte buffers because they're fast, efficient, and offer incredibly convenient means of serializing a
     * stream of primitive types to their minimal binary representations.  The same operations on objects + object
     * streams require significantly more CPU cycles, consume more memory, and tend to be brittle (i.e. if a class
     * definition changes slightly, serialization may break).  Since the data we're dealing with is pretty simple, we
     * opt for the low-level approach.
     */

    /* Because we'll eventually use the window indices to map a mz range to a list of triples that fall within that
     * range, verify that all of the indices are unique.  If they're not, we'll end up overwriting the data in and
     * corrupting the structure of the index. */
    ensureUniqueMZWindowIndices(windows);

    // For every mz window, allocate a buffer to hold the indices of the triples that fall in that window.
    ByteBuffer[] mzWindowTripleBuffers = new ByteBuffer[windows.size()];
    for (int i = 0; i < mzWindowTripleBuffers.length; i++) {
      /* Note: the mapping between these buffers and their respective mzWindows is purely positional.  Specifically,
       * mzWindows.get(i).getIndex() != i, but mzWindowTripleBuffers[i] belongs to mzWindows.get(i).  We'll map windows
       * indices to the contents of mzWindowTripleBuffers at the very end of this function. */
      mzWindowTripleBuffers[i] = ByteBuffer.allocate(Long.BYTES * 4096); // Start with 4096 longs = 8 pages per window.
    }

    // Every TMzI gets an index which we'll use later when we're querying by m/z and time.
    long counter = -1; // We increment at the top of the loop.
    // Note: we could also write to an mmapped file and just track pointers, but then we might lose out on compression.

    // We allocate all the buffers strictly here, as we know how many bytes a long and a triple will take.  Then reuse!
    ByteBuffer counterBuffer = ByteBuffer.allocate(Long.BYTES);
    ByteBuffer valBuffer = ByteBuffer.allocate(TMzI.BYTES);
    List<Float> timepoints = new ArrayList<>(2000); // We can be sloppy here, as the count is small.

    /* We use a sweep-line approach to scanning through the m/z windows so that we can aggregate all intensities in
     * one pass over the current LCMSSpectrum (this saves us one inner loop in our extraction process).  The m/z
     * values in the LCMSSpectrum become our "critical" or "interesting points" over which we sweep our m/z ranges.
     * The next window in m/z order is guaranteed to be the next one we want to consider since we address the points
     * in m/z order as well.  As soon as we've passed out of the range of one of our windows, we discard it.  It is
     * valid for a window to be added to and discarded from the working queue in one application of the work loop. */
    LinkedList<MZWindow> tbdQueueTemplate = new LinkedList<>(windows); // We can reuse this template to init the sweep.

    int spectrumCounter = 0;
    while (iter.hasNext()) {
      LCMSSpectrum spectrum = iter.next();
      float time = spectrum.getTimeVal().floatValue();

      // This will record all the m/z + intensity readings that correspond to this timepoint.  Exactly sized too!
      ByteBuffer triplesForThisTime = ByteBuffer.allocate(Long.BYTES * spectrum.getIntensities().size());

      // Batch up all the triple writes to reduce the number of times we hit the disk in this loop.
      // Note: huge success!
      RocksDBAndHandles.RocksDBWriteBatch<ColumnFamilies> writeBatch = dbAndHandles.makeWriteBatch();

      // Initialize the sweep line lists.  Windows go follow: tbd -> working -> done (nowhere).
      LinkedList<MZWindow> workingQueue = new LinkedList<>();
      LinkedList<MZWindow> tbdQueue = (LinkedList<MZWindow>) tbdQueueTemplate.clone(); // clone is in the docs, so okay!
      for (Pair<Double, Double> mzIntensity : spectrum.getIntensities()) {
        // Very important: increment the counter for every triple.  Otherwise we'll overwrite triples = Very Bad (tm).
        counter++;

        // Brevity = soul of wit!
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
          mzWindowTripleBuffers[window.getIndex()] = // Must assign when calling appendOrRealloc.
              Utils.appendOrRealloc(mzWindowTripleBuffers[window.getIndex()], counterBuffer);
        }

        // We flipped after reading, so we should be good to rewind (to be safe) and write here.
        counterBuffer.rewind();
        valBuffer.rewind();
        writeBatch.put(ColumnFamilies.ID_TO_TRIPLE, Utils.toCompactArray(counterBuffer), Utils.toCompactArray(valBuffer));

        // Rewind again for another read.
        counterBuffer.rewind();
        triplesForThisTime.put(counterBuffer);
      }

      writeBatch.write();

      // We should have used up every byte we had.  TODO: make this an assert() call.
      assert(triplesForThisTime.position() == triplesForThisTime.capacity());

      ByteBuffer timeBuffer = ByteBuffer.allocate(Float.BYTES).putFloat(time);
      timeBuffer.flip(); // Prep both bufers for reading so they can be written to the DB.
      triplesForThisTime.flip();
      dbAndHandles.put(ColumnFamilies.TIMEPOINT_TO_TRIPLES,
          Utils.toCompactArray(timeBuffer), Utils.toCompactArray(triplesForThisTime));

      timepoints.add(time);

      spectrumCounter++;
      if (spectrumCounter % 1000 == 0) {
        LOGGER.info("Extracted %d time spectra", spectrumCounter);
      }
    }
    LOGGER.info("Extracted %d total time spectra", spectrumCounter);

    // Now write all the mzWindow to triple indexes.
    RocksDBAndHandles.RocksDBWriteBatch<ColumnFamilies> writeBatch = dbAndHandles.makeWriteBatch();
    ByteBuffer idBuffer = ByteBuffer.allocate(Integer.BYTES);
    for (int i = 0; i < mzWindowTripleBuffers.length; i++) {
      idBuffer.clear();
      idBuffer.putInt(windows.get(i).getIndex());
      idBuffer.flip();

      ByteBuffer triplesBuffer = mzWindowTripleBuffers[i];
      triplesBuffer.flip(); // Prep for read.

      writeBatch.put(ColumnFamilies.WINDOW_ID_TO_TRIPLES, Utils.toCompactArray(idBuffer), Utils.toCompactArray(triplesBuffer));
    }
    writeBatch.write();

    dbAndHandles.put(ColumnFamilies.TIMEPOINTS, TIMEPOINTS_KEY, Utils.floatListToByteArray(timepoints));
    dbAndHandles.flush(true);
  }

  private void ensureUniqueMZWindowIndices(List<MZWindow> windows) {
    Set<Integer> ids = new HashSet<>(windows.size());
    for (MZWindow window : windows) {
      if (ids.contains(window.getIndex())) {
        String msg = String.format("Assumption violation: found duplicate mzWindow index when all should be unique: %d",
            window.getIndex());
        LOGGER.error(msg);
        // A hard invariant has been violated, so crash the program.
        throw new RuntimeException(msg);
      }
    }
  }

  /**
   * Writes all MZWindows to the DB as serialized objects.  Windows are keyed by target MZ (as a Double) for easy lookup
   * in the case that we have a target m/z that exactly matches a window.
   *
   * @param windows The windows to write.
   * @throws RocksDBException
   * @throws IOException
   */
  protected void writeWindowsToDB(List<MZWindow> windows) throws RocksDBException, IOException {
    for (MZWindow window : windows) {
      byte[] keyBytes = Utils.serializeObject(window.getTargetMZ());
      byte[] valBytes = Utils.serializeObject(window);

      dbAndHandles.put(ColumnFamilies.TARGET_TO_WINDOW, keyBytes, valBytes);
    }

    dbAndHandles.flush(true);
    LOGGER.info("Done writing window data to index");
  }
}
