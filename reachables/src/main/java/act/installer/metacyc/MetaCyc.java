package act.installer.metacyc;

import act.server.MongoDB;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import act.shared.Chemical;

public class MetaCyc {
  public static final String METACYC_COMPOUND_FILE_NAME = "compounds.dat";

  // map from location of biopax L3 file to the corresponding parsed organism model
  HashMap<String, OrganismComposition> organismModels;
  String sourceDir;

  // if onlyTier12 is set, then only the 38 main files are processed
  // we identify them as not having names that contain one of
  // ("hmpcyc", "wgscyc", more than three successive digits)
  // See http://biocyc.org/biocyc-pgdb-list.shtml and the descriptions of Tier1 and Tier2
  // Outside of these 38, there are 3487 Tier3 files that have not received manual
  // curation and are just the dump output of their PathLogic program.
  boolean onlyTier12;

  public MetaCyc(String dirWithL3Files) {
    this.organismModels = new HashMap<String, OrganismComposition>();
    this.sourceDir = dirWithL3Files;

    // by default, we process of level3 biopax files found in the directory
    // so we set the flag that restricts to Tier 1 and 2 as false.
    // Use loadOnlyTier12 if a restriction to those files is needed.
    this.onlyTier12 = false;
  }

  public void loadOnlyTier12(boolean flag) {
    this.onlyTier12 = flag;
  }

  // processes num files in source directory (num = -1 for all)
  public void process(int num) {
    if (num > 15)
      warnAboutMem(num);

    if (num > 0)
      process(0, num); // process only num files
    else
      process(getOWLs()); // process all files
  }

  public void process(int start, int end) {
    if (end-start > 50) warnAboutMem(end-start);
    List<String> files = getOWLs();
    if (end > files.size()) {
      System.out.format("Chunk end index %d is out of bounds, limiting to %d\n", end, files.size());
      end = files.size();
    }
    files = files.subList(start, end); // only process a sublist from [start, end)
    process(files);
  }

  private void warnAboutMem(int num_asked) {
    System.out.println("You asked to process more than 15 files: " + num_asked);
    System.out.println("You can process about 10 files in 4GB of runtime memory");
  }

  // process only the source file whose names are passed
  public void process(List<String> files) {


    FileInputStream f = null;
    for (String file : files) {
      System.out.format("Processing biopax file %s\n", new File(this.sourceDir, file).getAbsolutePath());
      HashMap<String, String> uniqueKeyToInChIMap = generateUniqueKeyToInChIMapping(new File(this.sourceDir, file));

      System.out.println("Processing: " + file);
      if (file.endsWith("leishcyc/biopax-level3.owl")) {
        System.out.println("Friendly reminder: Did you patch this leishcyc file with the diff in src/main/resources/leishcyc.biopax-level3.owl.diff to take care of the bad data in the original? If you are running over the plain downloaded file, then this will crash.");
      }

      try {
        f = new FileInputStream(this.sourceDir + "/" + file);
      } catch (FileNotFoundException e) {
        System.err.println("Could not find: " + file + ". Abort."); System.exit(-1);
      }

      OrganismComposition o = new OrganismComposition(uniqueKeyToInChIMap);
      new BioPaxFile(o).initFrom(f);
      this.organismModels.put(file, o);

      try {
        f.close();
      } catch (IOException e) {
        System.err.println("Could not close: " + file);
      }

    }
  }

  private static final Pattern METACYC_COMPOUNDS_COMMENT_PATTERN = Pattern.compile("^\\s*#");
  private static final Pattern METACYC_COMPOUNDS_ENTITY_END_PATTERN = Pattern.compile("^//$");
  private static final Pattern METACYC_COMPOUNDS_FIELD_PATTERN = Pattern.compile("^([a-zA-Z_-]+)\\s+-\\s+(.*)$");
  private static final Pattern METACYC_COMPOUNDS_STRING_CONTINUATION_PATTERN = Pattern.compile("^/(.*)$");
  private static final String METACYC_COMPOUNDS_UNIQUE_ID_FIELD = "UNIQUE-ID";
  private static final String METACYC_COMPOUNDS_INCHI_FIELD = "INCHI";
  private static final String METACYC_COMPOUNDS_NON_STANDARD_INCHI_FIELD = "NON-STANDARD-INCHI";
  private enum METACYC_COMPOUNDS_LAST_FIELD {
    UNIQUE_ID,
    INCHI,
    NON_STANDARD_INCHI,
    OTHER,
  }

