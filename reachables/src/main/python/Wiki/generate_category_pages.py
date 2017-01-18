from pymongo import MongoClient
import json
import operator
import csv
import math
import sys
import os

if (len(sys.argv) < 4):
    print("Not enough arguments given.")
    sys.exit()

reachables_db = sys.argv[1]
base_dir = sys.argv[2]
db_name = sys.argv[3]

client = MongoClient('localhost', 27017)
db = client[db_name]

# Make the category page directory
categoryPath = os.path.join(base_dir, "Categories")

if not os.path.exists(categoryPath):
    os.makedirs(categoryPath)

def makePage(page_name, chemicals):
    print(page_name)
    fileName = page_name
    target = open(os.path.join(categoryPath, fileName), 'w')

    for chemical in chemicals:
        if (chemical["inchikey"]):
            chemName = chemical["inchikey"].encode("utf-8")
            chemLink = "[[{0}]]".format(chemName)
            target.write(chemLink)
            target.write("\n\n")

    target.close()

### generate Drug molecules pages
makePage("Drug_Molecules", db[reachables_db].find({"xref.DRUGBANK": {"$exists": True}}))

### generate sigma molecules pages
makePage("Sigma_Molecules", db[reachables_db].find({"xref.SIGMA": {"$exists": True}}))

### generate wikipedia molecules pages
makePage("Wikipedia_Molecules", db[reachables_db].find({"xref.WIKIPEDIA": {"$exists": True}}))

usageTerms = {"aroma": {}, "flavor": {}, "monomer": {}, "polymer": {}, "analgesic": {}}

### Generate a dictionary of proportion of all usage terms of a given chemical that corresponds to the usage terms of
### interest, as specified above.

for chemical in db[reachables_db].find({"usage-wordcloud-filename": {"$ne": None}}):
    if (chemical["inchikey"]):
        chemName = chemical["inchikey"].encode("utf-8")
        chemLink = "[[{0}]]".format(chemName)

        dictTerms = {}
        totalCount = 0

        for usage_term in chemical["xref"]["BING"]["metadata"]["usage_terms"]:
            dictTerms[usage_term["usage_term"]] = len(usage_term["urls"])
            totalCount += len(usage_term["urls"])

        for key in dictTerms.keys():
            for usageKey in usageTerms.keys():
                if usageKey in key:
                    freq = (dictTerms[key] * 1.0 / totalCount) * 100.0
                    freq = math.floor(freq)
                    if freq in usageTerms[usageKey]:
                        usageTerms[usageKey][freq] += [chemLink]
                    else:
                        usageTerms[usageKey][freq] = [chemLink]

### We generate the usage terms pages that are sorted by the usage frequency

for term in usageTerms:
    print(term)
    fileName = term.capitalize()
    target = open(os.path.join(categoryPath, fileName), 'w')

    sortedKeys = sorted(usageTerms[term].keys(), reverse=True)
    for key in sortedKeys:
        for chemLink in usageTerms[term][key]:
            target.write(chemLink)
            target.write("\n\n")

    target.close()
