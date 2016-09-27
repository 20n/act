"""
Deep.py Default Values
"""
cluster_number = 200
mz_split = 0.01
encoding_size = 5
window_size = 90
mz_min = 49
mz_max = 450

"""
Lcms_Autoencoder.py Default Values
"""
# The lowest max value a window can have before we drop that window.
lowest_encoded_window_max_value = 1e3

loss_function = "mse"
metrics = ["accuracy"]
training_split = 0.9
batch_size = 10000
