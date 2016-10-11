import csv
import os

import numpy as np

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

        if time_tolerance <= max_time:
            time_tolerance += time_step
        if mz_tolerance <= max_mz:
            mz_tolerance += mz_step

    return aligned, unaligned


def align_old_alignment_to_new_sample(previous_alignment, sample_two, tolerance_mz, tolerance_time):
    previous_alignment = sorted(previous_alignment, key=lambda data: data[0].get_mz())
    sample_two = sorted(sample_two, key=lambda data: data.get_mz())
    aligned_peaks = []

    i = 0
    trailing_tracker = 0
    while i < len(previous_alignment):
        sample_one_peak = previous_alignment[i]

        j = trailing_tracker
        while j < len(sample_two):
            sample_two_peak = sample_two[j]

            mz_closeness = sample_one_peak[0].get_mz() - sample_two_peak.get_mz()
            if abs(mz_closeness) <= tolerance_mz:
                if abs(sample_one_peak[0].get_rt() - sample_two_peak.get_rt()) <= tolerance_time:
                    aligned_peaks.append(previous_alignment.pop(i) + [sample_two.pop(j)])
                    break
            elif mz_closeness < -tolerance_mz:
                j = len(sample_two)
            elif j != 0 and \
                    (sample_one_peak.get_mz() - sample_two[j - 1].get_mz() > tolerance_mz) and \
                    (sample_one_peak.get_mz() - sample_two[j].get_mz() <= tolerance_mz):
                trailing_tracker = j

            j += 1
        else:
            i += 1

    unaligned = [[] for _ in range(0, len(previous_alignment[0]))]
    for peak in previous_alignment:
        for index in range(len(peak)):
            unaligned[index].append(peak[index])

    return aligned_peaks, unaligned + sample_two


def two_sample_alignment(sample_one, sample_two, tolerance_mz, tolerance_time):
    sample_one = sorted(sample_one, key=lambda data: data.get_mz())
    sample_two = sorted(sample_two, key=lambda data: data.get_mz())
    aligned_peaks = []

    i = 0
    trailing_tracker = 0
    while i < len(sample_one):
        sample_one_peak = sample_one[i]

        j = trailing_tracker
        while j < len(sample_two):
            sample_two_peak = sample_two[j]

            mz_closeness = sample_one_peak.get_mz() - sample_two_peak.get_mz()
            rt_closeness = abs(sample_one_peak.get_rt() - sample_two_peak.get_rt())
            if abs(mz_closeness) <= tolerance_mz and rt_closeness <= tolerance_time:
                aligned_peaks.append([sample_one.pop(i), sample_two.pop(j)])
                break
            elif mz_closeness < -tolerance_mz:
                j = len(sample_two)
            elif j != 0 and \
                    (sample_one_peak.get_mz() - sample_two[j - 1].get_mz() > tolerance_mz) and \
                    (sample_one_peak.get_mz() - sample_two[j].get_mz() <= tolerance_mz):
                trailing_tracker = j

            j += 1
        else:
            i += 1

    return aligned_peaks, [sample_one, sample_two]


def iterative_alignment(unaligned_samples):
    initial_two = unaligned_samples[0:2]
    anything_after = unaligned_samples[2:]

    aligned, unaligned = stepwise_alignment(initial_two,
                                            min_mz=0.001, mz_step=0.001, max_mz=0.01,
                                            min_time=1, time_step=0.5, max_time=5)

    for sample in anything_after:
        temp_aligned, temp_unaligned = stepwise_alignment(sample, previous_alignment=aligned,
                                                          min_mz=0.001, mz_step=0.001, max_mz=0.01,
                                                          min_time=1, time_step=0.5, max_time=5)

        aligned = temp_aligned
        unaligned_samples.append(temp_unaligned)

    return aligned, unaligned


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

    noise_window = [500] * len(aligned_peaks[0][0].get_intensity_window())

    for peaks in unaligned_peaks[0]:
        prepared_peaks.append(normalize(peaks.get_intensity_window(), noise_window))
        infos = {}
        infos["exp_std_dev"] = 0
        infos["ctrl_std_dev"] = 0
        infos["sn"] = peaks.get_maxo() / 1000.0
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
        infos["sn"] = peaks.get_maxo() / 1000.0
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


def merge_lcms_replicates(autoencoder, lcms_directory, output_directory, samples, cond):
    # Returns a list of replicates
    scans = [autoencoder.process_lcms_trace(lcms_directory, plate) for plate in samples]

    all_peaks = []
    for sample in scans:
        single_sample_peaks = []
        for peak in sample:
            single_sample_peaks.append(Peak(peak["mz"], peak["rt"], peak["rtmin"], peak["rtmax"], peak["maxo"],
                                            [peak[str(k)] for k in range(0, 150)]))
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
