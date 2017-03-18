/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PatentSummary {
  private static final String BASE_URL_FORMAT = "http://patft1.uspto.gov/netacgi/nph-Parser?patentnumber=%s";

  @JsonProperty(value = "id", required = true)
  private String id;
  @JsonProperty(value = "title", required = true)
  private String title;
  @JsonProperty(value = "relevance_score", required = false)
  private Float relevanceScore;

  private PatentSummary() {

  }

  public PatentSummary(String id, String title, Float relevanceScore) {
    this.id = id;
    this.title = title;
    this.relevanceScore = relevanceScore;
  }

  public String getId() {
    return id;
  }

  private void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  private void setTitle(String title) {
    this.title = title;
  }

  public Float getRelevanceScore() {
    return relevanceScore;
  }

  private void setRelevanceScore(Float relevanceScore) {
    this.relevanceScore = relevanceScore;
  }

  public String generateUSPTOURL() {
    // With help from http://patents.stackexchange.com/questions/3304/how-to-get-a-patent-or-patent-application-permalink-at-the-uspto-website

    // Strip country designator prefix and leading zeroes, then ditch the publication file suffix.
    String shortId = id.replaceFirst("^[A-Z]*0*", "").replaceFirst("-.*$", "");
    return String.format(BASE_URL_FORMAT, shortId);
  }
}
