package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;


class Precursor implements Serializable {
    @JsonProperty("precursor_inchis")
    private List<String> precursorMolecules;
    @JsonProperty("source")
    private List<String> sources;

    public Precursor(List<String> precursorMolecules, List<String> sources) {
        this.precursorMolecules = precursorMolecules;
        this.sources = sources;
    }

    @JsonIgnore
    public List<String> getMolecules(){
        return precursorMolecules;
    }

    @JsonIgnore
    public List<String> getSources(){
        return sources;
    }
}
