from __future__ import absolute_import, division, print_function

import csv
import os

import defaults
from sklearn.cluster import MiniBatchKMeans
from tqdm import tqdm
from utility import row_to_mz


class LcmsClusterer:
    def __init__(self, n_cluster, block_size, mz_split, mz_min, verbose=True):
        self.verbose = verbose

        self.n_cluster = n_cluster

        self.kmeans = MiniBatchKMeans(n_clusters=self.n_cluster, random_state=defaults.kmeans_random_state)

        self.block_size = block_size
        self.mz_split = mz_split
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
        self.kmeans.fit(training_output)

    def predict(self, encoded_data, raw_normalized_data, extra_information, retention_times, output_tsv_file_name,
                valid_peaks=None):
        """
        :param encoded_data:            The encoded version of the original matrix.  Size (# Samples x Encoding Length)
        :param raw_normalized_data:     The raw, normalized version of the
                                        input matrix. Size (# Samples x # time points)
        :param extra_information:       A matrix that carries extra information on.  The three fields in order are:

                                        1) The information is (Row number in the original matrix,
                                        2) Time point window was centered on,
                                        3) Maximum value of the window [What it was normalized by]
        :param retention_times:         Retention time of each time index.  Size (# Of time points x 1)
        :param output_tsv_file_name:    Name of the output file
        :param valid_peaks:             A list, if provided, of clusters containing valid peaks.
                                        If not provided, all clusters are written to file.
        """
        clusters = self.kmeans.predict(encoded_data)
        if self.verbose:
            print("Writing results to file")

        with open(os.path.join(self.output_directory, output_tsv_file_name + ".tsv"), "w") as f:
            header = ["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into", "maxo", "sn", "cluster"] + \
                     [str(x) for x in range(0, self.block_size)]

            writer = csv.DictWriter(f, header, delimiter=defaults.separator)
            writer.writeheader()

            # For each original window
            for i in tqdm(range(0, len(raw_normalized_data))):
                normalizer = extra_information[i][2]
                row_in_array = extra_information[i][0]
                starting_time_index = int(extra_information[i][1])

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
                # into == integrated intensity of original raw peak
                row["into"] = sum(raw_normalized_data[i]) * normalizer

                # We normalize by max value so this works out.
                row["maxo"] = normalizer
                row["cluster"] = str(clusters[i])

                # TODO Calculate
                row["sn"] = 1

                # Check if it is in the valid peaks or if no valid peaks were supplied.
                if (valid_peaks and clusters[i] in valid_peaks) or not valid_peaks:
                    writer.writerow(row)
