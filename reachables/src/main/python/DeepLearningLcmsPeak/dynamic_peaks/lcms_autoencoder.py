from __future__ import absolute_import, division, print_function

import csv
import math
import operator
import os

import numpy as np
import pandas as pd
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

    def call_peak(self, sm_w, f_peaks, threshold, max_seconds, seconds_interval, max_index):
        time_points = [c[2] for c in f_peaks]
        intensity_values = [c[0] for c in f_peaks]

        smallest_timepoint = int(math.floor(min(time_points)))
        largest_timepoint = int(math.ceil(max(time_points)))
        interpolation = np.interp(
            np.linspace(smallest_timepoint,
                        largest_timepoint,
                        int((largest_timepoint - smallest_timepoint) / seconds_interval)),
            time_points,
            intensity_values,
            left=threshold,
            right=threshold)

        # Ensure center value is still max
        interpolation[int(len(interpolation) / 2)] = sm_w[max_index][0]

        """
        Pad with noise-level so that we have constant width windows.  Each window has a max second size,
        but where those seconds are is undetermined.
        """
        number_of_spots_to_fill = int(max_seconds / seconds_interval) - len(interpolation)

        left_pad = ([threshold] * int(math.ceil(number_of_spots_to_fill / 2.0)))
        right_pad = ([threshold] * (int(number_of_spots_to_fill / 2)))

        final_result = left_pad + list(interpolation) + right_pad

        row = {
            "maxo": sm_w[max_index][0],
            "rt": sm_w[max_index][2],
            "rtmin": f_peaks[0][2],
            "rtmax": f_peaks[-1][2],
            "mz": sm_w[max_index][1],
        }

        for i in range(0, len(final_result)):
            if (final_result[i]) < threshold:
                row[str(i)] = threshold
            else:
                row[str(i)] = final_result[i]

        return row

    def all_peaks_at_rt(self, smoothed_window, rounding_level, centered_mz_key, threshold, max_seconds,
                        seconds_interval, called_window_values):
        """
        Found a larger value locally, so let's avoid writing two peaks right next to each other
        as this tends to just mean we are hititng the edge of a window
        """
        all_for_this_mz = [i for i in smoothed_window if round(i[1], rounding_level) == centered_mz_key]
        if len(all_for_this_mz) == 0:
            return

        maxo_for_current_mz = max(all_for_this_mz)
        if maxo_for_current_mz[0] < threshold:
            return

        peaks_nearby_in_time = [(i, point) for i, point in enumerate(smoothed_window) if
                                abs(point[2] - maxo_for_current_mz[2]) <= max_seconds / 2.0]
        max_index, max_peak_within_time_window = max(peaks_nearby_in_time, key=operator.itemgetter(1))

        if max_index == 0:
            return

        if max_peak_within_time_window[0] > maxo_for_current_mz[0]:
            if self.debug:
                print("Found larger max locally, skipping this one.  "
                      "Local max was {}, mz window max was {}".format(max_peak_within_time_window, maxo_for_current_mz))
            return

        """
        Nothing large enough to call in sample
        """
        if maxo_for_current_mz[0] < threshold:
            if self.debug:
                print("Discarding this m/z window as the called peak was below the threshold.  "
                      "Threshold was {}, while called peak was {}".format(threshold, maxo_for_current_mz))
            return

        """
        Figure out how wide we want to make the peak by looking left and right and
        taking values until they are significantly below the threshold or our max window size is reached.
        """
        width = 0
        stop_looking = threshold / 2
        while (max_index - width > 0) and (max_index + width < len(smoothed_window) - 1):
            # Keep looking as long as there are things to find so we grab all the elements

            width += 1
            left = smoothed_window[max_index - width]
            right = smoothed_window[max_index + width]

            if left < stop_looking and right < stop_looking:
                break

        final_peaks = [x for x in smoothed_window[max_index - width: max_index + width + 1]]

        # If we've found an incredibly large peak, it is likely just junk.
        # We filter it so that it is just peaks within our max time range around its max value,
        # as it could still be something, I guess
        if math.ceil(smoothed_window[-1][2]) - math.floor(smoothed_window[0][2]) >= max_seconds:
            final_peaks = [peak for peak in final_peaks if
                           abs(smoothed_window[max_index][2] - peak[2]) < math.floor(max_seconds / 2.0)]

        if (max(smoothed_window)[0] - min(smoothed_window)[0]) / max(smoothed_window)[0] > 0.3:
            called_window_values.append(
                self.call_peak(smoothed_window, final_peaks, threshold, max_seconds, seconds_interval, max_index))

        self.all_peaks_at_rt(smoothed_window[:max_index - width], rounding_level, centered_mz_key, threshold,
                             max_seconds, seconds_interval, called_window_values)
        self.all_peaks_at_rt(smoothed_window[max_index + width + 1:], rounding_level, centered_mz_key, threshold,
                             max_seconds, seconds_interval, called_window_values)

    def process_lcms_trace(self, lcms_directory, scan_file_name):
        """
        The goal of this function is to take in a raw LCMS trace and convert it into a time
        and m/z aligned matrix that we can do later processing on.

        :param lcms_directory:  Directory of the LCMS file
        :param scan_file_name:  Actual LCMS scan file.
        :return:                Prepared matrix
        """
        # Plate file stuff
        lcms_directory = os.path.join(lcms_directory, '')
        lcms_plate_name = scan_file_name.split(".nc")[0]
        assert lcms_plate_name.endswith("01"), "This module only processes MS1 data which should always have a " \
                                               "file ending of '01'.  Your supplied file " \
                                               "was {}".format(scan_file_name)

        current_trace_file = os.path.join(lcms_directory, scan_file_name)
        assert os.path.exists(current_trace_file), "The trace file at {} does not exist.".format(current_trace_file)

        output_tsv = os.path.join(self.output_directory, "{}.tsv".format(lcms_plate_name))
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
                    if intensity_value > 100:
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
                left_key = mz_keys[mz_k_index]
                while (abs(centered_mz_key - left_key) <= magic.within_range) and (mz_k_index - left_index >= 0):
                    left_key = mz_keys[mz_k_index - left_index]
                    current_mz_keys.append(left_key)
                    left_index += 1

                current_mz_keys.append(centered_mz_key)

                right_index = 1
                right_key = mz_keys[mz_k_index]
                while (abs(centered_mz_key - right_key) <= magic.within_range) and (
                        mz_k_index + right_index < len(mz_keys)):
                    right_key = mz_keys[mz_k_index + right_index]
                    current_mz_keys.append(right_key)
                    right_index += 1

                """
                Get all the time values for any window placed nearby
                """
                time_key_set = {unique_time for mz in current_mz_keys for unique_time in mz_map[mz].keys()}

                """
                Stack all the local time values together
                """
                window = []
                sorted_keys = sorted(time_key_set)
                for i, time in enumerate(sorted_keys):
                    for mz in current_mz_keys:
                        if mz_map[mz].get(time) is not None:
                            window.append(mz_map[mz][time])

                """
                Remove really small values that we may have pulled in
                """
                smoothed_window = []
                for i, sub_win in enumerate(window):
                    # Keep all points in main
                    if round(sub_win[1], magic.rounding_level) == centered_mz_key:
                        smoothed_window.append(sub_win)
                    else:
                        # Add in points that could be part of this peak, aka are close
                        if len(smoothed_window) > 0 and i < len(window) - 1:
                            # Both are larger, this is just pulling and creating noise
                            if window[i - 1][0] > sub_win[0] and window[i + 1][0] > sub_win[0]:
                                continue

                        smoothed_window.append(sub_win)

                self.all_peaks_at_rt(smoothed_window, magic.rounding_level, centered_mz_key, magic.threshold,
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
