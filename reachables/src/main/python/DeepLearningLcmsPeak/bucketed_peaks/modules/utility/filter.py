import os
import sys

import pandas as pd

import magic


def filter_by_valid_clusters(input_tsv, input_dir):
    output_directory = os.path.join(input_dir, "Filtered_By_Cluster/")
    if not os.path.exists(output_directory):
        os.makedirs(output_directory)

    in_fi = os.path.join(input_dir, input_tsv)
    print("Filtering plate {}".format(in_fi))

    df = pd.read_csv(in_fi, sep=magic.separator)
    df = df[df.cluster.isin([0, 1, 2, 3, 4, 5, 6, 10, 11, 13, 15, 16, 17, 18])]

    df.to_csv(os.path.join(output_directory, input_tsv), sep=magic.separator, index=False)


if "__main__" == __name__:
    input_tsv = sys.argv[1]
    input_directory = sys.argv[2]
    filter_by_valid_clusters(input_tsv, input_directory)
