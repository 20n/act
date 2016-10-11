from __future__ import absolute_import, division, print_function

import math


def row_to_mz(row, mz_division, min_mz):
    return row * mz_division + min_mz - mz_division * 0.5


# Fill numpy array with appropriate values
def assign_row_by_mz(mz, mz_split, mz_min):
    mz_row_assignment = float(mz - mz_min) / mz_split
    if mz_row_assignment < 0:
        raise RuntimeError("M/Z assignment was less than 0. M/Z "
                           "value supplied was {}, granularity was {}, "
                           "min M/Z value was {}".format(mz, mz_split, mz_min))
    return int(round(mz_row_assignment))


def assign_column_by_time(time, time_step, time_min):
    time_column = float(time - time_min) / time_step
    if time_column < 0:
        raise RuntimeError("Time column is less than 0.  "
                           "Time value supplied was {}, granularity was {}, "
                           "min time was {}".format(time, time_step, time_min))
    return int(math.floor(time_column))


def column_number_to_time(colum_number, time_step, time_min):
    return colum_number * time_step + time_min
