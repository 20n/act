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

package act.installer.bing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBObject;

/**
 * This class contains the structure of a single search result, as returned by the Bing Search API.
 * It also provides convenient translation methods from JsonNode and BasicDBObject and to BasicDBObject.
 */

public class SearchResult {

  // The ID field was removed with v5.0 of the API
  // Since we didn't use it much, we removed it from SearchResults as well.
  // See upgrade guide: https://msdn.microsoft.com/en-US/library/mt707570.aspx

  private String title;
  private String description;
  private String url;

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  public void populateFromJsonNode(JsonNode result) {
    setDescription(result.path("snippet").textValue());
    setTitle(result.path("name").textValue());
    setUrl(result.path("displayUrl").textValue());
  }

  public void populateFromBasicDBObject(BasicDBObject result) {
    setDescription((String) result.get("description"));
    setTitle((String) result.get("title"));
    setUrl((String) result.get("url"));
  }

  public BasicDBObject getBasicDBObject() {
    BasicDBObject topSearchResultDBObject = new BasicDBObject();
    topSearchResultDBObject.put("title", getTitle());
    topSearchResultDBObject.put("description", getDescription());
    topSearchResultDBObject.put("url", getUrl());
    return topSearchResultDBObject;
  }
}
