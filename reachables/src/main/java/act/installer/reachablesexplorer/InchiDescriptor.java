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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

class InchiDescriptor implements Serializable {
    private String name;

    @JsonProperty("InChI")
    private String inchi;

    private String inchiKey;

    private InchiDescriptor() {

    }

    public InchiDescriptor(String name,
                           String inchi,
                           String inchiKey){
        this.name = name;
        this.inchi = inchi;
        this.inchiKey = inchiKey;
    }

    InchiDescriptor(Reachable reachable){
        this.name = reachable.getPageName();
        this.inchi = reachable.getInchi();
        this.inchiKey = reachable.getInchiKey();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInchi() {
        return inchi;
    }

    public void setInchi(String inchi) {
        this.inchi = inchi;
    }

    public String getInchiKey() {
        return inchiKey;
    }

    public void setInchiKey(String inchiKey) {
        this.inchiKey = inchiKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InchiDescriptor that = (InchiDescriptor) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (inchi != null ? !inchi.equals(that.inchi) : that.inchi != null) return false;
        return inchiKey != null ? inchiKey.equals(that.inchiKey) : that.inchiKey == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (inchi != null ? inchi.hashCode() : 0);
        result = 31 * result + (inchiKey != null ? inchiKey.hashCode() : 0);
        return result;
    }
}
