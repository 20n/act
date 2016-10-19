package com.act.lcms.v3;

public interface MolecularStructure {
  String getInchi();
  Boolean parseStructure(String inchi);
  Double getMass();
}
