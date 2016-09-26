from __future__ import absolute_import, division, print_function

import csv
import os

from sklearn.cluster import MiniBatchKMeans
from tqdm import tqdm
from utility import row_to_mz


class LcmsClusterer:
    def __init__(self, n_cluster, output_directory, block_size, mz_split, mz_min, verbose=True):
        self.verbose = verbose

        self.n_cluster = n_cluster

        self.output_directory = output_directory
        if not os.path.exists(self.output_directory):
            os.makedirs(self.output_directory)

        self.kmeans = MiniBatchKMeans(n_clusters=self.n_cluster)

        self.block_size = block_size
        self.mz_split = mz_split
        self.mz_min = mz_min

    def fit(self, training_output):
        if self.verbose:
            print("Clustering")
        self.kmeans.fit(training_output)

    def predict(self, encoded_data, raw_normalized_data, row_numbers, retention_times, output_tsv_file_name):
        clusters = self.kmeans.predict(encoded_data)
        if self.verbose:
            print("Writing results to file")

        with open(os.path.join(self.output_directory, output_tsv_file_name + ".tsv"), "w") as f:
            header = ["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into", "maxo", "cluster"] + [str(x) for x in
                                                                                                    range(0,
                                                                                                          self.block_size)]

            writer = csv.DictWriter(f, header, delimiter="\t")
            writer.writeheader()

            # For each original window
            for i in tqdm(range(0, len(raw_normalized_data))):
                normalizer = row_numbers[i][2]
                row_in_array = row_numbers[i][0]
                starting_time_index = int(row_numbers[i][1])

                row = {}

                # Get the max intensity index.  Additionally, assign the row values.
                max_value_index = 0
                for time_number in range(0, len(raw_normalized_data[i])):
                    if (raw_normalized_data[i][time_number]) == 1:
                        max_value_index = time_number
                    row[str(time_number)] = raw_normalized_data[i][time_number]

                # Which m/z bucket
                row["mz"] = row_to_mz(row_in_array, self.mz_split, self.mz_min)
                # Min and max within window
                row["mzmin"] = row["mz"]
                row["mzmax"] = row["mz"] + self.mz_split

                # Largest intensity value
                row["rt"] = retention_times[starting_time_index + max_value_index]
                row["rtmin"] = retention_times[starting_time_index]
                row["rtmax"] = retention_times[starting_time_index + len(raw_normalized_data[i]) - 1]

                # Sum of all points aprox of AUTC
                row["into"] = sum(raw_normalized_data[i]) * normalizer

                # We normalize by max value so this works out.
                row["maxo"] = normalizer
                row["cluster"] = str(clusters[i])

                writer.writerow(row)
