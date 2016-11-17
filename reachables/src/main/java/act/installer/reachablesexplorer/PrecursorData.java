package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class PrecursorData implements Serializable {
    @JsonProperty("prediction_precursors")
    private List<Precursor> precursors;

    public PrecursorData() {
        this.precursors = new ArrayList<>();
    }

    public PrecursorData(List<Precursor> precursors) {
        this.precursors = precursors;
    }

    @JsonIgnore
    public void addPrecursor(Precursor precursor) {
        this.precursors.add(precursor);
    }

    @JsonIgnore
    public List<Precursor> getPrecursors() {
        return this.precursors;
    }
}
