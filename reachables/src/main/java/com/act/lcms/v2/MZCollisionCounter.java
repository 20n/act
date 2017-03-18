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

package com.act.lcms.v2;

import com.act.utils.CLIUtil;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
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
  private static final String OPTION_WINDOW_HALFWIDTH = "s";
  private static final String OPTION_ONLY_CONSIDER_IONS = "n";
  private static final Double DEFAULT_WINDOW_TOLERANCE = 0.01;

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
        .desc("Write collision histogram data to an output file")
        .hasArg()
        .required()
        .longOpt("output-file")
    );
    add(Option.builder(OPTION_COUNT_WINDOW_INTERSECTIONS)
        .desc("Count intersections of +/-0.01 Dalton mass charge windows instead of counting exact collisions; " +
            "counts the number of structures that might fall within each window and bins by count")
        .longOpt("window-collisions")
    );
    add(Option.builder(OPTION_WINDOW_HALFWIDTH)
        .desc(String.format("Sets the window half-width for collision counting in window mode, default is %.3f",
            DEFAULT_WINDOW_TOLERANCE))
        .longOpt("window-half-width")
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
        /* After some deliberation (thanks Gil!), the windowed variant of this calculation counts the number of
         * structures whose 0.01 Da m/z windows (for some set of ions) overlap with each other.
         *
         * For example, let's assume we have five total input structures, and are only searching for one ion.  Let's
         * also assume that three of those structures have m/z A and the remaining two have m/z B.  The windows might
         * look like this in the m/z domain:
         * |----A----|
         *        |----B----|
         * Because A represents three structures and overlaps with B, which represents two, we assign A a count of 5--
         * this is the number of structures we believe could fall into the range of A given our current peak calling
         * approach.  Similarly, B is assigned a count of 5, as the possibility for collision/confusion is symmetric.
         *
         * Note that this is an over-approximation of collisions, as we could more precisely only consider intersections
         * when the exact m/z of B falls within the window around A and vice versa.  However, because we have observed
         * cases where the MS sensor doesn't report structures at exactly the m/z we predict, we employ this weaker
         * definition of intersection to give a slightly pessimistic view of what confusions might be possible. */
        // Compute windows for every m/z.  We don't care about the original mz values since we just want the count.
        List<Double> mzs = mzMap.ionMZsSorted();

        final Double windowHalfWidth;
        if (cl.hasOption(OPTION_WINDOW_HALFWIDTH)) {
          // Don't use get with default for this option, as we want the exact FP value of the default tolerance.
          windowHalfWidth = Double.valueOf(cl.getOptionValue(OPTION_WINDOW_HALFWIDTH));
        } else {
          windowHalfWidth = DEFAULT_WINDOW_TOLERANCE;
        }

        /* Window = (lower bound, upper bound), counter of represented m/z's that collide with this window, and number
         * of representative structures (which will be used in counting collisions). */
        LinkedList<CollisionWindow> allWindows = new LinkedList<CollisionWindow>() {{
          for (Double mz : mzs) {
            // CPU for memory trade-off: don't re-compute the window bounds over and over and over and over and over.
            try {
              add(new CollisionWindow(mz, windowHalfWidth, mzMap.ionMZToMZSources(mz).size()));
            } catch (NoSuchElementException e) {
              LOGGER.error("Caught no such element exception for mz %f: %s", mz, e.getMessage());
              throw e;
            }
          }
        }};

        // Sweep line time!  The window ranges are the interesting points.  We just accumulate overlap counts as we go.
        LinkedList<CollisionWindow> workingSet = new LinkedList<>();
        List<CollisionWindow> finished = new LinkedList<>();

        while (allWindows.size() > 0) {
          CollisionWindow thisWindow = allWindows.pop();
          // Remove any windows from the working set that don't overlap with the next window.
          while (workingSet.size() > 0 && workingSet.peekFirst().getMaxMZ() < thisWindow.getMinMZ()) {
            finished.add(workingSet.pop());
          }

          for (CollisionWindow w : workingSet) {
            /* Add the size of the new overlapping window's structure count to each of the windows in the working set,
             * which represents the number of possible confused structures that fall within the overlapping region.
             * We exclude the window itself as it should already have counted the colliding structures it represents. */
            w.getAccumulator().add(thisWindow.getStructureCount());

            /* Reciprocally, add the structure counts of all windows with which the current window overlaps to it. */
            thisWindow.getAccumulator().add(w.getStructureCount());
          }

          // Now that accumulation is complete, we can safely add the current window.
          workingSet.add(thisWindow);
        }

        // All the interesting events are done, so drop the remaining windows into the finished set.
        finished.addAll(workingSet);

        Map<Long, Long> collisionHistogram = histogram(finished.stream().map(w -> w.getAccumulator().longValue()));
        List<Long> sortedCollisions = new ArrayList<>(collisionHistogram.keySet());
        Collections.sort(sortedCollisions);
        for (Long collision : sortedCollisions) {
          tsvWriter.append(new HashMap<String, Long>() {{
            put("collisions", collision);
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

  private static class CollisionWindow {
    Double min;
    Double max;
    LongAdder accumulator = new LongAdder();
    Integer structureCount;

    public CollisionWindow(Double mz, Double windowHalfWidth, Integer structureCount) {
      this.min = mz - windowHalfWidth;
      this.max = mz + windowHalfWidth;
      this.structureCount = structureCount;

      // Set the base
      this.accumulator.add(structureCount);
    }

    Double getMinMZ() {
      return min;
    }

    Double getMaxMZ() {
      return max;
    }

    LongAdder getAccumulator() {
      return accumulator;
    }

    Integer getStructureCount() {
      return structureCount;
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
