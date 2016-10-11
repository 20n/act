import operator
from collections import namedtuple

import numpy as np
from tqdm import tqdm

from bucketed_peaks.utility import assign_row_by_mz, assign_column_by_time


class ScanConverter:
    @staticmethod
    def process_lcms_trace(loaded_triples, mz_min, mz_max, mz_step, time_min, time_max, time_step, verbose=False):
        """
        The process:
        Step 1) Load the LCMS file using the netcdf_parser
        Step 2) Bucket every value by time and m/z value
        Step 3) Apply a smoothing to the buckets such that if a value is 0, but the flanking values
                are non-zero, we say that the 0 value is actually the average of the flanking values.
        Step 4) Save processed file for later reuse.
        """
        # Add 1 to make inclusive bounds.
        row_count = assign_row_by_mz(mz_max, mz_step, mz_min) + 1
        column_count = assign_column_by_time(time_max, time_step, time_min)

        # We initialize the array as all 0s because merging a bunch of arrays is slow in numpy
        processing_array = np.zeros((row_count, column_count))
        # Holds the absolute m/z of a given bucket where the max value resides.
        exact_mz_array = np.zeros((row_count, column_count))

        if verbose:
            print("LCMS array has shape {}".format(processing_array.shape))
        """
        General structure:

        Row = M/Z, Column = Time

        The time has no strict meaning, but we'll deal with that later.

              1   2   3   4   5   6 ... 1910
        49
        49.1
        49.2
        49.3
        ...
        950
        """
        # Step 2
        for triple in tqdm(loaded_triples):
            """
            We place each triple into a matrix at the assigned location and
            also keep track of how many values we placed there.
            """

            # List of M/Z values
            mass_list = triple["mz"]

            # List of Intensities aligned with M/Z value (Index 1 in M/Z == Index 1 in Intensity)
            intensity_list = triple["intensity"]

            # The current relative time, based on the index in the triples array
            # (Each index has one time, though they are not equally spaced/distributed).
            sample_time = assign_column_by_time(triple["time"], time_step, time_min)

            for mz_index in range(0, len(triple["mz"])):
                current_mz = mass_list[mz_index]
                row = assign_row_by_mz(current_mz, mz_step, mz_min)

                intensity_value = float(intensity_list[mz_index])

                """
                Take the max of what is currently there and the new value we found so that
                each bucket contains the highest value found within that bucket as the intensity.
                """
                if intensity_value > processing_array[row, sample_time]:
                    processing_array[row, sample_time] = intensity_value
                    exact_mz_array[row, sample_time] = current_mz

        # Fill in blanks with interpolated values after getting the first pass values in.
        # TODO: Evaluate if this is effective and useful.

        # Step 3
        for row in tqdm(range(0, len(processing_array))):
            """
            Don't try to interpolate the first and last values, but try to correct
            them initial to the value next to them if they are 0.
            """
            if processing_array[row][0] == 0:
                processing_array[row][0] = processing_array[row][1]
            if processing_array[row][-1] == 0:
                processing_array[row][-1] = processing_array[row][-2]

            """
            Go through all the other columns and convert 0s to the average of the two flanking values.
            This helps fix holes that would otherwise occur.
            """
            for column in range(1, len(processing_array[row]) - 1):
                # If unassigned, interpolate.  Unassigned values are always 0 because we initialize the array to 0.
                if processing_array[row][column] == 0:
                    # We really only want to do this while traversing up a peak, not to augment the noise floor.
                    # This single statement increases the number of iterations we can do each second by roughly 55%.
                    before_and_after_sum = processing_array[row][column - 1] + processing_array[row][column + 1]
                    processing_array[row][column] = float(before_and_after_sum) / 2.0

        # Step 4
        processing_array = np.nan_to_num(processing_array)

        return processing_array, exact_mz_array


class LcmsScan:
    def __init__(self, processed_array, max_mz_array, std_deviation=None):
        self.processed_array = processed_array
        self.max_mz_array = max_mz_array
        self.std_deviation = std_deviation

    def get_array(self):
        return self.processed_array

    def get_bucket_mz(self):
        return self.max_mz_array

    def get_std_deviation(self, row, column):
        if self.std_deviation is not None:
            return self.std_deviation[int(row), int(column)]
        else:
            return None

    def normalize_array(self, normalizer):
        self.processed_array /= normalizer


