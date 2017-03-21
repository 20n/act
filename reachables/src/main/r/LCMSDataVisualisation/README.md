This module lets you setup a visualization server that allows comparative display of UntargetedMetabolomics analysis.

  <p align="center"> <img width=65% src="http://20n.com/assets/img/lcms-viz.png"> </p>

## Requirements

R version > 3.1

## Instructions

### Get a fresh version of the reachables project

- If needed, clone a new version of the `act` repository
```
mkdir lcms-viz 
git clone git@github.com:20n/act.git lcms-viz/
```

- Sync your lib directory with the NAS and install the Chemaxon license

```
cd lcms-viz/reachables/ 
rsync -azP /mnt/data-level1/lib/ lib/ 
license /mnt/shared-data/3rdPartySoftware/Chemaxon/license_Start-up.cxl
```
At this point, you should be able to fully compile your SBT project.
Try performing a clean compilation and running tests on the project: `sbt clean compile test`.

### Link to outside resources
The app needs to access outside resources, including the 20n logo, but more importantly some methods in the reachables project, such as the `MS1` class for m/z values computation. By creating symlinks in the app's directory, we provide the path to these resources.

- Create a fat JAR with SBT
```
sbt assembly
```

- Create symlinks in the app directory: 
```
cd src/main/r/LCMSDataVisualisation/
ln -s /var/20n/home/thomas/lcms-viz/reachables/target/scala-2.10/reachables-assembly-0.1.jar reachables-assembly-0.1.jar
ln -s /var/20n/home/thomas/lcms-viz/reachables/src/main/resources/20n.png 20nlogo
```

- Finally, make sure that the directory `/home/thomas/data/mol-structure-cache/` exists. This is where molecule structure images will be stored. TODO: add a symlink for this too!

### Get R dependencies and install Scala through R
- Start R and install the required packages
```
sudo R
```
In the R console:
```R
R.Version() # Should be > 3.1
install.packages(c("shiny", "rscala", "dplyr", "plot3D", "classInt", "jsonlite", "logging", "digest"))
library(rscala) # loads the "rscala" library
scalaInstall() # downloads and installs Scala
# Package "mzR" needs to be installed through the bioconductor package
source("https://bioconductor.org/biocLite.R") # try replacing http:// with https:// if it gives you an error
biocLite("mzR")
```

### Start the server
In the R console:
```R
library(shiny)
runApp(port = 8083, host = "0.0.0.0")
```
The app should be now accessible from `http://hostname:8083`

### Test using demo UntargetMetabolomics file
Try uploading the [lcms-demo.json](lcms-demo.json) file for sample visualization.
