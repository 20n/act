import os
import sys

import pandas as pd


def filter_by_valid_clusters(input_dir):
    plates = ["differential_expression"]
    output_directory = os.path.join(input_dir, "Filtered_By_Cluster/")
    if not os.path.exists(output_directory):
        os.makedirs(output_directory)

    for plate in plates:
        print("Filtering plate {}".format(plate))
        in_fi_name = plate + ".tsv"
        in_fi = os.path.join(input_dir, in_fi_name)

        df = pd.read_csv(in_fi, sep="\t")
        df = df[df.cluster.isin([0, 1, 2, 3, 4, 5, 6, 10, 11, 13, 15, 16, 17, 18])]

        df.to_csv(os.path.join(output_directory, in_fi_name), sep="\t", index=False)


if "__main__" == __name__:
    input_directory = sys.argv[1]
    filter_by_valid_clusters(input_directory)
