import csv
import os

import numpy as np
from tqdm import tqdm

from modules.utility import magic


def align_replicates(mz_lists):
    aligned, unaligned = iterative_alignment(mz_lists)
    return aligned, unaligned


def stepwise_alignment(initial_samples, min_mz, mz_step, max_mz, min_time, time_step, max_time,
                       previous_alignment=None):
    assert time_step != 0 or mz_step != 0

    mz_tolerance = min_mz
    time_tolerance = min_time

    aligned = []
    if previous_alignment is None:
        unaligned = initial_samples
    else:
        unaligned = [previous_alignment, initial_samples]
        aligned_peaks, unaligned = align_old_alignment_to_new_sample(unaligned[0], unaligned[1], mz_tolerance,
                                                                     time_tolerance)
        aligned.extend(aligned_peaks)

    while time_tolerance < max_time or mz_tolerance < max_mz:
        print("Aligning peaks.  Number of input peaks is {}".format(len(unaligned[0]) + len(unaligned[1])))
        aligned_peaks, unaligned = two_sample_alignment(unaligned[0], unaligned[1], mz_tolerance, time_tolerance)

        if len(aligned_peaks) != 0:
            aligned.extend(aligned_peaks)
        print("Finished aligning peaks.  Number of aligned peaks is {}, number of unaligned peaks is {}".format(
            len(aligned), len(unaligned[0]) + len(unaligned[1])))

        # Try to achieve the closest alignment at each step.
        # Once we've aligned all the peaks as well as possible, we expand the window to
        # look for things in a less strict manner than the previous iteration.
        if time_tolerance <= max_time:
            time_tolerance += time_step
        if mz_tolerance <= max_mz:
            mz_tolerance += mz_step

    aligned, unaligned = replacement_alignment(aligned, unaligned)

    return aligned, unaligned


def replacement_alignment(aligned_peaks, unaligned_peaks):
    """
    See if a previously aligned pair of peaks might actually have a shifted maximum that was not aligned.
    If that shifted, unaligned max exists, replace the current peak with that.

    :param aligned_peaks:
    :param unaligned_peaks:
    :return: A tuple of aligned peaks and unaligned peaks, after replacements were made.
    """

    def sort_ordering(data):
        return data.get_mz(), data.get_rt()

    still_unaligned_peaks = list()

    print("Replacing peak alignments with superior ones if a larger peak can be found locally.")
    replacement_count = 0
    drop_count = 0
    for unaligned_peaks_index in tqdm(range(0, len(unaligned_peaks))):
        unaligned_peak_set = sorted(unaligned_peaks[unaligned_peaks_index], key=sort_ordering)

        for unaligned_peak in unaligned_peak_set:

            for aligned_peak_index, aligned_peak_group in enumerate(aligned_peaks):
                mz_difference = unaligned_peak.get_mz() - aligned_peak_group[unaligned_peaks_index].get_mz()
                if mz_difference < -magic.mz_replacement_threshold:
                    continue

                rt_difference = unaligned_peak.get_rt() - aligned_peak_group[unaligned_peaks_index].get_rt()

                if abs(rt_difference) <= magic.rt_replacement_threshold \
                        and abs(mz_difference) <= magic.mz_replacement_threshold:
                    if aligned_peak_group[unaligned_peaks_index].get_maxo() < unaligned_peak.get_maxo():
                        aligned_peaks[aligned_peak_index][unaligned_peaks_index] = unaligned_peak
                        replacement_count += 1
                    else:
                        # Hit close, but was not the largest local alignment so it is dropped.
                        # That means that this peak shouldn't be called as an unaligned peak,
                        # because we were able to align it.
                        # Yet, it was not a large peak in the array and is likely an aberrant peak call.
                        drop_count += 1

                    # The else case is that this is a small duplicate peak.
                    break
            else:
                # Didn't find anything, readd to unaligned list
                if len(still_unaligned_peaks) <= unaligned_peaks_index:
                    still_unaligned_peaks.append([])

                still_unaligned_peaks[unaligned_peaks_index].append(unaligned_peak)

    print("Replaced {} peaks, while dropping {} local minimum peaks.  "
          "Number of aligned peaks is {}, number of unaligned peaks is {}".format(replacement_count, drop_count,
                                                                                  len(aligned_peaks), sum(
            len(p) for p in still_unaligned_peaks)))

    return aligned_peaks, still_unaligned_peaks


