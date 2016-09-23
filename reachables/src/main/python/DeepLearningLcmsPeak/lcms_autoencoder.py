from __future__ import absolute_import, division, print_function

import math
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

    def __init__(self, lcms_directory, lcms_plate_name, output_directory, window_size, encoding_size, verbose=True,
                 debug=False):
        self.debug = debug
        self.verbose = verbose

        # Plate file stuff
        self.lcms_directory = os.path.join(lcms_directory, '')
        self.lcms_plate = lcms_plate_name.split(".nc")[0]
        self.current_trace_file = os.path.join(self.lcms_directory, self.lcms_plate + ".nc")
        assert (os.path.exists(self.current_trace_file))

        self.output_directory = os.path.join(output_directory, '')
        if not os.path.exists(self.output_directory):
            os.makedirs(self.output_directory)

        self.window_size = window_size
        self.encoding_size = encoding_size

        self.model, self.encoder = self.compile_model()

    def process_lcms_trace(self, mz_split, mz_min, max_mz):
        saved_array_name = self.lcms_plate + "_mz_split_" + str(mz_split)

        try:
            processing_array = np.load(os.path.join(self.output_directory, saved_array_name + ".npy"))
            if self.verbose:
                print("Using cached version of the LCMS trace.")
            return processing_array
        except IOError:
            # Fill numpy array with appropriate values
            def assign_column_by_mz(mz):
                mz_column_assignment = mz / mz_split - mz_min / mz_split
                if mz_column_assignment < 0:
                    raise RuntimeError("M/Z assignment was less than 0. M/Z "
                                       "value supplied was {}, granularity was {}, "
                                       "min M/Z value was {}".format(mz, mz_split, mz_min))
                return int(math.floor(mz_column_assignment))

            loaded_triples = load_lcms_trace(self.current_trace_file)

            # Add 1 to make inclusive bounds.
            row_count = int(float(max_mz - mz_min + 1) / mz_split)
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

                    predicted_mz = row_to_mz(row, mz_split, mz_min)

                    if self.debug:
                        assert (predicted_mz <= current_mz + mz_split)
                        assert (predicted_mz >= current_mz - mz_split)

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

            retention_times = [t["time"] for t in loaded_triples]

            # Save the times so we can access later
            np.save(os.path.join(self.output_directory, LcmsAutoencoder.RETENTION_TIMES_FILE_NAME),
                    np.asarray(retention_times))

            processing_array = np.nan_to_num(processing_array)
            np.save(os.path.join(self.output_directory, saved_array_name), processing_array)
            return processing_array

    def prepare_matrix_for_encoding(self, input_matrix, block_size, step_size, lowest_max_value=1e3,
                                    training_split=0.9):
        # Already available
        if self.verbose:
            print("Checking if prepared matrix already exists.")

        training_file_name = os.path.join(self.output_directory, LcmsAutoencoder.INPUT_TRAINING_FILE_NAME + ".npy")
        validation_file_name = os.path.join(self.output_directory, LcmsAutoencoder.INPUT_VALIDATION_FILE_NAME + ".npy")
        if os.path.exists(training_file_name) and os.path.exists(validation_file_name):
            if self.verbose:
                print("Using cached prepared matrix.")
            return np.load(training_file_name), np.load(validation_file_name)

        """
        Create intervals of size {block_size}
        """
        if self.verbose:
            print("Generating intervals from input m/z rows (One row is all the time values for a given m/z slice)")
        thresholded_groups = []
        row_and_time = []
        # For each sample
        for row_number in tqdm(range(0, len(input_matrix))):
            # Windows within sample
            single_row = input_matrix[row_number]
            for i in range(0, (len(single_row) - block_size), step_size):
                window = single_row[i:(i + block_size)]

                window_max = float(max(window))
                window_min = float(min(window))

                # Check that it has at least a single value
                if window_max > lowest_max_value and window_min / window_max < 0.1:
                    normalized_window = window / float(window_max)

                    thresholded_groups.append(normalized_window)
                    row_and_time.append([row_number, i, window_max])

        samples = np.asarray(thresholded_groups)
        extra_information = np.asarray(row_and_time)

        split_training = samples[0:int(len(samples) * training_split)]
        split_training_row_numbers = extra_information[0:int(len(samples) * training_split)]
        split_validation = samples[int(len(samples) * training_split):]
        split_validation_row_numbers = extra_information[int(len(samples) * training_split):]

        # Save for future use.
        np.save(
            os.path.join(self.output_directory, LcmsAutoencoder.TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME),
            np.asarray(split_training_row_numbers))
        np.save(
            os.path.join(self.output_directory, LcmsAutoencoder.VALIDATION_OUTPUT_ROW_NUMBERS_FILE_NAME),
            np.asarray(split_validation_row_numbers))

        np.save(training_file_name, split_training)
        np.save(validation_file_name, split_validation)
        return split_training, split_validation

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

    def train(self, training_samples, validation_samples):
        self.model.fit(training_samples, training_samples,
                       validation_data=(validation_samples, validation_samples),
                       batch_size=10000,
                       nb_epoch=15000,
                       shuffle=True,
                       callbacks=[EarlyStopping(monitor='val_loss', patience=5, verbose=True, mode="auto")])

        training_output = self.encoder.predict(training_samples)

        np.save(os.path.join(self.output_directory, LcmsAutoencoder.TRAINING_OUTPUT_FILE_NAME), training_output)

    def cluster(self, number_clusters, block_size, mz_split, mz_min):
        clusterer = LcmsClusterer(number_clusters,
                                  os.path.join(self.output_directory,
                                               LcmsAutoencoder.TRAINING_OUTPUT_FILE_NAME + ".npy"),
                                  os.path.join(self.output_directory,
                                               LcmsAutoencoder.INPUT_TRAINING_FILE_NAME + ".npy"),
                                  os.path.join(self.output_directory,
                                               LcmsAutoencoder.TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME + ".npy"),
                                  os.path.join(self.output_directory,
                                               LcmsAutoencoder.RETENTION_TIMES_FILE_NAME + ".npy"),
                                  self.output_directory,
                                  verbose=self.verbose
                                  )

        output_predictions = clusterer.cluster()
        clusterer.write_to_file(output_predictions, block_size, mz_split, mz_min)

    def visualize(self, number_clusters):
        visualization_path = os.path.join(self.output_directory, "Visualizations")
        if not os.path.exists(visualization_path):
            os.makedirs(visualization_path)
        if self.verbose:
            print("Loading large CSV into dataframe")
        df = pd.DataFrame.from_csv(os.path.join(self.output_directory, "clustered_output_file.csv"), index_col=None)

        if self.verbose:
            print("Finished loading CSV into dataframe")

        for ci in range(0, number_clusters):
            cluster = df[df["cluster"] == ci]
            print("Cluster {}".format(ci))

            cluster = cluster.drop("normalizer", 1)
            cluster = cluster.drop("cluster", 1)
            cluster = cluster.drop("mass", 1)
            cluster = cluster.drop("time", 1)
            cluster = cluster.drop("max_intensity_time", 1)

            # Skip big clusters as they likely lack information
            # if len(cluster) > 10000:
            #     continue

            print("Creating plot")
            if len(cluster) == 0:
                continue

            sns.tsplot(cluster.as_matrix(), color="indianred", err_style="unit_traces")
            print("Saving plot")

            sns.plt.ylim(0, 1)
            sns.plt.title("Cluster {} : Count {}".format(ci, len(cluster)))
            sns.plt.savefig(os.path.join(visualization_path, "Cluster_{}_Count_{}.png".format(ci, len(cluster))))

            # Make sure to clear after creating each figure.
            sns.plt.cla()
            sns.plt.clf()
