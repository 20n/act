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

package act.server;

import java.io.PrintStream;

public class Logger {
  static private int maxImportanceToBeShown; // 0 -> showstopper; 10 -> informational; etc.. 100 -> extra junk
  static private PrintStream stream;

  static {
    Logger.stream = System.out;
    // default: print everthing including extra junk...
    // use setMaxImpToShow to change the verbosity level
    Logger.maxImportanceToBeShown = Integer.MAX_VALUE;
  }

  public static int getMaxImpToShow() {
    return Logger.maxImportanceToBeShown;
  }

  public static void setMaxImpToShow(int maxImportanceToBeShown) {
    Logger.maxImportanceToBeShown = maxImportanceToBeShown;
  }

  public static void print(int zeroMeansVeryImportantInfIsJunk, String msg) {
    if (zeroMeansVeryImportantInfIsJunk <= Logger.maxImportanceToBeShown)
      stream.print(msg);
  }

  public static void println(int zeroMeansVeryImportantInfIsJunk, String ln) {
    if (zeroMeansVeryImportantInfIsJunk <= Logger.maxImportanceToBeShown)
      stream.println(ln);
  }

  public static void printf(int zeroMeansVeryImportantInfIsJunk, String format, Object ... args) {
    if (zeroMeansVeryImportantInfIsJunk <= Logger.maxImportanceToBeShown)
      stream.printf(format, args);
  }
}
