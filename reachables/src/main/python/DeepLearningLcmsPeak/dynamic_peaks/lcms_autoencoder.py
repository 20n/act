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
import math
import operator
import os

import matplotlib
import numpy as np
import pandas as pd

matplotlib.use('Agg')
import seaborn as sns
from keras.callbacks import EarlyStopping
from keras.layers import Input, Dense
from keras.models import Model
from keras.optimizers import RMSprop
from tqdm import tqdm

from dynamic_peaks.cluster import LcmsClusterer
from dynamic_peaks.modules.utility import magic
from netcdf import netcdf_parser


class LcmsAutoencoder:
    TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME = "training_output_row_numbers.npy"
    VALIDATION_OUTPUT_ROW_NUMBERS_FILE_NAME = "validation_output_row_numbers.npy"

    TRAINING_OUTPUT_FILE_NAME = "predicted_training_encodings.npy"
    INPUT_TRAINING_FILE_NAME = "input_training_data.npy"
    INPUT_VALIDATION_FILE_NAME = "input_validation_data.npy"

    def __init__(self, output_directory, block_size, encoding_size, number_of_clusters, mz_min,
                 mz_max, verbose=True, debug=False):
        """
        Initializes an LcmsAutoencoder.

        :param output_directory:    Where to put the any create file or directory.
        :param encoding_size:       How large the encoding is.
        :param number_of_clusters:  Number of kMeans clusters to put the encoding into.
        :param block_size:          The size of a LCMS window in trace time units. 15 time units ~ 3 seconds.
        :param mz_min:              The minimum M/Z value that will be encountered in this trace.
        :param mz_max:              The maximum M/Z value that will be encountered in this trace.
        :param verbose:             Whether or not to print the print statements
        :param debug:               Whether or not to print off debug messages
        """
        self.debug = debug
        self.verbose = verbose

        self.encoding_size = encoding_size

        self.block_size = block_size
        self.mz_min = mz_min
        self.mz_max = mz_max

        self.number_of_clusters = number_of_clusters

        self.output_directory = None

        self.clusterer = LcmsClusterer(self.number_of_clusters,
                                       self.block_size,
                                       self.mz_min, verbose=self.verbose)

        self.set_output_directory(output_directory)

        # Model is an autoencoder, and encoder is just the encoder portion of that autoencoder.
        self.model, self.encoder = self.compile_model()

    def set_output_directory(self, output_directory):
        """
        Sets the output directory in a way that also modifies the cluster output directory and creates
        the directory structure if it does not exist.  This allows us to reuse
        models and change all the outputs at the same time.

        :param output_directory:    Directory to write all our output files to.
        :return:
        """
        self.output_directory = os.path.join(output_directory, '')
        if not os.path.exists(self.output_directory):
            print("Creating {} as it did not previously exist.  "
                  "This it the output directory.".format(self.output_directory))
            os.makedirs(self.output_directory)
        self.clusterer.set_output_directory(output_directory)

    def call_peak(self, f_peaks, left_width, right_width):
        time_points = [c[2] for c in f_peaks]
        intensity_values = [c[0] for c in f_peaks]

        smallest_timepoint = int(math.floor(min(time_points)))
        largest_timepoint = int(math.ceil(max(time_points)))
        interpolation = np.interp(
            np.linspace(smallest_timepoint,
                        largest_timepoint,
                        int((largest_timepoint - smallest_timepoint) / magic.seconds_interval)),
            time_points,
            intensity_values,
            left=magic.threshold,
            right=magic.threshold)

        # TODO Add in some peak alignment stuff here so that the max values are always in the same spot.

        """
        Pad with noise-level so that we have constant width windows.  Each window has a max second size,
        but where those seconds are is undetermined.
        """
        total_spots = int(magic.max_seconds / magic.seconds_interval)
        number_of_spots_to_fill = total_spots - len(interpolation)

        # Too many points! Only take the ones close to the max,
        # but bias which side we take based on the relative amount of points on each side.
        if number_of_spots_to_fill < 0:
            # Previously, we found that we have fewer than 0 time points to fill, which means are window is too large.
            #  The code below calculates the position of the maximum intensity data point
            # by using our knowledge of how far the peak was shifted left.
            max_position = math.ceil((f_peaks[left_width][2] - smallest_timepoint) / float(magic.seconds_interval))

            count_to_left = max_position
            count_to_right = len(interpolation) - max_position

            # Once we've gotten how many points are left and right of the max,
            # we scale it such that we discard points proportional to their prior amount.
            division_factor = (count_to_left + count_to_right) / total_spots
            indexes_on_left = math.floor(count_to_left / division_factor)
            indexes_on_right = math.floor(count_to_right / division_factor)

            interpolation = [max_position - indexes_on_left, max_position + indexes_on_right + 1]

            number_of_spots_to_fill = total_spots - len(interpolation)

        left_pad = ([magic.threshold] * int(math.ceil(number_of_spots_to_fill / 2.0)))
        right_pad = ([magic.threshold] * (int(number_of_spots_to_fill / 2)))

        final_result = left_pad + list(interpolation) + right_pad

        row = {
            "maxo": f_peaks[left_width][0],
            "rt": f_peaks[left_width][2],
            "rtmin": f_peaks[0][2],
            "rtmax": f_peaks[-1][2],
            "mz": f_peaks[left_width][1]
        }

        for i in range(0, len(final_result)):
            if (final_result[i]) < magic.threshold:
                row[str(i)] = magic.threshold
            else:
                row[str(i)] = final_result[i]

        return row

    def all_peaks_at_rt(self, smoothed_window, rounding_level, centered_mz_key, threshold, max_seconds,
                        seconds_interval, called_window_values):
        """
        Found a larger value locally, so let's avoid writing two peaks right next to each other
        as this tends to just mean we are hititng the edge of a window
        """
        if len(smoothed_window) == 0:
            return

        # Centered_mz_key will always be on the same rounding level as we
        # created the keys initially by rounding off the mz values.
        values_for_this_mz = [i for i in smoothed_window if round(i[1], magic.rounding_level) == centered_mz_key]
        if len(values_for_this_mz) <= 0:
            return

        max_for_this_mz = max(values_for_this_mz)[0]

        max_index, max_value = max(enumerate(smoothed_window), key=operator.itemgetter(1))

        if max_value[0] <= threshold:
            return

        """
        Figure out how wide (Time wise) we want to make the peak by looking left and right and
        taking values until they are significantly below the threshold or our max window size is reached.
        """
        left_width = 0
        stop_looking = threshold
        while max_index - left_width > 0:
            # Keep looking as long as there are things to find so we grab all the elements

            left_width += 1
            left = smoothed_window[max_index - left_width]

            if left[0] <= stop_looking:
                break

        right_width = 0
        while max_index + right_width < len(smoothed_window) - 1:
            # Keep looking as long as there are things to find so we grab all the elements

            right_width += 1
            right = smoothed_window[max_index + right_width]

            if right[0] <= stop_looking:
                break

        final_peaks = smoothed_window[max_index - left_width: max_index + right_width + 1]

        # If we've found an incredibly large peak, it is likely just junk.
        # We filter it so that it is just peaks within our max time range around its max value,
        # as it could still be something, I guess
        if abs(final_peaks[0][2] - final_peaks[-1][2]) < max_seconds and max_value[0] == max_for_this_mz:
            called_window_values.append(self.call_peak(final_peaks, left_width, right_width))
        else:
            if self.debug:
                print("Discarding peak because it extends too far in time or it is not the local max. "
                      "Peak was located at mz = {}, rt = {}",
                      smoothed_window[max_index][1], smoothed_window[max_index][2])

        left_remainder = smoothed_window[:max_index - left_width]
        if len(left_remainder) > 0:
            self.all_peaks_at_rt(left_remainder, rounding_level, centered_mz_key, threshold,
                                 max_seconds, seconds_interval, called_window_values)

        right_remainder = smoothed_window[max_index + right_width + 1:]
        if len(right_remainder) > 0:
            self.all_peaks_at_rt(right_remainder, rounding_level, centered_mz_key, threshold,
                                 max_seconds, seconds_interval, called_window_values)

    def process_lcms_trace(self, lcms_directory, trace_file_name):
        """
        The goal of this function is to take in a raw LCMS trace and convert it into a time
        and m/z aligned matrix that we can do later processing on.

        :param lcms_directory:  Directory of the LCMS file
        :param trace_file_name:  Actual LCMS scan file.
        :return:                Prepared matrix
        """
        lcms_directory = os.path.join(lcms_directory, '')
        scan_file_name = trace_file_name.split(".nc")[0]
        assert scan_file_name.endswith("01"), "This module only processes MS1 data which should always have a " \
                                              "file ending of '01'.  Your supplied file " \
                                               "was {}".format(scan_file_name)

        current_trace_file = os.path.join(lcms_directory, trace_file_name)
        assert os.path.exists(current_trace_file), "The trace file at {} does not exist.".format(current_trace_file)

        output_tsv = os.path.join(self.output_directory, "{}.tsv".format(scan_file_name))
        # Check for cached version.
        if os.path.exists(output_tsv):
            if self.verbose:
                print("Using cached version of the LCMS trace at {}.".format(scan_file_name))

            with open(output_tsv) as f:
                reader = csv.DictReader(f, delimiter=magic.separator)
                return_peaks = []
                for line in reader:
                    for key in line.keys():
                        if line[key] != "":
                            line[key] = float(line[key])
                        else:
                            line.pop(key)
                    return_peaks.append(line)
                return return_peaks

        else:
            """
            The process:
            Step 1) Load the LCMS file using the netcdf_parser
            Step 2) Bucket every value by time and m/z value
            Step 3) Apply a smoothing to the buckets such that if a value is 0, but the flanking values
                    are non-zero, we say that the 0 value is actually the average of the flanking values.
            Step 4) Save processed file for later reuse.
            """
            # Step 1
            # This is a list of (Time, [M/Z], [Intensity]) values.
            loaded_triples = netcdf_parser.load_lcms_trace(current_trace_file)

            mz_map = {}

            # Step 2
            for triple in tqdm(loaded_triples):
                """
                We place each triple into a matrix at the assigned location and
                also keep track of how many values we placed there.
                """

                # List of M/Z values
                mass_list = triple["mz"]

                # List of Intensities aligned with M/Z value (Index 1 in M/Z == Index 1 in Intensity)
                intensity_list = triple["intensity"]

                for mz_index in range(0, len(triple["mz"])):
                    intensity_value = float(intensity_list[mz_index])
                    if intensity_value > magic.lowest_point_to_keep:
                        current_mz = mass_list[mz_index]
                        rounded_mz = round(current_mz, magic.rounding_level)

                        if mz_map.get(rounded_mz) is None:
                            mz_map[rounded_mz] = {}

                        mz_map[rounded_mz][triple["time"]] = (intensity_value, current_mz, triple["time"])

            # Step 3
            called_window_values = []

            mz_keys = sorted(mz_map.keys())

            for mz_k_index, centered_mz_key in enumerate(tqdm(mz_keys)):
                """
                Assign all the M/Z values within a given range to this one so we have a constant overlap.
                """
                current_mz_keys = []

                left_index = 1
                left_key = centered_mz_key
                while (round(abs(centered_mz_key - left_key), magic.rounding_level) < magic.within_range) and (
                                mz_k_index - left_index >= 0):
                    left_key = mz_keys[mz_k_index - left_index]
                    current_mz_keys.append(left_key)
                    left_index += 1

                current_mz_keys.append(centered_mz_key)

                right_index = 1
                right_key = centered_mz_key
                while (round(abs(centered_mz_key - right_key), magic.rounding_level) < magic.within_range) and (
                        mz_k_index + right_index < len(mz_keys)):
                    right_key = mz_keys[mz_k_index + right_index]
                    current_mz_keys.append(right_key)
                    right_index += 1

                """
                Get all the time values for any window placed nearby
                """
                time_key_set = sorted(
                    list({unique_time for mz in current_mz_keys for unique_time in mz_map[mz].keys()}))

                this_mz_time_ordered = sorted(mz_map[centered_mz_key].values(), key=operator.itemgetter(2))

                time_ordered_with_low_noise = []
                current_largest = 0
                for value in this_mz_time_ordered:
                    # We don't want lower values causing a lot of null peaks to be called.
                    # This is a way of smoothing bumpy areas that jump between being in and out,
                    # when there still exist a good number of points that are at that level.
                    if value[0] > magic.threshold:
                        current_time = value[2]

                        # Take the amount of time from the largest time seen and the amount of time from the
                        # current time.  If we skipped a few integer time points, place the threshold
                        # value at that position so that we fill in the valleys with reasonable noise values.
                        time_generator = ((magic.threshold, value[1], before_time)
                                          for before_time in
                                          range(int(current_largest) + 1, int(math.floor(current_time))))

                        time_ordered_with_low_noise.extend(time_generator)
                        time_ordered_with_low_noise.append(value)
                        current_largest = math.floor(current_time)

                if len(time_ordered_with_low_noise) <= 0:
                    continue

                # The interpolation can smooth out the peaks a bit so that we know when to snap points
                # from nearby m/z values to the line.
                # We don't want to snap small points to the line, though we don't always have great resolution
                # in this regard, so we use the interpolation to make sure that we can view points from
                # another m/z that may differ in temporal location as if we were comparing them to a line.
                interpolation = np.interp(time_key_set,
                                          [t[2] for t in time_ordered_with_low_noise],
                                          [i[0] for i in time_ordered_with_low_noise],
                                          left=magic.threshold,
                                          right=magic.threshold)

                window = []
                for i, time in enumerate(time_key_set):
                    for mz in current_mz_keys:
                        if mz_map[mz].get(time) is not None:
                            value = mz_map[mz][time]
                            # We threshold on where the interpolation says the line is so that we
                            # only snap large values to the line
                            # (The ones we didn't want to miss), but don't snap noise.
                            if value[0] >= interpolation[i]:
                                window.append(value)

                smoothed_window = sorted(window, key=operator.itemgetter(2))

                smoothed_window_with_low_noise = []
                current_largest = math.floor(smoothed_window[0][2])
                for value in smoothed_window:
                    current_time = value[2]
                    step = 2
                    smoothed_window_with_low_noise.extend((magic.threshold, value[1], before_time) for before_time in
                                                          range(int(current_largest) + step,
                                                                int(math.floor(current_time)), step))
                    smoothed_window_with_low_noise.append(value)
                    current_largest = math.floor(current_time)

                self.all_peaks_at_rt(smoothed_window_with_low_noise, magic.rounding_level, centered_mz_key,
                                     magic.threshold,
                                     magic.max_seconds, magic.seconds_interval, called_window_values)

            with open(output_tsv, "w") as f:
                header = ["mz", "rt", "rtmin", "rtmax", "maxo"] + [str(x) for x in
                                                                   range(0, int(
                                                                       magic.max_seconds / magic.seconds_interval) + 1)]
                writer = csv.DictWriter(f, header, delimiter=magic.separator)
                writer.writeheader()

                for called_window_value in tqdm(called_window_values):
                    writer.writerow(called_window_value)

            return called_window_values

    def compile_model(self, loss_function=magic.loss_function):
        """
        Takes the model we've created below and compiles it so that we can fit the model.

        :param loss_function:   The function that will be used to calculate loss.
        :return:                Two models, a full autoencoder and an encoder.  They use the same layers, so by
                                training the autoencoder we can then encode any input in the same way the autoencoder
                                is by simply passing the input to the encoder.
        """
        assert self.block_size > self.encoding_size, "The window size must be strictly larger than the encoding, " \
                                                     "otherwise we are doing no encoding.  Supplied values were " \
                                                     "window size of {} and encoding size of " \
                                                     "{}".format(self.block_size, self.encoding_size)

        # Encoding size is average of previous encoding and final encoding size.
        first_layer_dim = int((self.block_size - self.encoding_size) / 2)
        second_layer_dim = int((first_layer_dim - self.encoding_size) / 2)

        input_layer = Input((self.block_size,))

        # Encode down
        encoded = Dense(output_dim=first_layer_dim, activation="linear")(input_layer)
        encoded = Dense(output_dim=second_layer_dim, activation="linear")(encoded)

        # The encoded layer
        encoded = Dense(output_dim=self.encoding_size, activation="linear")(encoded)

        # Decode back up
        decoded = Dense(second_layer_dim, activation="linear")(encoded)
        decoded = Dense(first_layer_dim, activation="linear")(decoded)
        decoded = Dense(self.block_size, activation="linear")(decoded)

        model = Model(input_layer, decoded)

        # Just the encoder, so we can extract compressed layers.
        encoder = Model(input=input_layer, output=encoded)

        rmsprop = RMSprop()
        model.compile(optimizer=rmsprop, loss=loss_function, metrics=magic.metrics)

        return model, encoder

    def train(self, samples, training_split=magic.training_split):
        """
        Trains an autoencoder based on the input samples.

        :param samples:         A vector of equally sized windows
        :param training_split:  What percent of the training data we should use as a
                                validation set to allow for early-stopping.
        :return:
        """
        # TODO Random Sampling
        training_samples = samples[0:int(len(samples) * training_split)]
        validation_samples = samples[int(len(samples) * training_split):]

        """
        A brief overview of the model's params:

        X, Y: The target data sets.  We input our training samples and
        expect the network to reconstitute them at the end.

        Validation data: Same as above, but are not used in training.
        These are used by the callbacks to stop the network from overtraining.

        Batch_size: How many samples should be evaluated at one time.  This means that if we have many samples,
        we can train the network and calculate the gradients multiple times per iteration, increasing our convergence
        speed.  For example, if I had 10 samples and set batch_size to 2,
        it would calculate the gradient (Modify the weights) 5 times, one for each batch on each iteration.

        Nb_epoch: How many epochs to train for.  We just set this to a really high number
        and expect the callback to terminate prior.

        Shuffle: If to shuffle the data when training.  Pretty self-explanatory.

        Callbacks:
            Early Stopping: This stops the model given that certain preconditions are met.  We look at the validation
                            loss value and stop the model from training once the validation loss stops significantly
                            changing.
        """
        self.model.fit(x=training_samples, y=training_samples,
                       validation_data=(validation_samples, validation_samples),
                       batch_size=magic.batch_size,
                       nb_epoch=15000,
                       shuffle=True,
                       callbacks=[EarlyStopping(monitor='val_loss', patience=5, verbose=True, mode="auto")])

    def predict(self, samples):
        """
        Uses the encoder to encode a list of samples

        :param samples: Samples to be encoded.
        :return:        Encoded samples
        """
        return self.encoder.predict(samples)

    def fit_clusters(self, encoded_matrix):
        """
        Takes an encoded matrix and clusters the encodings

        :param encoded_matrix:  Encoded input
        """
        self.clusterer.fit(encoded_matrix)

    def predict_clusters(self, training_output, training_input, row_numbers, output_tsv_file_name,
                         valid_peak_array=None, drop_rt=None):
        """
        Takes all the necessary parameters to cluster an encoding and output
        it as a TSV full of all interesting information.

        :param training_output:         Output of training, the encoded samples
        :param training_input:          Input of the training, the unencoded samples
        :param row_numbers:             The location in the original array of the window.
                                        Can be used to calculate time and m/z
        :param output_tsv_file_name:    Name of output file
        :param valid_peak_array:        List of clusters that may be supplied as valid.
                                        This effectively filters by cluster.
        """
        self.clusterer.predict(training_output, training_input, row_numbers, output_tsv_file_name, valid_peak_array,
                               drop_rt)

    def visualize(self, tsv_name, lower_axis=0, higher_axis=1):
        """
        Given a CSV file that has clusters, visualize each of those clusters based on the original normalized values.

        :param tsv_name:    The name of the TSV file to visualize the clusters of
        :param lower_axis:  The lower bound of each graph, default 0
        :param higher_axis: The upper bound of each graph, default 1
        :return:
        """
        visualization_path = os.path.join(self.output_directory, "Visualizations")
        if not os.path.exists(visualization_path):
            os.makedirs(visualization_path)
        if self.verbose:
            print("Loading large CSV into dataframe")
        df = pd.DataFrame.from_csv(os.path.join(self.output_directory, tsv_name + ".tsv"),
                                   index_col=None,
                                   sep=magic.separator)

        if self.verbose:
            print("Finished loading CSV into dataframe")

        for ci in range(0, self.number_of_clusters):
            cluster = df[df["cluster"] == ci]
            if self.verbose:
                print("Cluster {}".format(ci))
            just_time_values = \
                cluster.drop(["mz", "mzmin", "mzmax", "rt", "rtmin",
                              "rtmax", "into", "maxo", "exp_maxo", "ctrl_maxo",
                              "cluster", "sn", "abs_sn", "exp_std_dev", "ctrl_std_dev"], 1)

            if self.verbose:
                print("Creating plot")
            if len(just_time_values) == 0:
                continue

            sns.tsplot(just_time_values.as_matrix(), color="indianred", err_style="unit_traces")

            # Count first so we can order by count easily.
            save_location = os.path.join(visualization_path, "{}_Cluster_{}.png".format(ci, len(just_time_values)))
            if self.verbose:
                print("Saving plot at {}".format(save_location))

            sns.plt.ylim(lower_axis, higher_axis)
            sns.plt.title("Cluster {} : Count {}".format(ci, len(cluster)))
            sns.plt.savefig(save_location)

            # Make sure to clear after creating each figure.
            sns.plt.cla()
            sns.plt.clf()


class LcmsScan:
    def __init__(self, processed_array, max_mz_array, std_deviation=None):
        self.processed_array = processed_array
        self.max_mz_array = max_mz_array
        self.std_deviation = std_deviation

    def get_array(self):
        return self.processed_array

    def get_bucket_mz(self):
        return self.max_mz_array

    def get_std_deviation(self, row, column):
        if self.std_deviation is not None:
            return self.std_deviation[row, column]
        else:
            return None

    def normalize_array(self, normalizer):
        self.processed_array /= normalizer
