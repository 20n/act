for i in `ls -- [0-9]*.contributor.details`; 
do 
  type=`cat -- $i | grep -A 5 "Contributor classification" | tail -1 | sed 's/^.*<td>//g' | sed 's/<\/td>.*$//'` ; 
  sane_type=`echo $type | sed 's/ /_/g' | sed 's/\//_/g' | sed 's/(/_/g' | sed 's/)/_/g' `; 
  if [ x$sane_type != "x" ] 
  then
    mkdir -p $sane_type
    mv -- $i $sane_type/$i
  fi
  id=`echo $i | sed 's/.contributor.details//g'`
  echo "$id	$sane_type"
done > vendor_categories.tsv
