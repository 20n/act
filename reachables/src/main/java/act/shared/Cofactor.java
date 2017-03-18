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

package act.shared;

import java.io.Serializable;
import java.util.List;

public class Cofactor implements Serializable {
  private static final long serialVersionUID = 42L;
  public Cofactor() { /* default constructor for serialization */ }

  private Long uuid;
  private String inchi;
  private List<String> names; // synonyms

  public Cofactor(Long uuid, String inchi, List<String> names) {
    this.uuid = uuid;
    this.inchi = inchi;
    this.names = names;
  }

  public Long getUuid() { return this.uuid; }
  public List<String> getNames() { return this.names; }
  public String getInChI() { return this.inchi; }
}
