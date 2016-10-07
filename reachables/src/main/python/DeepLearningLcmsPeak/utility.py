from __future__ import absolute_import, division, print_function

import math


def row_to_mz(row, mz_division, min_mz):
    return row * mz_division + min_mz


# Fill numpy array with appropriate values
def assign_row_by_mz(mz, mz_split, mz_min):
    mz_column_assignment = float(mz - mz_min) / mz_split
    if mz_column_assignment < 0:
        raise RuntimeError("M/Z assignment was less than 0. M/Z "
                           "value supplied was {}, granularity was {}, "
                           "min M/Z value was {}".format(mz, mz_split, mz_min))
    return int(math.floor(mz_column_assignment))
