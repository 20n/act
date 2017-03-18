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

package act.installer.metacyc.references;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class Publication extends BPElement {
  Integer year;
  String title;
  Set<String> source;
  String db, id;
  Set<String> authors;

  // an example (xml, but jsonified for readability, so please ignore the multikey) being:
  // { year: 2004,
  //   title: "A Bayesian method for identifying missing enzymes in predicted metabolic pathway databases."
  //   source: "BMC Bioinformatics 5;76"
  //   id: "15189570"
  //   db: "PubMed"
  //   author: "Green ML"
  //   author: "Karp PD"
  // }

  public Publication(BPElement basics, int year, String t, Set<String> src, String db, String id, Set<String> auth) {
    super(basics);
    this.year = year;
    this.title = t;
    this.source = src;
    this.db = db;
    this.id = id;
    this.authors = auth;
  }

  public String citation() {
    return "\""  + title + "\", " + source + ", " + year + ", " + authors;
  }

  public String dbid() {
    return db + "_" + id;
  }
}

