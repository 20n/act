## Setting up the Substructure Search Service ##

This repository contains everything needed to package the components
of the substructure search service for use on a dedicated server.
This is a work in progress, so please report any deployment problems.

### Overview ###
The high-level setup procedure for the substructure search service follows these steps:
* Ensure mediawiki is working, and that nginx is handling `*.php` requests properly.
* Install Java (probably the JDK, just for easier production debugging) onto the host.
* Copy the service JAR to the wiki host, and get all data (like the reachables list) and configuration dependencies in place (like the config JSON) in place.  TODO: get logging working correctly.
* Copy the /etc/init.d scripts in place to start the service.  Start it and make a test request to it.
* Update the mediawiki nginx config to correctly proxy requests to the search service.
* Place the compiled frontend code into a mediawiki subdirectory, which will make them immediately accessible via nginx.
* Navigate to the substructure search page and make some searches to ensure things are working.
* Navigate to the order service page and try placing an order.
* Add a link to the substructure search on the wiki's main page (and maybe in the side bar too).

TODO: maybe we can automate some of this with ansible or something similar, but that's a lot of overhead to incur.

### Preparation ###
Before installing the substructure search on a host, ensure the firewall is operating in default-deny mode or the AWS security group restrictions deny public Internet traffic by default.  Once setup is completed, the only process that should be listening to traffic from the internet is nginx (on either port 80 or 443 if using SSL).  This can be done either by manipulating AWS or Azure routing rules to only allow traffic on the desired port, or by using firewall software such as UFW (uncomplicated firewall) to restrict access.  Configuring either of these is outside the scope of this document.

Mediawiki should already be installed on the host, as we'll be using it as the root directory for serving static content.  This practice is a little sketchy but it reduces the complexity of our setup procedure.

### Steps to deployment ###

#### Build and Install a JAR ###
Run `sbt assembly` in the `substructureSearch` repository to build an uber-jar.  This will appear at `target/scala-2.10/wikiServices-assembly-0.1.jar`.  This should be familiar to you. :grimace:  It should be least 150MB in size.  Get this rsync-ing to your wiki host first, as it'll take a while to complete.