class ScanWindower:
    @staticmethod
    def prepare_matrix_for_encoding(input_matrix, experimental_grid, control_grid, threshold, block_size,
                                    local_halfwidth, verbose=False):
        """
        The goal of this function is to window and threshold an input matrix such that the output
        can be directly used by the autoencoder to learn.

        :param input_matrix:        Processed LCMS matrix
        :param threshold:    The lowest maximum value that a window can have and still be considered
        :param snr:                 A SNR calculation supplied from outside that should be used if a window is valid.
                                    The SNR output value here is the highest - lowest SNR in a window.
        :return:                    A vector of valid windows.
        """

        # Handle edge cases that can corrupt our numpy array.
        def get_grid_max(grid, width=0):
            assert width >= 0, "Unsupported negative width of {} when trying to determine a sub-area max.".format(width)
            mz_start, mz_end = row_number - width, row_number + width
            rt_start, rt_end = centered_time, centered_time + block_size

            grid_slice = grid.get_array()[mz_start, mz_end, rt_start, rt_end]
            return np.max(grid_slice)

        if verbose:
            print("Checking if prepared matrix already exists.")

        """
        Create intervals of size {block_size}
        """
        if verbose:
            print("Generating intervals from input m/z rows (One row is all the time values for a given m/z slice)")
        thresholded_groups = []

        # Center is the middle of the block_size
        center = block_size / 2

        # For each sample
        for row_number in tqdm(range(0, len(input_matrix))):
            # Windows within sample
            single_row = input_matrix[row_number]

            """
            The windowing algorithm

            Goes through all the times, sampling windows.  If there is no value above our threshold we ignore it.

            We only take windows that, when centered the value in the middle is the max.
            If you wish to detect peaks that are more closely clustered, decrease the block size so
            that fewer values are looked at.
            """
            max_window_start = len(single_row) - block_size
            i = 0
            while i < max_window_start:
                window = single_row[i:(i + block_size)]

                # Get both index and value of min/max
                window_max_index, window_max = max(enumerate(window), key=operator.itemgetter(1))
                window_min_index, window_min = min(enumerate(window), key=operator.itemgetter(1))

                # Special case when doing differential analysis which can be negative.
                # Iff all values are positive this will never be true (Aka non-differential time).
                if abs(window_min) > window_max:
                    window_max_index, window_max = window_min_index, window_min

                if abs(window_max) >= threshold:
                    """
                    We center the window onto the max value we just found.
                    We do this by taking the current index, adding whichever index the max_index was in
                    the previous window, and then subtracting the center value.

                    For example, let's say we are at index 100 with a window size of 30.
                    Thus, our previous window stretched from 100-130.

                    i = 100

                    We find the max value at the 20th index of the window, so

                    window_max_index = 20

                    That means are window is currently 100 + 120, or the first value of our window would be the
                    max_value.

                    We shift it back so that the max_value is centered by subtracting the center (30/2 == 15).

                    120 - 15 = 105, making our window 105-135, thus centering 120.
                    """
                    centered_time = int(i + window_max_index - center)
                    max_centered_window = np.asarray(single_row[centered_time: (centered_time + block_size)])

                    # By dividing by the max, we normalize the entire window to our max value that we previously found.
                    normalized_window = max_centered_window / abs(float(window_max))

                    if len(normalized_window) == block_size:
                        single_exp_max, single_ctrl_max = get_grid_max(experimental_grid), get_grid_max(control_grid)
                        local_exp_max, local_ctrl_max = get_grid_max(experimental_grid,
                                                                     width=local_halfwidth), get_grid_max(control_grid,
                                                                                                          width=local_halfwidth)

                        if single_exp_max == local_exp_max or single_ctrl_max == local_ctrl_max:
                            if abs(max(normalized_window, key=abs)) <= 1:
                                exp_std, ctrl_std = experimental_grid.get_std_deviation(row_number,
                                                                                        centered_time + center), control_grid.get_std_deviation(
                                    row_number, centered_time + center)

                                Window = namedtuple("Window", ["window",
                                                               "row",
                                                               "time",
                                                               "maxo",
                                                               "exp_std_dev",
                                                               "ctrl_std_dev",
                                                               ])
                                formatted_window = Window(window=normalized_window,
                                                          row=row_number,
                                                          time=centered_time,
                                                          maxo=window_max,
                                                          exp_std_dev=exp_std,
                                                          ctrl_std_dev=ctrl_std)
                                thresholded_groups.append(formatted_window)

                    # We take one step here to not miss anything
                    i += window_max_index + 1
                else:
                    # There were no valid points (Points greater than our min threshold) in this block,
                    # so we can just skip all the points in this block.
                    i += block_size

        return thresholded_groups