def align_old_alignment_to_new_sample(previous_alignment, sample_two, tolerance_mz, tolerance_time):
    def get_mz(s):
        return s.get_mz()

    def get_rt(s):
        return s.get_rt()

    def add_to_list(o, t):
        return o + [t]

    aligned_peaks = __align_two_samples(previous_alignment, sample_two,
                                        tolerance_mz, tolerance_time,
                                        get_mz, get_rt, add_to_list)

    unaligned = [[] for _ in range(0, len(previous_alignment[0]))]
    for peak in previous_alignment:
        for index in range(len(peak)):
            unaligned[index].append(peak[index])

    return aligned_peaks, unaligned + sample_two


def two_sample_alignment(sample_one, sample_two, tolerance_mz, tolerance_time):
    def get_mz(s):
        return s.get_mz()

    def get_rt(s):
        return s.get_rt()

    def add_to_list(o, t):
        return [o, t]

    aligned_peaks = __align_two_samples(sample_one, sample_two,
                                        tolerance_mz, tolerance_time,
                                        get_mz, get_rt, add_to_list)

    return aligned_peaks, [sample_one, sample_two]


def __align_two_samples(sample_one, sample_two, tolerance_mz, tolerance_time, get_mz, get_rt, add_to_list):
    sample_one = sorted(sample_one, key=lambda data: (get_mz(data), get_rt(data)))
    sample_two = sorted(sample_two, key=lambda data: (get_mz(data), get_rt(data)))

    aligned_peaks = []
    i = 0
    trailing_tracker = 0
    while i < len(sample_one):
        sample_one_peak = sample_one[i]

        j = trailing_tracker
        while j < len(sample_two):
            sample_two_peak = sample_two[j]

            # Determine how close sample one's m/z is to sample two's
            mz_closeness = get_mz(sample_one_peak) - sample_two_peak.get_mz()
            # Determine how close sample one's rt is to sample two's
            rt_closeness = abs(get_rt(sample_one_peak) - sample_two_peak.get_rt())

            # If both the time and mz are close, we add the peaks to the aligned peak list and stop looking.
            # Otherwise, we either stop if we've passed our m/z significantly, or we indicate that this
            # is where the next iteration should start if we've just crossed the threshold.
            if abs(mz_closeness) <= tolerance_mz and rt_closeness <= tolerance_time:
                aligned_peaks.append(add_to_list(sample_one.pop(i), sample_two.pop(j)))
                break
            elif mz_closeness < -tolerance_mz:
                j = len(sample_two)
            elif j != 0 and \
                    (get_mz(sample_one_peak) - sample_two[j - 1].get_mz() > tolerance_mz) and \
                    (get_mz(sample_one_peak) - sample_two[j].get_mz() <= tolerance_mz):
                trailing_tracker = j

            j += 1
        else:
            i += 1

    return aligned_peaks


def iterative_alignment(unaligned_samples):
    initial_two = unaligned_samples[0:2]
    anything_after = unaligned_samples[2:]

    print("Aligning samples 1 and 2.")
    aligned, unaligned = stepwise_alignment(initial_two,
                                            min_mz=0.001, mz_step=0.001, max_mz=0.01,
                                            min_time=1, time_step=0.5, max_time=5)

    for i, sample in enumerate(anything_after):
        print("Aligning previous samples with sample {}".format(i + 2))
        # TODO Make it so that we track unaligned when sample number is greater than 2
        aligned, _ = stepwise_alignment(sample, previous_alignment=aligned,
                                                          min_mz=0.001, mz_step=0.001, max_mz=0.01,
                                                          min_time=0.1, time_step=0.2, max_time=5)

    if len(anything_after) == 0:
        return aligned, unaligned
    else:
        return aligned, []


