sort -n vendor_categories.tsv | sed '/^[[:blank:]]*$/d' > sorted_vendors.tsv
sort -n all_contributors.tsv | sed '/^[[:blank:]]*$/d' > sorted_constributors.tsv
join -t $'\t' sorted_constributors.tsv sorted_vendors.tsv  > final_vendor_tagged.tsv

grep Substance_Vendors final_vendor_tagged.tsv > final_substrate_vendors.tsv
grep Substance_Vendors final_vendor_tagged.tsv | cut -f 2 > vendor_names.txt
