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
    return this.getCommandLine();
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
