from __future__ import absolute_import, division, print_function

import argparse
import csv
import json
import os
import pickle
import sys

import numpy as np


def align_replicates(mz_lists):
    def check_tolerance(items, func, tolerance):
        return (func(max(items, key=func)) - func(min(items, key=func))) <= tolerance

    accepted_retention_time_deviation = 1
    accepted_mz_deviation = 0.001

    aligned_peaks = []
    unaligned_peaks = mz_lists
    # Check for close m/z
    while accepted_mz_deviation < 0.01:
        current_peaks = unaligned_peaks
        unaligned_peaks = [[] for _ in mz_lists]

        index_pointers = [0] * len(current_peaks)
        while all([index_pointers[i] < len(p) for i, p in enumerate(current_peaks)]):
            current_levels = [current_peaks[which][index] for (which, index) in enumerate(index_pointers)]
            if check_tolerance(current_levels, lambda data: data["mz"], accepted_mz_deviation) and check_tolerance(
                    current_levels, lambda data: data["rt"], accepted_retention_time_deviation):
                aligned_peaks.append(current_levels)
                # Move all pointers as we found peaks
                index_pointers = [i + 1 for i in index_pointers]
            else:
                min_index, min_peak = min(enumerate(current_levels), key=lambda data: data[1]["mz"])
                unaligned_peaks[min_index].append(min_peak)
                index_pointers[min_index] += 1

        accepted_mz_deviation += 0.001

    while accepted_retention_time_deviation < 10:
        current_peaks = unaligned_peaks
        unaligned_peaks = [[] for _ in mz_lists]

        index_pointers = [0] * len(current_peaks)
        while all([index_pointers[i] < len(p) for i, p in enumerate(current_peaks)]):
            current_levels = [current_peaks[which][index] for (which, index) in enumerate(index_pointers)]
            if check_tolerance(current_levels, lambda data: data["mz"], accepted_mz_deviation) and check_tolerance(
                    current_levels, lambda data: data["rt"], accepted_retention_time_deviation):
                aligned_peaks.append(current_levels)
                # Move all pointers as we found peaks
                index_pointers = [i + 1 for i in index_pointers]
            else:
                min_index, min_peak = min(enumerate(current_levels), key=lambda data: data[1]["mz"])
                unaligned_peaks[min_index].append(min_peak)
                index_pointers[min_index] += 1

        accepted_retention_time_deviation += 1

    return aligned_peaks, unaligned_peaks


def create_differential_peak_windows(exp, ctrl):
    pair_list = [exp, ctrl]

    def check_tolerance(items, func, tolerance):
        return (func(max(items, key=func)) - func(min(items, key=func))) <= tolerance

    accepted_retention_time_deviation = 1
    accepted_mz_deviation = 0.001

    aligned_peaks = []
    unaligned_peaks = pair_list
    # Check for close m/z
    while accepted_mz_deviation < 0.01:
        current_peaks = unaligned_peaks
        unaligned_peaks = [[] for _ in pair_list]

        try:
            index_pointers = [0] * len(current_peaks)
            while (True):
                current_levels = [current_peaks[which][index] for (which, index) in enumerate(index_pointers)]
                if check_tolerance(current_levels, lambda peak: peak.get_mz(),
                                   accepted_mz_deviation) and check_tolerance(current_levels,
                                                                              lambda peak: peak.get_rt(),
                                                                              accepted_retention_time_deviation):
                    aligned_peaks.append(current_levels)
                    # Move all pointers as we found peaks
                    index_pointers = [i + 1 for i in index_pointers]
                else:
                    min_index, min_peak = min(enumerate(current_levels), key=lambda peak: peak[1].get_mz())
                    unaligned_peaks[min_index].append(min_peak)
                    index_pointers[min_index] += 1
        except IndexError:
            pass

        accepted_mz_deviation += 0.001

    while accepted_retention_time_deviation < 10:
        current_peaks = unaligned_peaks
        unaligned_peaks = [[] for _ in pair_list]

        try:
            index_pointers = [0] * len(current_peaks)
            while (True):
                current_levels = [current_peaks[which][index] for (which, index) in enumerate(index_pointers)]
                if check_tolerance(current_levels, lambda peak: peak.get_mz(),
                                   accepted_mz_deviation) and check_tolerance(current_levels,
                                                                              lambda peak: peak.get_rt(),
                                                                              accepted_retention_time_deviation):
                    aligned_peaks.append(current_levels)
                    # Move all pointers as we found peaks
                    index_pointers = [i + 1 for i in index_pointers]
                else:
                    min_index, min_peak = min(enumerate(current_levels), key=lambda peak: peak[1].get_mz())
                    unaligned_peaks[min_index].append(min_peak)
                    index_pointers[min_index] += 1
        except IndexError:
            pass

        accepted_retention_time_deviation += 1

    extra_info = []
    prepared_peaks = []
    for peak_pair in aligned_peaks:
        prepared_peaks.append(normalize(peak_pair[0].get_intensity_window(), peak_pair[1].get_intensity_window()))
        infos = {}
        infos["exp_std_dev"] = 0
        infos["ctrl_std_dev"] = 0
        infos["sn"] = max(peak_pair, key=lambda peak: peak.get_maxo()).get_maxo() / \
                      min(peak_pair, key=lambda peak: peak.get_maxo()).get_maxo()

        if peak_pair[1].get_maxo() > peak_pair[0].get_maxo():
            infos["sn"] *= -1

        infos["rtmin"] = min(peak_pair, key=lambda peak: peak.get_rtmin()).get_rtmin()
        infos["rtmax"] = min(peak_pair, key=lambda peak: peak.get_rtmax()).get_rtmax()
        infos["rt"] = peak_pair[0].get_rt()
        infos["maxo"] = max(peak_pair, key=lambda peak: peak.get_maxo()).get_maxo()
        infos["exp_maxo"] = peak_pair[0].get_maxo()
        infos["ctrl_maxo"] = peak_pair[1].get_maxo()
        infos["mz"] = peak_pair[0].get_mz()

        extra_info.append(infos)

    noise_window = [500] * len(aligned_peaks[0][0].get_intensity_window())

    for peaks in unaligned_peaks[0]:
        prepared_peaks.append(normalize(peaks.get_intensity_window(), noise_window))
        infos = {}
        infos["exp_std_dev"] = 0
        infos["ctrl_std_dev"] = 0
        infos["sn"] = 0
        infos["rtmin"] = peaks.get_rtmin()
        infos["rtmax"] = peaks.get_rtmax()
        infos["rt"] = peaks.get_rt()
        infos["maxo"] = peaks.get_maxo()
        infos["exp_maxo"] = peaks.get_maxo()
        infos["ctrl_maxo"] = 0
        infos["mz"] = peaks.get_mz()
        extra_info.append(infos)

    for peaks in unaligned_peaks[1]:
        prepared_peaks.append(normalize(noise_window, peaks.get_intensity_window()))
        infos = {}
        infos["exp_std_dev"] = 0
        infos["ctrl_std_dev"] = 0
        infos["sn"] = 0
        infos["rtmin"] = peaks.get_rtmin()
        infos["rtmax"] = peaks.get_rtmax()
        infos["rt"] = peaks.get_rt()
        infos["maxo"] = peaks.get_maxo()
        infos["exp_maxo"] = 0
        infos["ctrl_maxo"] = peaks.get_maxo()
        infos["mz"] = peaks.get_mz()
        extra_info.append(infos)

    return np.vstack(prepared_peaks), extra_info


