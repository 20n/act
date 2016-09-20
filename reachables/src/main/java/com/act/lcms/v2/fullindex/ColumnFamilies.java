package com.act.lcms.v2.fullindex;

import com.act.utils.rocksdb.ColumnFamilyEnumeration;

import java.util.HashMap;
import java.util.Map;

// Package private so the searcher can use this enum but nobody else.
enum ColumnFamilies implements ColumnFamilyEnumeration<ColumnFamilies> {
  TARGET_TO_WINDOW("target_mz_to_window_obj"),
  TIMEPOINTS("timepoints"),
  ID_TO_TRIPLE("id_to_triple"),
  TIMEPOINT_TO_TRIPLES("timepoints_to_triples"),
  WINDOW_ID_TO_TRIPLES("windows_to_triples"),
  ;

  private static final Map<String, ColumnFamilies> reverseNameMap =
      new HashMap<String, ColumnFamilies>() {{
        for (ColumnFamilies cf : ColumnFamilies.values()) {
          put(cf.getName(), cf);
        }
      }};

  private String name;

  ColumnFamilies(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public ColumnFamilies getFamilyByName(String name) {
    return reverseNameMap.get(name);
  }
}
