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

package com.act.lcms.db.io;

import com.act.lcms.db.io.writer.PlateCompositionWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ExportPlateCompositionFromDB {
  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    opts.addOption(Option.builder("b")
            .argName("barcode")
            .desc("The barcode of the plate to print")
            .hasArg()
            .longOpt("barcode")
            .build()
    );
    opts.addOption(Option.builder("n")
            .argName("name")
            .desc("The name of the plate to print")
            .hasArg()
            .longOpt("name")
            .build()
    );

    opts.addOption(Option.builder("o")
            .argName("output file")
            .desc("An output file to which to write this plate's composition table (writes to stdout if omitted")
            .hasArg()
            .longOpt("output-file")
            .build()
    );

    // DB connection options.
    opts.addOption(Option.builder()
            .argName("database url")
            .desc("The url to use when connecting to the LCMS db")
            .hasArg()
            .longOpt("db-url")
            .build()
    );
    opts.addOption(Option.builder("u")
            .argName("database user")
            .desc("The LCMS DB user")
            .hasArg()
            .longOpt("db-user")
            .build()
    );
    opts.addOption(Option.builder("p")
            .argName("database password")
            .desc("The LCMS DB password")
            .hasArg()
            .longOpt("db-pass")
            .build()
    );
    opts.addOption(Option.builder("H")
            .argName("database host")
            .desc(String.format("The LCMS DB host (default = %s)", DB.DEFAULT_HOST))
            .hasArg()
            .longOpt("db-host")
            .build()
    );
    opts.addOption(Option.builder("P")
            .argName("database port")
            .desc(String.format("The LCMS DB port (default = %d)", DB.DEFAULT_PORT))
            .hasArg()
            .longOpt("db-port")
            .build()
    );
    opts.addOption(Option.builder("N")
            .argName("database name")
            .desc(String.format("The LCMS DB name (default = %s)", DB.DEFAULT_DB_NAME))
            .hasArg()
            .longOpt("db-name")
            .build()
    );

    // Everybody needs a little help from their friends.
    opts.addOption(Option.builder("h")
            .argName("help")
            .desc("Prints this help message")
            .longOpt("help")
            .build()
    );

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      return;
    }

    if (!cl.hasOption("b") && !cl.hasOption("n")) {
      System.err.format("Must specify either plate barcode or plate name.");
      new HelpFormatter().printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), opts, true);
      System.exit(1);
    }

    DB db = null;
    try {
      if (cl.hasOption("db-url")) {
        db = new DB().connectToDB(cl.getOptionValue("db-url"));
      } else {
        Integer port = null;
        if (cl.getOptionValue("P") != null) {
          port = Integer.parseInt(cl.getOptionValue("P"));
        }
        db = new DB().connectToDB(cl.getOptionValue("H"), port, cl.getOptionValue("N"),
            cl.getOptionValue("u"), cl.getOptionValue("p"));
      }

      Writer writer = null;
      if (cl.hasOption("o")) {
        writer = new FileWriter(cl.getOptionValue("o"));
      } else {
        writer = new OutputStreamWriter(System.out);
      }

      PlateCompositionWriter cw = new PlateCompositionWriter();
      if (cl.hasOption("b")) {
        cw.writePlateCompositionByBarcode(db, cl.getOptionValue("b"), writer);
      } else if (cl.hasOption("n")) {
        cw.writePlateCompositionByName(db, cl.getOptionValue("n"), writer);
      }

      writer.close();
    } finally {
      if (db != null) {
        db.close();
      }
    }
  }
}