def normalize(first, second):
    peaks_subtracted = np.asarray(first) - np.asarray(second)

    if max(peaks_subtracted) > abs(min(peaks_subtracted)):
        return np.divide(peaks_subtracted, max(peaks_subtracted))
    elif max(peaks_subtracted) < abs(min(peaks_subtracted)):
        return -np.divide(peaks_subtracted, min(peaks_subtracted))
    else:
        return np.divide(peaks_subtracted, 1)


class Peak:
    def __init__(self, mz, rt, rtmin, rtmax, maxo, intensity_window):
        self.mz = float(mz)
        self.rt = float(rt)
        self.rtmin = float(rtmin)
        self.rtmax = float(rtmax)
        self.maxo = float(maxo)
        self.intensity_window = [float(x) for x in intensity_window]

    def get_mz(self):
        return self.mz

    def get_rt(self):
        return self.rt

    def get_rtmin(self):
        return self.rtmin

    def get_rtmax(self):
        return self.rtmax

    def get_intensity_window(self):
        return self.intensity_window

    def get_maxo(self):
        return self.maxo


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
    if model_location and os.path.exists(model_location):
        print("Using previously created model at {}".format(model_location))
        autoencoder = pickle.load(open(model_location, "rb"))
        autoencoder.set_output_directory(output_directory)
    else:
        autoencoder = LcmsAutoencoder(output_directory, block_size, encoding_size,
                                      number_clusters, mz_division, mz_min, mz_max, debug=False)


    def merge_lcms_replicates(samples, cond):
        # Returns a list of replicates
        scans = [autoencoder.process_lcms_trace(lcms_directory, plate) for plate in samples]
        aligned_windows, unaligned_windows = align_replicates(scans)

        if len(unaligned_windows) > 0:
            with open(os.path.join("/Volumes/shared-data/Michael/InterpolatedValuesCallMultipleRts",
                                   cond + "_unaligned_peaks" + ".tsv"), "w") as f:
                writer = csv.DictWriter(f, ["mz", "rt", "maxo", "replicate_number"], delimiter=magic.separator)

                writer.writeheader()

                for rep_num, r in enumerate(unaligned_windows):
                    for c in r:
                        sr = dict()
                        sr["mz"] = c["mz"]
                        sr["rt"] = c["rt"]
                        sr["maxo"] = c["maxo"]
                        sr["replicate_number"] = rep_num
                        writer.writerow(sr)

        peaks = []
        for window_set in aligned_windows:
            # Medians
            mz_value = sorted([w["mz"] for w in window_set])[int(len(window_set) / 2)]
            rt_value = sorted([w["rt"] for w in window_set])[int(len(window_set) / 2)]

            rt_max = max([w["rtmax"] for w in window_set])
            rt_min = min([w["rtmin"] for w in window_set])
            # Average
            av_intensity = sum([w["maxo"] for w in window_set]) / len(window_set)

            merged_window = []
            # TODO Unhard code this second number
            window_positions = list(range(0, 100))
            for position in window_positions:
                # Median
                merged_window.append(sorted([w[str(position)] for w in window_set])[int(len(window_set) / 2)])

            peaks.append(Peak(mz_value, rt_value, rt_max, rt_min, av_intensity, merged_window))
        return peaks


    experimental_peaks = merge_lcms_replicates(experimental_samples, "experimental_condition")
    ctrl_peaks = merge_lcms_replicates(control_samples, "ctrl_condition")

    processed_samples, aux_info = create_differential_peak_windows(experimental_peaks, ctrl_peaks)
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
    with open(os.path.join(output_directory, "differential_expression_run_summary.txt"), "w") as f:
        json.dump(summary_dict, f)

    if not model_location:
        model_location = os.path.join(output_directory, "differential_expression.model")

        with open(model_location, "w") as f:
            # Complex objects require more recursive steps to pickle.
            sys.setrecursionlimit(10000)
            pickle.dump(autoencoder, f)