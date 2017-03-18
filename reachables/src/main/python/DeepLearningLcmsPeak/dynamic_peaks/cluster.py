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

import csv
import os

from sklearn.cluster import MiniBatchKMeans
from tqdm import tqdm

from dynamic_peaks.modules.utility import magic


class LcmsClusterer:
    def __init__(self, n_cluster, block_size, mz_min, verbose=True):
        self.verbose = verbose

        self.n_cluster = n_cluster

        self.kmeans = MiniBatchKMeans(n_clusters=self.n_cluster, random_state=magic.kmeans_random_state)

        self.block_size = block_size
        self.mz_min = mz_min

        self.output_directory = None

    def set_output_directory(self, output_directory):
        self.output_directory = output_directory
        if not os.path.exists(self.output_directory):
            print("Creating {} as it did not previously exist.  "
                  "This it the output directory.".format(self.output_directory))
            os.makedirs(self.output_directory)

    def fit(self, training_output):
        if self.verbose:
            print("Clustering")
        try:
            self.kmeans.fit(training_output)
        except ValueError:
            self.kmeans = None

    def predict(self, encoded_data, raw_normalized_data, extra_information, output_tsv_file_name, valid_peaks=None,
                drop_rt=None):
        """
        :param encoded_data:            The encoded version of the original matrix.  Size (# Samples x Encoding Length)
        :param raw_normalized_data:     The raw, normalized version of the
                                        input matrix. Size (# Samples x # time points)
        :param extra_information:       A matrix that carries extra information on.  The three fields in order are:

                                        1) The information is (Row number in the original matrix,
                                        2) Time point window was centered on,
                                        3) Maximum value of the window [What it was normalized by]
        :param output_tsv_file_name:    Name of the output file
        :param valid_peaks:             A list, if provided, of clusters containing valid peaks.
                                        If not provided, all clusters are written to file.
        :param drop_rt:                 If a sample is under this retention time, we drop it.
        """

        if self.kmeans is not None:
            clusters = self.kmeans.predict(encoded_data)
        else:
            print("Too few samples to cluster, assigning cluster of 0 to all.")
            clusters = [0] * len(encoded_data)

        if self.verbose:
            print("Writing results to file")

        with open(os.path.join(self.output_directory, output_tsv_file_name + ".tsv"), "w") as f:
            header = ["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into", "exp_maxo", "ctrl_maxo",
                      "maxo", "sn", "abs_sn", "cluster", "exp_std_dev", "ctrl_std_dev"] + \
                     [str(x) for x in range(0, self.block_size)]

            writer = csv.DictWriter(f, header, delimiter=magic.separator)
            writer.writeheader()

            # For each original window
            for i in tqdm(range(0, len(raw_normalized_data))):
                max_intensity_value = extra_information[i]["maxo"]

                row = {}

                for time_number in range(0, len(raw_normalized_data[i])):
                    row[str(time_number)] = raw_normalized_data[i][time_number]

                # Largest intensity value is where we call the retention time at
                row["rt"] = extra_information[i]["rt"]
                if drop_rt and row["rt"] <= drop_rt:
                    continue

                row["rtmin"] = extra_information[i]["rtmin"]
                row["rtmax"] = extra_information[i]["rtmax"]

                # Sum of all points aprox of AUTC
                # into == integrated intensity of original raw peak
                row["into"] = sum(raw_normalized_data[i]) * max_intensity_value

                # We normalize by max value so this works out.
                row["exp_maxo"] = extra_information[i]["exp_maxo"]
                row["ctrl_maxo"] = extra_information[i]["ctrl_maxo"]
                row["maxo"] = max_intensity_value
                row["cluster"] = str(clusters[i])

                if extra_information[i].get("sn"):
                    row["sn"] = extra_information[i]["sn"]
                else:
                    row["sn"] = 1

                row["abs_sn"] = abs(row["sn"])

                row["mz"] = extra_information[i]["mz"]

                row["exp_std_dev"] = extra_information[i]["exp_std_dev"]
                row["ctrl_std_dev"] = extra_information[i]["ctrl_std_dev"]

                # Check if it is in the valid peaks or if no valid peaks were supplied.
                if (valid_peaks and clusters[i] in valid_peaks) or not valid_peaks:
                    writer.writerow(row)
