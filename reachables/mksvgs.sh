#!/bin/sh

# This is script is slow, but robust to errors
# If there were no errors in the inchis then we
# could use the muuuuch faster batch renderer
# in mksvgs-batch.sh


if [ $# -ne 1 ] 
then
  echo "Usage $0 outputdir"
  exit -1
fi

# ensure that obabel is installed on the system
command -v obabel >/dev/null 2>&1 || { echo >&2 "I require obabel but it's not installed.  Aborting.\nInstall from http://openbabel.org/wiki/Install"; exit 1; }

DIR=$1

# mkdir if it does not exist
[ -d $DIR ] || mkdir $DIR

cd $DIR

grep -v -E "^-?[0-9]+\tnull" ../chemicals.tsv > chemicals-filtered.tsv

total=`wc -l chemicals-filtered.tsv | sed 's/^ *//'`
count=0
while read id smiles inchi names
do
  count=$(($count + 1))
  echo "Processing $count/$total (ID: $id)"
  echo $inchi > chem.inchi

  obabel -i "inchi" chem.inchi -o "svg" -O "ii" -x

  mv "ii$i" "img$id.svg"
done < chemicals-filtered.tsv

rm chemicals-filtered.tsv chem.inchi
