from __future__ import absolute_import, division, print_function

import csv
import os

import numpy as np
from sklearn.cluster import MiniBatchKMeans
from tqdm import tqdm
from utility import row_to_mz


class LcmsClusterer:
    def __init__(self, n_cluster, training_output_file, training_input_file, row_numbers_file, retention_times_file,
                 output_directory, verbose=True):
        self.verbose = verbose

        self.n_cluster = n_cluster

        self.training_output_file = training_output_file
        self.training_input_file = training_input_file
        self.row_numbers_file = row_numbers_file
        self.retention_times_file = retention_times_file

        assert (os.path.exists(self.training_output_file))
        assert (os.path.exists(self.training_input_file))
        assert (os.path.exists(self.row_numbers_file))
        assert (os.path.exists(self.retention_times_file))

        self.output_directory = output_directory
        if not os.path.exists(self.output_directory):
            os.makedirs(self.output_directory)

    def cluster(self):
        training_output = np.load(self.training_output_file)

        kmeans = MiniBatchKMeans(n_clusters=self.n_cluster)

        if self.verbose:
            print("Clustering")
        kmeans.fit(training_output)

        if self.verbose:
            print("Fitting data to clusters.")
        return kmeans.predict(training_output)

    def write_to_file(self, output_tsv_file_name, clusters, block_size, mz_split, mz_min):
        training_real = np.load(self.training_input_file)
        row_numbers = np.load(self.row_numbers_file)
        retention_times = np.load(self.retention_times_file)

        if self.verbose:
            print("Writing results to file")

        with open(os.path.join(self.output_directory, output_tsv_file_name + ".tsv"), "w") as f:
            header = ["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into", "maxo", "cluster"] + [str(x) for x in
                                                                                                    range(0,
                                                                                                          block_size)]

            writer = csv.DictWriter(f, header, delimiter="\t")
            writer.writeheader()

            # For each original window
            for i in tqdm(range(0, len(training_real))):
                normalizer = row_numbers[i][2]
                row_in_array = row_numbers[i][0]
                starting_time_index = int(row_numbers[i][1])

                row = {}

                # Get the max intensity index.  Additionally, assign the row values.
                max_value_index = 0
                for time_number in range(0, len(training_real[i])):
                    if (training_real[i][time_number]) == 1:
                        max_value_index = time_number
                    row[str(time_number)] = training_real[i][time_number]

                # Which m/z bucket
                row["mz"] = row_to_mz(row_in_array, mz_split, mz_min)
                # Min and max within window
                row["mzmin"] = row["mz"]
                row["mzmax"] = row["mz"] + mz_split

                # Largest intensity value
                row["rt"] = retention_times[starting_time_index + max_value_index]
                row["rtmin"] = retention_times[starting_time_index]
                row["rtmax"] = retention_times[starting_time_index + len(training_real[i]) - 1]

                # Sum of all points aprox of AUTC
                row["into"] = sum(training_real[i]) * normalizer

                # We normalize by max value so this works out.
                row["maxo"] = normalizer
                row["cluster"] = str(clusters[i])

                writer.writerow(row)
