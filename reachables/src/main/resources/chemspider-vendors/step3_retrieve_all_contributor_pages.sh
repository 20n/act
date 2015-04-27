for i in `cut -f 1 all_contributors.tsv `; do curl www.chemspider.com/DatasourceDetails.aspx?id=$i > $i.contributor.details; done
