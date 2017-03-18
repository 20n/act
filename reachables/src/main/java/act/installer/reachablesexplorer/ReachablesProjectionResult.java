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

package act.installer.reachablesexplorer;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// The original ProjectionResult is a Scala case class, which Jackson doesn't handle well.
// This class is the Java equivalent, used for the purpose of Jackson de-serialization.
// TODO: ProjectionResult.scala and this class should be eventually merged together

public class ReachablesProjectionResult {

  @JsonProperty("substrates")
  private final List<String> substrates;

  @JsonProperty("products")
  private final List<String> products;

  @JsonProperty("ros")
  private final String ros;

  @JsonCreator
  public ReachablesProjectionResult(
      @JsonProperty("substrates") List<String> substrates,
      @JsonProperty("products") List<String> products,
      @JsonProperty("ros") String ros) {
    this.substrates = substrates;
    this.products = products;
    this.ros = ros;
  }

  public List<String> getSubstrates() {
    return substrates;
  }

  public List<String> getProducts() {
    return products;
  }

  public String getRos() {
    return ros;
  }
}
