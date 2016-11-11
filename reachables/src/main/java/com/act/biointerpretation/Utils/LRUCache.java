package com.act.biointerpretation.Utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple LRU cache using LinkedHashMap's built-in functionality.
 * @param <K> The key type.
 * @param <V> The value type.
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
  private int capacity;

  /**
   * Create a new LRUCache with fixed capacity and LRU behavior.
   * @param capacity The number of entries to cache.
   * @param accessOrder If set to true, accessing an element in the map constitutes usage for LRU determination; if not,
   *                    insertion order is used instead.
   */
  public LRUCache(int capacity, boolean accessOrder) {
    super(capacity + 1, 1.0f, accessOrder);
    this.capacity = capacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return this.size() > this.capacity;
  }
}
