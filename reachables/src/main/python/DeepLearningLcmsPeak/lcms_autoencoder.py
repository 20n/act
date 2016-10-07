from __future__ import absolute_import, division, print_function

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

import magic
from cluster import LcmsClusterer
from netcdf_parser import load_lcms_trace
from utility import assign_row_by_mz, assign_column_by_time, column_number_to_time, row_to_mz


class LcmsAutoencoder:
    TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME = "training_output_row_numbers.npy"
    VALIDATION_OUTPUT_ROW_NUMBERS_FILE_NAME = "validation_output_row_numbers.npy"

    TRAINING_OUTPUT_FILE_NAME = "predicted_training_encodings.npy"
    INPUT_TRAINING_FILE_NAME = "input_training_data.npy"
    INPUT_VALIDATION_FILE_NAME = "input_validation_data.npy"

    def __init__(self, output_directory, block_size, encoding_size, number_of_clusters, mz_split, mz_min,
                 mz_max, verbose=True, debug=False):
        """
        Initializes an LcmsAutoencoder.

        :param output_directory:    Where to put the any create file or directory.
        :param encoding_size:       How large the encoding is.
        :param number_of_clusters:  Number of kMeans clusters to put the encoding into.
        :param block_size:          The size of a LCMS window in trace time units. 15 time units ~ 3 seconds.
        :param mz_split:            The step size of the M/Z buckets.
                                    Examples:

                                    mz_step = 0.1 results in 49, 49.1, 49.2, 49.3
                                    mz_step = 0.2 results in 49, 49.2, 49.4, 49.6

        :param mz_min:              The minimum M/Z value that will be encountered in this trace.
        :param mz_max:              The maximum M/Z value that will be encountered in this trace.
        :param verbose:             Whether or not to print the print statements
        :param debug:               Whether or not to print off debug messages
        """
        self.debug = debug
        self.verbose = verbose

        self.encoding_size = encoding_size

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

        saved_array_name = lcms_plate_name + "_mz_split_" + str(self.mz_split) + ".npy"
        saved_mz_name = lcms_plate_name + "_mz_split_" + str(self.mz_split) + "_mz_" + ".npy"

        processed_file_name = os.path.join(self.output_directory, saved_array_name)
        saved_mz_file_name = os.path.join(self.output_directory, saved_mz_name)

        # Check for cached version.
        if os.path.exists(processed_file_name) and os.path.exists(saved_mz_file_name):
            if self.verbose:
                print("Using cached version of the LCMS trace at {}.".format(scan_file_name))
            return LcmsScan(np.load(processed_file_name), np.load(saved_mz_file_name))
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
            loaded_triples = load_lcms_trace(current_trace_file)

            # Add 1 to make inclusive bounds.
            row_count = assign_row_by_mz(self.mz_max, self.mz_split, self.mz_min) + 1
            column_count = assign_column_by_time(magic.time_max, magic.time_step, magic.time_min)

            # We initialize the array as all 0s because merging a bunch of arrays is slow in numpy
            processing_array = np.zeros((row_count, column_count))
            # Holds the absolute m/z of a given bucket where the max value resides.
            exact_mz_array = np.zeros((row_count, column_count))
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

                # The current relative time, based on the index in the triples array
                # (Each index has one time, though they are not equally spaced/distributed).
                sample_time = assign_column_by_time(triple["time"], magic.time_step, magic.time_min)

                for mz_index in range(0, len(triple["mz"])):
                    current_mz = mass_list[mz_index]
                    row = assign_row_by_mz(current_mz, self.mz_split, self.mz_min)

                    intensity_value = float(intensity_list[mz_index])

                    """
                    Take the max of what is currently there and the new value we found so that
                    each bucket contains the highest value found within that bucket as the intensity.
                    """
                    if intensity_value > processing_array[row, sample_time]:
                        processing_array[row, sample_time] = intensity_value
                        exact_mz_array[row, sample_time] = current_mz

            # Fill in blanks with interpolated values after getting the first pass values in.
            # TODO: Evaluate if this is effective and useful.

            # Step 3
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
                    # If unassigned, interpolate.  Unassigned values are always 0 because we initialize the array to 0.
                    if processing_array[row][column] == 0:
                        # We really only want to do this while traversing up a peak, not to augment the noise floor.
                        # This single statement increases the number of iterations we can do each second by roughly 55%.
                        before_and_after_sum = processing_array[row][column - 1] + processing_array[row][column + 1]
                        processing_array[row][column] = float(before_and_after_sum) / 2.0

            # Step 4
            processing_array = np.nan_to_num(processing_array)

            np.save(processed_file_name, processing_array)
            np.save(saved_mz_file_name, exact_mz_array)

            return LcmsScan(processing_array, exact_mz_array)

    def prepare_matrix_for_encoding(self, input_matrix, r1, r2, lowest_max_value=magic.lowest_encoded_window_max_value,
                                    snr=None):
        """
        The goal of this function is to window and threshold an input matrix such that the output
        can be directly used by the autoencoder to learn.

        :param input_matrix:        Processed LCMS matrix
        :param lowest_max_value:    The lowest maximum value that a window can have and still be considered
        :param snr:                 A SNR calculation supplied from outside that should be used if a window is valid.
                                    The SNR output value here is the highest - lowest SNR in a window.
        :return:                    A vector of valid windows.
        """
        if self.verbose:
            print("Checking if prepared matrix already exists.")

        training_file_name = os.path.join(self.output_directory, LcmsAutoencoder.INPUT_TRAINING_FILE_NAME)
        row_numbers_file = os.path.join(self.output_directory, LcmsAutoencoder.TRAINING_OUTPUT_ROW_NUMBERS_FILE_NAME)

        # # Check for cached version.
        # if os.path.exists(training_file_name) and os.path.exists(row_numbers_file):
        #     if self.verbose:
        #         print("Using cached prepared matrix.")
        #     training = np.load(training_file_name)
        #     row_numbers = np.load(row_numbers_file)
        #
        #     # Don't use this one if the block size differs between cached and desired versions.
        #     if training.shape[1] == self.block_size:
        #         return training, row_numbers

        """
        Create intervals of size {block_size}
        """
        if self.verbose:
            print("Generating intervals from input m/z rows (One row is all the time values for a given m/z slice)")
        thresholded_groups = []
        row_index_and_max = []

        # Center is the middle of the block_size
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
                window_min_index, window_min = min(enumerate(window), key=operator.itemgetter(1))

                # Special case when doing differential analysis which can be negative.
                # Iff all values are positive this will never be true (Aka non-differential time).
                if abs(window_min) > window_max:
                    window_max_index = window_min_index
                    window_max = window_min

                if abs(window_max) > lowest_max_value:
                    """
                    We center the window onto the max value we just found.
                    We do this by taking the current index, adding whichever index the max_index was in
                    the previous window, and then subtracting the center value.

                    For example, let's say we are at index 100 with a window size of 30.
                    Thus, our previous window stretched from 100-130.

                    i = 100

                    We find the max value at the 20th index of the window, so

                    window_max_index = 20

                    That means are window is currently 100 + 120, or the first value of our window would be the
                    max_value.

                    We shift it back so that the max_value is centered by subtracting the center (30/2 == 15).

                    120 - 15 = 105, making our window 105-135, thus centering 120.
                    """
                    centered_time = int(i + window_max_index - center)
                    max_centered_window = np.asarray(single_row[centered_time: (centered_time + self.block_size)])

                    # By dividing by the max, we normalize the entire window to our max value that we previously found.
                    normalized_window = max_centered_window / abs(float(window_max))

                    # Handle edge cases that can corrupt our numpy array.
                    if len(normalized_window) == self.block_size:

                        single_exp_max = np.max(
                            r1.get_array()[row_number, centered_time: (centered_time + self.block_size)])
                        single_ctrl_max = np.max(
                            r2.get_array()[row_number, centered_time: (centered_time + self.block_size)])

                        local_exp_max = np.max(r1.get_array()[row_number - 3: row_number + 3,
                                               centered_time: (centered_time + self.block_size)])
                        local_ctrl_max = np.max(r2.get_array()[row_number - 3: row_number + 3,
                                                centered_time: (centered_time + self.block_size)])

                        level_of_difference = abs((local_exp_max - local_ctrl_max) / max(local_exp_max, local_ctrl_max))

                        if single_exp_max == local_exp_max or single_ctrl_max == local_ctrl_max:
                            if abs(max(normalized_window, key=abs)) <= 1 and level_of_difference > 0.2:
                                if self.debug:
                                    print("Found differential peak at m/z {} and retention time {}".format(row_to_mz(
                                        row_number, mz_division=magic.mz_split, min_mz=magic.mz_min),
                                        column_number_to_time(centered_time + self.block_size / 2, magic.time_step,
                                                              magic.time_min)))
                                thresholded_groups.append(normalized_window)

                                exp_std = r1.get_std_deviation(row_number, centered_time + center)
                                ctrl_std = r2.get_std_deviation(row_number, centered_time + center)

                                extra_info = {"row": row_number,
                                              "time": centered_time,
                                              "maxo": window_max,
                                              "exp_std_dev": exp_std,
                                              "ctrl_std_dev": ctrl_std}

                                if snr is not None:
                                    """
                                    One problem that can occur here is when the window is off-center.
                                    We see that the off-centering tends to happen a couple seconds apart.
                                    We try to fix this by taking the using the difference between the most
                                    negative and most positive SN values as our actual SN value.
                                    If we saw two widely different, oppositely signed SN values in a single window,
                                    we'd likely assume that a shift is just causing one peak to look like two.
                                    """
                                    signed_snr = \
                                        np.sign(max_centered_window) * \
                                        snr[row_number][centered_time: (centered_time + self.block_size)]

                                    max_signed = max(signed_snr)
                                    min_signed = abs(min(signed_snr))

                                    extra_info.update({"sn": max_signed - min_signed})
                                row_index_and_max.append(extra_info)
                        else:
                            if self.debug:
                                print("Skipping window as another, larger peak was found nearby.")

                    # We take one step here to not miss anything
                    i += window_max_index + 1
                else:
                    # There were no valid points (Points greater than our min threshold) in this block,
                    # so we can just skip all the points in this block.
                    i += self.block_size

        # Convert to array
        samples = np.asarray(thresholded_groups)

        # Extra information == row number, index of time, and max window value.
        extra_information = np.asarray(row_index_and_max)

        # Save for future use.
        np.save(row_numbers_file, extra_information)
        np.save(training_file_name, samples)

        return samples, extra_information

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

    def predict_clusters(self, training_output, training_input, row_numbers, output_tsv_file_name, row_matrices,
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
        self.clusterer.predict(training_output, training_input, row_numbers, output_tsv_file_name, row_matrices,
                               valid_peak_array, drop_rt)

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
                              "rtmax", "into", "maxo", "cluster", "sn", "abs_sn"], 1)

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
            return self.std_deviation[int(row), int(column)]
        else:
            return None

    def normalize_array(self, normalizer):
        self.processed_array /= normalizer
