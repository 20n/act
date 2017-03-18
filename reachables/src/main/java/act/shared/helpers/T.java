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

package act.shared.helpers;

import java.io.Serializable;

public class T<S1, S2, S3> implements Serializable
{
  private static final long serialVersionUID = 1L;
  S1 fst; S2 snd; S3 third;
  public S1 fst() { return this.fst; } // set { this.fst = value; } }
  public S2 snd() { return this.snd; }  // set { this.snd = value; } }
  public S3 third() { return this.third; }  // set { this.snd = value; } }

  public T(){}

  public T(S1 fst, S2 snd, S3 third)
  {
    this.fst = fst;
    this.snd = snd;
    this.third = third;
  }
  @Override
  public String toString() {
    return "(" + fst + "," + snd + "," + third + ")";
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof T<?,?,?>)) return false;
    T<S1, S2, S3> t = (T<S1, S2, S3>)o;
    return t.fst.equals(this.fst) && t.snd.equals(this.snd) && t.third.equals(this.third);
  }
  @Override
  public int hashCode() {
    return this.fst.hashCode() ^ this.snd.hashCode() ^ this.third.hashCode();
  }
}