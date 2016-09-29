from __future__ import absolute_import, division, print_function

import argparse
import json
import os
import pickle
import sys

import numpy as np

import magic
from lcms_autoencoder import LcmsAutoencoder

"""
This is the primary control file.  Run new Deep processings from here.
"""
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--lcmsDirectory", help="The LCMS plate directory.")
    parser.add_argument("--experimental", help="List of names of experimental files.", nargs='*')
    parser.add_argument("--control", help="List of names of control files.", nargs='*')
    parser.add_argument("--outputDirectory", help="Where to save all intermediate and final files.")

    parser.add_argument("--previousModelLocation", help="Location of a previously created model.")

    parser.add_argument("-w", "--lcmsWindowSize",
                        type=int,
                        help="The size of the window to include over time.",
                        default=magic.window_size)

    parser.add_argument("-e", "--encodingSize", type=int,
                        help="The size of the NN's encoding layer. "
                             "This is the compressed plot's representation and how many neurons it has to move around.",
                        default=magic.encoding_size)
    parser.add_argument("-z", "--mzSplit", type=float,
                        help="The level of granularity when dividing LCMS plots up.",
                        default=magic.mz_split)
    parser.add_argument("-c", "--clusterNumber",
                        type=int,
                        help="Number of kMeans clusters to cluster on.",
                        default=magic.cluster_number)

    parser.add_argument("-n", "--mzMin", type=int, help="The lowest M/Z value allowed.", default=magic.mz_min)
    parser.add_argument("-x", "--mzMax", type=int, help="The highest M/Z value allowed.", default=magic.mz_max)

    args = parser.parse_args()

    lcms_directory = args.lcmsDirectory
    experimental_samples = args.experimental
    control_samples = args.control
    output_directory = args.outputDirectory

    model_location = args.previousModelLocation

    block_size = args.lcmsWindowSize
    encoding_size = args.encodingSize
    mz_division = args.mzSplit
    mz_min = args.mzMin
    mz_max = args.mzMax
    number_clusters = args.clusterNumber

    # model_location = os.path.join(output_directory, "differential_expression" + ".model")

    # Copy of args dictionary, vars converts args from Namespace => dictionary
    summary_dict = {}
    summary_dict.update(vars(args))
    summary_dict["model_location"] = model_location

    # Train matrix
    if os.path.exists(model_location):
        print("Using previously created model at {}".format(model_location))
        autoencoder = pickle.load(open(model_location, "rb"))
        autoencoder.set_output_directory(output_directory)
    else:
        autoencoder = LcmsAutoencoder(output_directory, block_size, encoding_size,
                                      number_clusters, mz_division, mz_min, mz_max)

    experimental_arrays = [autoencoder.process_lcms_trace(lcms_directory, plate) for plate in experimental_samples]
    row_matrix1 = np.min(np.dstack(experimental_arrays), axis=2)

    control_arrays = [autoencoder.process_lcms_trace(lcms_directory, plate) for plate in control_samples]
    row_matrix2 = np.min(np.dstack(control_arrays), axis=2)

    """
    For the reader: A conditional within a numpy array index causes a mask to be created and applied to the array
    it is inside.

    Example: Array = [1,2,3], Array < 2 creates a mask [1, 0, 0].  Array[Array < 2]
    applies that mask so that Array[Array < 2] = 5 would create the array [5, 2, 3]
    """
    # Prevent very small values from have a disproportionate effect
    row_matrix1[row_matrix1 <= 10] = 1
    row_matrix2[row_matrix2 <= 10] = 1
    summary_dict["experimental_total_intensity"] = np.sum(row_matrix1)
    summary_dict["control_total_intensity"] = np.sum(row_matrix2)

    overall_normalizer = np.sum(row_matrix1) / np.sum(row_matrix2)
    summary_dict["normalizer_value"] = overall_normalizer
    row_matrix1 /= overall_normalizer

    np.seterr(divide="ignore")
    """
    Max(Value) * (Difference/Sum) == Good normalization

    Snr = Max/Min for each position
    """
    row_matrix = np.multiply(np.max(np.dstack((row_matrix1, row_matrix2)), axis=2),
                             np.divide(row_matrix1 - row_matrix2, row_matrix1 + row_matrix2))

    snr = np.divide(np.max(np.dstack((row_matrix1, row_matrix2)), axis=2),
                    np.min(np.dstack((row_matrix1, row_matrix2)), axis=2))
    np.seterr(divide=None)

    processed_samples, auxilariy_information = autoencoder.prepare_matrix_for_encoding(row_matrix, snr=snr)
    summary_dict["number_of_valid_windows"] = len(processed_samples)

    if not os.path.exists(model_location):
        autoencoder.train(processed_samples)
    encoded_samples = autoencoder.predict(processed_samples)

    if not os.path.exists(model_location):
        autoencoder.fit_clusters(encoded_samples)

    # This currently also does the writing
    autoencoder.predict_clusters(encoded_samples, processed_samples, auxilariy_information,
                                 "differential_expression", drop_rt=15)

    if not os.path.exists(model_location):
        autoencoder.visualize("differential_expression", lower_axis=-1)

    # Write run summary information
    with open(os.path.join(output_directory, "differential_expression_run_summary.txt"), "w") as f:
        json.dump(summary_dict, f)

    with open(model_location, "w") as f:
        # Complex objects require more recursive steps to pickle.
        sys.setrecursionlimit(10000)
        pickle.dump(autoencoder, f)
