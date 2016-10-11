from __future__ import absolute_import, division, print_function

import argparse
import os

import numpy as np
from modules.lcms_autoencoder import LcmsAutoencoder
from modules.preprocessing.LcmsPreprocessing import ScanWindower
from modules.utility import magic, utility_functions

"""
This is the primary control file.  Run new Deep processings from here.
"""
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("lcmsDirectory", help="The LCMS scan directory.")
    parser.add_argument("lcmsScanName", help="The name of the LCMS scan file.  Currently supports '.nc' format.")
    parser.add_argument("outputDirectory", help="Where to save all intermediate and final files.")

    parser.add_argument("--previousModelLocation", help="Location of a previously created model.")

    parser.add_argument("-w", "--lcmsWindowSize",
                        type=int,
                        help="The size of the window to include over time. Each unit is about 0.2 seconds here.",
                        default=magic.window_size)

    parser.add_argument("-e", "--encodingSize",
                        type=int,
                        help="The size of the NN's encoding layer. "
                             "This is the compressed plot's representation and how many neurons it has to move around.",
                        default=magic.encoding_size)
    parser.add_argument("-z", "--mzSplit",
                        type=float,
                        help="The level of granularity when dividing LCMS plots up.",
                        default=magic.mz_split)
    parser.add_argument("-c", "--clusterNumber",
                        type=int,
                        help="Number of kMeans clusters to cluster on.",
                        default=magic.cluster_number)

    parser.add_argument("-d", "--outputDescriptor", help="The label output files should be labeled with",
                        default="differential_expression")

    parser.add_argument("-n", "--mzMin", type=int, help="The lowest M/Z value allowed.", default=magic.mz_min)
    parser.add_argument("-x", "--mzMax", type=int, help="The highest M/Z value allowed.", default=magic.mz_max)

    args = parser.parse_args()

    lcms_directory = args.lcmsDirectory
    lcms_scan_name = args.lcmsScanName
    output_directory = args.outputDirectory

    model_location = args.previousModelLocation
    output_descriptor = args.outputDescriptor

    block_size = args.lcmsWindowSize
    encoding_size = args.encodingSize
    mz_division = args.mzSplit
    mz_min = args.mzMin
    mz_max = args.mzMax
    number_clusters = args.clusterNumber

    summary_dict = {}
    summary_dict.update(vars(args))
    summary_dict["model_location"] = model_location

    if model_location and os.path.exists(model_location):
        autoencoder = utility_functions.load_previous_model(model_location, output_directory)
    else:
        autoencoder = LcmsAutoencoder(output_directory, block_size, encoding_size,
                                      number_clusters, mz_division, mz_min, mz_max, debug=True)

    row_matrix = autoencoder.process_lcms_scan(lcms_directory, lcms_scan_name)

    named_windows = ScanWindower.prepare_matrix_for_encoding(row_matrix.get_array(), row_matrix, row_matrix,
                                                             magic.threshold, block_size,
                                                             magic.local_area_band_halfwidth)

    training_data = np.vstack([w.window for w in named_windows])

    summary_dict["number_of_valid_windows"] = len(named_windows)

    if not model_location or not os.path.exists(model_location):
        autoencoder.train(training_data)
    encoded_samples = autoencoder.predict(training_data)

    if not model_location or not os.path.exists(model_location):
        autoencoder.fit_clusters(encoded_samples)

    # This currently also does the writing
    autoencoder.predict_clusters(encoded_samples, named_windows, output_descriptor,
                                 [row_matrix], drop_rt=0)

    if not model_location or not os.path.exists(model_location):
        autoencoder.visualize(output_descriptor, lower_axis=-1)

    if not model_location:
        summary_dict["model_location"] = utility_functions.save_model(output_directory, output_descriptor, autoencoder)

    # Write the summary information out for later analysis of what occurred.
    utility_functions.output_analysis_summary(output_directory, output_descriptor, summary_dict)
