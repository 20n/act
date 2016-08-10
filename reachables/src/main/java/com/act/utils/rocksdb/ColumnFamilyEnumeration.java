package com.act.utils.rocksdb;

public interface ColumnFamilyEnumeration<T extends ColumnFamilyEnumeration> {
  String getName();
  T getFamilyByName(String name);
}
