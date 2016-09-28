from __future__ import absolute_import, division, print_function

"""
Deep.py Default Values
"""
cluster_number = 200
mz_split = 0.01
encoding_size = 2
window_size = 10
mz_min = 49
mz_max = 951
time_min = 0
time_max = 450
time_step = 2.5

"""
Lcms_Autoencoder.py Default Values
"""
# The lowest max value a window can have before we drop that window.
lowest_encoded_window_max_value = 1e4

loss_function = "mse"
metrics = ["accuracy"]
training_split = 0.9
batch_size = 10000

"""
Cluster.py Default Values
"""
# As the joke goes, random state of 1337 for reproducibility
kmeans_random_state = 1337

"""
*SV file separator (Default tsv)
"""
separator = "\t"
