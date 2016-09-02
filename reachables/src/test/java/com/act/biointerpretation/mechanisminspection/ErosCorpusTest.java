package com.act.biointerpretation.mechanisminspection;

import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ErosCorpusTest {

  @Test
  public void filterCorpusBySubstrateCount() throws Exception {
    ErosCorpus fullCorpus = new ErosCorpus();
    fullCorpus.loadValidationCorpus();

    ErosCorpus corpusOneSubstrate = new ErosCorpus();
    corpusOneSubstrate.loadValidationCorpus();

    corpusOneSubstrate.filterCorpusBySubstrateCount(1);
    assertEquals("One substrate ERO lists match in size",
        fullCorpus.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(1)).collect(Collectors.toList()).size(),
        corpusOneSubstrate.getRos().size()
    );

    assertEquals("Re-filtering substrate count filtered eros yields same list",
        corpusOneSubstrate.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(1)).collect(Collectors.toList()),
        corpusOneSubstrate.getRos()
    );


    ErosCorpus corpusTwoSubstrates = new ErosCorpus();
    corpusTwoSubstrates.loadValidationCorpus();

    corpusTwoSubstrates.filterCorpusBySubstrateCount(2);
    assertEquals("Two substrates ERO lists match in size",
        fullCorpus.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(2)).collect(Collectors.toList()).size(),
        corpusTwoSubstrates.getRos().size()
    );

    assertEquals("Re-filtering substrate count filtered eros yields same list",
        corpusTwoSubstrates.getRos().stream().
            filter(ero -> ero.getSubstrate_count().equals(2)).collect(Collectors.toList()),
        corpusTwoSubstrates.getRos()
    );
  }
}
