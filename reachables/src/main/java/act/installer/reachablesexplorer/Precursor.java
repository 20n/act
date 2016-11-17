package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;


class Precursor implements Serializable {
    @JsonProperty("precursor_inchis")
    private List<String> precursorMolecules;
    @JsonProperty("source")
    private String source;

    @JsonCreator
    public Precursor(@JsonProperty("precursor_inchis") List<String> precursorMolecules,
                     @JsonProperty("source") String source) {
        this.precursorMolecules = precursorMolecules;
        this.source = source;
    }

    @JsonIgnore
    public List<String> getMolecules(){
        return precursorMolecules;
    }

    @JsonIgnore
    public String getSources(){
        return source;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Precursor) &&
                source.equals(((Precursor) o).getSources()) &&
                precursorMolecules.equals(((Precursor) o).getMolecules());
    }

    @Override
    public int hashCode() {
        int start = 31;
        start = 31 * start + source.hashCode();
        for (String m: this.precursorMolecules){
            start = 31*start + m.hashCode();
        }

        return start;
    }
}
