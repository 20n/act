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

package com.act.utils.parser;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.io.DNASequenceCreator;
import org.biojava.nbio.core.sequence.io.GenbankReader;
import org.biojava.nbio.core.sequence.io.GenbankReaderHelper;
import org.biojava.nbio.core.sequence.io.GenericGenbankHeaderParser;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;


public class GenbankInterpreter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInterpreter.class);
  private static final String OPTION_GENBANK_PATH = "p";
  private static final String OPTION_SEQ_TYPE = "s";
  private static final String PROTEIN = "Protein";
  private static final String DNA = "DNA";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class parses Genbank Protein sequence files. It can be used on the command line with ",
      "a file path as a parameter."}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_GENBANK_PATH)
        .argName("genbank file")
        .desc("genbank dna or protein sequence file containing sequence and annotations")
        .hasArg()
        .longOpt("genbank")
        .required()
    );
    add(Option.builder(OPTION_SEQ_TYPE)
        .argName("sequence type")
        .desc("declares whether the sequence type is DNA or Protein")
        .hasArg()
        .longOpt("sequence")
        .required()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Example of usage: -p filepath.gb -s DNA")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private File protFile;
  private String seq_type;
  private ArrayList<AbstractSequence> sequences = new ArrayList<>();

  /**
   * Parses every sequence object from the Genbank File
   * @throws Exception
   */
  public void init() throws Exception {
    if (seq_type.equals(PROTEIN)) {
      Map<String, ProteinSequence> sequences;

      if (protFile.getName().endsWith(".gz")) {
        try (InputStream is = new GZIPInputStream(new FileInputStream(protFile))) {
          sequences = GenbankReaderHelper.readGenbankProteinSequence(is);
        }
      } else {
        sequences = GenbankReaderHelper.readGenbankProteinSequence(protFile);
      }

      for (AbstractSequence sequence : sequences.values()) {
        this.sequences.add(sequence);
      }

    } else if (seq_type.equals(DNA)) {
      Map<String, DNASequence> sequences;

      if (protFile.getName().endsWith(".gz")) {
        try (InputStream is = new GZIPInputStream(new FileInputStream(protFile))) {
          /* the AmbiguityDNACompoundSet is necessary due to the presence of ambiguous nucleotide (non-ATCG) compounds
          in the parsed DNA sequences */
          GenbankReader genbankReader = new GenbankReader(is, new GenericGenbankHeaderParser<>(),
              new DNASequenceCreator(AmbiguityDNACompoundSet.getDNACompoundSet()));

          sequences = genbankReader.process();
        }
      } else {
        /* the AmbiguityDNACompoundSet is necessary due to the presence of ambiguous nucleotide (non-ATCG) compounds
          in the parsed DNA sequences */
        GenbankReader genbankReader = new GenbankReader(protFile, new GenericGenbankHeaderParser<>(),
            new DNASequenceCreator(AmbiguityDNACompoundSet.getDNACompoundSet()));

        sequences = genbankReader.process();
      }

      for (AbstractSequence sequence : sequences.values()) {
        this.sequences.add(sequence);
      }

    } else {
      String msg = "No proper sequence type given; must be either DNA or Protein";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  /**
   * Checks if sequence object has been initialized, throws RuntimeException if not
   */
  private void checkInit() {
    if (sequences == null) {
      String msg = "Class hasn't been appropriately initialized, no sequence object";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public GenbankInterpreter(File GenbankFile, String type) {
    protFile = GenbankFile;
    seq_type = type;
  }

  /**
   * Prints the genetic sequences extracted from the sequence objects
   */
  public void printSequences() {
    checkInit();

    for (AbstractSequence sequence : this.sequences) {
      System.out.println("Sequence:");
      System.out.println(sequence.getSequenceAsString());
      System.out.println("\n");
    }
  }

  /**
   * Extracts the genetic sequence as a string from the sequence objects
   * @return A list of genetic sequences as strings
   */
  public ArrayList<String> getSequenceStrings() {
    checkInit();

    ArrayList<String> sequences = new ArrayList<>();

    for (AbstractSequence sequence : this.sequences) {
      sequences.add(sequence.getSequenceAsString());
    }

    return sequences;
  }

  /**
   * Prints all the Features and corresponding Qualifiers for each sequence object
   */
  public void printFeaturesAndQualifiers() {
    checkInit();

    for (AbstractSequence sequence : sequences) {
      List<FeatureInterface<AbstractSequence<Compound>, Compound>> features =
          sequence.getFeatures();

      for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
        System.out.println("Type: " + feature.getType() + "; Source: " + feature.getSource() + "\n");

        Map<String, List<Qualifier>> qualifiers = feature.getQualifiers();

        for (List<Qualifier> qual_list : qualifiers.values()) {
          for (Qualifier qual : qual_list) {
            if (qual.getName().equals("dbxref")) {
              System.out.println("/" + qual.getName() + "=\"" + ((DBReferenceInfo) qual).getDatabase() + ":" +
                  ((DBReferenceInfo) qual).getId() + "\" |");
            } else {
              System.out.println("/" + qual.getName() + "=\"" + qual.getValue() + "\" |");
            }
          }
        }

        System.out.println("=======================\n");
      }
    }
  }

  /**
   * Extracts feature types from the sequence object
   * @return list of all feature types in the Genbank file
   */
  public ArrayList<ArrayList<String>> getFeatures() {
    checkInit();

    ArrayList<ArrayList<String>> all_feature_types = new ArrayList<>();

    for (AbstractSequence sequence : sequences) {
      ArrayList<String> feature_types = new ArrayList<String>();

      List<FeatureInterface<AbstractSequence<Compound>, Compound>> features =
          sequence.getFeatures();

      for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
        feature_types.add(feature.getType());
      }

      all_feature_types.add(feature_types);
    }

    return all_feature_types;
  }

  /**
   * Extracts qualifiers for a particular feature in the sequence object
   * @param sequence_index the index of the sequence object of interest in the sequences list
   * @param feature_type i.e. "source", "gene", "CDS", etc
   * @param feature_source i.e. "1..678"
   * @return Map of the corresponding qualifiers with the key being the Qualifier name (i.e. organism, mol_type, etc)
   * and the value being the list of Qualifiers that have the same name as the key
   */
  public Map<String, List<Qualifier>> getQualifiers(int sequence_index, String feature_type, String feature_source) {
    checkInit();

    List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = sequences.get(sequence_index).getFeatures();

    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
      if (feature.getType().equals(feature_type) && feature.getSource().equals(feature_source)) {
        return feature.getQualifiers();
      }
    }

    return null;
  }

  /**
   * Adds a Qualifier to a particular Feature i.e. /organism="Escherichia Coli"
   * @param feature the feature object you'd like to add the qualifier to
   * @param qual_name e.g. "organism"
   * @param qual_value e.g. "Escherichia Coli"
   */
  public void addQualifier(AbstractFeature<AbstractSequence<Compound>, Compound> feature, String qual_name,
                           String qual_value) {
    feature.addQualifier(qual_name, new Qualifier(qual_name, qual_value));
  }

  /**
   * Constructs a Feature with a particular type (i.e. gene) and source (i.e. 1..678)
   * @param type e.g. "gene", "source", "CDS"
   * @param source e,g. "1..678"
   * @return the constructed Feature object
   */
  public AbstractFeature<AbstractSequence<Compound>, Compound> constructFeature(String type, String source) {
    return new AbstractFeature<AbstractSequence<Compound>, Compound>(type, source) {};
  }

  /**
   * prints the description string for each sequence
   */
  public void printDescription() {
    for (AbstractSequence sequence : sequences) {
      System.out.println(sequence.getDescription());
    }
  }

  /**
   * prints the Accession ID for each sequence
   */
  public void printAccessionID() {
    for (AbstractSequence sequence : sequences) {
      System.out.println(sequence.getAccession().getID());
    }
  }

  public void printHeader() {
    for (AbstractSequence sequence : sequences) {
      System.out.println(sequence.getOriginalHeader());
    }
  }

  public List<AbstractSequence> getSequences() {
    return this.sequences;
  }

  /**
   * Once the Feature has been constructed and all the qualifiers have been added, this method adds the feature to
   * a specific sequence
   * @param bioStart the start index of the source of the feature
   * @param bioEnd the end index of the source of the feature
   * @param feature the feature object to be added
   * @param sequence_index the index of the sequence of interest in the sequences list
   */
  public void addFeature(int bioStart, int bioEnd, AbstractFeature<AbstractSequence<Compound>,
      Compound> feature, int sequence_index) {
    checkInit();
    sequences.get(sequence_index).addFeature(bioStart, bioEnd, feature);
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
      LOGGER.error("Argument parsing failed: %s", e.getMessage());
      HELP_FORMATTER.printHelp(GenbankInterpreter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(GenbankInterpreter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File genbankFile = new File(cl.getOptionValue(OPTION_GENBANK_PATH));
    String seq_type = cl.getOptionValue(OPTION_SEQ_TYPE);

    if (!genbankFile.exists()) {
      String msg = "Genbank file path is null";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      GenbankInterpreter reader = new GenbankInterpreter(genbankFile, seq_type);
      reader.init();
      reader.printSequences();
    }
  }
}
