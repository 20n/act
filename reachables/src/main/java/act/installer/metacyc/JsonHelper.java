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

package act.installer.metacyc;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Set;
import java.util.ArrayList;

public class JsonHelper {
  OrganismComposition src;
  JSONObject json;

  public JsonHelper(OrganismComposition src) {
    this.src = src;
    this.json = new JSONObject();
  }

  public void add(String k, Object v) {
    if (!this.json.has(k)) {
      // install new k,v pair as nothing existed before
      this.json.put(k, v);
    } else {
      // create an array with the old value + optionally anything that is already there
      Object vold = this.json.get(k);
      if (vold instanceof JSONArray) {
        // already promoted to array then just add to that array
        ((JSONArray)vold).put(v);
      } else if (vold instanceof String || vold instanceof JSONObject) {
        // need to promote old value to array and add new value
        JSONArray a = new JSONArray();
        a.put(vold);
        a.put(v);
        this.json.put(k, a);
      }
    }
  }

  // public void add(String k, Set v) {
  //   if (v.size() > 1) {
  //     JSONArray a = new JSONArray();
  //     for (Object o : v) {
  //       a.put(((BPElement)o).json(this.src));
  //     }
  //   } else if (v.size() == 1) {
  //     Object singleton = null;
  //     for (Object o : v)
  //       singleton = o;
  //     if (singleton != null)
  //       this.json.put(k, ((BPElement)singleton).json(this.src));
  //     else
  //       this.json.put(k, "(null)");
  //   } else
  //     this.json.put(k, new JSONArray());
  // }

  public JSONObject getJSON() {
    return json;
  }

}

