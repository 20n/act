#!/bin/sh

# This is script is slow, but robust to errors
# If there were no errors in the inchis then we
# could use the muuuuch faster batch renderer
# in mksvgs-batch.sh

if [ $# -ne 2 ] 
then
  echo "Usage $0 chemicals.tsv.file outputdir"
  exit -1
fi

CHEMS=$1
DIR=$2

# mkdir if it does not exist
[ -d $DIR ] || mkdir $DIR

cd $DIR

##################################################################################################
# Ensure we have the third-party command available before starting processing
##################################################################################################

# ensure that obabel is installed on the system
command -v obabel >/dev/null 2>&1 || { echo >&2 "I require obabel but it's not installed.  Aborting.\nInstall from http://openbabel.org/wiki/Install"; exit 1; }

# ensure that dot is installed on the system
command -v dot >/dev/null 2>&1 || { echo >&2 "I require dot (from graphviz); but it is not installed.  Aborting.\nInstall using sudo apt-get install graphviz"; exit 1; }

##################################################################################################
# First render all chemicals to img<id>.svg
##################################################################################################

grep -v -E "^-?[0-9]+\tnull" ../$CHEMS > chemicals-filtered.tsv

total=`wc -l chemicals-filtered.tsv | sed 's/^ *//'`
count=0
while read line
do
  id=$(echo "$line" | cut -f1)
  inchi=$(echo "$line" | cut -f3)
  # echo "ID: $id and InCHI: $inchi"
  count=$(($count + 1))
  echo "$count/$total (ID: $id)"
  echo $inchi > chem.inchi

  ret=`obabel -i "inchi" chem.inchi -o "svg" -O "ii" -x 2>&1`

  if [ "x$ret" = "x1 molecule converted" ]
  then
    mv "ii$i" "img$id.svg"
  else
    echo "<svg><text x='20' y='20'>$inchi</text></svg>" > "img$id.svg"
    echo "[WARN] Failed to render ID: $id" 1>&2
  fi

done < chemicals-filtered.tsv

rm chemicals-filtered.tsv chem.inchi

##################################################################################################
# Next, render all cscd*.dot files to their equivalent cscd*.svg files
##################################################################################################

ls cscd*.dot | while read dotfile
do
  svgfile=`echo $dotfile | sed 's/.dot$/.svg/'`
  dot -Tsvg $dotfile > $svgfile
  echo "Rendered cascade $dotfile to $svgfile"
done

