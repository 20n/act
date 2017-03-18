"""
"                                                                        "
"  This file is part of the 20n/act project.                             "
"  20n/act enables DNA prediction for synthetic biology/bioengineering.  "
"  Copyright (C) 2017 20n Labs, Inc.                                     "
"                                                                        "
"  Please direct all queries to act@20n.com.                             "
"                                                                        "
"  This program is free software: you can redistribute it and/or modify  "
"  it under the terms of the GNU General Public License as published by  "
"  the Free Software Foundation, either version 3 of the License, or     "
"  (at your option) any later version.                                   "
"                                                                        "
"  This program is distributed in the hope that it will be useful,       "
"  but WITHOUT ANY WARRANTY; without even the implied warranty of        "
"  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         "
"  GNU General Public License for more details.                          "
"                                                                        "
"  You should have received a copy of the GNU General Public License     "
"  along with this program.  If not, see <http://www.gnu.org/licenses/>. "
"                                                                        "
"""

from __future__ import absolute_import, division, print_function

import netCDF4 as nc4


def load_lcms_trace(filename):
    # netCDF4 documentation lives at
    # http://unidata.github.io/netcdf4-python/
    print("loading %s" % filename)
    nc_file = nc4.Dataset(filename, "r", format="NETCDF4")

    timepoints = nc_file.variables['scan_acquisition_time']

    scan_points_start = nc_file.variables['scan_index']
    scan_points_count = nc_file.variables['point_count']

    all_mz_values = nc_file.variables['mass_values']
    all_intensity_values = nc_file.variables['intensity_values']

    results = []

    for i in range(timepoints.size):
        points_start = scan_points_start[i]
        point_count = scan_points_count[i]
        points_end = points_start + point_count

        mzs = all_mz_values[points_start:points_end]
        assert mzs.size == point_count, "mz count mistmatch: %d vs %d" % (point_count, mzs.size)
        intensities = all_intensity_values[points_start:points_end]
        assert intensities.size == point_count, "intensity count mistmatch: %d vs %d" % (point_count, intensities.size)

        spectrum = {
            'time': timepoints[i],
            'mz': mzs,
            'intensity': intensities
        }

        results.append(spectrum)

    return results
