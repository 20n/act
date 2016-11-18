package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collection;
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
        getPrecursors().add(precursor);
    }

    public void addPrecursors(Collection<Precursor> precursors) {
        this.precursors.addAll(precursors);
    }

    @JsonIgnore
    public Set<Precursor> getPrecursors() {
        if (this.precursors == null) {
            this.precursors = new HashSet<>();
            return this.precursors;
        }
        return this.precursors;
    }
}
