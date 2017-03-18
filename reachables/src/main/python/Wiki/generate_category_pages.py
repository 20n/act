"""
"                                                                        "
"  This file is part of the 20n/act project.                             "
"  20n/act enables DNA prediction for synthetic biology/bioengineering.  "
"  Copyright (C) 2017 20n Labs, Inc.                                     "
"                                                                        "
"  Please direct all queries to act@20n.com.                             "
"                                                                        "
"  This program is free software: you can redistribute it and/or modify  "
"  it under the terms of the GNU General Public License as published by  "
"  the Free Software Foundation, either version 3 of the License, or     "
"  (at your option) any later version.                                   "
"                                                                        "
"  This program is distributed in the hope that it will be useful,       "
"  but WITHOUT ANY WARRANTY; without even the implied warranty of        "
"  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         "
"  GNU General Public License for more details.                          "
"                                                                        "
"  You should have received a copy of the GNU General Public License     "
"  along with this program.  If not, see <http://www.gnu.org/licenses/>. "
"                                                                        "
"""

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

def makePage(fileName, chemicals):
    with open(os.path.join(categoryPath, fileName), 'w') as target:
        for chemical in chemicals:
            if (chemical["inchiKey"] is not None):
                inchiKey = chemical["inchiKey"].encode("utf-8")
                chemName = chemical["pageName"].encode("utf-8")
                chemLink = "[[{0} | {1}]]".format(inchiKey, chemName)
                target.write(chemLink)
                target.write("\n\n")

    print("Finished printing page name: " + fileName)

### generate Drug molecules pages
makePage("Drug_Molecules", db[reachables_db].find({"xref.DRUGBANK": {"$exists": True}}))

### generate sigma molecules pages
makePage("Sigma_Molecules", db[reachables_db].find({"xref.SIGMA": {"$exists": True}}))

### generate wikipedia molecules pages
makePage("Wikipedia_Molecules", db[reachables_db].find({"xref.WIKIPEDIA": {"$exists": True}}))

usageTerms = {"aroma": [], "flavor": [], "monomer": [], "polymer": [], "analgesic": []}

### For each of the usage pages, we want to rank order the chemicals based on the proportion
### of links that are related to the usage term. If a chemical has a greater porpotion of it's
### usage links linked to the usage term, it should list higher in the page. In order to do this,
### we create a dictionary for every usage term that maps the frequency of the usage term appearing
### to the chemical.

### First we find all the chemicals that have usage terms associated with them.
for chemical in db[reachables_db].find({"wordCloudFilename": {"$ne": None}}):
    if (chemical["inchiKey"] is not None):
        inchiKey = chemical["inchiKey"].encode("utf-8")
        chemName = chemical["pageName"].encode("utf-8")
        chemLink = "[[{0} | {1}]]".format(inchiKey, chemName)

        dictOfUsageTerms = {}
        totalCount = 0

        if (chemical["xref"] is not None and "BING" in chemical["xref"]):
            for usage_term in chemical["xref"]["BING"]["metadata"]["usage_terms"]:
                dictOfUsageTerms[usage_term["usage_term"]] = len(usage_term["urls"])
                totalCount += len(usage_term["urls"])

        for key in dictOfUsageTerms.keys():
            for usageKey in usageTerms.keys():
                if usageKey in key:
                    freq = (dictOfUsageTerms[key] / totalCount) * 100
                    usageTerms[usageKey] += [(chemLink, freq)]

### We generate the usage terms pages that are sorted by the usage frequency
for term in usageTerms:
    fileName = term.capitalize()

    with open(os.path.join(categoryPath, fileName), 'w') as target:
        sortedChemicals = sorted(usageTerms[term], key=lambda x: x[1])
        for chemLink, freq in sortedChemicals:
                target.write(chemLink)
                target.write("\n\n")

    print("Finished printing page name: " + fileName)
