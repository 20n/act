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

public class P<S1, S2>
{
  S1 fst; S2 snd;
  public S1 fst() { return this.fst; } // set { this.fst = value; } }
  public void SetFst(S1 value) { this.fst = value; }
  public S2 snd() { return this.snd; }  // set { this.snd = value; } }
  public void SetSnd(S2 value) { this.snd = value; }
  public P(S1 fst, S2 snd)
  {
    this.fst = fst;
    this.snd = snd;
  }
  @Override
  public String toString() {
    return "(" + fst + "," + snd + ")";
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof P<?,?>)) return false;
    P<S1, S2> p = (P<S1, S2>)o;
    if ((p.fst == null && this.fst != null) || (p.fst != null && this.fst == null))
      return false;
    if ((p.snd == null && this.snd != null) || (p.snd != null && this.snd == null))
      return false;
    return ((p.fst == null && this.fst == null) || p.fst.equals(this.fst)) &&
        ((p.snd == null && this.snd == null) || p.snd.equals(this.snd));
  }
  @Override
  public int hashCode() {
    if (this.fst == null)
      return this.snd.hashCode();
    if (this.snd == null)
      return this.fst.hashCode();
    return this.fst.hashCode() ^ this.snd.hashCode();
  }
}