  public HashMap<String, String> generateUniqueKeyToInChIMapping(File biopaxFilePath) {
    File compoundFile = new File(biopaxFilePath.getParentFile(), METACYC_COMPOUND_FILE_NAME);
    if (!compoundFile.exists()) {
      // TODO: should this throw an exception?
      System.err.format("ERROR: Missing " + METACYC_COMPOUND_FILE_NAME + " file for bioxpax file %s at %s\n",
          biopaxFilePath, compoundFile.getAbsolutePath());
      return new HashMap<>(0);
    }

    HashMap<String, String> keyToInChIMap = new HashMap<>();
    try (
        BufferedReader reader = new BufferedReader(new FileReader(compoundFile));
    ) {
      // Hacky stateful parser for Metacyc's compounds.dat files.
      // TODO: it'd be easier to read compound-links.dat, but those files don't always exist.  Why not?
      METACYC_COMPOUNDS_LAST_FIELD lastField = METACYC_COMPOUNDS_LAST_FIELD.OTHER;
      String uniqueId = null;
      String inchi = null;
      String nonstandardInchi = null;

      int lineNum = 0;
      String line = null;
      while ((line = reader.readLine()) != null) {
        lineNum++;
        /* Note: this loop uses continues rather than if/else statements so that the field matcher can be created/saved
         * only as needed. */

        // Skip blank lines and comments.
        if (line.isEmpty() || METACYC_COMPOUNDS_COMMENT_PATTERN.matcher(line).find()) {
          continue;
        }

        /* We're at the end of an entity in the compounds file.  Store the unique id + inchi, or complain if they're
         * undefined at this point. */
        if (METACYC_COMPOUNDS_ENTITY_END_PATTERN.matcher(line).matches()) {
          if (uniqueId == null || (inchi == null && nonstandardInchi == null)) {
            System.err.format(
                "ERROR: Found malformed entity line at %s L%d: unique-id='%s' inchi='%s' non-standard-inchi=%s\n",
                compoundFile.getAbsolutePath(), lineNum, uniqueId, inchi, nonstandardInchi);
          } else {
            /* The counts of InChIs and entities in some of the compoounds files don't line up.  Note those for
             * further investigation. */
            if (inchi == null && nonstandardInchi.startsWith("InChI=")) {
              System.err.format("WARNING: Found only non-standard inchi for %s: %s\n", uniqueId, nonstandardInchi);
              inchi = nonstandardInchi;
            }
            // Only store the InChI if it's from the INCHI field or looks like an InChI.
            if (inchi != null) {
              keyToInChIMap.put(uniqueId, inchi);
            }
          }
          // Reset for the next chemical element.
          uniqueId = null;
          inchi = null;
          nonstandardInchi = null;
          lastField = METACYC_COMPOUNDS_LAST_FIELD.OTHER;
          continue;
        }

        /* See if we've encountered a dash-delimited field line; store the line's contents if the field is useful. */
        Matcher fieldMatcher = METACYC_COMPOUNDS_FIELD_PATTERN.matcher(line);
        if (fieldMatcher.matches()) {
          switch (fieldMatcher.group(1)) {
            case METACYC_COMPOUNDS_UNIQUE_ID_FIELD:
              if (uniqueId != null) {
                // We don't expect to see more than one id per compound, so warn if we find any.
                System.err.format(
                    "ERROR: Found duplicate ID in %s at line %d: unique-id='%s' new-id='%s'\n",
                    compoundFile.getAbsolutePath(), lineNum, uniqueId, fieldMatcher.group(2));
              }
              uniqueId = fieldMatcher.group(2);
              lastField = METACYC_COMPOUNDS_LAST_FIELD.UNIQUE_ID;
              break;
            case METACYC_COMPOUNDS_INCHI_FIELD:
              //System.out.format("Matching on InchiField: %s\n", fieldMatcher.group(2));
              if (inchi != null) {
                // We don't expect to see more than one InChI per compound, so warn if we find any.
                System.err.format(
                    "ERROR: Found duplicate InChI in %s at line %d: unique-id='%s' inchi='%s' new-inchi='%s'\n",
                    compoundFile.getAbsolutePath(), lineNum, uniqueId, inchi, fieldMatcher.group(2));
              }
              inchi = fieldMatcher.group(2);
              lastField = METACYC_COMPOUNDS_LAST_FIELD.INCHI;
              break;
            case METACYC_COMPOUNDS_NON_STANDARD_INCHI_FIELD:
              // It isn't clear if/how these should be constrained, so just take the last one.
              nonstandardInchi = fieldMatcher.group(2);
              lastField = METACYC_COMPOUNDS_LAST_FIELD.NON_STANDARD_INCHI;
              break;
            default:
              // Ignore all other kinds of fields.
              lastField = METACYC_COMPOUNDS_LAST_FIELD.OTHER;
              break;
          }
          continue;
        }

        // Cautiously handle all string continuations, though we hope not to see them with ids/InChIs.
        Matcher stringContinuationMatcher = METACYC_COMPOUNDS_STRING_CONTINUATION_PATTERN.matcher(line);
        if (stringContinuationMatcher.matches()) {
          switch (lastField) {
            case UNIQUE_ID:
              uniqueId += stringContinuationMatcher.group(1);
              System.err.format("WARNING: found unexpected continued UNIQUE_ID: %s\n", uniqueId);
              break;
            case INCHI:
              inchi += stringContinuationMatcher.group(1);
              System.err.format("WARNING: found unexpected continued InChI: %s\n", inchi);
              break;
            case NON_STANDARD_INCHI:
              nonstandardInchi += stringContinuationMatcher.group(1);
              System.err.format("WARNING: found unexpected continued non-standard InChI: %s\n", nonstandardInchi);
              break;
            default:
              // No need to store fields other than the id/InChIs.
              break;
          }
          continue;
        }

      }
    } catch (IOException e) {
      // TODO: handle this better.
      System.err.format("Caught IO exception when reading file at %s: %s\n",
          compoundFile.getAbsoluteFile(), e.getMessage());
      return new HashMap<>(0);
    }

    System.out.format("Loaded %d unique id -> InChI mappings from %s\n",
        keyToInChIMap.size(), compoundFile.getAbsolutePath());
    return keyToInChIMap;
  }

