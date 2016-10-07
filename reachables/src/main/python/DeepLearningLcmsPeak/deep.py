from __future__ import absolute_import, division, print_function

import argparse
import os
import pickle
import sys

import defaults
from lcms_autoencoder import LcmsAutoencoder

"""
This is the primary control file.  Run new Deep processings from here.
"""
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("lcmsDirectory", help="The LCMS scan directory.")
    parser.add_argument("lcmsScanFileName", help="The name of the LCMS scan file.  Currently supports '.nc' format.")
    parser.add_argument("outputDirectory", help="Where to save all intermediate and final files.")

    parser.add_argument("-w", "--lcmsWindowSize",
                        type=int,
                        help="The size of the window to include over time. Each unit is about 0.2 seconds here.",
                        default=defaults.window_size)

    parser.add_argument("-e", "--encodingSize", type=int,
                        help="The size of the NN's encoding layer. "
                             "This is the compressed plot's representation and how many neurons it has to move around.",
                        default=defaults.encoding_size)
    parser.add_argument("-z", "--mzSplit", type=float,
                        help="The level of granularity when dividing LCMS plots up.",
                        default=defaults.mz_split)
    parser.add_argument("-c", "--clusterNumber",
                        type=int,
                        help="Number of kMeans clusters to cluster on.",
                        default=defaults.cluster_number)

    parser.add_argument("-n", "--mzMin", type=int, help="The lowest M/Z value allowed.", default=defaults.mz_min)
    parser.add_argument("-x", "--mzMax", type=int, help="The highest M/Z value allowed.", default=defaults.mz_max)

    args = parser.parse_args()

    lcms_directory = args.lcmsDirectory
    lcms_scan_file_name = args.lcmsScanFileName
    output_directory = args.outputDirectory

    block_size = args.lcmsWindowSize
    encoding_size = args.encodingSize
    mz_division = args.mzSplit
    mz_min = args.mzMin
    mz_max = args.mzMax
    number_clusters = args.clusterNumber

    # Train matrix
    autoencoder = LcmsAutoencoder(output_directory, block_size, encoding_size,
                                  number_clusters, mz_division, mz_min, mz_max)

    row_matrix, retention_times = autoencoder.process_lcms_trace(lcms_directory, lcms_scan_file_name)

    processed_samples, auxilariy_information = autoencoder.prepare_matrix_for_encoding(row_matrix)

    autoencoder.train(processed_samples)
    encoded_samples = autoencoder.predict(processed_samples)

    autoencoder.fit_clusters(encoded_samples)

    # This currently also does the writing
    autoencoder.predict_clusters(encoded_samples, processed_samples, auxilariy_information, retention_times,
                                 lcms_scan_file_name.split(".nc")[0])

    autoencoder.visualize(lcms_scan_file_name.split(".nc")[0])

    with open(os.path.join(output_directory, lcms_scan_file_name + ".model"), "w") as f:
        # Complex objects require more recursive steps to pickle.
        sys.setrecursionlimit(10000)
        pickle.dump(autoencoder, f)
