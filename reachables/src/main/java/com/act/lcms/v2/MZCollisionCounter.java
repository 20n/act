package com.act.lcms.v2;

import com.act.utils.CLIUtil;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MZCollisionCounter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MZCollisionCounter.class);

  private static final String OPTION_INPUT_INCHI_LIST = "i";
  private static final String OPTION_OUTPUT_FILE = "o";
  private static final String OPTION_COUNT_WINDOW_INTERSECTIONS = "w";
  private static final String OPTION_ONLY_CONSIDER_IONS = "n";
  private static final Double WINDOW_TOLERANCE = 0.01;

  private static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class calculates the monoisoptic masses of a list of InChIs, computes ion m/z's for those masses, ",
      "and computes the distribution of m/z collisions over these m/z's, producing a histogram as its output."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_INCHI_LIST)
        .argName("input-file")
        .desc("An input file containing just InChIs")
        .hasArg()
        .required()
        .longOpt("input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output-file")
        .desc("Write collision histogram data to an output file (default: stdout)")
        .hasArg()
        .required()
        .longOpt("output-file")
    );
    add(Option.builder(OPTION_COUNT_WINDOW_INTERSECTIONS)
        .desc("Count intersections of +/-0.01 Dalton mass charge windows instead of counting exact collisions")
        .longOpt("window-collisions")
    );
    add(Option.builder(OPTION_ONLY_CONSIDER_IONS)
        .argName("ions")
        .desc("Only consider these ions when computing mass/charges (comma separated list)")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("only-ions")
    );
  }};

  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(MassChargeCalculator.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    File inputFile = new File(cl.getOptionValue(OPTION_INPUT_INCHI_LIST));
    if (!inputFile.exists()) {
      cliUtil.failWithMessage("Input file at does not exist at %s", inputFile.getAbsolutePath());
    }

    List<MassChargeCalculator.MZSource> sources = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        sources.add(new MassChargeCalculator.MZSource(line));
        if (sources.size() % 1000 == 0) {
          LOGGER.info("Loaded %d sources from input file", sources.size());
        }
      }
    }

    Set<String> considerIons = Collections.emptySet();
    if (cl.hasOption(OPTION_ONLY_CONSIDER_IONS)) {
      List<String> ions = Arrays.asList(cl.getOptionValues(OPTION_ONLY_CONSIDER_IONS));
      LOGGER.info("Only considering ions for m/z calculation: %s", StringUtils.join(ions, ", "));
      considerIons = new HashSet<>(ions);
    }

    TSVWriter<String, Long> tsvWriter = new TSVWriter<>(Arrays.asList("collisions", "count"));
    tsvWriter.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));

    try {
      LOGGER.info("Loaded %d sources in total from input file", sources.size());

      MassChargeCalculator.MassChargeMap mzMap = MassChargeCalculator.makeMassChargeMap(sources, considerIons);

      if (!cl.hasOption(OPTION_COUNT_WINDOW_INTERSECTIONS)) {
        // Do an exact analysis of the m/z collisions if windowing is not specified.

        LOGGER.info("Computing precise collision histogram.");
        Iterable<Double> mzs = mzMap.ionMZIter();
        Map<Integer, Long> collisionHistogram =
            histogram(StreamSupport.stream(mzs.spliterator(), false).map(mz -> { // See comment about Iterable below.
              try {
                return mzMap.ionMZToMZSources(mz).size();
              } catch (NoSuchElementException e) {
                LOGGER.error("Caught no such element exception for mz %f: %s", mz, e.getMessage());
                throw e;
              }
            }));
        List<Integer> sortedCollisions = new ArrayList<>(collisionHistogram.keySet());
        Collections.sort(sortedCollisions);
        for (Integer collision : sortedCollisions) {
          tsvWriter.append(new HashMap<String, Long>() {{
            put("collisions", collision.longValue());
            put("count", collisionHistogram.get(collision));
          }});
        }
      } else {
        // Compute windows for every m/z.  We don't care about the original mz values since we just want the count.
        List<Double> mzs = mzMap.ionMZsSorted();
        // Window = lower bound, counter, upper bound.  Counter in the middle = easy to remember what's what.
        LinkedList<Triple<Double, LongAdder, Double>> allWindows = new LinkedList<Triple<Double, LongAdder, Double>>() {{
          for (Double mz : mzs) {
            // CPU for memory trade-off: don't re-compute the window bounds over and over and over and over and over.
            add(Triple.of(mz - WINDOW_TOLERANCE, new LongAdder(), mz + WINDOW_TOLERANCE));
          }
        }};

        // Sweep line time!  The window ranges are the interesting points.  We just accumulate overlap counts as we go.
        LinkedList<Triple<Double, LongAdder, Double>> workingSet = new LinkedList<>();
        List<Triple<Double, LongAdder, Double>> finishedSet = new LinkedList<>();

        while (allWindows.size() > 0) {
          Triple<Double, LongAdder, Double> thisWindow = allWindows.pop();
          // Remove any windows from the working set that don't overlap with the next window.
          while (workingSet.size() > 0 && workingSet.peekFirst().getRight() < thisWindow.getLeft()) {
            finishedSet.add(workingSet.pop());
          }
          workingSet.add(thisWindow);

          // Add one to each overlapping window to remember that this new window intersects with all of them.
          workingSet.forEach(t -> t.getMiddle().increment());
        }

        // All the interesting events are done, so drop the remaining windows into the finished set.
        finishedSet.addAll(workingSet);

        Map<Long, Long> collisionHistogram = histogram(finishedSet.stream().map(t -> t.getMiddle().longValue()));
        List<Long> sortedCollisions = new ArrayList<>(collisionHistogram.keySet());
        Collections.sort(sortedCollisions);
        for (Long collision : sortedCollisions) {
          tsvWriter.append(new HashMap<String, Long>() {{
            put("collisions", collision.longValue());
            put("count", collisionHistogram.get(collision));
          }});
        }
      }
    } finally {
      if (tsvWriter != null) {
        tsvWriter.close();
      }
    }
  }

  public static <T> Map<T, Long> histogram(Stream<T> stream) {
    Map<T, Long> hist = new HashMap<>();
    // This could be done with reduce (fold) or collector cleverness, but this invocation makes the intention clear.
    //stream.forEach(x -> hist.merge(x, 1l, (acc, one) -> one + acc));
    stream.forEach(x -> {
      try {
        hist.put(x, hist.getOrDefault(x, 0l) + 1);
      } catch (NoSuchElementException e) {
        LOGGER.error("Caught no such element exception for %s: %s", x.toString(), e.getMessage());
        throw e;
      }
    });

    return hist;
  }
}
