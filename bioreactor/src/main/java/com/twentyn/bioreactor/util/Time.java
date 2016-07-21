package com.twentyn.bioreactor.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class Time {
  public static DateTime now() {
    return new DateTime().withZone(DateTimeZone.UTC);
  }
}
