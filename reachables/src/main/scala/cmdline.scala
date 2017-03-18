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

package com.act.reachables

class CmdLine(args: Array[String]) {
  val pairs = args.toList.map(split)
  val map = pairs.toMap

  def split(arg: String) = {
    val sploc = arg indexOf '='
    if (arg.startsWith("--")) {
      if (sploc != -1) {
        val spl = arg splitAt sploc
        (spl._1 drop 2, spl._2 drop 1) // remove the "--" from _1 and "=" from _2
      } else {
        (arg drop 2, "true")
      }
    } else {
      ("nokey", arg)
    }
  }

  def get(key: String) = {
    map get key
  }
}
