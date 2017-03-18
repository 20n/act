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
import java.util.List;


public class Precursor implements Serializable {
    @JsonProperty("precursor_molecules")
    private List<InchiDescriptor> precursorMolecules;
    @JsonProperty("source")
    private String source;
    @JsonProperty("sequences")
    private List<String> sequences;

    private Precursor() {

    }

    public Precursor(List<InchiDescriptor> precursorMolecules, String source, List<String> sequences) {
        this.precursorMolecules = precursorMolecules;
        this.source = source;
        this.sequences = sequences;
    }

    public List<InchiDescriptor> getMolecules(){
        return precursorMolecules;
    }

    public String getSources(){
        return source;
    }

    public List<String> getSequences() {
        return sequences;
    }

    public void setMolecules(List<InchiDescriptor> precursorMolecules){
        this.precursorMolecules = precursorMolecules;
    }

    public void setSources(String source){
        this.source = source;
    }

    public void setSequences(List<String> sequences) {
        this.sequences = sequences;
    }

    @JsonIgnore
    public void addSequence(String sequence){
        getSequences().add(sequence);
    }

    @JsonIgnore
    public void addSequences(List<String> sequence){
        getSequences().addAll(sequence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Precursor precursor = (Precursor) o;

        if (!precursorMolecules.equals(precursor.precursorMolecules)) return false;
        if (!source.equals(precursor.source)) return false;
        return sequences != null ? sequences.equals(precursor.sequences) : precursor.sequences == null;
    }

    @Override
    public int hashCode() {
        int result = precursorMolecules.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + (sequences != null ? sequences.hashCode() : 0);
        return result;
    }
}
