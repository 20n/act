from __future__ import absolute_import, division, print_function

import csv
import os

from sklearn.cluster import MiniBatchKMeans
from tqdm import tqdm

from bucketed_peaks.modules.utility import magic, utility_functions


class LcmsClusterer:
    """
    This class is created by piping in 2D matrix of each sample's encodings to create a cluster fit,
    followed by additional information indicated in predict.

    cluster = LcmsClusterer(n_cluster=2, block_size=20, mz_split=0.01, mz_min=49)

    # Encoded samples come from a pre-processing step.  They are equally sized window vectors.
    cluster.fit(encoded_samples)

    # Description of these found below.
    cluster.predict(encoded_samples, named_windows, output_descriptor, [row_matrices], drop_rt=0

    This allows us to go from encoding => output TSV.
    """

    def __init__(self, n_cluster, block_size, mz_split, mz_min):
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
        print("Clustering")
        self.kmeans.fit(training_output)

    def predict(self, encoded_data, named_windows, output_tsv_file_name, row_matrices, valid_peaks=None, drop_rt=None):
        """
        :param encoded_data:            The encoded version of the original matrix.  Size (# Samples x Encoding Length)
                                        This is the output of lcms_autoencoder.py's autoencoder

        :param named_windows:           The raw, normalized version of the
                                        input matrix. Size (# Samples x # time points)
                                        This is the output of LcmsPreprocessing.ScanWindower.prepare_matrix_for_encoding

        :param row_matrices             This is a list of all the matrices used to create the encoding
                                        (Combined replicates count as 1 row_matrix).

        :param output_tsv_file_name:    Name of the output file
        :param valid_peaks:             A list, if provided, of clusters containing valid peaks.
                                        If not provided, all clusters are written to file.
        :param drop_rt:                 If a sample is under this retention time, we drop it.
        """
        clusters = self.kmeans.predict(encoded_data)
        print("Writing results to file")

        with open(os.path.join(self.output_directory, output_tsv_file_name + magic.default_output_file_ending),
                  "w") as f:
            header = ["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into",
                      "maxo", "sn", "abs_sn", "cluster", "exp_std_dev", "ctrl_std_dev"] + \
                     [str(x) for x in range(0, self.block_size)]

            writer = csv.DictWriter(f, header, delimiter=magic.separator)
            writer.writeheader()

            # For each original window
            for i in tqdm(range(0, len(named_windows))):
                max_intensity_value = named_windows[i].maxo
                row_in_array = named_windows[i].row
                time_index = int(named_windows[i].time)

                row = {}

                """
                For each column in the window containing the points that were used in the encoding,
                we set them in order for this row so that we can print these to the output array.
                """
                for time_number in range(0, len(named_windows[i].window)):
                    row[str(time_number)] = named_windows[i].window[time_number]

                max_value_index, _ = max(enumerate(named_windows[i].window))

                # Largest intensity value is where we call the retention time at
                row["rt"] = utility_functions.column_number_to_time(time_index + max_value_index, magic.time_step,
                                                                    magic.time_min)
                if drop_rt and row["rt"] <= drop_rt:
                    continue

                row["rtmin"] = utility_functions.column_number_to_time(time_index, magic.time_step, magic.time_min)
                row["rtmax"] = utility_functions.column_number_to_time(time_index + len(named_windows[i]) - 1,
                                                                       magic.time_step,
                                                                       magic.time_min)

                # Sum of all points aprox of AUTC
                # into == integrated intensity of original raw peak
                # The multiplication here removes the window normalization,
                # as we've previously divided the entire window by the max value.
                row["into"] = sum(named_windows[i].window) * max_intensity_value

                # We normalize by max value so this works out.
                row["maxo"] = max_intensity_value
                row["cluster"] = str(clusters[i])

                row["sn"] = named_windows[i].sn

                row["abs_sn"] = abs(row["sn"])

                # Which m/z bucket
                # call it in the middle
                which_sample = 0 if row["sn"] > 0 else 1
                row["mz"] = row_matrices[which_sample].get_bucket_mz()[
                    int(row_in_array), int(time_index + max_value_index)]
                if row["mz"] == 0:
                    # So we don't get 0 if something messes up.
                    row["mz"] = utility_functions.row_to_mz(row_in_array, self.mz_split,
                                                            self.mz_min) + 0.5 * self.mz_split
                # Min and max within window
                row["mzmin"] = utility_functions.row_to_mz(row_in_array, self.mz_split, self.mz_min)
                row["mzmax"] = utility_functions.row_to_mz(row_in_array, self.mz_split, self.mz_min) + self.mz_split

                row["exp_std_dev"] = named_windows[i].exp_std_dev
                row["ctrl_std_dev"] = named_windows[i].ctrl_std_dev

                # Check if it is in the valid peaks or if no valid peaks were supplied.
                if (valid_peaks and clusters[i] in valid_peaks) or not valid_peaks:
                    writer.writerow(row)
