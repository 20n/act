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

from __future__ import absolute_import, division, print_function

"""
Deep.py Default Values
"""
cluster_number = 40
mz_split = 0.003
encoding_size = 10  # Meaning we can visualize it on a 2D plot!!
window_size = 150
mz_min = 49
mz_max = 951
time_min = 0
time_max = 450
time_step = 2.5  # Seconds
local_area_band_halfwidth = 3

"""
Lcms_Autoencoder.py Default Values
"""
# The lowest max value a window can have before we drop that window.
threshold = 1e4

loss_function = "mse"
metrics = ["accuracy"]
training_split = 0.9
batch_size = 100

"""
Cluster.py Default Values
"""
# As the joke goes, random state of 1337 for reproducibility
kmeans_random_state = 1337

"""
*SV file separator (Default tsv)
"""
separator = "\t"
default_output_file_ending = ".tsv"
