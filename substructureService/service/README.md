== Setting up the Substructure Search Service ==

This repository contains everything needed to package the components
of the substructure search service for use on a dedicated server.
This is a work in progress, so please report any deployment problems.

=== Preparation ===
Before installing the substructure search on a host, ensure the firewall is operating in default-deny mode.  Once setup is completed, the only process that should be listening to traffic from the internet is nginx (on either port 80 or 443 if using SSL).  This can be done either by manipulating AWS or Azure routing rules to only allow traffic on the desired port, or by using firewall such as UFW (uncomplicated firewall) to restrict access.  Configuring either of these is outside the scope of this document.

Mediawiki should already be installed on the host, as we'll be using it as the root directory for serving static content.  This practice is a little sketchy but it reduces the complexity of our setup procedure.

=== Steps to deployment ===
* Run `sbt assembly` in the `substructureSearch` repository to build an uber-jar.  This will appear at `target/scala-2.10/substructureSearch-assembly-0.1.jar`.
* Build a static frontend package.  `cd` to frontend and run `npm run build`.  You may have to futz with dependency installation if this is the first time building this project--required packages live in `project.json`.  The output will live in `frontend/build`.
* Use `rsync` to copy `target/scala-2.10/substructureSearch-assembly-0.1.jar`, the `build` directory, and the files in this `service` directory to your remote server.  If this is your first time setting up the service, also rsync `../scripts/azure/install_java` and a suitable JDK package (which will be named something like `jdk-8u77-linux-x64.tar.gz`).
* SSH to the server to begin installation.
* If this is the first time setting up the service, start by running `install_java` and installing jsvc:
```
# Replace the example JDK version here with whatever version you want to install.
$ jdk_zip=jdk-8u77-linux-x64.tar.gz
$ ./install_java $jdk_zip
$ sudo apt-get install jsvc
```
* Now install the substructure search components:
```
# These directories should be owned by root
$ sudo mkdir -p /usr/local/software/substructure_search
$ sudo mkdir -p /var/log/java
# Replace the name of this JAR file if using a different version number, or add a version prefix yourself.
$ jar_file=substructureSearch-assembly-0.1.jar
$ sudo cp $jar_file /usr/local/software/substructure_search
$ sudo ln -sf /usr/local/software/substructure_search/$jar_file /usr/local/software/substructure_search/current.jar
```
* Now install the init.d script so we can start the service:
```
$ sudo cp substructure_search.initd /etc/init.d/substructure_search
$ sudo update-rc.d substructure_search defaults 99
```
* Confirm `S99substructure_search` exists in `/etc/rc2.d`, and retry `update-rc.d` if not:
```
$ ls /etc/rc2.d/S99substructure_search
```
* Start and check the substructure search:
```
$ sudo /etc/init.d/substructure_search start
$ sudo /etc/init.d/substructure_search status
# Should print 'Running'
```

* Copy the static frontend assets into the appropriate directory:
```
# Remove source maps, as we don't expect to debug in production and don't want to give away our raw code.
$ find ./build -name '*.map' | xargs rm
$ sudo mkdir -p
# Sketchy, but it works!
$ sudo cp build /var/www/mediawiki/substructure
```
* Install the mediawiki site config to ensure all paths and reverse proxy configurations are up to date:
```
# Assumes there is no other mediawiki configuration in /etc/nginx/sites-allowed
$ sudo cp site-wiki /etc/nginx/sites-allowed && sudo ln -sf /etc/nginx/sites-allowed/site-wiki /etc/nginx/sites-enabled/site-wiki
# Reload the site config
$ sudo /etc/init.d/nginx reload
```
Now you should be able to visit http://your-host/substructure and make a search query to test.
