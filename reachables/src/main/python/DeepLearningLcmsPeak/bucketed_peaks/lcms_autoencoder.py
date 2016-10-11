from __future__ import absolute_import, division, print_function

import os

import numpy as np
import pandas as pd
import seaborn as sns
from keras.callbacks import EarlyStopping
from keras.layers import Input, Dense
from keras.models import Model
from keras.optimizers import RMSprop

from bucketed_peaks.cluster import LcmsClusterer
from bucketed_peaks.preprocessing import LcmsPreprocessing
from bucketed_peaks.preprocessing import netcdf_parser
from bucketed_peaks.utility import magic, utility_functions


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

    def process_lcms_scan(self, lcms_directory, scan_file_name):
        """
        The goal of this function is to take in a raw LCMS trace and convert it into a time
        and m/z aligned matrix that we can do later processing on.

        :param lcms_directory:  Directory of the LCMS file
        :param scan_file_name:  Actual LCMS scan file.
        :return:                Prepared matrix
        """
        # Plate file stuff
        lcms_directory = os.path.join(lcms_directory, '')
        lcms_scan_name = utility_functions.parse_lcms_scan_file_name(scan_file_name)
        assert lcms_scan_name.endswith("01"), "This module only processes MS1 data which should always have a " \
                                              "file ending of '01'.  Your supplied file " \
                                              "was {}".format(scan_file_name)

        current_trace_file = os.path.join(lcms_directory, scan_file_name)
        assert os.path.exists(current_trace_file), "The trace file at {} does not exist.".format(current_trace_file)

        saved_array_name = lcms_scan_name + "_mz_split_" + str(self.mz_split) + ".npy"
        saved_mz_name = lcms_scan_name + "_mz_split_" + str(self.mz_split) + "_mz_" + ".npy"

        processed_file_name = os.path.join(self.output_directory, saved_array_name)
        saved_mz_file_name = os.path.join(self.output_directory, saved_mz_name)

        # Check for cached version.
        if os.path.exists(processed_file_name) and os.path.exists(saved_mz_file_name):
            if self.verbose:
                print("Using cached version of the LCMS trace at {}.".format(scan_file_name))
            return LcmsPreprocessing.LcmsScan(np.load(processed_file_name), np.load(saved_mz_file_name))
        else:
            loaded_triples = netcdf_parser.load_lcms_trace(current_trace_file)
            grid, exact_mz = LcmsPreprocessing.ScanConverter.process_lcms_trace(loaded_triples,
                                                                                magic.mz_min, magic.mz_max,
                                                                                magic.mz_split,
                                                                                magic.time_min, magic.time_max,
                                                                                magic.time_step,
                                                                                self.verbose)
            # Cache for later
            np.save(processed_file_name, grid)
            np.save(saved_mz_file_name, exact_mz)
            return LcmsPreprocessing.LcmsScan(grid, exact_mz)

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
        :param row_matrices:            Extra information that is add to the output TSV files.
        :param drop_rt:                 Any values under this retention time will not be written to the output file.
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
        df = pd.DataFrame.from_csv(os.path.join(self.output_directory, tsv_name + magic.default_output_file_ending),
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
            sns.plt.title("Cluster Number {} - Count {}".format(ci, len(cluster)))
            sns.plt.savefig(save_location)

            # Make sure to clear after creating each figure.
            sns.plt.cla()
            sns.plt.clf()
