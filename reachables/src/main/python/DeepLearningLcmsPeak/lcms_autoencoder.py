from __future__ import absolute_import, division, print_function

import math
import operator
import os

import numpy as np
import pandas as pd
import seaborn as sns
from cluster import LcmsClusterer
from keras.callbacks import EarlyStopping
from keras.layers import Input, Dense
from keras.models import Model
from keras.optimizers import RMSprop
from netcdf_parser import load_lcms_trace
from tqdm import tqdm
from utility import row_to_mz


class LcmsAutoencoder:
    TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME = "training_output_row_numbers"
    RETENTION_TIMES_FILE_NAME = "retention_times"
    VALIDATION_OUTPUT_ROW_NUMBERS_FILE_NAME = "validation_output_row_numbers"

    TRAINING_OUTPUT_FILE_NAME = "predicted_training_encodings"
    INPUT_TRAINING_FILE_NAME = "input_training_data"
    INPUT_VALIDATION_FILE_NAME = "input_validation_data"

    def __init__(self, output_directory, window_size, encoding_size, number_of_clusters, block_size, mz_split, mz_min,
                 mz_max, verbose=True,
                 debug=False):
        self.debug = debug
        self.verbose = verbose

        self.window_size = window_size
        self.encoding_size = encoding_size

        self.model, self.encoder = self.compile_model()

        self.block_size = block_size
        self.mz_split = mz_split
        self.mz_min = mz_min
        self.mz_max = mz_max

        self.number_of_clusters = number_of_clusters

        self.output_directory = None

        self.clusterer = LcmsClusterer(self.number_of_clusters,
                                       self.block_size, self.mz_split,
                                       self.mz_min, verbose=self.verbose)

        self.set_output_directory(output_directory)

    def set_output_directory(self, output_directory):
        self.output_directory = os.path.join(output_directory, '')
        if not os.path.exists(self.output_directory):
            os.makedirs(self.output_directory)
        self.clusterer.set_output_directory(output_directory)

    def process_lcms_trace(self, lcms_directory, lcms_plate_filename):
        # Plate file stuff
        lcms_directory = os.path.join(lcms_directory, '')
        lcms_plate_name = lcms_plate_filename.split(".nc")[0]
        current_trace_file = os.path.join(lcms_directory, lcms_plate_filename)
        assert (os.path.exists(current_trace_file))

        saved_array_name = lcms_plate_name + "_mz_split_" + str(self.mz_split)

        processed_file_name = os.path.join(self.output_directory,
                                           saved_array_name + ".npy")
        retention_time_file_name = os.path.join(self.output_directory,
                                                LcmsAutoencoder.RETENTION_TIMES_FILE_NAME + ".npy")

        try:
            processing_array = np.load(processed_file_name)
            retention_times = np.load(retention_time_file_name)
            if self.verbose:
                print("Using cached version of the LCMS trace.")
            return processing_array, retention_times
        except IOError:
            # Fill numpy array with appropriate values
            def assign_column_by_mz(mz):
                mz_column_assignment = mz / self.mz_split - self.mz_min / self.mz_split
                if mz_column_assignment < 0:
                    raise RuntimeError("M/Z assignment was less than 0. M/Z "
                                       "value supplied was {}, granularity was {}, "
                                       "min M/Z value was {}".format(mz, self.mz_split, self.mz_min))
                return int(math.floor(mz_column_assignment))

            loaded_triples = load_lcms_trace(current_trace_file)

            # Add 1 to make inclusive bounds.
            row_count = int(float(self.mz_max - self.mz_min + 1) / self.mz_split)
            column_count = int(len(loaded_triples))

            processing_array = np.zeros((row_count, column_count))
            if self.verbose:
                print("LCMS array has shape {}".format(processing_array.shape))
            """
            General structure:

            Row = M/Z, Column = Time

            The time has no strict meaning, but we'll deal with that later.

                  1   2   3   4   5   6 ... 1910
            49
            49.1
            49.2
            49.3
            ...
            950
            """
            # Don't print an error if a divide by 0 occurs because we expect it to happen during averaging.
            np.seterr(divide="ignore")
            for index, triple in tqdm(enumerate(loaded_triples)):
                # Used to track count for later averaging

                """
                We place each triple into a matrix at the assigned location and
                also keep track of how many values we placed there.
                """
                this_triples_mass = triple["mz"]
                this_triple_intensity = triple["intensity"]
                sample_time = index

                row_column = np.zeros(len(processing_array))
                averaging_array = np.zeros(len(processing_array))

                for mz_index in range(0, len(triple["mz"])):

                    current_mz = this_triples_mass[mz_index]
                    row = assign_column_by_mz(current_mz)

                    predicted_mz = row_to_mz(row, self.mz_split, self.mz_min)

                    if self.debug:
                        assert (predicted_mz <= current_mz + self.mz_split)
                        assert (predicted_mz >= current_mz - self.mz_split)

                    intensity_value = this_triple_intensity[mz_index]

                    row_column[row] += float(intensity_value)
                    averaging_array[row] += 1

                averaging_array[averaging_array == 0] = 1
                normalized_row = np.divide(row_column, averaging_array)
                processing_array[:, sample_time] = normalized_row

            # Return divide by 0 after grace zone
            np.seterr(divide=None)

            # Fill in blanks with interpolated values after getting the first pass values in.
            for row in tqdm(range(0, len(processing_array))):
                """
                Don't try to interpolate the first and last values, but try to correct
                them initial to the value next to them if they are 0.
                """
                if processing_array[row][0] == 0:
                    processing_array[row][0] = processing_array[row][1]
                if processing_array[row][-1] == 0:
                    processing_array[row][-1] = processing_array[row][-2]

                """
                Go through all the other columns and convert 0s to the average of the two flanking values.
                This helps fix holes that would otherwise occur.
                """
                for column in range(1, len(processing_array[row]) - 1):
                    # If unassigned, interpolate
                    if processing_array[row][column] == 0:
                        processing_array[row][column] = (processing_array[row][column - 1] + processing_array[row][
                            column + 1]) / 2.0

            retention_times = np.asarray([t["time"] for t in loaded_triples])

            # Save the times so we can access later
            np.save(retention_time_file_name, retention_times)

            processing_array = np.nan_to_num(processing_array)
            np.save(processed_file_name, processing_array)
            return processing_array, retention_times

    def prepare_matrix_for_encoding(self, input_matrix, lowest_max_value=1e3):
        # Already available
        if self.verbose:
            print("Checking if prepared matrix already exists.")

        training_file_name = os.path.join(self.output_directory, LcmsAutoencoder.INPUT_TRAINING_FILE_NAME + ".npy")
        validation_file_name = os.path.join(self.output_directory, LcmsAutoencoder.INPUT_VALIDATION_FILE_NAME + ".npy")
        if os.path.exists(training_file_name) and os.path.exists(validation_file_name):
            if self.verbose:
                print("Using cached prepared matrix.")
            training = np.load(training_file_name)
            validation = np.load(validation_file_name)

            # Don't use this one if the block size differs between cached and desired versions.
            if training.shape[1] == self.block_size:
                return training, validation

        """
        Create intervals of size {block_size}
        """
        if self.verbose:
            print("Generating intervals from input m/z rows (One row is all the time values for a given m/z slice)")
        thresholded_groups = []
        row_index_and_max = []

        center = self.block_size / 2

        # For each sample
        for row_number in tqdm(range(0, len(input_matrix))):
            # Windows within sample
            single_row = input_matrix[row_number]

            """
            The windowing algorithm

            Goes through all the times, sampling windows.  If there is no value above our threshold we ignore it.

            We only take windows that, when centered the value in the middle is the max.
            If you wish to detect peaks that are more closely clustered, decrease the block size so
            that fewer values are looked at.
            """
            max_window_start = len(single_row) - self.block_size
            i = 0
            while i < max_window_start:
                window = single_row[i:(i + self.block_size)]

                # Get both index and value of max
                window_max_index, window_max = max(enumerate(window), key=operator.itemgetter(1))

                if window_max > lowest_max_value:
                    value_to_center_on_max_index = int(i + window_max_index - center)

                    max_centered_window = single_row[
                                          value_to_center_on_max_index: (
                                          value_to_center_on_max_index + self.block_size)]
                    max_centered_window = np.asarray(max_centered_window)

                    normalized_window = max_centered_window / float(window_max)

                    # Handle edge cases that can corrupt our numpy array.
                    if len(normalized_window) == self.block_size:
                        """
                        TODO - This should be fixed so that we better handle this situation,
                        as it could cause us to lose some double peaks. (The max(normalized_window) <= 1 part)
                        """
                        if max(normalized_window) <= 1:
                            thresholded_groups.append(normalized_window)
                            row_index_and_max.append([row_number, value_to_center_on_max_index, window_max])

                    # Take the window after this current max value.
                    i += window_max_index + 1
                else:
                    i += self.block_size

        samples = np.asarray(thresholded_groups)
        extra_information = np.asarray(row_index_and_max)

        # Save for future use.

        np.save(os.path.join(self.output_directory, LcmsAutoencoder.TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME),
                np.asarray(extra_information))
        np.save(training_file_name, samples)

        return samples, extra_information

    def compile_model(self, loss_function="mse"):
        assert (self.window_size > self.encoding_size)

        # Encoding size is average of previous encoding and final encoding size.
        first_layer_dim = int((self.window_size - self.encoding_size) / 2)
        second_layer_dim = int((first_layer_dim - self.encoding_size) / 2)

        input_layer = Input((self.window_size,))

        # Encode down
        encoded = Dense(output_dim=first_layer_dim, activation="linear")(input_layer)
        encoded = Dense(output_dim=second_layer_dim, activation="linear")(encoded)

        # The encoded layer
        encoded = Dense(output_dim=self.encoding_size, activation="linear")(encoded)

        # Decode back up
        decoded = Dense(second_layer_dim, activation="linear")(encoded)
        decoded = Dense(first_layer_dim, activation="linear")(decoded)
        decoded = Dense(self.window_size, activation="linear")(decoded)

        model = Model(input_layer, decoded)

        # Just the encoder, so we can extract compressed layers.
        encoder = Model(input=input_layer, output=encoded)

        rmsprop = RMSprop()
        model.compile(optimizer=rmsprop, loss=loss_function, metrics=["accuracy"])

        return model, encoder

    def train(self, samples, training_split=0.9):
        # TODO Random Sampling
        training_samples = samples[0:int(len(samples) * training_split)]
        validation_samples = samples[int(len(samples) * training_split):]

        self.model.fit(training_samples, training_samples,
                       validation_data=(validation_samples, validation_samples),
                       batch_size=10000,
                       nb_epoch=15000,
                       shuffle=True,
                       callbacks=[EarlyStopping(monitor='val_loss', patience=5, verbose=True, mode="auto")])

    def predict(self, samples):
        return self.encoder.predict(samples)

    def fit_clusters(self, encoded_matrix):
        self.clusterer.fit(encoded_matrix)

    def predict_clusters(self, training_output, training_input, row_numbers, retention_times, output_tsv_file_name,
                         valid_peak_array=None):
        self.clusterer.predict(training_output, training_input, row_numbers, retention_times, output_tsv_file_name,
                               valid_peak_array)

    def visualize(self, lcms_plate):
        visualization_path = os.path.join(self.output_directory, "Visualizations")
        if not os.path.exists(visualization_path):
            os.makedirs(visualization_path)
        if self.verbose:
            print("Loading large CSV into dataframe")
        df = pd.DataFrame.from_csv(os.path.join(self.output_directory, lcms_plate + ".tsv"), index_col=None, sep="\t")

        if self.verbose:
            print("Finished loading CSV into dataframe")

        for ci in range(0, self.number_of_clusters):
            cluster = df[df["cluster"] == ci]
            if self.verbose:
                print("Cluster {}".format(ci))
            cluster = cluster.drop(["mz", "mzmin", "mzmax", "rt", "rtmin", "rtmax", "into", "maxo", "cluster"], 1)

            if self.verbose:
                print("Creating plot")
            if len(cluster) == 0:
                continue

            sns.tsplot(cluster.as_matrix(), color="indianred", err_style="unit_traces")

            # Count first so we can order by count easily.
            save_location = os.path.join(visualization_path, "{}_Cluster_{}.png".format(ci, len(cluster)))
            if self.verbose:
                print("Saving plot at {}".format(save_location))

            sns.plt.ylim(0, 1)
            sns.plt.title("Cluster {} : Count {}".format(ci, len(cluster)))
            sns.plt.savefig(save_location)

            # Make sure to clear after creating each figure.
            sns.plt.cla()
            sns.plt.clf()
