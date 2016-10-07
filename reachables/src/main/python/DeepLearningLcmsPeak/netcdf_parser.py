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
