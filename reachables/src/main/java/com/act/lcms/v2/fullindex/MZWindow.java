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

package com.act.lcms.v2.fullindex;

import java.io.Serializable;

// TODO: unify this with the MZWindow from TraceIndexExtractor, or better yet replace TraceIndexExtractor altogether.
public class MZWindow implements Serializable {
  private static final long serialVersionUID = -3326765598920871504L;

  int index;
  Double targetMZ;
  double min;
  double max;

  public MZWindow(int index, Double targetMZ) {
    this.index = index;
    this.targetMZ = targetMZ;
    this.min = targetMZ - Builder.WINDOW_WIDTH_FROM_CENTER;
    this.max = targetMZ + Builder.WINDOW_WIDTH_FROM_CENTER;
  }

  public int getIndex() {
    return index;
  }

  public Double getTargetMZ() {
    return targetMZ;
  }

  public double getMin() {
    return min;
  }

  public double getMax() {
    return max;
  }
}
