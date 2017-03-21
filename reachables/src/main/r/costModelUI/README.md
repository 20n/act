## Code
```
reachables/src/main/r/costModelUI/server.R
```

## Prerequisites
We assume the following to be true:
- R is installed on the server
- port 8080 is open to outside traffic
- the R libraries `shiny` and `rscala` are available
```
R
> install.packages(c("shiny", "rscala"))
> scalaInstall()
```
- symlinks to a `reachables` project fat jar and to the 20n logo are present in the working directory `reachables/src/main/r/costModelUI`
```
sbt assembly
cd act
ln -s reachables/target/scala-2.10/reachables-assembly-0.1.jar reachables/src/main/r/costModelUI/reachables-assembly-0.1.jar
ln -s reachables/src/main/resources/20n.png reachables/src/main/r/costModelUI/20nlogo
```
## Usage
Start R, then load the `shiny` library and start the app
```
R
> library(shiny)
> runApp(port = 8080, host = "0.0.0.0")
```
You can then access the app from `https://<host-public-IP>:8080`

