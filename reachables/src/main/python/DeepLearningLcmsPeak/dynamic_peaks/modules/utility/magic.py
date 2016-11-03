from __future__ import absolute_import, division, print_function

"""
Deep.py Default Values
"""
cluster_number = 200
encoding_size = 10
mz_min = 49
mz_max = 951
time_min = 0
time_max = 450

# Aligner settings
rounding_level = 3
max_seconds = 50
seconds_interval = 0.1
# TODO Introduce optimizations that allows this method not use a threshold
threshold = 10000.0
within_range = 0.004

"""
Lcms_Autoencoder.py Default Values
"""
# The lowest max value a window can have before we drop that window.
lowest_encoded_window_max_value = threshold
lowest_point_to_keep = threshold

loss_function = "mse"
metrics = ["accuracy"]
training_split = 0.9
batch_size = 100

"""
Cluster.py Default Values
"""
# As the joke goes, random state of 1337 for reproducibility
kmeans_random_state = 1337

"""
*SV file separator (Default tsv)
"""
separator = "\t"

"""
Aligner default values
"""
# These numbers designate how close the aligner should need to be to find a set
# of aligned peaks the same as an unaligned peak.  Values were determined by observation of alignment errors.
mz_replacement_threshold = 0.01
rt_replacement_threshold = 2
