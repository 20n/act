package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;


class Precursor implements Serializable {
    @JsonProperty("precursor_inchis")
    private List<InchiDescriptor> precursorMolecules;

    @JsonProperty("source")
    private String source;

    @JsonProperty("sequences")
    private List<String> sequences;

    @JsonCreator
    public Precursor(@JsonProperty("precursor_inchis") List<InchiDescriptor> precursorMolecules,
                     @JsonProperty("source") String source,
                     @JsonProperty("sequences") List<String> sequences) {
        this.precursorMolecules = precursorMolecules;
        this.source = source;
        this.sequences = sequences;
    }

    @JsonIgnore
    public List<InchiDescriptor> getMolecules(){
        return precursorMolecules;
    }

    @JsonIgnore
    public String getSources(){
        return source;
    }

    @JsonIgnore
    public List<String> getSequences() {
        return sequences;
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
