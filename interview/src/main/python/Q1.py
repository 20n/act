"""
"                                                                        "
"  This file is part of the 20n/act project.                             "
"  20n/act enables DNA prediction for synthetic biology/bioengineering.  "
"  Copyright (C) 2017 20n Labs, Inc.                                     "
"                                                                        "
"  Please direct all queries to act@20n.com.                             "
"                                                                        "
"  This program is free software: you can redistribute it and/or modify  "
"  it under the terms of the GNU General Public License as published by  "
"  the Free Software Foundation, either version 3 of the License, or     "
"  (at your option) any later version.                                   "
"                                                                        "
"  This program is distributed in the hope that it will be useful,       "
"  but WITHOUT ANY WARRANTY; without even the implied warranty of        "
"  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         "
"  GNU General Public License for more details.                          "
"                                                                        "
"  You should have received a copy of the GNU General Public License     "
"  along with this program.  If not, see <http://www.gnu.org/licenses/>. "
"                                                                        "
"""

def f(g1, g2, partial_mapping):
  """Find the full mapping for these two graphs"""
  """Make assumptions as necessary"""

  full_mapping = {}
  return full_mapping

gLeft = {
    0: [1, 5, 10],
    1: [2, 0],
    2: [1, 3],
    3: [2, 4],
    4: [3, 5],
    5: [0, 4],
    6: [9],
    7: [9],
    8: [9],
    9: [10, 6, 7, 8],
    10: [0, 9, 11],
    11: [10]
    }
gRight = {
    0: [1, 5, 7],
    1: [2, 0],
    2: [1, 3],
    3: [2, 4],
    4: [3, 5],
    5: [0, 4],
    6: [7],
    7: [0, 6, 8],
    8: [7, 9, 10, 11],
    9: [8],
    10: [8],
    11: [8]
    }

"""
gLeft = {
0: [1, 5, 9],
1: [0, 2],
2: [1, 3],
3: [2, 4],
4: [3, 5],
5: [0, 4],
6: [7, 8],
7: [6, 8],
8: [6, 7, 9],
9: [0, 8, 10],
10:  [9, 11,  12],
11:  [10,  12],
12:  [10,  11,  13],
13:  [12],
}

gRight = {
0: [1, 5, 10],
1: [0, 2],
2: [1, 3],
3: [2, 4],
4: [3, 5],
5: [0, 4],
6: [7],
7: [6, 8, 9],
8: [7, 9, 10],
9: [7, 8],
10:  [0, 8, 11],
11:  [10,  12,  13,  14],
12:  [11],
13:  [11],
14:  [12],
}
"""


partial = {0:0, 1:1, 2:2, 3:3, 4:4, 5:5}

print f(gLeft, gRight, partial)
