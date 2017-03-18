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

public class SimplifiedReaction {
  private long uuid, substrate, product;
  private ReactionType type;
  private String fullReaction;
  private Double weight;

  public SimplifiedReaction(long uuid, Long substrate, Long product, ReactionType type) {
    this.uuid = uuid;
    this.substrate = substrate;
    this.product = product;
    this.type = type;
  }

  public long getUuid() {
    return uuid;
  }

  public void setUuid(long uuid) {
    this.uuid = uuid;
  }

  public long getSubstrate() {
    return substrate;
  }

  public void setSubstrate(long substrate) {
    this.substrate = substrate;
  }

  public long getProduct() {
    return product;
  }

  public void setProduct(long product) {
    this.product = product;
  }

  public ReactionType getType() {
    return type;
  }

  public void setType(ReactionType type) {
    this.type = type;
  }

  public String getFullReaction() {
    return fullReaction;
  }

  public void setFullReaction(String fullReaction) {
    this.fullReaction = fullReaction;
  }

  public double getWeight() {
    return weight;
  }

  public void setWeight(Double weight) {
    this.weight = weight;
  }
}
