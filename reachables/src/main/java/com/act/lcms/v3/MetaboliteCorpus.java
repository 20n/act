package com.act.lcms.v3;

import java.util.List;

public interface MetaboliteCorpus {

  List<Metabolite> fetchMetabolitesInMassWindow(Double minMass, Double maxMass);
  List<Metabolite> getMetaboliteList();

}