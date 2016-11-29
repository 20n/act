package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.List;


public class Precursor implements Serializable {
    private List<InchiDescriptor> precursorMolecules;
    private String source;
    private List<String> sequences;

    public Precursor() {}

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
