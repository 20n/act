package com.act.lcms.v2;

import com.act.lcms.MS1;
import com.act.lcms.MassCalculator;
import com.act.utils.CLIUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MassChargeCalculator {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MassChargeCalculator.class);

  private static String OPTION_INPUT_INCHI_LIST = "i";

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
  }};


  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(MassChargeCalculator.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    File inputFile = new File(OPTION_INPUT_INCHI_LIST);
    if (!inputFile.exists()) {
      cliUtil.failWithMessage("Input file at does not exist at %s", inputFile.getAbsolutePath());
    }

    List<MZSource> sources = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        sources.add(new MZSource(line));
      }
    }

    MassChargeMap mzMap = MassChargeCalculator.makeMassChargeMap(sources);

    // This is actually a map of collision counts to counts of collision counts, but that is too long!
    Map<Integer, Integer> collisionCounts = new HashMap<>();
    Iterator<Double> mzs = mzMap.ionMZIterator();
    while (mzs.hasNext()) {
      Double mz = mzs.next();
      Integer collisions = mzMap.ionMZToMZSources(mz).size();
      Integer oldCount = collisionCounts.get(collisions);
      if (oldCount == null) {
        oldCount = 0;
      }
      collisionCounts.put(collisions, oldCount + 1);
    }

    List<Integer> sortedCollisions = new ArrayList<>(collisionCounts.keySet());
    Collections.sort(sortedCollisions);
    for (Integer collision : sortedCollisions) {
      LOGGER.info("%d: %d", collision, collisionCounts.get(collision));
    }
  }


  /**
   * An MZSource is a handle to the result of ionic mass/charge computation.  Once m/z computation has been completed
   * for a given set of mass sources (like InChIs, arbitrary numeric values, and the results of the very convenient
   * Utils.extractMassFromString), the id of the resulting MZSource object can be used to access all of the m/z values
   * computed from that source of a monoisotopic mass.  In turn, any peaks associated with a particular m/z can be
   * mapped back to the source from which the search window in which the peak was found was originally derived.
   */
  public static class MZSource {
    private static volatile AtomicInteger instanceCounter = new AtomicInteger(0);

    private enum KIND {
      INCHI, // An InChI whose mass to import.
      ARBITRARY_MASS, // Some monoisotopic mass specified without a source molecule.
      PRE_EXTRACTED_MASS, // The output of Utils.extractMassFromString, which is very handy.
      UNKNOWN, // For deserialization.
      ;
    }

    @JsonProperty("kind")
    KIND kind;
    @JsonProperty("inchi")
    String inchi;
    @JsonProperty("arbitrary_mass")
    Double arbitraryMass;
    @JsonProperty("pre_extracted_mass") // TODO: will Pair de/serialize without a custom class?
    Pair<String, Double> preExtractedMass;
    Integer objectId;

    public MZSource(String inchi) {
      this.kind = KIND.INCHI;
      this.inchi = inchi;
      setId();
    }

    public MZSource(Double arbitraryMass) {
      this.kind = KIND.ARBITRARY_MASS;
      this.arbitraryMass = arbitraryMass;
      setId();
    }

    public MZSource(Pair<String, Double> extractMassFromStringResult) {
      this.kind = KIND.PRE_EXTRACTED_MASS;
      this.preExtractedMass = extractMassFromStringResult;
      setId();
    }

    private MZSource() { // For de/serialization.

    }

    private void setId() {
      objectId = instanceCounter.getAndIncrement();
    }

    public Integer getId() {
      return objectId;
    }

    // No protection specification here = package private.
    KIND getKind() {
      return kind;
    }

    String getInchi() {
      return inchi;
    }

    Double getArbitraryMass() {
      return arbitraryMass;
    }

    Pair<String, Double> getPreExtractedMass() {
      return preExtractedMass;
    }

    /* Note: these methods are more complicated than they need be.  Specifically, since objectId should be unique, it's
     * really the only thing we should need to determine if two MZSource objects are equivalent.  However, given the
     * complexities of serialization, testing, etc., comprehensive implementations of these methods seem like a good
     * idea for now.
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MZSource mzSource = (MZSource) o;

      if (kind != mzSource.kind) return false;
      if (inchi != null ? !inchi.equals(mzSource.inchi) : mzSource.inchi != null) return false;
      if (arbitraryMass != null ? !arbitraryMass.equals(mzSource.arbitraryMass) : mzSource.arbitraryMass != null)
        return false;
      if (preExtractedMass != null ? !preExtractedMass.equals(mzSource.preExtractedMass) : mzSource.preExtractedMass != null)
        return false;
      return objectId.equals(mzSource.objectId);

    }

    @Override
    public int hashCode() {
      int result = kind.hashCode();
      result = 31 * result + (inchi != null ? inchi.hashCode() : 0);
      result = 31 * result + (arbitraryMass != null ? arbitraryMass.hashCode() : 0);
      result = 31 * result + (preExtractedMass != null ? preExtractedMass.hashCode() : 0);
      result = 31 * result + objectId.hashCode();
      return result;
    }
  }

  public static class MassChargeMap {
    // Tracks the monoisotopic mass for a given source, which can then be used to find collisions in the reverse map.
    Map<MZSource, Double> monoisotopicMasses;
    // Maps each monoisotopic mass to any colliding sources.
    Map<Double, List<MZSource>> reverseMonoisotopicMasses = new HashMap<>();

    // For a given ionic mass, it is a <ION_TYPE> for mass <MONOISOTOPIC MASS>, e.g., M+H for
    Map<Double, List<Pair<String, Double>>> reverseIonicMasses = new HashMap<>();

    // Package-private again.
    MassChargeMap() {

    }

    public List<MZSource> monoMassToMZSources(Double monoisotopicMass) {
      return Collections.unmodifiableList(reverseMonoisotopicMasses.get(monoisotopicMass));
    }

    public List<Pair<String, Double>> ionMZtoMonoMassAndIonName(Double mz) {
      return Collections.unmodifiableList(reverseIonicMasses.get(mz));
    }

    public Set<MZSource> ionMZToMZSources(Double mz) {
      List<Pair<String, Double>> reverseIons = reverseIonicMasses.get(mz);
      if (reverseIons == null) {
        return Collections.emptySet();
      }
      return Collections.unmodifiableSet(
          reverseIons.stream().
              map(p -> reverseMonoisotopicMasses.get(p.getRight())).
              flatMap(List::stream). // Flattens stream of List<MZSource> into single stream of MZSource.
              collect(Collectors.toSet())
      );
    }

    public Iterator<MZSource> mzSourceIterator() {
      return monoisotopicMasses.keySet().iterator();
    }

    public Iterator<Double> monoisotopicMassIterator() {
      return reverseIonicMasses.keySet().iterator();
    }

    public Iterator<Double> ionMZIterator() {
      return reverseIonicMasses.keySet().iterator();
    }

    // Package private again.
    void loadSourcesAndMasses(List<Pair<MZSource, Double>> sourcesAndMasses) throws IOException {
      monoisotopicMasses = new LinkedHashMap<>(sourcesAndMasses.size()); // Preserve order for easier testing.

      for (Pair<MZSource, Double> sourceAndMass : sourcesAndMasses) {
        MZSource source = sourceAndMass.getLeft();
        Double monoMass = sourceAndMass.getRight();
        monoisotopicMasses.put(source, monoMass);

        if (reverseMonoisotopicMasses.containsKey(monoMass)) {
          reverseMonoisotopicMasses.get(monoMass).add(source);
          /* And we're done!  We've already computed the ions for this monoisotopic mass, so we've just squashed a
           * duplicate.  The source -> results correspondence will come out when we map the hits over the ionic masses,
           * then tie those back to their corresponding MZSources. */
          continue;
        }

        List<MZSource> sourcesWithThisMass = new ArrayList<MZSource>(1) {{
          add(source);
        }};
        reverseMonoisotopicMasses.put(monoMass, sourcesWithThisMass);

        Map<String, Double> ions = MS1.getIonMasses(monoMass, MS1.IonMode.POS);

        for (Map.Entry<String, Double> ion : ions.entrySet()) {
          List<Pair<String, Double>> reverseMapping = reverseIonicMasses.get(ion.getValue());
          if (reverseMapping == null) {
            reverseMapping = new ArrayList<Pair<String, Double>>(1); // Assume few collisions.
            reverseIonicMasses.put(ion.getValue(), reverseMapping);
          }
          reverseMapping.add(Pair.of(ion.getKey(), monoMass));
        }
      }
    }

    public List<Double> getUniqueIonicMasses() {
      List<Double> masses = new ArrayList<Double>(reverseIonicMasses.keySet());
      Collections.sort(masses);
      // The keySet has already unique'd all the values, so all we need to do is sort 'em and return 'em.
      return Collections.unmodifiableList(masses); // Make the list unmodifiable for safety's sake (we own the masses).
    }

    /**
     * Accumulates all of the MZSources associated with this ion and the ion name per each source for a given ion m/z.
     * @param ionicMassCharge The ion's m/z for which to search.
     * @return A list of each corresponding MZSource and the ionic variant the specified m/z represents for this source.
     *         Returns an empty list if no matching sources are found.
     */
    public List<Pair<MZSource, String>> mapIonMZToSources(Double ionicMassCharge) {
      List<Pair<String, Double>> reverseMapping = reverseIonicMasses.get(ionicMassCharge);
      if (reverseMapping == null) {
        return Collections.emptyList();
      }

      // Flattening this list with the stream API is a bit clumsy, so just loop over all the reverse mappings.
      List<Pair<MZSource, String>> results = new ArrayList<>();
      for (Pair<String, Double> ionNameAndMonoMass : reverseMapping) {
        List<MZSource> mzSources = reverseMonoisotopicMasses.get(ionNameAndMonoMass.getRight());
        for (MZSource source : mzSources) {
          results.add(Pair.of(source, ionNameAndMonoMass.getLeft()));
        }
      }

      return results;
    }
  }

  public static MassChargeMap makeMassChargeMap(List<MZSource> mzSources) throws IOException {
    MassChargeMap map = new MassChargeMap();
    /* Map over the sources, extracting or computing the mass as we go to encapsulate any unsafe behavior outside the
     * MassChargeMap constructor. */
    map.loadSourcesAndMasses(mzSources.stream().map(x -> Pair.of(x, computeMass(x))).collect(Collectors.toList()));
    return map;
  }

  protected static Double computeMass(MZSource mzSource) {
    Double mass;
    String msg;
    switch (mzSource.getKind()) {
      case INCHI:
        Pair<Double, Integer> massAndCharge = MassCalculator.calculateMassAndCharge(mzSource.getInchi());
        if (massAndCharge.getRight() > 0) {
          LOGGER.warn("(MZSrc %d) Molecule %s has a positive charge %d; ionization may not have the expected effect",
              mzSource.getId(), mzSource.getInchi(), massAndCharge.getRight());
        } else if (massAndCharge.getRight() < 0) {
          LOGGER.warn("(MZSrc %d) Molecule %s has a negative charge %d; it may not fly in positive ion mode",
              mzSource.getId(), mzSource.getInchi(), massAndCharge.getRight());
        }
        mass = massAndCharge.getLeft();
        break;
      case ARBITRARY_MASS:
        mass = mzSource.getArbitraryMass();
        break;
      case PRE_EXTRACTED_MASS:
        mass = mzSource.getPreExtractedMass().getRight();
        break;
      case UNKNOWN:
        msg = String.format("mzSource with id %d has UNKNOWN kind, which is invalid", mzSource.getId());
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      default:
        msg = String.format("mzSource with id %d has unknown kind (should be impossible): %s",
            mzSource.getId(), mzSource.getKind());
        LOGGER.error(msg);
        throw new RuntimeException(msg);
    }
    return mass;
  }
}
