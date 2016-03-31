package com.act.biointerpretation.step2_desalting;

import act.server.DBIterator;
import act.server.NoSQLAPI;
import act.shared.Chemical;

import java.util.Iterator;

public class ChemicalDesalter {
  private NoSQLAPI api;
  private Desalter desalter;

  public void run() throws Exception {
    Desalter desalter = new Desalter();

    Iterator<Chemical> chemicalIterator = api.readChemsFromInKnowledgeGraph();

    while (chemicalIterator.hasNext()) {
      Chemical chem = chemicalIterator.next();

    }
  }
}
