#!/usr/bin/python

from pymongo import MongoClient
import json
import operator
import csv
import math
import sys
import os

reachables_db = sys.argv[1]
base_dir = sys.argv[2]

defaultDBName = 'wiki_reachables'
client = MongoClient('localhost', 27017)
db = client[defaultDBName]

# Make the category page directory
categoryPath = os.path.join(base_dir, "Categories")

if not os.path.exists(categoryPath):
    os.makedirs(categoryPath)

def makePage(page_name, chemicals):
    print(page_name)
    fileName = page_name
    target = open(os.path.join(categoryPath, fileName), 'w')

    counter = 0

    for chemical in chemicals:
        if (chemical["inchikey"]):
            chemName = chemical["inchikey"].encode("utf-8")
            chemLink = "[[{0}]]".format(chemName)
            target.write(chemLink)
            target.write("\n\n")
            counter += 1

    print(counter)

    target.close()


### generate all chemicals
#makePage("All_Chemicals", db[reachables_db].find())

### generate Drug molecules pages
makePage("Drug_Molecules", db[reachables_db].find({"xref.DRUGBANK": {"$exists": True}}))

### generate sigma molecules pages
makePage("Sigma_Molecules", db[reachables_db].find({"xref.SIGMA": {"$exists": True}}))

### generate wikipedia molecules pages
makePage("Wikipedia_Molecules", db[reachables_db].find({"xref.WIKIPEDIA": {"$exists": True}}))

usageTerms = {"aroma": {}, "flavor": {}, "monomer": {}, "polymer": {}, "analgesic": {}}

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

for term in usageTerms:
    print(term)
    fileName = term.capitalize()
    target = open(os.path.join(categoryPath, fileName), 'w')

    counter = 0

    sortedKeys = sorted(usageTerms[term].keys(), reverse=True)
    for key in sortedKeys:
        for chemLink in usageTerms[term][key]:
            target.write(chemLink)
            target.write("\n\n")
            counter += 1

    print(counter)

    target.close()
