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

package com.act.lcms;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class XZ implements Serializable {
  private static final long serialVersionUID = -5116293669998344905L;

  @JsonProperty("time")
  private Double time;

  @JsonProperty("intensity")
  private Double intensity;

  public XZ(Double t, Double i) {
    this.time = t;
    this.intensity = i;
  }

  public XZ() {}

  public Double getTime() {
        return time;
    }

  public Double getIntensity() {
        return intensity;
    }

  public void setTime(Double time) {
    this.time = time;
  }

  public void setIntensity(Double intensity) {
    this.intensity = intensity;
  }
}
