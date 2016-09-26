from __future__ import absolute_import, division, print_function

import argparse
import pickle

"""
This is the primary control file.  Run new Deep processings from here.
"""
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("lcmsDirectory", help="The LCMS plate directory.")
    parser.add_argument("lcmsPlateName", help="The name of the LCMS plate file.  Currently supports '.nc' format.")
    parser.add_argument("lcmsAutoEncoderModel", help="Path to a prepared LcmsEncoderModel prepared using deep.py.")
    parser.add_argument("outputDirectory", help="Where to save all intermediate and final files.")

    args = parser.parse_args()

    lcms_directory = args.lcmsDirectory
    lcms_plate_name = args.lcmsPlateName
    model = args.lcmsAutoEncoderModel
    output_directory = args.outputDirectory

    autoencoder = pickle.load(model)

    row_matrix, retention_times = autoencoder.process_lcms_trace(lcms_directory, lcms_plate_name)

    processed_samples, auxilariy_information = autoencoder.prepare_matrix_for_encoding(row_matrix)

    encoded_samples = autoencoder.predict(processed_samples)

    autoencoder.predict_clusters(encoded_samples, processed_samples,
                                 auxilariy_information, retention_times, lcms_plate_name.split(".nc")[0])

    autoencoder.visualize()
