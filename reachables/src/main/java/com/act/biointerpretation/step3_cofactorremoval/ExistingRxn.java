package com.act.biointerpretation.step3_cofactorremoval;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 3/8/16.
 */
public class ExistingRxn {
    public Set<Integer> subCos = new HashSet<>();
    public Set<Integer> pdtCos = new HashSet<>();
    public Set<Long> subIds = new HashSet<>();
    public Set<Long> pdtIds = new HashSet<>();

    @Override
    public int hashCode() {
        int out = 0;
        for(Integer id : subCos) {
            out += (id);
        }
        for(Integer id : pdtCos) {
            out += (id);
        }
        for(Long id : subIds) {
            out += (id);
        }
        for(Long id : pdtIds) {
            out += (id);
        }
        return out;
    }

    @Override
    public boolean equals(Object obj) {
        try {
            ExistingRxn existing = (ExistingRxn) obj;

            if(!this.subCos.equals(existing.subCos)) {
                return false;
            }

            if(!this.subCos.equals(existing.pdtCos)) {
                return false;
            }

            if(!this.subCos.equals(existing.subIds)) {
                return false;
            }

            if(!this.subCos.equals(existing.pdtIds)) {
                return false;
            }

            return true;

        } catch(Exception err) {
            return false;
        }
    }
}
