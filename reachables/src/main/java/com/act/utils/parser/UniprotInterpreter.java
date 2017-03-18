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
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompoundSet;
import org.biojava.nbio.core.sequence.loader.UniprotProxySequenceReader;
import org.biojava.nbio.core.util.XMLHelper;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UniprotInterpreter {

  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInterpreter.class);
  private static final String OPTION_UNIPROT_PATH = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class parses Uniprot Protein sequence files. It can be used on the command line with ",
      "a file path as a parameter."}, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_UNIPROT_PATH)
        .argName("uniprot file")
        .desc("uniprot protein sequence file containing sequence and annotations")
        .hasArg()
        .longOpt("uniprot")
        .required()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Example of usage: -p filepath.xml")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private File xmlFile;
  private Document xmlDocument;
  private ProteinSequence seq;

  public void init() throws IOException, SAXException, ParserConfigurationException, CompoundNotFoundException {
    BufferedReader br = new BufferedReader(new FileReader(xmlFile));
    String line;
    StringBuilder sb = new StringBuilder();

    while((line=br.readLine()) != null) {
      sb.append(line.trim());
    }

    xmlDocument = XMLHelper.inputStreamToDocument(new ByteArrayInputStream(sb.toString().getBytes()));

    AminoAcidCompoundSet aminoAcidCompoundSet = AminoAcidCompoundSet.getAminoAcidCompoundSet();

    UniprotProxySequenceReader uniprotProxySequenceReader =
        new UniprotProxySequenceReader(xmlDocument, aminoAcidCompoundSet);

    seq = new ProteinSequence(uniprotProxySequenceReader);
  }

  private void checkInit() {
    if (xmlDocument == null || seq == null) {
      String msg = "Class hasn't been appropriately initialized, no Document and/or ProteinSequence object";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public UniprotInterpreter(File uniprotFile) {
    xmlFile = uniprotFile;
  }

  public Document getXmlDocument() {
    checkInit();
    return this.xmlDocument;
  }

  public String getSequence() {
    checkInit();
    return seq.getSequenceAsString();
  }

  public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException,
      CompoundNotFoundException {
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
      HELP_FORMATTER.printHelp(UniprotInterpreter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(UniprotInterpreter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File uniprotFile = new File(cl.getOptionValue(OPTION_UNIPROT_PATH));

    if (!uniprotFile.exists()) {
      String msg = "Uniprot file path is null";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      UniprotInterpreter reader = new UniprotInterpreter(uniprotFile);
      reader.init();
    }
  }

}
