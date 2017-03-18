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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class PrecursorData implements Serializable {
    @JsonProperty("precursors")
    private Set<Precursor> precursors;

    public PrecursorData() {
        this.precursors = new HashSet<>();
    }

    public PrecursorData(Set<Precursor> precursors) {
        this.precursors = precursors;
    }

    @JsonIgnore
    public void addPrecursor(Precursor precursor) {
        if (this.precursors == null) {
            this.precursors = new HashSet<>();
        }
        this.precursors.add(precursor);
    }

    @JsonIgnore
    public void addPrecursors(Collection<Precursor> precursors) {
        precursors.stream().forEach(this::addPrecursor);
    }

    public Set<Precursor> getPrecursors() {
        return this.precursors;
    }

    public void setPrecursors(Collection<Precursor> precursors){
        this.precursors = new HashSet<>(precursors);
    }
}