def create_differential_peak_windows(exp, ctrl):
    print("Aligning the experimental to the control samples.")
    aligned_peaks, unaligned_peaks = iterative_alignment([exp, ctrl])

    extra_info = []
    prepared_peaks = []
    for peak_pair in sorted(aligned_peaks, key=lambda data: (data[0].get_mz(), data[0].get_rt())):
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

    noise_window = [magic.threshold] * len(aligned_peaks[0][0].get_intensity_window())

    # Below, we switch the order of the two calls so that we keep the sign consistent.
    # If two peaks are differentially expressed in two different samples, they should have opposite signs.
    for peaks in unaligned_peaks[0]:
        prepared_peaks.append(normalize(peaks.get_intensity_window(), noise_window))
        infos = {}
        infos["exp_std_dev"] = 0
        infos["ctrl_std_dev"] = 0
        infos["sn"] = peaks.get_maxo() / magic.threshold
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
        infos["sn"] = - peaks.get_maxo() / magic.threshold
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
        return np.divide(peaks_subtracted, abs(min(peaks_subtracted)))
    else:
        return np.divide(peaks_subtracted, 1)


def merge_lcms_replicates(autoencoder, lcms_directory, output_directory, samples, cond):
    """
    The goal of this function is to take in a single sample that may have multiple plates and merge them into one.
    If a peak doesn't exist in one of the replicates, it is discarded.
    If a peak exists in both, we remedy it so that the information from all peak calls are used.
    """
    # Returns a list of replicates
    scans = [autoencoder.process_lcms_trace(lcms_directory, plate) for plate in samples]

    all_peaks = []
    for sample in scans:
        single_sample_peaks = []
        for peak in sample:
            # TODO Remove by finding root cause.  The problem is that sometimes very small
            # peaks will be created, likely an edge case on the window creation algorithm.
            try:
                single_sample_peaks.append(Peak(peak["mz"], peak["rt"], peak["rtmin"], peak["rtmax"], peak["maxo"],
                                                [peak[str(k)] for k in
                                                 range(0, int(magic.max_seconds / magic.seconds_interval))]))
            except KeyError as e:
                print("Peak was smaller than expected, likely of size 2." + e.message)
        all_peaks.append(single_sample_peaks)

    aligned_windows, unaligned_windows = align_replicates(all_peaks)

    if len(unaligned_windows) > 0:
        with open(os.path.join(output_directory, cond + "_unaligned_peaks" + ".tsv"), "w") as f:
            writer = csv.DictWriter(f, ["mz", "rt", "maxo", "replicate_number"], delimiter=magic.separator)
            writer.writeheader()

            for rep_num, r in enumerate(unaligned_windows):
                for c in r:
                    sr = dict()
                    sr["mz"] = c.get_mz()
                    sr["rt"] = c.get_rt()
                    sr["maxo"] = c.get_maxo()
                    sr["replicate_number"] = rep_num
                    writer.writerow(sr)
    else:
        print("No unaligned peaks found.  Not writing an unaligned peak file.")

    peaks = []
    with open(os.path.join(output_directory, cond + "_aligned_peaks" + ".tsv"), "w") as f:
        fields = ["mz", "rt", "rtmax", "rtmin", "maxo"]
        writer = csv.DictWriter(f, fields, delimiter=magic.separator)
        writer.writeheader()
        for window_set in aligned_windows:
            # Medians
            mz_value = sorted([w.get_mz() for w in window_set])[int(len(window_set) / 2)]
            rt_value = sorted([w.get_rt() for w in window_set])[int(len(window_set) / 2)]

            rt_max = max([w.get_rtmax() for w in window_set])
            rt_min = min([w.get_rtmin() for w in window_set])
            # Average
            av_intensity = sum([w.get_maxo() for w in window_set]) / len(window_set)

            merged_window = []
            window_positions = list(range(0, int(magic.max_seconds / magic.seconds_interval)))
            for position in window_positions:
                # Median
                merged_window.append(
                    sorted([w.get_intensity_window()[position] for w in window_set])[int(len(window_set) / 2)])

            writer.writerow({
                "mz": mz_value,
                "rt": rt_value,
                "rtmin": rt_min,
                "rtmax": rt_max,
                "maxo": av_intensity
            })

            peaks.append(Peak(mz_value, rt_value, rt_max, rt_min, av_intensity, merged_window))
    return peaks


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
