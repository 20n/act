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

package act.installer.brenda;

import java.util.regex.Pattern;

public class BrendaRxnEntry {
  public static final Pattern BRACKETS_PATTERN = Pattern.compile("^\\[.*\\]");

  protected String ecNumber;
  protected String substrates;
  protected String commentarySubstrates;
  protected String literatureSubstrates;
  protected String organismSubstrates;
  protected String products;
  protected String reversibility;
  protected Integer id;
  protected Boolean isNatural;

  public BrendaRxnEntry(String ecNumber, String substrates, String commentarySubstrates, String literatureSubstrates,
                        String organismSubstrates, String products, String reversibility,
                        Integer id, Boolean isNatural) {
    this.ecNumber = ecNumber;
    this.substrates = substrates;
    this.commentarySubstrates = commentarySubstrates;
    this.literatureSubstrates = literatureSubstrates;
    if (organismSubstrates != null) {
      organismSubstrates = organismSubstrates.trim();
      if (BRACKETS_PATTERN.matcher(organismSubstrates).find()) {
        // Remove the square braces around the organism's genus to handle cases like '[Brevibacterium] flavum'.
        organismSubstrates = organismSubstrates.replace("[", "").replace("]", "");
      }
    }

    this.organismSubstrates = organismSubstrates;
    this.products = products;
    this.reversibility = reversibility;
    this.id = id;
    this.isNatural = isNatural;
  }

  public String getOrganism() {
    return this.organismSubstrates;
  }

  public String getReversibility() {
    return this.reversibility;
  }

  public String getSubstrateStr() {
    return this.substrates;
  }

  public String getProductStr() {
    return this.products;
  }

  public String getEC() {
    return this.ecNumber;
  }

  public String getLiteratureRef() {
    return this.literatureSubstrates;
  }

  public String getBrendaID() {
    return this.id.toString();
  }

  public Boolean isNatural() {
    return this.isNatural;
  }
}

