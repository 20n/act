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

package act.shared.helpers;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class MongoDBToJSON {

  public static DBObject conv(JSONArray a) {
    BasicDBList result = new BasicDBList();
    try {
      for (int i = 0; i < a.length(); ++i) {
        Object o = a.get(i);
        if (o instanceof JSONObject) {
          result.add(conv((JSONObject)o));
        } else if (o instanceof JSONArray) {
          result.add(conv((JSONArray)o));
        } else {
          result.add(o);
        }
      }
      return result;
    } catch (JSONException je) {
      return null;
    }
  }

  public static DBObject conv(JSONObject o) {
    BasicDBObject result = new BasicDBObject();
    try {
      Iterator i = o.keys();
      while (i.hasNext()) {
        String k = (String)i.next();
        Object v = o.get(k);
        if (v instanceof JSONArray) {
          result.put(k, conv((JSONArray)v));
        } else if (v instanceof JSONObject) {
          result.put(k, conv((JSONObject)v));
        } else {
          result.put(k, v);
        }
      }
      return result;
    } catch (JSONException je) {
      return null;
    }
  }

  public static JSONArray conv(BasicDBList a) {
    JSONArray result = new JSONArray();
    for (int i = 0; i < a.size(); ++i) {
      Object o = a.get(i);
      if (o instanceof DBObject) {
        result.put(conv((DBObject)o));
      } else if (o instanceof BasicDBList) {
        result.put(conv((BasicDBList)o));
      } else {
        result.put(o);
      }
    }
    return result;
  }

  public static JSONObject conv(DBObject o) {
    JSONObject result = new JSONObject();
    try {
      for (String k : o.keySet()) {
        Object v = o.get(k);
        if (v instanceof BasicDBList) {
          result.put(k, conv((BasicDBList)v));
        } else if (v instanceof DBObject) {
          result.put(k, conv((DBObject)v));
        } else {
          result.put(k, v);
        }
      }
      return result;
    } catch (JSONException je) {
      return null;
    }
  }
}