  public OrganismComposition get(String file) {
    return this.organismModels.get(file);
  }

  public int getNumFilesToBeProcessed() {
    return getOWLs().size();
  }

  static String[] tier12 = new String[] {
    "10403s_rastcyc",
    "agrocyc",
    "ano2cyc",
    "anthracyc",
    "aurantimonascyc",
    "bsubcyc",
    "cattlecyc",
    "caulocyc",
    "caulona1000cyc",
    "chlamycyc",
    "cparvumcyc",
    "ecol199310cyc",
    "ecol316407cyc",
    "ecol413997cyc",
    "ecoo157cyc",
    "flycyc",
    "hominiscyc",
    "hpycyc",
    "mob3bcyc",
    "mousecyc",
    "mtbrvcyc",
    "pbergheicyc",
    "pchabaudicyc",
    "pchrcyc",
    "plasmocyc",
    "pvivaxcyc",
    "pyoeliicyc",
    "scocyc",
    "shigellacyc",
    "smancyc",
    "synelcyc",
    "toxocyc",
    "trypanocyc",
    "vchocyc",

    // The following data directories only contain an ocelot file dump
    // which is a lisp format raw dump of the db in their own custom
    // format. (http://bioinformatics.ai.sri.com/ptools/flatfile-format.html)
    // It does not make sense for us to write a custom parser for these
    // three files
    "clossaccyc",     // Clostridium saccharoperbutylacetonicum
                      // http://biocyc.org/CLOSSAC/organism-summary?object=CLOSSAC
    "mtbcdc1551cyc",  // Mycobacterium tuberculosis
                      // http://biocyc.org/MTBCDC1551/organism-summary?object=MTBCDC1551
    "thapscyc",       // Thalassiosira pseudonana
                      // http://biocyc.org/THAPS/organism-summary?object=THAPS

    // Cannot locate the data file corresponding to: Candida albicans, Strain SC5314
    // http://biocyc.org/CALBI/organism-summary?object=CALBI
    // NCBI Taxonomy ID: 237561
    // The above URL suggests it should be called calbicyc (this is how we derived
    // the names of all valid 37 files above), but we cannot find that dir
    "calbicyc",       // Candida albicans
                      // http://biocyc.org/CALBI/organism-summary?object=CALBI
  };

  public List<String> getOWLs() {

    String dir = this.sourceDir;
    boolean onlyTier12Files = this.onlyTier12;

    final List<String> tier12files = Arrays.asList(tier12);

    FilenameFilter subdirfltr = new FilenameFilter() {
      public boolean accept(File dir, String sd) {
        if (!new File(dir, sd).isDirectory())
          return false;
        if (onlyTier12Files) {
          // additional checks if only looking for tier1,2 files
          // Tier1,2 are the important ones because they are the
          // only ones that have received manual curation:
          // http://biocyc.org/biocyc-pgdb-list.shtml

          return tier12files.contains(sd);
          // -- The below is an old heuristic that eliminates 7 valid files.
          // -- Instead we do a direct check as above from a static list of filenames
          // -- // It is a Tier1,2 file if its name does not contain one of
          // -- // ("hmpcyc", "wgscyc", more than three successive digits)
          // -- if (sd.contains("hmpcyc") || sd.contains("wgscyc"))
          // --   return false;
          // -- if (sd.matches("^.*[0-9][0-9][0-9].*$"))
          // --   return false;
        }
        return true;
      }
    };

    FilenameFilter owlfltr = new FilenameFilter() {
      public boolean accept(File dir, String nm) { return nm.endsWith("level3.owl"); }
    };

    List<String> allL3 = new ArrayList<String>();
    for (String subdir : new File(dir).list(subdirfltr)) {
      for (String owlfile : new File(dir, subdir).list(owlfltr)) {
        allL3.add(subdir + "/" + owlfile);
      }
    }

    Collections.sort(allL3);
    return allL3;
  }

  public void sendToDB(MongoDB db) {
    Chemical.REFS originDB = Chemical.REFS.METACYC;
    for (String oid : this.organismModels.keySet()) {
      OrganismCompositionMongoWriter owriter = new OrganismCompositionMongoWriter(db, this.organismModels.get(oid), oid, originDB);
      owriter.write();
    }
  }

}

