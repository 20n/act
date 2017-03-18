/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.lcms.v2.fullindex;

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
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * This is the conjoined twin of Builder.  If IndexBuilder changes in a material way, this class should also.
 */
public class Searcher {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Searcher.class);
  private static final Character RANGE_SEPARATOR = ':';
  private static final String OUTPUT_HEADER = StringUtils.join(new String[] {
      "id", "time", "m/z", "intensity"
  }, "\t");

  public static final String OPTION_INDEX_PATH = "x";
  public static final String OPTION_MZ_RANGE   = "m";
  public static final String OPTION_TIME_RANGE = "t";
  public static final String OPTION_OUTPUT_FILE = "o";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "Queries a triple index constructed by Builder for readings in some m/z and time window.",
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

  public static class Factory {
    public static Searcher makeSearcher(File indexDir)
        throws RocksDBException, ClassNotFoundException, IOException {
      RocksDBAndHandles<ColumnFamilies> dbAndHandles =
          DBUtil.openExistingRocksDB(indexDir, ColumnFamilies.values());
      Searcher searcher = new Searcher(dbAndHandles);
      searcher.init();
      return searcher;
    }
  }

  private RocksDBAndHandles<ColumnFamilies> dbAndHandles;
  private List<MZWindow> mzWindows;
  private List<Float> timepoints;

  Searcher(RocksDBAndHandles<ColumnFamilies> dbAndHandles) {
    this.dbAndHandles = dbAndHandles;
  }

  public static void main(String args[]) throws Exception {
    CLIUtil cliUtil = new CLIUtil(Searcher.class, HELP_MESSAGE, OPTION_BUILDERS);
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

    Searcher searcher = Factory.makeSearcher(indexDir);
    List<TMzI> results = searcher.searchIndexInRange(mzRange, timeRange);

    if (cl.hasOption(OPTION_OUTPUT_FILE)) {
      try (PrintWriter writer = new PrintWriter(new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE)))) {
        Searcher.writeOutput(writer, results);
      }
    } else {
      // Don't close the print writer if we're writing to stdout.
      Searcher.writeOutput(new PrintWriter(new OutputStreamWriter(System.out)), results);
    }

    LOGGER.info("Done");
  }

  private static void writeOutput(PrintWriter writer, List<TMzI> results) throws IOException {
    int counter = 0;
    writer.println(OUTPUT_HEADER);
    for (TMzI triple : results) {
      writer.format("%d\t%.6f\t%.6f\t%.6f\n", counter, triple.getTime(), triple.getMz(), triple.getIntensity());
      counter++;
    }
    writer.flush();
  }

  private static Pair<Double, Double> extractRange(String rangeStr) {
    // Skip empty ranges so we can just limit on time or m/z.
    if (rangeStr == null || rangeStr.isEmpty()) {
      return null;
    }
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

  protected void init() throws RocksDBException, ClassNotFoundException, IOException {
    LOGGER.info("Initializing DB");

    // TODO: hold onto the byte representation of the timepoints so we can use them as keys more easily.
    timepoints = Utils.byteArrayToFloatList(
        dbAndHandles.get(ColumnFamilies.TIMEPOINTS, Builder.TIMEPOINTS_KEY)
    );
    LOGGER.info("Loaded %d timepoints", timepoints.size());
    // Assumes timepoints are sorted.  TODO: check!

    mzWindows = new ArrayList<>();
    RocksDBAndHandles.RocksDBIterator mzIter = dbAndHandles.newIterator(ColumnFamilies.TARGET_TO_WINDOW);
    mzIter.reset();
    while (mzIter.isValid()) {
      // The keys are the target m/z's, so we can ignore them.
      mzWindows.add(Utils.deserializeObject(mzIter.value()));
      mzIter.next();
    }

    // Sort windows so we can easily search through them
    Collections.sort(mzWindows, (a, b) -> a.getTargetMZ().compareTo(b.getTargetMZ()));

    LOGGER.info("Loaded %d m/z windows", mzWindows.size());
  }

  /**
   * Searches an LCMS index for all (time, m/z, intensity) triples within some time and m/z ranges.
   *
   * Note that this method is very much a first-draft/WIP.  There are many opportunities for optimization and
   * improvement here, but this works as an initial attempt.  This method is littered with TODOs, which once TODone
   * should make this a near optimal method of searching through LCMS readings.
   *
   * @param mzRange The range of m/z values for which to search.
   * @param timeRange The time range for which to search.
   * @return A list of (time, m/z, intensity) triples that fall within the specified ranges.
   * @throws RocksDBException
   * @throws ClassNotFoundException
   * @throws IOException
   */
  public List<TMzI> searchIndexInRange(
      Pair<Double, Double> mzRange,
      Pair<Double, Double> timeRange)
      throws RocksDBException, ClassNotFoundException, IOException {
    // TODO: gracefully handle the case when only range is specified.
    // TODO: consider producing some sort of query plan structure that can be used for optimization/explanation.

    DateTime start = DateTime.now();
    /* Demote the time range to floats, as we know that that's how we stored times in the DB.  This tight coupling would
     * normally be a bad thing, but given that this class is joined at the hip with Builder necessarily, it
     * doesn't seem like a terrible thing at the moment. */
    Pair<Float, Float> tRangeF = // My kingdom for a functor!
        Pair.of(timeRange.getLeft().floatValue(), timeRange.getRight().floatValue());

    LOGGER.info("Running search for %.6f <= t <= %.6f, %.6f <= m/z <= %.6f",
        tRangeF.getLeft(), tRangeF.getRight(), mzRange.getLeft(), mzRange.getRight()
    );

    // TODO: short circuit these filters.  The first failure after success => no more possible hits.
    List<Float> timesInRange = timepointsInRange(tRangeF);

    byte[][] timeIndexBytes = extractValueBytes(
        ColumnFamilies.TIMEPOINT_TO_TRIPLES,
        timesInRange,
        Float.BYTES,
        ByteBuffer::putFloat
    );
    // TODO: bail if all the timeIndexBytes lengths are zero.

    List<MZWindow> mzWindowsInRange = mzWindowsInRange(mzRange);

    byte[][] mzIndexBytes = extractValueBytes(
        ColumnFamilies.WINDOW_ID_TO_TRIPLES,
        mzWindowsInRange,
        Integer.BYTES,
        (buff, mz) -> buff.putInt(mz.getIndex())
    );
    // TODO: bail if all the mzIndexBytes are zero.

    /* TODO: if the number of entries in one range is significantly smaller than the other (like an order of magnitude
     * or more, skip extraction of the other set of ids and just filter at the end.  This will be especially helpful
     * when the number of ids in the m/z domain is small, as each time point will probably have >10k ids. */

    LOGGER.info("Found/loaded %d matching time ranges, %d matching m/z ranges",
        timesInRange.size(), mzWindowsInRange.size());

    // TODO: there is no need to union the time indices since they are necessarily distinct.  Just concatenate instead.
    Set<Long> unionTimeIds = unionIdBuffers(timeIndexBytes);
    Set<Long> unionMzIds = unionIdBuffers(mzIndexBytes);
    // TODO: handle the case where one of the sets is empty specially.  Either keep all in the other set or drop all.
    // TODO: we might be able to do this faster by intersecting two sorted lists.
    Set<Long> intersectionIds = new HashSet<>(unionTimeIds);
    /* TODO: this is effectively a hash join, which isn't optimal for sets of wildly different cardinalities.
     * Consider using sort-merge join instead, which will reduce the object overhead (by a lot) and allow us to pass
     * over the union of the ids from each range just once when joining them.  Additionally, just skip this whole step
     * and filter at the end if one of the set's sizes is less than 1k or so and the other is large. */
    intersectionIds.retainAll(unionMzIds);
    LOGGER.info("Id intersection results: t = %d, mz = %d, t ^ mz = %d",
        unionTimeIds.size(), unionMzIds.size(), intersectionIds.size());

    List<Long> idsToFetch = new ArrayList<>(intersectionIds);
    Collections.sort(idsToFetch); // Sort ids so we retrieve them in an order that exploits index locality.

    LOGGER.info("Collecting TMzI triples");
    // Collect all the triples for the ids we extracted.
    // TODO: don't manifest all the bytes: just create a stream of results from the cursor to reduce memory overhead.
    List<TMzI> results = new ArrayList<>(idsToFetch.size());
    byte[][] resultBytes = extractValueBytes(
        ColumnFamilies.ID_TO_TRIPLE,
        idsToFetch,
        Long.BYTES,
        ByteBuffer::putLong
    );
    for (byte[] tmziBytes : resultBytes) {
      results.add(TMzI.readNextFromByteBuffer(ByteBuffer.wrap(tmziBytes)));
    }

    // TODO: do this filtering inline with the extraction.  We shouldn't have to load all the triples before filtering.
    LOGGER.info("Performing final filtering");
    int preFilterTMzICount = results.size();
    results = results.stream().filter(tmzi ->
        tmzi.getTime() >= tRangeF.getLeft() && tmzi.getTime() <= tRangeF.getRight() &&
        tmzi.getMz() >= mzRange.getLeft() && tmzi.getMz() <= mzRange.getRight()
    ).collect(Collectors.toList());
    LOGGER.info("Precise filtering results: %d -> %d", preFilterTMzICount, results.size());

    DateTime end = DateTime.now();
    LOGGER.info("Search completed in %dms", end.getMillis() - start.getMillis());

    // TODO: return a stream instead that can load the triples lazily.
    return results;
  }

  private List<Float> timepointsInRange(Pair<Float, Float> tRange) {
    // TODO: short circuit these filters.  The first failure after success => no more possible hits.
    List<Float> timesInRange = new ArrayList<>( // Use an array list as we'll be accessing by index.
        timepoints.stream().filter(x -> x >= tRange.getLeft() && x <= tRange.getRight()).collect(Collectors.toList())
    );
    if (timesInRange.size() == 0) {
      LOGGER.warn("Found zero times in range %.6f - %.6f", tRange.getLeft(), tRange.getRight());
    }
    return timesInRange;
  }

  private List<MZWindow> mzWindowsInRange(Pair<Double, Double> mzRange) {
    List<MZWindow> mzWindowsInRange = new ArrayList<>( // Same here--access by index.
        mzWindows.stream().filter(x -> rangesOverlap(mzRange.getLeft(), mzRange.getRight(), x.getMin(), x.getMax())).
            collect(Collectors.toList())
    );
    if (mzWindowsInRange.size() == 0) {
      LOGGER.warn("Found zero m/z windows in range %.6f - %.6f", mzRange.getLeft(), mzRange.getRight());
    }
    return mzWindowsInRange;
  }

  /**
   * Extracts the value bytes from the index corresponding to a list of keys of fixed primitive type.
   * @param cf The column family from which to read.
   * @param keys A list of keys whose values to extract.
   * @param keyBytes The exact number of bytes required by a key; should be uniform for primitive-typed keys
   * @param put A function that writes a key to a ByteBuffer.
   * @param <K> The type of the key.
   * @return An array of arrays of bytes, one per key, containing the values of the key at that position.
   * @throws RocksDBException
   */
  private <K> byte[][] extractValueBytes(
      ColumnFamilies cf, List<K> keys, int keyBytes, BiFunction<ByteBuffer, K, ByteBuffer> put)
      throws RocksDBException {
    byte[][] valBytes = new byte[keys.size()][];
    ByteBuffer keyBuffer = ByteBuffer.allocate(keyBytes);
    for (int i = 0; i < keys.size(); i++) {
      K k = keys.get(i);
      keyBuffer.clear();
      put.apply(keyBuffer, k).flip();
      // TODO: try compacting the keyBuffer array to be safe?
      valBytes[i] = dbAndHandles.get(cf, keyBuffer.array());
      assert(valBytes[i] != null);
    }
    return valBytes;
  }

  private static boolean rangesOverlap(double aMin, double aMax, double bMin, double bMax) {
    /* You can push this through negation and De Morgan's Law to get
     * !(aMax < bMin || bMax < aMin) -> !(A to the left of B || B to the left of A) = intersection */
    return aMax >= bMin && bMax >= aMin;
  }

  private static Set<Long> unionIdBuffers(byte[][] idBytes) {
    /* TODO: this doesn't take advantage of the fact that all of the ids are in sorted order in every idBytes sub-array.
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
