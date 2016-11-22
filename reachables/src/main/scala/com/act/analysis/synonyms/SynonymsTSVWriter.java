package com.act.analysis.synonyms;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.util.List;

public class SynonymsTSVWriter {

  public static String toCSV(List<Synonyms> synonyms) throws JsonProcessingException {
    CsvMapper mapper = new CsvMapper();
    CsvSchema schema = mapper.schemaFor(Synonyms.class).withHeader();
    return mapper.writer(schema).writeValueAsString(synonyms);
  }
}
