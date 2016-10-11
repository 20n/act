from __future__ import absolute_import, division, print_function

import argparse
import json
import os
import pickle
import sys

from dynamic_peaks import aligner
from dynamic_peaks.lcms_autoencoder import LcmsAutoencoder
from dynamic_peaks.modules.utility import magic

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

    parser.add_argument("-e", "--encodingSize", type=int,
                        help="The size of the NN's encoding layer. "
                             "This is the compressed plot's representation and how many neurons it has to move around.",
                        default=magic.encoding_size)
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

    encoding_size = args.encodingSize
    mz_min = args.mzMin
    mz_max = args.mzMax
    number_clusters = args.clusterNumber

    # model_location = os.path.join(output_directory, "differential_expression" + ".model")

    # Copy of args dictionary, vars converts args from Namespace => dictionary
    summary_dict = {}
    summary_dict.update(vars(args))
    summary_dict["model_location"] = model_location

    # Train matrix
    if model_location and os.path.exists(model_location):
        print("Using previously created model at {}".format(model_location))
        autoencoder = pickle.load(open(model_location, "rb"))
        autoencoder.set_output_directory(output_directory)
    else:
        autoencoder = LcmsAutoencoder(output_directory, encoding_size, int(magic.max_seconds / magic.seconds_interval),
                                      number_clusters, mz_min, mz_max, debug=False)

    experimental_peaks = aligner.merge_lcms_replicates(experimental_samples, "experimental_condition")
    ctrl_peaks = aligner.merge_lcms_replicates(control_samples, "ctrl_condition")

    processed_samples, aux_info = aligner.create_differential_peak_windows(experimental_peaks, ctrl_peaks)
    summary_dict["number_of_valid_windows"] = len(processed_samples)

    if not model_location or not os.path.exists(model_location):
        autoencoder.train(processed_samples)
    encoded_samples = autoencoder.predict(processed_samples)

    if not model_location or not os.path.exists(model_location):
        autoencoder.fit_clusters(encoded_samples)

    # This currently also does the writing
    autoencoder.predict_clusters(encoded_samples, processed_samples, aux_info,
                                 "differential_expression", [experimental_peaks, ctrl_peaks], drop_rt=0)

    if not model_location or not os.path.exists(model_location):
        autoencoder.visualize("differential_expression", lower_axis=-1)

    # Write run summary information
    with open(os.path.join(output_directory, "differential_expression_run_summary.json"), "w") as f:
        json.dump(summary_dict, f, indent=4, sort_keys=True)

    if not model_location:
        model_location = os.path.join(output_directory, "differential_expression.model")

        with open(model_location, "w") as f:
            # Complex objects require more recursive steps to pickle.
            sys.setrecursionlimit(10000)
            pickle.dump(autoencoder, f)
