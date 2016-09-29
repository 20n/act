import os

import pandas as pd

plates = ["differential_expression"]
input_directory = "/Volumes/shared-data-1/Michael/DifferentialExpressionDeepLearning/PerlsteinKnockoutYeastModel/"
output_directory = os.path.join(input_directory, "Filtered_By_Cluster/")
if not os.path.exists(output_directory):
    os.makedirs(output_directory)

for plate in plates:
    print("Filtering plate {}".format(plate))
    in_fi_name = plate + ".tsv"
    in_fi = input_directory + "/" + in_fi_name

    df = pd.read_csv(in_fi, sep="\t")
    # df = df[df["rt"] > 15]
    df = df[df.cluster.isin([0, 1, 3, 4, 11, 12, 13, 16, 18, 19])]

    df.to_csv(os.path.join(output_directory, in_fi_name), sep="\t", index=False)
