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

package com.twentyn.bioreactor.util.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyn.bioreactor.sensors.DOSensorData;
import com.twentyn.bioreactor.util.Time;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DateTimeSerdeTest {
  @Test
  public void testRoundTripOnClassUsingCustomSerializer() throws Exception {
    ObjectMapper om = new ObjectMapper();

    // Note: round-tripping floating point numbers through JSON is dangerous.  These values seem to work, though.
    DOSensorData dosd = new DOSensorData(6.00, 99.9, "test_device", Time.now());
    String json = om.writeValueAsString(dosd);
    DOSensorData dosd2 = om.readValue(json, new TypeReference<DOSensorData>() {});
    assertEquals("Original and serialized/deserialized objects match.", dosd, dosd2);
  }
}
