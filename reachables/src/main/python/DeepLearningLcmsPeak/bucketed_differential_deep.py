from __future__ import absolute_import, division, print_function

import argparse
import os

import numpy as np

from bucketed_peaks.modules.lcms_autoencoder import LcmsAutoencoder
from bucketed_peaks.modules.preprocessing import LcmsPreprocessing
from bucketed_peaks.modules.utility import magic, utility_functions

"""
This is the primary control file.  Run new Deep processings from here.
"""
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--lcmsDirectory", help="The LCMS scan directory.")
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

    parser.add_argument("-d", "--outputDescriptor", help="The label output files should be labeled with",
                        default="differential_expression")

    parser.add_argument("-n", "--mzMin", type=int, help="The lowest M/Z value allowed.", default=magic.mz_min)
    parser.add_argument("-x", "--mzMax", type=int, help="The highest M/Z value allowed.", default=magic.mz_max)

    args = parser.parse_args()

    lcms_directory = args.lcmsDirectory
    experimental_samples = args.experimental
    control_samples = args.control
    output_directory = args.outputDirectory

    model_location = args.previousModelLocation
    output_descriptor = args.outputDescriptor

    block_size = args.lcmsWindowSize
    encoding_size = args.encodingSize
    mz_division = args.mzSplit
    mz_min = args.mzMin
    mz_max = args.mzMax
    number_clusters = args.clusterNumber

    # Copy of args dictionary, vars converts args from Namespace => dictionary
    summary_dict = {}
    summary_dict.update(vars(args))

    # Train matrix
    if model_location and os.path.exists(model_location):
        autoencoder = utility_functions.load_previous_model(model_location, output_directory)
    elif model_location and not os.path.exists(model_location):
        raise \
            RuntimeError("Supplied a model location path of {}, but that model does not exist.".format(model_location))
    else:
        autoencoder = LcmsAutoencoder(output_directory, block_size, encoding_size,
                                      number_clusters, mz_division, mz_min, mz_max, debug=True)


    def merge_lcms_replicates(samples):
        """
        For the reader: A conditional within a numpy array index causes a mask to be created and applied to the array
        it is inside.

        Example: Array = [1,2,3], Array < 2 creates a mask [1, 0, 0].  Array[Array < 2]
        applies that mask so that Array[Array < 2] = 5 would create the array [5, 2, 3]
        """
        scans = [autoencoder.process_lcms_scan(lcms_directory, scan) for scan in samples]

        # Normalize within replicates.
        # Each replicate is normalized to the first replicate such that
        # each array has the same sum as the first replicate.
        first_scan_sum = np.sum(scans[0].get_array())
        for i in range(1, len(scans)):
            this_array_sum = np.sum(scans[i].get_array())

            normalized_value = float(first_scan_sum) / this_array_sum
            scans[i].normalize_array(normalized_value)

        # Get the min
        stacked_replicates = np.dstack([a.get_array() for a in scans])

        standard_deviations = np.std(stacked_replicates, axis=2)
        # To do: Add elementwise statistics on the std dev.

        representation = np.max(stacked_replicates, axis=2)

        bucket_list = [scan.get_bucket_mz() for scan in scans]

        mz_index_of_maxes = np.argmax(stacked_replicates, axis=2)
        mz_buckets = np.zeros(bucket_list[0].shape)

        """
        For each scan we create a mask such taht it only selects value where this bucket is the max intensity.
        We then assign the m/z value at that max intensity point to be what the m/z value actually is.
        """
        for index, bucket in enumerate(bucket_list):
            # Where the index arg is equal to the current index we find the m/z value that will be used moving forward.
            bucket_mask = mz_index_of_maxes == index
            mz_buckets[bucket_mask] = bucket[bucket_mask]

        # Prevent very small values from have a disproportionate effect
        representation[representation <= 10] = 1

        return LcmsPreprocessing.LcmsScan(representation, mz_buckets, standard_deviations)


    row_matrix1 = merge_lcms_replicates(experimental_samples)
    row_matrix2 = merge_lcms_replicates(control_samples)

    summary_dict["experimental_total_intensity"] = np.sum(row_matrix1.get_array())
    summary_dict["control_total_intensity"] = np.sum(row_matrix2.get_array())

    overall_normalizer = np.sum(row_matrix1.get_array()) / np.sum(row_matrix2.get_array())
    summary_dict["normalizer_value"] = overall_normalizer
    row_matrix1.normalize_array(overall_normalizer)

    np.seterr(divide="ignore")
    """
    Max(Value) * (Difference/Sum) == Good normalization
    The goal is to do two things:
    1) Accentuate differences in magnitude
    2) Rank differences where very large magnitudes are different by the same ratio as smaller magnitudes higher
    The above equation effectively accomplishes these two goals.


    Snr = Max/Min for each position
    """
    row_matrix = np.multiply(np.max(np.dstack((row_matrix1.get_array(), row_matrix2.get_array())), axis=2),
                             np.divide(row_matrix1.get_array() - row_matrix2.get_array(),
                                       row_matrix1.get_array() + row_matrix2.get_array()))

    snr = np.divide(np.max(np.dstack((row_matrix1.get_array(), row_matrix2.get_array())), axis=2),
                    np.min(np.dstack((row_matrix1.get_array(), row_matrix2.get_array())), axis=2))
    np.seterr(divide=None)

    named_windows = LcmsPreprocessing.ScanWindower.prepare_matrix_for_encoding(row_matrix, row_matrix1, row_matrix2,
                                                                               magic.threshold,
                                                                               block_size,
                                                                               magic.local_area_band_halfwidth, snr=snr)
    summary_dict["number_of_valid_windows"] = len(named_windows)

    training_data = np.vstack([w.window for w in named_windows])

    if not model_location or not os.path.exists(model_location):
        autoencoder.train(training_data)
    encoded_samples = autoencoder.predict(training_data)

    if not model_location or not os.path.exists(model_location):
        autoencoder.fit_clusters(encoded_samples)

    # This currently also does the writing
    autoencoder.predict_clusters(encoded_samples, named_windows, output_descriptor,
                                 [row_matrix1, row_matrix2], drop_rt=0)

    if not model_location or not os.path.exists(model_location):
        try:
            autoencoder.visualize(output_descriptor, lower_axis=-1)
        except RuntimeError as e:
            # We expect this to occur if the Display variable is not valid.
            print("Unable to create visualizations due to runtime error of \n:{}".format(e.message))
            pass

    if not model_location:
        summary_dict["model_location"] = utility_functions.save_model(output_directory, output_descriptor, autoencoder)

    # Write the summary information out for later analysis of what occurred.
    utility_functions.output_analysis_summary(output_directory, output_descriptor, summary_dict)
