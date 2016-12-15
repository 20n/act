package com.twentyn.search.substructure;

/**
 * A simple container class to bind to form submission.
 * See http://docs.spring.io/spring/docs/current/spring-framework-reference/html/view.html#view-simple-binding
 */
public class QueryForm {
  private String query;

  QueryForm() {

  }

  public QueryForm(String query) {
    this.query = query;
  }

  public String getQuery() {
    return query;
  }

  void setQuery(String query) {
    this.query = query;
  }
}
