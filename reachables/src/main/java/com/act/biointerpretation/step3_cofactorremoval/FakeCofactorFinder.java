package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import com.act.biointerpretation.step2_desalting.Desalter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class finds for cofactor-like components within a fake inchi.
 * Created by jca20n on 11/12/1.
 */
public class FakeCofactorFinder {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Desalter.class);
  private Map<String, String> fakeCofactorToRealCofactorName;

  public FakeCofactorFinder() {
    try {
      FakeCofactorCorpus corpus = new FakeCofactorCorpus();
      corpus.loadCorpus();
      fakeCofactorToRealCofactorName = corpus.getFakeCofactorNameToRealCofactorName();
    } catch (IOException e) {
      LOGGER.error(String.format("Error hydrating the fake cofactor corpus. Error: %s", e.getMessage()));
      fakeCofactorToRealCofactorName = new LinkedHashMap<>();
    }
  }

  /**
   * This function scans a fake inchi chemical and detects if it has a cofactor in it.
   * @param chemical - The chemical being analyzed
   * @return - The cofactor present within the chemical.
   */
  public String scanAndReturnCofactorNameIfItExists(Chemical chemical) {
    Set<String> names = new HashSet<>();
    names.addAll(chemical.getBrendaNames());
    names.addAll(chemical.getSynonyms());
    names.addAll(chemical.getPubchemNames().keySet());
    names.addAll(chemical.getPubchemNameTypes());
    names.addAll(chemical.getKeywords());
    names.addAll(chemical.getCaseInsensitiveKeywords());

    JSONObject metacycData = chemical.getRef(Chemical.REFS.METACYC);

    if (metacycData != null) {
      JSONArray meta = metacycData.getJSONArray("meta");
      JSONObject firstMetaObject = meta.getJSONObject(0);
      JSONArray metaNames = firstMetaObject.getJSONArray("names");
      for (int i = 0; i < metaNames.length(); i++) {
        String chemicalName = metaNames.getString(i);
        names.add(chemicalName);
      }
    }

    LOGGER.debug(String.format("The size of fakeCofactorToRealCofactorName is %d and the size of name is %d",
        fakeCofactorToRealCofactorName.size(), names.size()));

    for (String name : names) {
      if (fakeCofactorToRealCofactorName.containsKey(name)) {
        return fakeCofactorToRealCofactorName.get(name);
      }
    }

    return null;
  }
}
