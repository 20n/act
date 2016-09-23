from __future__ import absolute_import, division, print_function


def row_to_mz(row, mz_division, min_mz):
    return row * mz_division + min_mz
