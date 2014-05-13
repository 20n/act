#!/bin/sh



# BUG:
#
# This script is fast as it does batch conversion using the openbabel -m flag
# and runs at approximately 1500 chemicals/minute, completing the 17k in 12m
#
# Problem is that it is not robust to errors. when there is a problem with 
# the inchi being rendered, obabel just dumps a msg to stderr and then continues
# + its output using the -m flag is sequentially numbered. So if it skips a file
# the sequential numbering goes haywire and we cannot use it to reconnect back
# to the IDs in the original file.
#
# If there was a way to pass the id for the output files alongside the 
# chemicals.inchi that would solve the problem, but it appears there isn't
# which is why we use the move at the end of the script.
#



if [ $# -ne 1 ] 
then
  echo "Usage $0 outputdir"
  exit -1
fi

DIR=$1

# mkdir if it does not exist
[ -d $DIR ] || mkdir $DIR

cd $DIR

grep -v -E "^-?[0-9]+\tnull" ../chemicals.tsv > chemicals-filtered.tsv
cut -f3 chemicals-filtered.tsv > chemicals.inchi
cut -f1 chemicals-filtered.tsv > chemicals.ids

# ensure that obabel is installed on the system
command -v obabel >/dev/null 2>&1 || { echo >&2 "I require obabel but it's not installed.  Aborting.\nInstall from http://openbabel.org/wiki/Install"; exit 1; }

obabel -i "inchi" chemicals.inchi -o "svg" -O "ii" -m -x

i=0
while read line
do
  i=$(($i + 1))
  id=$line
  mv "ii$i" "img$id.svg"
done < chemicals.ids

rm chemicals.ids chemicals.inchi chemicals-filtered.tsv
