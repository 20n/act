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

    @JsonCreator
    public Precursor(@JsonProperty("precursor_inchis") List<InchiDescriptor> precursorMolecules,
                     @JsonProperty("source") String source) {
        this.precursorMolecules = precursorMolecules;
        this.source = source;
    }

    @JsonIgnore
    public List<InchiDescriptor> getMolecules(){
        return precursorMolecules;
    }

    @JsonIgnore
    public String getSources(){
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Precursor precursor = (Precursor) o;

        if (precursorMolecules != null ? !precursorMolecules.equals(precursor.precursorMolecules) : precursor.precursorMolecules != null)
            return false;
        return source != null ? source.equals(precursor.source) : precursor.source == null;

    }

    @Override
    public int hashCode() {
        int result = precursorMolecules != null ? precursorMolecules.hashCode() : 0;
        result = 31 * result + (source != null ? source.hashCode() : 0);
        return result;
    }
}
