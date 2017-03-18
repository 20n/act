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

public abstract class LargeMapKey implements Serializable {
  private static final long serialVersionUID = 1L;
  private int keyid;
    public int Keyid() { return this.keyid;}
    public int LevelId(int level, int levelsize) {
        // returns the digits at the level'th location in the base(levelsize) representation of keyid
        // e.g., level=2, levelsize=100, returns 23 for 10230001
        int shifted = this.keyid / ((int)Math.pow(levelsize, level));
        // System.out.format("Key: %d, Level: %d, fanout: %d, shifted: %d, file: %d\n", this.keyid, level, levelsize, shifted, shifted % levelsize);
        return shifted % levelsize;
    }
    protected LargeMapKey(int keyid) { this.keyid = keyid;}

    // ensure that children override equals and hashcode
    @Override
    public abstract boolean equals(Object o);
    @Override
    public abstract int hashCode();
    @Override
    public String toString() {
        return "SwapKey:" + this.keyid;
    }
}

class TestKey extends LargeMapKey {
  private static final long serialVersionUID = 1L;

  TestKey(int id) { super(id); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TestKey))
            return false;
        return ((TestKey)o).Keyid() == this.Keyid();
    }

    @Override
    public int hashCode() {
        return this.Keyid();
    }
}