##### One-time Setup: Install Java #####
If this is your first time setting up the service, also rsync `../scripts/azure/install_java` and a suitable JDK package (which will be named something like `jdk-8u77-linux-x64.tar.gz`).  You can get a JDK package from the Oracle website, or use this command to download one directly on the host where it'll be installed (which is much faster):
```
curl -v -j -k -L -O -H "Cookie: oraclelicense=accept-securebackup-cookie" 'https://download.oracle.com/otn-pub/java/jdk/8u111-b14/jdk-8u111-linux-x64.tar.gz'
```
(See [this Stack Overflow post](http://stackoverflow.com/questions/10268583/downloading-java-jdk-on-linux-via-wget-is-shown-license-page-instead) for more tips.)

To install Java, run these commands:
```
# Replace the example JDK version here with whatever version you want to install.
$ jdk_zip=jdk-8u111-linux-x64.tar.gz
$ ./install_java $jdk_zip
```

##### One time setup: Install `JSVC` #####
You'll also need to install `jsvc`, which is a handy utility from Apache to daemonize Java services.  The easiest way to do this is via `apt-get`, but unfortunately this installs OpenJDK at the same time.  We'll just let that go and make sure we're running the correct version of Java after the fact:
```
$ sudo apt-get install jsvc
$ which java
/usr/local/bin/java
$ java -version
java version "1.8.0_111"
Java(TM) SE Runtime Environment (build 1.8.0_111-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.111-b14, mixed mode)
```
Note that this will include Ubuntu in the name if it's using OpenJDK.  There's nothing wrong with OpenJDK now--we just use the Oracle version to be consistent with what we use on other hosts (including our lappys).

##### Install the Assembly JAR #####
Now put the assembly JAR in place for the `init.d` scripts to find later:
```
# These directories should be owned by root
$ sudo mkdir -p /usr/local/software/wiki_web_services
$ sudo mkdir -p /var/log/java && sudo chown -R www-data:www-data /var/log/java
# Replace the name of this JAR file if using a different version number, or add a version prefix yourself.
$ jar_file=wikiServices-assembly-0.1.jar
$ sudo cp $jar_file /usr/local/software/wiki_web_services
$ sudo ln -sf /usr/local/software/wiki_web_services/$jar_file /usr/local/software/wiki_web_services/current.jar
$ sudo chown -R root:root /usr/local/software/wiki_web_services
```

##### Ensure `www-data` has a Writable Home Directory #####
The default home directory for `www-data` is `/var/www`, which (correctly) is owned by the root user and not world-writable.  This is a problem for the Chemaxon libraries, as they unpack the native libraries on which they depend into the user's home directory before loading them.  To work around this, we'll make a proper home for `www-data` and set that directory as its home:
```
$ sudo mkdir /home/www-data
$ sudo chown -R www-data:www-data /home/www-data
# We have to stop all of www-data's processes before we can invoke usermod.
$ sudo /etc/init.d/nginx stop
$ sudo /etc/init.d/php7.0-fpm stop
# Change www-data's home directory.
$ sudo usermod -d /home/www-data www-data
# Start up the services again.
$ sudo /etc/init.d/php7.0-fpm start
$ sudo /etc/init.d/nginx start
```

#### Build a frontend package ####

Next, build a static frontend package.  This will take a little while if you're building for the first time, so while we won't do anything with it until a later step, it's good to start it running in the background.  It's probably best to do this on an office server (like speakeasy) for consistency, though you'll need to install `npm` and `node` manually to do so (the `apt` packages for Ubuntu 14.04 are too old to run the build scripts we use).  To check the version of node you're using run this:
```
$ node --version
v6.9.2
```

##### One time Node.js setup #####

While we don't need Node.js to run the substructure search, we do need to use it when building the substructure search's JS package.

On speakeasy, we currently have v6.9.2, and we need at least v4 to build our frontend code--hurrah!  If `node` isn't recognized or is too old, download a Node.js distribution from the Node website, verify it with checksums/gpg, and then run this to install it (in this example I'm installing 6.9.2, but you should use whatever version you downloaded):
```
$ tar zxvf node-v6.9.2-linux-x64.tar.gz
$ sudo mv node-v6.9.2-linux-x64 /usr/local/software/
$ sudo chown -R root:root /usr/local/software/node-v6.9.2-linux-x64
$ sudo ln -s /usr/local/software/node-v6.9.2-linux-x64/bin/node /usr/local/bin/node
$ sudo ln -s /usr/local/software/node-v6.9.2-linux-x64/bin/npm /usr/local/bin/npm
```
Now check that we're using the right node with the version we expect:
```
$ which node
/usr/local/bin/node
$ which npm
/usr/local/bin/npm
$ node --version
v6.9.2
$ npm --version
3.10.9
```
If you get something different, make sure your `PATH` environment variable looks in `/usr/local/bin` first.  You can update PATH like this:
```
$ export PATH=/usr/local/bin:$PATH
```
but you should already know how to do that at this point. :grimace:

##### Preparing a Frontend Package #####
Now that we have an up-to-date version of Node installed, `cd frontend` and run `npm install` to install all required js packages; this will take a while, so go grab a coffee or something.  When that's done, run `npm run build` to make an optimized js build.  You may have to futz with dependency installation if this is the first time building this project--required packages live in `project.json`.  The output will live in `frontend/build`.  Finally, get rid of the source maps before upload.  This will prevent users from snooping on our original js source.  All the steps together look like this:
```
$ pushd frontend
$ npm install
$ npm run build
# TODO: maybe blow away old source maps
$ mkdir -p maps
$ find build -name '*.map'  | xargs -n1 -I__ mv __ maps
$ mv build/asset-manifest.json ./maps
$ popd
```

Once you have a build directory, copy it to the wiki host and install it:
```
$ sudo mv build /var/www/mediawiki/substructure
$ sudo chown -R www-data:www-data /var/www/mediawiki/substructure
```

#### Configuring the Wiki Web Services ####

##### Substructure Search Config #####

You can modify the configuration JSON file at `service/substructure_config.json` to fit your particular setup.  In general, this should not be necessary, as the configuration file has been written to work in a hostname-agnostic way, and uses paths that are consistent within this setup procedure.

For non-standard setups, set the URL prefixes to match the (soon to be) location of your molecule image assets, and the wiki prefix to be whatever root path you're using for the wiki.  Our default nginx config puts these as seen in the example below.  The default configuration file also expects the reachables list and license file to live under `/etc/wiki_web_services`.  This is a reasonable home for such files, and you should make sure both the license file and reachables list are there before trying to start the service (if not, it'll crash).  Note that the port needs to be the one to which nginx will forward `/search` traffic.  `The config should look something like this:
```JSON
{
  "port": 8888,
  "reachables_file": "/etc/wiki_web_services/reachables",
  "license_file": "/etc/wiki_web_services/20n_Start-up_license.cxl",
  "wiki_url_prefix": "/",
  "image_url_prefix": "/assets/img/"
}
```

Copy this config file into `/etc/wiki_web_services` and make it owned by root.

##### Orders Service Config #####

The configuration file for the orders service contains AWS credentials that **must** be changed before running the service, so it will definitely need some editing before/during installation.

```JSON
{
  "port": 8889,
  "reachables_file": "/etc/wiki_web_services/reachables",
  "admin_email": "saurabh@20n.com",
  "wiki_url_prefix": "/",
  "image_url_prefix": "/assets/img/",
  "client_keyword": "PICK_A_KEYWORD",
  "aws_access_key_id": "SET_ACCESS_KEY",
  "aws_secret_access_key": "SET_SECRET_KEY",
  "aws_region": "us-west-2",
  "aws_sns_topic": "arn:aws:sns:us-west-2:523573371322:wiki_order_notifications"
}
```

You'll need to set:
* `client_keyword`: this will appear in the order emails that get sent to SNS, and should uniquely identify the client for whom this wiki has been constructed.  The order form also requires a contact email, so this may be somewhat redundant, but at least you'll know where the order came from if somebody enters a garbage email.
* `aws_access_key_id` and `aws_secret_access_key`: these are AWS credentials for the `wiki-order-notification-user` user.  This user has permission to do only one thing: publish messages to the SNS topic listed above.  You can create fresh access keys from the IAM management dashboard (under the "Security credentials" tab).

Note that the admin email will be used on the orders page for users who need assistance.  This should probably be an alias, but I've used a sensible default for now. :expressionless:

Copy this config file into `/etc/wiki_web_services` and make it owned by root.

#### Export a List of Reachables ####

The substructure search and orders services need to know what molecules are available for perusal/purchase, so we need to feed them a list of names, InChIs, and InChI Keys to use.  Note that this could also have been a database, but that seems overkill and adds more operational complexity than just giving them a flat file.

The `act.installer.reachablesexplorere.WikiWebServicesExporter` class will produce a list from a Reachables collection in MongoDB.  Run this class against the collection you are going to expose in the wiki.

Copy this TSV file into `/etc/wiki_web_services` and make it owned by root.

#### Drop In Static Assets ####

While the substructure search UI is a single-page react.js app, the orders service is just a simple form-based Java service.  It needs a couple of additional static resources in order to style the HTML that it generates.

##### Static JS/CSS #####

The `assets` directory in this `service` directory should be copied to `/var/www/mediawiki/assets` and its owner set to `www-data`.  These JS/CSS files are used by the orders service, and so need to be available at `/assets` via nginx.  Note that the `site-wiki` nginx config will allow access to the files in `assets` without issue.

```
# This is the directory at service/assets, which we don't expect to already exist in /var/www/mediawiki
$ sudo mv assets /var/www/mediawiki/assets
$ sudo chown -R www-data:www-data /var/www/mediawiki/assets
```

##### Put Molecule Renderings in Place #####

Unfortunately, we can't simply reuse the images we upload to mediawiki, as they're stored in some hashed directory scheme that I don't want to reverse engineer.  Instead, we'll dump renderings for all the molecules in our Reachables collection into a subdirectory of `mediawiki`.

If you're using the image paths in the config files without modification and you've uploaded all the images into a file called `renderings`, these commands ought to work for you:
```
$ find renderings -type f -name 'molecule*.png' | sudo xargs -I__ cp __ /var/www/mediawiki/assets/img
$ sudo chown -R www-data:www-data /var/www/mediawiki/assets
```

#### Install `init.d` Scripts ####

The `service` directory contains two `init.d` scripts that can be used to easily start and stop the substructure and orders services.  These scripts should be installed into `/etc/init.d` and then linked (using a utility) into `/etc/rd*.d` to ensure that the services will start automatically after a reboot.
```
$ sudo cp substructure_search.initd /etc/init.d/substructure_search
$ sudo chown root:root /etc/init.d/substructure_search
$ sudo chmod 755 /etc/init.d/substructure_search
$ sudo cp orders_service.initd /etc/init.d/orders_service
$ sudo chown root:root /etc/init.d/orders_service
$ sudo chmod 755 /etc/init.d/orders_service
$ sudo update-rc.d substructure_search defaults 99
$ sudo update-rc.d orders_service defaults 99
```

You should now see the following files in at least `/etc/rc5.d/`:
```
/etc/rc5.d/S03orders_service
/etc/rc5.d/S03substructure_search
```

### Home Stretch: Starting Up ###

Do a sanity check that all of these files exist.  Everything but `/var/log/java` and `/var/www/mediawiki/assets` should be owned by root.
```
/usr/local/software/wiki_web_services/wikiServices-assembly-0.1.jar
/usr/local/software/wiki_web_services/current.jar
/etc/wiki_web_services/substructure_config.json
/etc/wiki_web_services/orders_config.json
/etc/wiki_web_services/20n_Start-up_license.cxl
/etc/wiki_web_services/reachables
/var/log/java
/var/www/mediawiki/assets
/etc/init.d/orders_service
/etc/init.d/substructure_search
/etc/rc5.d/S03orders_service
/etc/rc5.d/S03substructure_search
```

And now we start the service!  If everything goes to plan, you should see `Running` after running the status check commands.
```
$ sudo /etc/init.d/substructure_search start
$ sudo /etc/init.d/substructure_search status
# Should print "Running"
$ sudo /etc/init.d/orders_service start
$ sudo /etc/init.d/orders_service status
# Should print "Running"
```

### Enabling Reverse-Proxy Endpoints in Nginx ###

It's likely that you're already using an nginx config file that passes proxy requests to the services, but if not we can install the `site-wiki` file in the `service` directory to enable public access to the services.
```
$ sudo cp site-wiki /etc/nginx/sites-available
$ sudo chown root:root /etc/nginx/sites-available/site-wiki
# Remove any default site, which will conflict with ours.
# Optionally you can remove *everything* in /etc/nginx/sites-enabled, but we won't do that by default.
$ sudo rm /etc/nginx/sites-enabled/default
# Enable the wiki site configuration.
$ sudo ln -sf /etc/nginx/sites-available/site-wiki /etc/nginx/sites-enabled/site-wiki
$ ls -l /etc/nginx/sites-enabled
# You should only see site-wiki in the output of ls
# Now, tell nginx to reload its configurations, which will enable the proxy endpoints.
$ sudo /etc/init.d/nginx reload
```

You can run these test queries on the wiki host to ensure proxying is working and the services are up:
```
$ curl -vvv http://localhost/search?q=C
$ curl -vvv http://localhost/substructure/
$ curl -vvv http://localhost/order
```

Alternatively, you can open an ssh tunnel to the webserver and access the pages through your browser:
```
ssh -L8080:localhost:80 <my-wiki-host>
```
Then navigate to any of these links in your browser:
```
http://localhost:8080/substructure/
http://localhost:8080/order
http://
```

All of these should return HTTP 200s and should not produce wiki HTML (it should be obvious from curl's output if they're working correctly).

Now you can visit in a browser, either via tunnels or by opening port 80 to the Internet, `http://<your host>/substructure` to run substructure searches, or `http://<your host>/order` (though the latter will need an `inchi_key=<inchi key>` parameter with a valid InChI Key from the reachables list to work) to test out the order page.

:moyai:
