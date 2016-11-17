package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

class PrecursorData implements Serializable {
    @JsonProperty("prediction_precursors")
    private Set<Precursor> precursors;


    public PrecursorData() {
        this.precursors = new HashSet<>();
    }
    @JsonCreator
    public PrecursorData(@JsonProperty("prediction_precursors") Set<Precursor> precursors) {
        this.precursors = precursors;
    }

    @JsonIgnore
    public void addPrecursor(Precursor precursor) {
        this.precursors.add(precursor);
    }

    @JsonIgnore
    public Set<Precursor> getPrecursors() {
        return this.precursors;
    }
}
