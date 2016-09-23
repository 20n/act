from __future__ import absolute_import, division, print_function

import argparse

from lcms_autoencoder import LcmsAutoencoder

"""
This is the primary control file.  Run new Deep processings from here.
"""
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("lcmsDirectory", help="The LCMS plate directory.")
    parser.add_argument("lcmsPlateName", help="The name of the LCMS plate file.  Currently supports '.nc' format.")
    parser.add_argument("outputDirectory", help="Where to save all intermediate and final files.")

    parser.add_argument("-w", "--lcmsWindowSize",
                        type=int,
                        help="The size of the window to include over time. Each unit is about 0.2 seconds here.",
                        default=90)

    parser.add_argument("-s", "--stepSize",
                        type=int,
                        help="The number of time points to skip between making each window",
                        default=10)

    parser.add_argument("-e", "--encodingSize", type=int,
                        help="The size of the NN's encoding layer. "
                             "This is the compressed plot's representation and how many neurons it has to move around.",
                        default=20)
    parser.add_argument("-z", "--mzSplit", type=float,
                        help="The level of granularity when dividing LCMS plots up.",
                        default=0.01)
    parser.add_argument("-c", "--clusterNumber",
                        type=int,
                        help="Number of kMeans clusters to cluster on.",
                        default=500)

    parser.add_argument("-n", "--mzMin", type=int, help="The lowest M/Z value allowed.", default=49)
    parser.add_argument("-x", "--mzMax", type=int, help="The highest M/Z value allowed.", default=950)

    args = parser.parse_args()

    lcms_directory = args.lcmsDirectory
    lcms_plate_name = args.lcmsPlateName
    output_directory = args.outputDirectory

    block_size = args.lcmsWindowSize
    step_size = args.stepSize
    encoding_size = args.encodingSize
    mz_division = args.mzSplit
    mz_min = args.mzMin
    mz_max = args.mzMax
    number_clusters = args.clusterNumber

    # Train matrix
    autoencoder = LcmsAutoencoder(lcms_directory, lcms_plate_name, output_directory, block_size, encoding_size)
    row_matrix = autoencoder.process_lcms_trace(mz_division, mz_min, mz_max)
    training_data, validation_data = autoencoder.prepare_matrix_for_encoding(row_matrix, block_size, step_size)
    autoencoder.train(training_data, validation_data)
    autoencoder.cluster(number_clusters, block_size)
    autoencoder.visualize(number_clusters)
