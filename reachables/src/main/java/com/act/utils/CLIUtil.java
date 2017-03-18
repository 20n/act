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

package com.act.utils;

import act.installer.pubchem.PubchemParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class CLIUtil {
  private static final Logger LOGGER = LogManager.getFormatterLogger(CLIUtil.class);

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  private Class callingClass;
  private String helpMessage;
  private List<Option.Builder> optionBuilders;
  private CommandLine commandLine;
  private Options opts;

  public CLIUtil(Class callingClass, String helpMessage, List<Option.Builder> optionBuilders) {
    this.callingClass = callingClass;
    this.helpMessage = helpMessage;
    this.optionBuilders = optionBuilders;

    List<Option.Builder> options = new ArrayList<>(optionBuilders);
    // Everybody needs a little help from their friends.
    options.add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );

    opts = new Options();
    for (Option.Builder b : optionBuilders) {
      opts.addOption(b.build());
    }
  }

  public CommandLine parseCommandLine(String[] args) {
    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      LOGGER.error("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(callingClass.getCanonicalName(), helpMessage, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(callingClass.getCanonicalName(), helpMessage, opts, null, true);
      System.exit(0);
    }

    commandLine = cl;

    return cl;
  }

  public CommandLine getCommandLine() {
    return this.commandLine;
  }

  public void failWithMessage(String formatStr, String... args) {
    failWithMessage(String.format(formatStr, (Object[]) args)); // Cast to make sure args are treated as varargs.
  }

  public void failWithMessage(String msg) {
    System.out.println(msg);
    HELP_FORMATTER.printHelp(callingClass.getCanonicalName(), helpMessage, opts, null, true);
    System.exit(1);
  }

}
