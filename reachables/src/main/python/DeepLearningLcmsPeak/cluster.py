from __future__ import absolute_import, division, print_function

import csv
import os

from sklearn.cluster import MiniBatchKMeans
from tqdm import tqdm

import magic
from utility import row_to_mz, column_number_to_time


class LcmsClusterer:
    def __init__(self, n_cluster, block_size, mz_split, mz_min, verbose=True):
        self.verbose = verbose

        self.n_cluster = n_cluster

        self.kmeans = MiniBatchKMeans(n_clusters=self.n_cluster, random_state=magic.kmeans_random_state)

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

    def predict(self, encoded_data, raw_normalized_data, extra_information, output_tsv_file_name, row_matrices,
                valid_peaks=None, drop_rt=None):
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
        :param drop_rt:                 If a sample is under this retention time, we drop it.
        """
        clusters = self.kmeans.predict(encoded_data)
        if self.verbose:
            print("Writing results to file")

        with open(os.path.join(self.output_directory, output_tsv_file_name + ".tsv"), "w") as f:
            header = ["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into",
                      "maxo", "sn", "abs_sn", "cluster", "exp_std_dev", "ctrl_std_dev"] + \
                     [str(x) for x in range(0, self.block_size)]

            writer = csv.DictWriter(f, header, delimiter=magic.separator)
            writer.writeheader()

            # For each original window
            for i in tqdm(range(0, len(raw_normalized_data))):
                max_intensity_value = extra_information[i]["maxo"]
                row_in_array = extra_information[i]["row"]
                time_index = int(extra_information[i]["time"])

                row = {}

                # Get the max intensity index.  Additionally, assign the row values.
                max_value_index = 0
                for time_number in range(0, len(raw_normalized_data[i])):
                    if abs(raw_normalized_data[i][time_number]) == 1:
                        max_value_index = time_number
                    row[str(time_number)] = raw_normalized_data[i][time_number]

                # Largest intensity value is where we call the retention time at
                row["rt"] = column_number_to_time(time_index + max_value_index, magic.time_step, magic.time_min)
                if drop_rt and row["rt"] <= drop_rt:
                    continue

                row["rtmin"] = column_number_to_time(time_index, magic.time_step, magic.time_min)
                row["rtmax"] = column_number_to_time(time_index + len(raw_normalized_data[i]) - 1, magic.time_step,
                                                     magic.time_min)

                # Sum of all points aprox of AUTC
                # into == integrated intensity of original raw peak
                row["into"] = sum(raw_normalized_data[i]) * max_intensity_value

                # We normalize by max value so this works out.
                row["maxo"] = max_intensity_value
                row["cluster"] = str(clusters[i])

                if extra_information[i].get("sn"):
                    row["sn"] = extra_information[i]["sn"]
                else:
                    row["sn"] = 1

                row["abs_sn"] = abs(row["sn"])

                # Which m/z bucket
                # call it in the middle
                which_sample = 0 if row["sn"] > 0 else 1
                row["mz"] = \
                    row_matrices[which_sample].get_bucket_mz()[int(row_in_array), int(time_index + max_value_index)]
                if row["mz"] == 0:
                    # So we don't get 0 if something messes up.
                    row["mz"] = row_to_mz(row_in_array, self.mz_split, self.mz_min) + 0.5 * self.mz_split
                # Min and max within window
                row["mzmin"] = row_to_mz(row_in_array, self.mz_split, self.mz_min)
                row["mzmax"] = row_to_mz(row_in_array, self.mz_split, self.mz_min) + self.mz_split

                row["exp_std_dev"] = extra_information[i]["exp_std_dev"]
                row["ctrl_std_dev"] = extra_information[i]["ctrl_std_dev"]

                # Check if it is in the valid peaks or if no valid peaks were supplied.
                if (valid_peaks and clusters[i] in valid_peaks) or not valid_peaks:
                    writer.writerow(row)
