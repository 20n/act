## Introduction: Mediawiki Web Services ##

This directory contains source and config files for web services that support our mediawiki installation.  These should only be enabled on **private** wiki installations, not on the public preview wiki.  The high-level steps to setting up a wiki are:

1. create wiki data (or use preview wiki data on NAS)
1. setup a wiki VM and upload data to it.

Private wikis should be created by launching new instances of existing wiki Azure VM images (or, if necessary, EC2 AMIs).  Instructions on creating VM images and AMIs from scratch appear in the [appendices of this document](#appendices) and in `service/README.md`; creating from-scratch images should *not* be necessary during normal wiki instance deployment.

## 1. Wiki Content Generation ##

If you need fresh wiki content generated prior to loading, start this process in the background while you set up a new VM--it will likely take longer than the VM setup process.

### Create an ACT DB ###

Note: it's best to run all of the following commands in a `screen` session, as they will take a long time to complete.


It is likely that you'll have an ACT database on hand from which to produce reachables and cascades.  If not, run this
command (on physical server `speakeasy`, or azure `twentyn-speakeasy-west2`):
```
$ screen -S mongo-database
  $ mongod --version 
     // should be over v3.0.0 or else will crash during reachables computation
     // upgrade if needed: https://docs.mongodb.com/master/tutorial/install-mongodb-on-ubuntu/
  $ mongod --dbpath /PATH_TO_DIR_ON_AT_LEAST_250GB_DISK

$ screen -S installer
  $ time sbt 'runMain com.act.reachables.initdb install omit_kegg omit_infer_ops omit_vendors omit_patents omit_infer_rxnquants omit_infer_sar omit_infer_ops omit_keywords omit_chebi'
```

This process will install a DB to `actv01` on machine.  This process will probably take somewhere on the
order of 48 hours to complete.  To setup a server to run this from scratch various external data dependencies are needed. The setup process is described in (Azure Server Setup)[https://github.com/20n/act/wiki/Architecture:-On-Azure#buildup-notes].

Once the installer has run, the biointerpretation pipeline should be run on `actv01`.  The configuration file for
the `BiointepretationDriver` class looks like this:
```JSON
[
  {
    "operation": "MERGE_REACTIONS",
    "read": "actv01",
    "write": "drknow_OPTIONALSUFFIX"
  },
  {
    "operation": "DESALT",
    "read": "drknow_OPTIONALSUFFIX",
    "write": "synapse_OPTIONALSUFFIX"
  },
  {
    "operation": "REMOVE_COFACTORS",
    "read": "synapse_OPTIONALSUFFIX",
    "write": "jarvis_OPTIONALSUFFIX"
  }
]
```
You can add `_OPTIONALSUFFIX` if you need to identify the run, e.g., tag it with a date `_20170111`.
This process will take on the order of 12-15 hours to complete.

Note that mechanistic validation is not included in this pipeline due to performance reasons.  To enable it, add the
following block to the array of operations above:
```JSON
  {
    "operation": "VALIDATE",
    "read": "jarvis_OPTIONALSUFFIX",
    "write": "marvin_OPTIONALSUFFIX"
  }
```
Validation on `master` may take up to a week to complete (Last time on azure took 8.53 days to complete).  The `limit-reactor-products-for-validation` branch has
WIP fixes that limit the scope of the validator's search, and may increase its performance by a significant margin.

Save your JSON configuration in a file, in our case `biointerpretation_config.json`, and run this command:
```
$ sbt 'runMain com.act.biointerpretation.BiointerpretationDriver -c biointerpretation_config.json'
```

The output of the installer pipeline will be a database named `jarvis_OPTIONALSUFFIX` (or `marvin_OPTIONALSUFFIX` if you ran mechanistic validation).
Your full db should now look like the following (`$ mongo localhost`):
```
> show dbs
actv01   59.925GB
drknow   37.936GB
jarvis   33.938GB
local     0.078GB
marvin   33.938GB
synapse  35.937GB
```

### Run Reachables and Cascades ###

**TODO: validate this section**

To run reachables computation on your new database, set all the appropriate variables as shown below:
```SHELL
$ today=`date +%Y%m%d`;
$ dirName="reachables-$today";
$ PRE="r-$today";
$ DEFAULT_DB=<Edit this to be your database name>
```

Then, run the commands as shown below.
```SHELL
$ cd act/reachables
$ sbt "runMain com.act.reachables.reachables --prefix=$PRE --defaultDbName=$DEFAULT_DB --useNativesFile=src/main/resources/reachables_input_files/valid_starting_points.txt --useCofactorsFile=src/main/resources/reachables_input_files/my_cofactors_file.txt -o $dirName";
$ sbt "runMain com.act.reachables.postprocess_reachables --prefix=$PRE --output-dir=$dirName --extractReachables --writeGraphToo --defaultDbName=$DEFAULT_DB";
$ sbt "runMain com.act.reachables.cascades --prefix=r-$today --output-dir=$dirName --cache-cascades=true --do-hmmer=false --out-collection=pathways_${DEFAULT_DB}_${today} --db-name=$DEFAULT_DB --verbosity=1"
```

Note that the last command `cascades` is a memory hog. There is a `.sbtopts` file in the repository ([sbtopts ref](https://medium.com/@jan______/sbtconfig-is-deprecated-650d6ff10236])) that sets the java flag for heap size to 25G (overestimated). Ensure that is read by running with the debug flag `sbt -d`, and check the presence of `-Xmx25G`. Change that to the appropriate size if needed. Also, if the process runs out of memory, just rerun. It will skip already computed cascades and continue. 

You should now have `r-${today}.reachables.txt` and `r-${today}-data` in your `reachables-$today` directory.  We'll
need these to complete the remaining steps.


### Augment the Installer with Bing Search data

In the absence of a subscription to the Bing Search API, the Bing Searcher relies on a local cache to populate installer
data with Bing cross-references. The cache stores the results of raw queries made to the Bing Search API and the Bing 
Searcher processes them to output relevant usage words and search count.

We run the Bing Searcher, to populate the installer with Bing results using the `-c` option to use only the cache and
not make queries to the Bing Search API.
```
sbt "runMain act.installer.bing.BingSearcher -n ${DEFAULT_DB} -h localhost -p 27017 -c"
```
To check the outcome, run the following command before and after the execution:
```
mongo localhost/marvin --quiet --eval "db.chemicals.count({'xref.BING.metadata.usage_terms.0': {\$exists: true}});"
```
Before running `BingSearcher` you should expect `12004` and afterwards `21485`.


### Run Word Cloud Generation ###

Word cloud generation must be done before the reachables collection is loaded, as the word cloud images must exist for
them to be recognized by the loader.  We cut out the InChIs from the reachables list and feed that to the word cloud
loader.  Note that Bing data must have been made available to the installer in the first step and the Bing search cache
be available for this process to work.

For this step we need R to be installed (specially `/usr/bin/Rscript`). Do the following if needed:
```
# install >3.3.1 version of R:
# instructions from https://www.digitalocean.com/community/tutorials/how-to-install-r-on-ubuntu-16-04-2
$ sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys E298A3A825C0D65DFD57CBB651716619E084DAB9
$ sudo add-apt-repository 'deb [arch=amd64,i386] https://cran.rstudio.com/bin/linux/ubuntu xenial/'
$ sudo apt-get update
$ sudo apt-get install r-base

# install dependency libraries needed by R install.packages below
$ sudo apt-get install libssl-dev
$ sudo apt-get install libsasl2-dev

# install reqd R package dependencies (also ensure R version is >3.3.1)
$ sudo -i R
> install.packages("mongolite")
> install.packages("slam")
> install.packages("wordcloud")
```

Run the generator:
```
$ cut -d$'\t' -f 3 reachables-${today}/r-${today}.reachables.txt >  reachables-${today}/r-${today}.reachables.just_inchis.txt
$ sbt "runMain act.installer.reachablesexplorer.WordCloudGenerator --inchis-path reachables-${today}/r-${today}.reachables.just_inchis.txt --r-location /usr/bin/Rscript --source-db-name ${DEFAULT_DB}"
```
Note that this reads the cache first, so if you already have `52971` files (`ls -l data/reachables-explorer-rendering-cache/ | wc -l`) then it might just run and say "success" without any additional output.

### Run the Loader to Create a Reachables Collection ###

Run the loader to produce a collection of `reachable` documents in MongoDB.

```
sbt "runMain act.installer.reachablesexplorer.Loader --source-db-name ${DEFAULT_DB} --reachables-dir $dirName --out-reachables-coll reachables_${today} --out-seq-coll sequences_${today} --projected-inchis-file data/L4n1\,2-Dec2016/L4n1_in_pubchem"
```

The `-l` option installs a set of L4 projections, and can be omitted if necessary.  This command will output any missing
molecule renderings to the rendering cache at `data/reachables-explorer-rendering-cache/`.  It
depends on a Virtuoso RDF store process being available to find synonyms and MeSH headings;
The Virtuoso host is assumed to be `localhost:8890` and if it is different setup an ssh tunnel appropriately. (If you followed the instructions in the [architecture doc](https://github.com/20n/act/wiki/Architecture:-On-Azure#buildup-notes), you should already have a virtuoso host running.)

### Enrich the Reachables with Patents ###

To find recent patents for reachable molecules, use the `PatentFinder` (The `-c` flag indicates which Reachables Collection should be used):
```
$ sbt "runMain act.installer.reachablesexplorer.PatentFinder -c reachables_${today}"
```

This expects a collection of patent indexes to live at `data/patents` (containing subdirs such as `2005.index` -- `2014.index`).  An alternative path can be
specified on the command line.

### Dot File Rendering ###

Once you have run the `Loader`, molecule renderings for all reachable molecules should be available at
`data/reachables-explorer-rendering-cache/`.  These are referenced by the dot files, which expect
them to live at `data/reachables-explorer-rendering-cache/`. If the command below dumps error messages
mentioning missing files from `/mnt/data-level1/data/..` then your dot files refer to stale data. Debug
how that happened and change them to relative paths.

To render the dot PNGs, run this command:
```
$ find reachables-${today}/r-${today}-data -name '*.dot'  -exec dot -Tpng {} -O \;
```
Note that this will write the images in the same directory as the dot files.  If this is not desired, you can copy them
to another directory with this command:
```
$ find reachables-${today}/r-${today}-data -name '*.dot.png' -exec cp {} <dest> \;
```

Note: The currently expected <dest> is `data/reachables-explorer-rendering-cache/`.

### Wiki Page Rendering ###

To render pathway-free wiki pages for all reachable molecules, run this command:
```
$ sbt "runMain act.installer.reachablesexplorer.FreemarkerRenderer -o wiki_pages --pathways pathways_jarvis_${today} -r reachables_${today} -i jarvis_2017-01-11 --no-pathways
```
Note that we specify a pathways collection here to make sure that no molecules are opportunistically loaded into the reachables collection from stale pathways.

You can later specify specific pages to re-render with pathways and sequence designs (we'll get to designs in a moment):
```
$ sbt "runMain act.installer.reachablesexplorer.FreemarkerRenderer -o wiki_pages_custom --pathways pathways_jarvis_${today} -r reachables_${today} -i jarvis_2017-01-11 -m MWOOGOJBHIARFG-UHFFFAOYSA-N
```

Once the pages are generated, follow the upload and import instructions below.

### Building DNA Designs ###

To produce DNA designs for just a few molcules, run the following:
```
$ inchi_key=<inchi key>
$ sbt "runMain org.twentyn.proteintodna.ProteinToDNADriver -c pathways_jarvis_${today} -d pathways_jarvis_w_designs_${today} -e designs_jarvis_${today} -m $inchi_key"
```
Then render just the pages for the molecules you're interested using the command above, like this:
```
$ sbt "runMain act.installer.reachablesexplorer.FreemarkerRenderer -o wiki_pages_custom --pathways pathways_jarvis_w_designs_${today} -r reachables_${today} -i jarvis_2017-01-11 -m $inchi_key
```

### Building category pages

To produce the category pages, the following command has to be run:

```
$ dbName="wiki_reachables"
$ dest="wiki_pages/Category_Pages" # for example
$ python src/main/python/Wiki/generate_category_pages.py reachables_${today} $dest $dbName
```
Since the script might be running at a later date, ensure that you do not overwrite `today` from when it was originally set. I.e., make sure this `reachables_` variable matches the above. 

Make sure the $dest dir is the same dir as the other pages generated in the FreemarkerRenderer process.

## 2. New Wiki Instance Setup Steps ##

At a high level, setting up a wiki follows these steps:

1.  Create a new Azure VM
1.  Update `LocalSettings.php`.
1.  Update `orders_config.json` and restart the orders service.
1.  Load content into the wiki.
1.  Set basic authentication credentials.
1.  Enable public access to the wiki.

### Create an Azure VM Instance ###

Azure's VM image deployment process is more involved than AWS's, but their costs are (for us as part of our YC membership) lower than AWS.  New VMs can be spun up using the Azure CLI tools, so you shouldn't only occasionally need to grapple with the web dashboard.

#### SSH Configuration ####

**Note: these instructions are identical to those in `act/scripts/azure/README.md`, but are partially included here for continuity.**

Before manipulating any Azure instances, you'll need to set up you `~/.ssh/config` file to access Azure hosts.  Thanks to Azure's convenient internal DNS service, we're able to reference instances by hostname (rather than by IP as must be done in AWS).  We capitalize on this situation by requiring that all ssh access to Azure hosts goes through a *bastion* VM.  This bastion is the only server in Azure that needs to allow ssh access to the public Internet--this means that we can trivially revoke a user's access to Azure, and can monitor all ssh access to any of our Azure hosts via the bastion.

The bastion does not have a meaningful public DNS name, but does have a static IP.  Add this block to your Azure configuration file:
```
Host *-wiki-west2
  ProxyCommand ssh 52.183.73.127 -W %h:%p
  ServerAliveInterval 30
  ForwardAgent Yes
```
Note that you may also need to specify a `User` directive if your laptop username is not the same as your server username.

We can also use the bastion as an HTTP proxy host, granting us easy web browser access to Azure hosts that are not publicly accessible.  You can enable HTTP proxying via ssh tunnels to the `-wiki-west2` zone the same way is done for other zones; consult `act/scripts/azure/README.md` for instructions.

#### Prerequisites ####

The setup process we'll use depend on the `azure` and `jq` cli tools.  You should be able to install them on your lappy using `homebrew`:
```
$ brew install azure
$ brew install jq
```

Visit `http://portal.azure.com` in your browser and sign in.  If you can't sign in, stop here and ask Saurabh for credentials. Once you're logged in, run the following commands on your lappy:
```
$ azure login
# Follow the login instructions, which will involve copying a URL to your browser and entering a code.
$ azure config mode arm
# Now you're using the Azure Resource Manager, which is what we want.
```
Follow the prompts to authenticate your host via a web browser.

You should set a default subscription ID in your Azure CLI environment to ensure that billing is handled the correct way.  Try the following command and look for `Microsoft Azure Sponsorship` in the `Name` field--that should be our sponsored Azure subscription, which is the one we should use for all things Azure:
```
$ azure account list
info:    Executing command account list
data:    Name                         Id                                    Current  State
data:    ---------------------------  ------------------------------------  -------  -------
data:    Microsoft Azure Sponsorship  xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx  true     Enabled
info:    account list command OK
```
The `Id` field should contain a UUID (omitted here).  If you can't see the `Microsoft Azure Sponsorship` subscription here, go to the Azure portal and access the `Subscriptions` panel.  The `Subscription Id` field should have the UUID you need.  Once you've got it, paste it into this command:
```
$ azure account set <subscription UUID>
```

All of the setup instructions can now be run from your lappy (which is a better option than a shared server, as your login credentials are now stored locally).

#### Instantiating New Wiki Instances ####

While the actual VM image setup is convoluted, creating a new wiki instance is not difficult.  In fact, we'll reuse `spawn_vm` in `act/scripts/azure` to create a new wiki instance with no data loaded and only vanillin available via the substructure search:
```
$ n=1 # Set a host number or designator accordingly.
$ ./spawn_vm reachables_wiki twentyn-azure-west-us-2 private-${n}-wiki-west2
```

This will create a wiki instance **without** a public IP so that you can set it up without it being exposed to the public Internet.  We'll need to make some modifications before exposing it to the public Internet, but we'll do that after the wiki is ready to go.

### SSH to Your New VM ###

To make the remaining modifications to your new wiki VM, you'll need to `ssh` to it.  If you followed the [SSH configuration instructions](#ssh-configuration) above, `ssh` will automatically proxy your connections through the appropriate Azure bastion (the bastions are the only hosts who serve `ssh` traffic from the public Internet).  Connect to your host like this:
```
$ n=1
$ ssh private-${n}-wiki-west2
```

You may have to specify a user if your server username doesn't match your laptop username (the name used on the VMs is the same username on our office servers).  If this is the case, add a `Username <server username>` directive to the `Host *-wiki-west2` block in your `~/.ssh/config` file.

If this is your first time connecting to *any* wiki host, you'll be asked for host key confirmation *twice*--this is expected, and you should answer "yes" both times.  If you've recreated a wiki VM with a name that's been used before, `ssh` will complain loudly about mismatching host keys.  In this happens, either edit your `~/.ssh/known_hosts` file manually to remove the previous entry for the problematic host, or follow the instructions in the error message to remedy the error.

Note that ignoring host key warnings is slightly sketchy, but it's really just part of the job of creating/destroying/recreating VMs in a virtualized hosting environment.  The fact that all of our VMs only allow public-key-based `ssh` authentication reduces the likelihood of someone compromising our hosts in a way that the keys could be replaced (Azure DNS security notwithstanding).

### Set an NGINX Hostname ###

In order to use our `*.bioreachables.com` SSL certificates, we must configure NGINX to identify itself using the hostname we expect to associate with the new VM.  This name must match the hostname [we set as `$wgServer`](#set-wgserver).  Here is a diff representing the required change to `/etc/nginx/sites-available/site-wiki-ssl`, where the new hostname is `stardust.bioreachables.com`:
```diff
--- site-wiki-ssl	2017-01-11 00:48:05.254290466 +0000
+++ site-wiki-ssl.new	2017-01-11 21:28:56.342049859 +0000
@@ -10,17 +10,17 @@

 server {
   # With help from http://serverfault.com/questions/250476/how-to-force-or-redirect-to-ssl-in-nginx
   listen 80;

-  server_name changeme.bioreachables.com;
+  server_name stardust.bioreachables.com;
   rewrite ^ https://$server_name$request_uri? permanent;
 }

 server {
   listen 443 ssl;
-  server_name changeme.bioreachables.com;
+  server_name stardust.bioreachables.com;

   keepalive_timeout 70;

   ssl_certificate /etc/nginx/ssl/bioreachables.com.crt;
   ssl_certificate_key /etc/nginx/ssl/bioreachables.com.key;
```

The commands will look like:
```
$ sudo vim /etc/nginx/sites-available/site-wiki-ssl
# Change `changeme` to `stardust` or some host name you will use.
$ sudo /etc/init.d/nginx reload
```

*You need not assign the DNS name before you set this variable.*  Certificate validation in your browser will require this hostname to be used, but you can still connect to the host via other names/addresses and override the certificate warnings to access it in a browser.  You can also use `curl`'s `--insecure` parameter to ignore the hostname mismatch; uses of `curl` later in this document make use of this parameter.

### Set an NGINX Password ###

Assuming you have SSL enabled, you'll want to protect the wiki with a username and password.  Full instructions are [in this section](#addingupdating-a-password), but you can just run this command:
```
$ sudo htpasswd -c /etc/nginx/htpasswd <username>
```
and input a password.  This will wipe out the default username and password (see the [basic auth](#basic-authentication) section) and replace it with whatever you specify--it will be the *only* username/password combination that will work.

### Set an NGINX Auth Domain ###

It is a good security practice to change the authentication domain for each wiki instance, which will prevent the unlikely event of a user who is authorized to access one wiki being able to reach another (note that this would require identical basic auth credentials on both hosts, which should be impossible if the password generation step above is followed).  To do so, find the following line in `/etc/nginx/sites-available/site-wiki-ssl` and change the value (in quotations) to something different:
```
  auth_basic "20n Wiki 1";
```
Then reload the NGINX configuration.

Example commands for this step are:
```
$ sudo vim /etc/nginx/sites-available/site-wiki-ssl
# Change auth_basic to "20n Wiki 2" or something else.
# Now reload the nginx configuration.
$ sudo /etc/init.d/nginx reload
```

### Update LocalSettings.php ###

The `reachables-wiki` Azure VM images and EC2 AMIs contain a full Mediawiki, MySQL, NGINX, and web services stack necessary to run a private wiki instance for a single client.  Only a few configuration changes are need to be made to prepare a wiki for client use; most of the work will involve loading data into the wiki--see the section on [Loading Data into the Wiki](#loading-data-into-the-wiki).

The mediawiki configuration file that we'll need to change lives here:
```
/var/www/mediawiki/LocalSettings.php
```
This is the *only* PHP file you should need to change as part of your VM instance configuration--everything else has already be configured/altered as necessary.

Edit this file with:
```
$ sudo vim /var/www/mediawiki/LocalSettings.php
```
(or use the editor of your choice).  Note that `LocalSettings.php` should be owned by `www-data`, so don't copy over this file without changing its ownership as well (editing will not change its ownership).

#### Set `$wgSecretKey` ####

An *important manual step* when setting up a new wiki instance is to replace `$wgSecretKey` in `LocalSettings.php`.  Doing so will limit the scope of work that needs to be done should any one wiki instance be compromised by a malicious party.

The following command will create a strongly random, 64 character hex string for use as a secret key:
```
$ cat /dev/random | hexdump -n32 -e '8/ "%08x"' -e '"\n"'
```
(See [this page](https://www.technovelty.org/tips/hexdump-format-strings.html) for more information on `hexdump` formatting).
Note that if you run this on Linux hosts, repeated invocations will block until `/dev/random`'s entropy pool has been refilled.  Run `cat /proc/sys/kernel/random/entropy_avail` to see the number of available bits; at 256, the command should be able to execute but will completely empty the pool.

The MySQL credentials for each wiki may be left at their default values.  While a leaked `LocalSettings.php` would expose these credentials, the MySQL processes running on our wiki instances are not publicly accessible with the current security groups: an attacker would need to gain (key-based) ssh access to our wiki hosts to access the DB, which is extremely unlikely.

#### Set `$wgServer` ####

Another *important manual step* is to set `$wgServer` to the appropriate base URL for all wiki links.  Update `$wgServer` in `LocalSettings.php` to reference whatever name you intend to assign to this VM in DNS.  This must match the hostname used in the [NGINX hostname configuration step above](#set-an-nginx-hostname).

*You need not assign the DNS name before you set this variable*: this is what mediawiki will use for its own URLs, and will be used even if you access the host via some other name or IP address.  Mediawiki has a tendency to rewrite the current URL with its canonical hostname, which may result in unexpected connection failures if the hostname is not updated before clients access the wiki.

Note that if you are using SSL to encrypt traffic to the wiki, use `https` as the protocol for `$wgServer`.  This will ensure all URL rewrites force secure HTTP (though NGINX should already be doing this if SSL is configured correctly).

### Set Orders Service Client Key ###

The `/order` service endpoint uses a host-specific key to identify where an order request came from.  You'll need to update the `client_keyword` parameter in `/etc/wiki_web_services/orders_config.json` to something that represents the client for whom the wiki is being set up (could be a name or a codeword).  Here are the commands you'll run:
```
$ sudo vim /etc/wiki_web_services/orders_config.json
# Make your edits.  Then restart the service.
$ sudo /etc/init.d/orders_service restart
```
for the config change to take place.

### Moving Files to the Wiki Host ###

Now, what remains is to move data (generated locally, using [the instructions above](#wiki-content-generation)) to the server, and we'll then use that data to populate the wiki. For example, for the preview wiki, the data lives on the NAS at `/shared-data/Mark/demo_wiki_2016-12-21`.

Assuming you've followed the [SSH configuration instructions](#ssh-configuration) above, you should be able to move files to Azure VMs using `rsync`.  By default `rsync` will use `ssh` as its transport, and `ssh` will transparently proxy all connections through the bastion.  In general, the command to use is:
```
$ rsync -azP my_local_directory_or_file private-${n}-wiki-west2:
```
This will copy files into your home directory on the VM.  The options used here are:
```
-a    Preserve all possible file attributes, like modification times and permissions.
-z    Do inline compression, which will increase transfer speed significantly.  Omit this option if you are rsync-ing compressed files.
-P    Report per-file transfer progress.  This gives you an idea how long the transfer will take.
```

**Important**: when copying directories to your home dir on the wiki VM, **do not include a trailing slash!**  That will likely result in your home directory being made globally writeable, which **will lock you out of the VM.**  Here's an example:
```
# This is fine:
$ rsync -azP my_local_directory private-${n}-wiki-west2:

# This is bad, do not do this:
$ rsync -azP my_local_directory/ private-${n}-wiki-west2:

# This is also fine--note that the destination is explicitly specified:
$ rsync -azP my_local_directory/ private-${n}-wiki-west2:my_local_directory
```

Note that running `rsync` from a `screen` session when copying many files is perilous: once you disconnect from `screen`, `rsync` and `ssh` will no longer have access to your `ssh agent`, and so will be unable to create new connections to the remote host.  Moving single large files (like `tar` files) is fine in screen, however.

### Upload {Reachables, Paths, Sequences, Categories} and Images (renderings, and cascade dot renderings) ###

```
# upload {Reachables, Paths, Sequences, Categories} that are within the wiki_pages dir
$ rsync -azP wiki_pages private-${n}-wiki-west2:

# upload the cascade image renderings
#   Note that in the "Dot File Rendering" step above, we shoved all cscd*dot.png images 
#   into `reachables-explorer-rendering-cache` (where the mol/wordclouds live)
#   and so the command below will upload all of them in one go.
# upload the wordcloud and molecule renderings
$ rsync -azP data/reachables-explorer-rendering-cache private-${n}-wiki-west2:wiki_pages/renderings
```

### Create, Upload, and Install a Reachables List ###

The substructure search and orders services require a static TSV of reachable molecules in order to do their jobs.  This needs to be exported from the *same reachables collection* as was used to generate the wiki pages (to be in sync) using a class in the `reachables` project and installed on the wiki host.  There's some documentation in the [service README](./service#export-a-list-of-reachables), but here's a quick set of commands to run.

```
# Run this command on the server where the MongoDB instance with the Reachables collection lives.
$ sbt 'runMain act.installer.reachablesexplorer.WikiWebServicesExporter -c <reachables collection> -s <sequence collection> -o reachables.out'
# Copy the output file to the wiki host.
$ n=1
$ rsync -azP reachables.out private-${n}-wiki-west2:
# Connect to the host for the next commands.
$ ssh private-${n}-wiki-west2

# Run these commands on the remote host.
$ Put the new reachables file in place.
$ sudo mv reachables.out /etc/wiki_web_services/reachables
# Restart the web services to load the new list of molecules.
$ sudo /etc/init.d/substructure_search restart
$ sudo /etc/init.d/orders_service restart
```

### Loading Data into the Wiki ###

Here's the home stretch: we have a wiki working, but it's empty by default.  You'll need to load a bunch of data into it for it to be useful.  If you started generating content at the beginning of these steps, you'll probably need to wait a bit before it's all ready.  If you already have content, upload it with `rsync` and continue on.

All of the content in the wiki will be uploaded using maintenance scripts.  These scripts are easy to use and fairly quick to run.

Here's the appropriate maintenance script to use when loading each type of content, and the sub-directory in which they live in the output of the `FreemarkerRenderer` (with `renderings` being populated with word clouds manually):

Content/directory | Maintenance Script | Extensions | Sub-directory
----------------- | ------------------ | ---------- | ---------
Reachables | `importTextFiles.php` | N/A | Reachables
Pathways | `importTextFiles.php` | N/A | Paths
DNA Designs | `importImages.php` | txt | Sequences
Renderings/word clouds | `importImages.php` | png | renderings
Category Pages | `importTextFiles.php` | N/A | Categories

Check out the demo wiki content on the NAS at `/shared-data/Mark/demo_wiki_2016-12-21` for an example of these files.

### Loading Images ###

To load a directory of PNGs into the wiki, use this command:
```
$ sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions png <directory of images>
```
E.g., if you are using the preview data from the NAS `<directory of images>` =  `demo_wiki_2016-12-21/renderings/`. Replace `png` with a different image type/extension if you need to upload other kinds of images.

### Loading Page Text ###

To load a directory of only pages into the wiki (no other files, please), use this command:
```
$ find <directory of page text files> -type f | sort -S1G | xargs sudo -u www-data php /var/www/mediawiki/maintenance/importTextFiles.php --overwrite
```
If you are using the preview data from the NAS `<directory of page text files>` = `demo_wiki_2016-12-21/{Paths/,Reachables/,Categories/}`, i.e., you run the command three times.

The Tabs extension we rely on doesn't automatically render the tab assets when using the maintenance script, so we have to force mediawiki to purge its cache and rebuild the page.  Below, you will need the username and password credentials you [created for NGINX above](https://github.com/20n/act/tree/master/wikiServices#set-an-nginx-password).  We can do this via the `api.php` endpoint:
```shell
$ export CRED=<user>:<pass>
$ function rebuild() { for i in $(ls $1); do echo $i; curl --insecure -vvv -X POST "https://${CRED}@localhost/api.php?action=purge&titles=${i}&format=json" 2>&1 | grep "HTTP"; done; }
$ rebuild <directory of page text files>
```
If you are using the preview data from the NAS then rerun `rebuild` with  each of `demo_wiki_2016-12-21/{Paths,Reachables}`. Make sure the output of `rebuild` only output "200 OK" messages.

Complete commands to import preview data into a wiki instance are available in one of the [example sections](#example-loading-the-preview-wiki-content).

You can redirect the output of `curl` to `/dev/null` if you want, but it's good to ensure that some of the requests are working first.

Note that this must be done on the wiki host itself: public access `api.php` is blocked to all traffic sources except `localhost`.

### Loading Protein and DNA design files ###

```
sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions txt Sequences/
```
We use the `importImages` script because we need the DNA and Protein designs to be loaded as attachments. 

### Loading the Wiki Front-Matter ###

There is a directory in this repository called `wiki_front_matter` that contains the main page and assets for our wiki.  Let's install it!

```
# Upload the images.
$ sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions png wiki_front_matter/images
# Disregard any warnings like `PHP Warning:  mkdir(): No such file or directory in /var/www/mediawiki/extensions/GraphViz/GraphViz_body.php on line 1786`.
# One of the images is a JPEG.
$ sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions jpg wiki_front_matter/images

# Upload all the pages.
$ find wiki_front_matter/pages -type f | sort -S1G | xargs sudo -u www-data php /var/www/mediawiki/maintenance/importTextFiles.php --overwrite

# Ensure they're re-rendered.  Don't use find, as we just want the page names.
$ export CRED=<user>:<pass>
for i in $(ls wiki_front_matter/pages); do
  echo $i;
  curl --insecure -vvv -X POST "https://${CRED}@localhost/api.php?action=purge&titles=${i}&format=json" 2>&1 | grep "HTTP";
done
# Make sure all responses codes are "200 OK"
```

The front page should now contain our usual intro page and images.  The `All_Chemicals` list is empty, You should generate a new one based on the data you have (replace `demo_wiki_2016-12-21` with whatever your parent directory is:
```
$ dir="demo_wiki_2016-12-21/Reachables/"
$ for page in `ls $dir`; do molecule=`cat $dir/$page | head -1 | sed 's/=//g'`; echo "[[$page|$molecule]]"; echo; done > wiki_front_matter/pages/All_Chemicals
$ find wiki_front_matter/pages -type f | sort -S1G | xargs sudo -u www-data php /var/www/mediawiki/maintenance/importTextFiles.php --overwrite
```

To edit the side bar content (i.e. to remove `Random Page` and `Recent Changes`), navigate to `/index.php?title=MediaWiki:Sidebar` and edit the source.  You will need to login as wiki_admin (ask SS for password from imp-20n_mdaly_credentials).  Use http://heartofgold.bioreachables.com/index.php?title=MediaWiki:Sidebar as an example of this.  Change it to:
```
* navigation
** mainpage|mainpage-description
** All_Chemicals|List of chemicals
** {{SERVER}}/substructure/|Substructure search
** helppage|help
```

### Category pages

The category pages can be found here (relative to the wiki url, example: `http://wiki/Category_Page_Name`):
```
a) Aroma
b) Analgesic
c) Flavor
d) Monomer
e) Polymer
f) Sigma_Molecules
g) Wikipedia_Molecules
h) Drug_Molecules
```

These urls have to added to the index page of the wiki. 
Edit the main page of the wiki `https://wiki/index.php?title=Main_Page&action=edit` to be the following:

```
Welcome to the 20n Bio-reachables Repository.  Please see the [[Introduction]] for an overview of this repository's contents.

View:
* [[All_Chemicals|Entire bio-reachables set]]

* Or explore molecules by:
** [[Drug_Molecules|Molecules found in DrugBank]]
** [[Sigma_Molecules|Molecules in the Sigma-Aldrich catalog]]
** [[Wikipedia_Molecules|Molecules found in Wikipedia]]

* Or explore by use-cases:
** [[Analgesic|Analgesic Molecules]]
** [[Aroma|Aromatic Molecules]]
** [[Flavor|Flavor Molecules]]
** [[Monomer|Monomer Molecules]]
** [[Polymer|Polymer Molecules]]

[[File:Bio-reachable-chemicals.jpg|600px|center|]]
```

### Example: Loading the Preview Wiki Content ###

This is an example for a limited version of the wiki. You should skip to the next section if you are not installing a "Preview Wiki".

On an office server:
```
$ n=1
# Note no trailing slash on the source directory.
$ rsync -azP /mnt/shared-data/Mark/demo_wiki_2016-12-21 private-${n}-wiki-west:
# Now connect and complete the remaining steps.
$ ssh private-${n}-wiki-west
```

Run these commands on the remote server:
```
# Upload all the molecule and word cloud images.
$ sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions png demo_wiki_2016-12-21/renderings
# Import the DNA designs.  We use `importImages` to make them appear as file uploads as opposed to wiki documents.
$ sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions txt demo_wiki_2016-12-21/Sequences

# Import the pathway pages:
$ find demo_wiki_2016-12-21/Paths -type f | sort -S1G | xargs -n 400 sudo -u www-data php /var/www/mediawiki/maintenance/importTextFiles.php --overwrite

# Import the reachables docs:
$ find demo_wiki_2016-12-21/Reachables -type f | sort -S1G | xargs -n 400 sudo -u www-data php /var/www/mediawiki/maintenance/importTextFiles.php --overwrite
# Invalid cached version of the reachables docs to ensure tabs are rendered correctly:
$ for i in $(ls demo_wiki_2016-12-21/Reachables/); do echo $i; curl --insecure -vvv -X POST "https://localhost:80/api.php?action=purge&titles=${i}&format=json"; done
```

### Making the VM Publicly Accessible ###

The wiki VM we created earlier only has a private IP address, which is fine while for setup.  To make it accessible from outside, you'll need to create and associate a public IP address and change the instance's network security group to one that allows public access on port 80:
```
# Pick your host number.
$ n=1

$ azure network public-ip create --allocation-method Static --name private-${n}-wiki-west2-public-ip --resource-group twentyn-azure-west-us-2 --idle-timeout 4 --location westus2
# IP is allocated!  Now let's associate it with an NIC.

# First, we'll look up the configuration name for the NIC on our wiki host
$ azure network nic ip-config list twentyn-azure-west-us-2 private-${n}-wiki-west2-nic

# The name column says it's `ipconfig1`.  Let's take a closer look.
$ azure network nic ip-config show twentyn-azure-west-us-2 private-${n}-wiki-west2-nic ipconfig1

# We should not see any public IP associated with the NIC at this time.  Let's connect the two!
$ azure network nic ip-config set --public-ip-name private-${n}-wiki-west2-public-ip twentyn-azure-west-us-2 private-${n}-wiki-west2-nic ipconfig1

# Run `show` again to make sure we did the right thing.
$ azure network nic ip-config show twentyn-azure-west-us-2 private-${n}-wiki-west2-nic ipconfig1
# Now there should be a looong resource id in place for the public IP field.

# We're almost done, but our wiki is still closed to the public internet.  Let's change that.
# First, we'll make sure we can see the `twentyn-public-access-wiki-west-us-2-nsg` security group.
$ azure network nsg list twentyn-azure-west-us-2

# If it's there, we can associate it with the wiki's NIC.
$ azure network nic set --network-security-group-name twentyn-public-access-wiki-west-us-2-nsg twentyn-azure-west-us-2 private-${n}-wiki-west2-nic
```

The network security group changes can take a little while to take effect.  You should be able to `curl` the public IP of your wiki instance about 90 seconds after the network security group change completes:
```
# First, let's check what the public IP is (in case we forgot what was printed at allocation time)
$ azure network public-ip show twentyn-azure-west-us-2 private-${n}-wiki-west2-public-ip
...
data:    IP Address                      : 52.183.69.103
...

# There's our IP!  Now let's try to access it.
# We're going to give it a complete mediawiki URL, as it'll try to redirect us to the designated hostname ($wgServer) if we just request /.
$ curl --insecure -vvv https://52.183.69.103/index.php?title=Main_Page
> GET /index.php?title=Main_Page HTTP/1.1
> Host: localhost
> User-Agent: curl/7.47.0
> Accept: */*
>
< HTTP/1.1 200 OK
< Server: nginx/1.10.0 (Ubuntu)
...

# There'll be a bunch of HTML output here.  The 200 status code at the top is really what we're looking for.
```

### Creating a public name for this wiki ###
Use the following instructions to create an appropriately named `A` record that points to this public IP in Route 53 (in AWS):

Go to https://20n.signin.aws.amazon.com/console/ and sign in using MFA (saurabh, ..). Navigate to Route 53 and to the Hosted Zone of `.bioreachables.com`. Within it create a `A` type Record Set, and use the name you used earlier in [NGINX hostname configuration step above](#set-an-nginx-hostname) and set the `A` value to the public IP address recovered earlier.

### Done! ###

If things are working, *STOP HERE*! You have a wiki now, and it has data in it. If the shortcut instructions above don't work, you can setup the wiki from scratch by following the instructions below.  In all likelihood, you should not have to proceed farther than this.

:end: :hand: :stop_sign: :stop_button:

---------
# Appendices

## A1. Remaining TODOs
* Serving multiple host names per instance, with proper URL hostname rewriting in NGINX
* Monitoring
* Backups/disaster recovery
* Anything but trivial ordering capabilities (we just send an email for now)

## A2. Mediawiki Setup from Scratch ##

These instructions shouldn't be strictly necessary, as an EC2 AMI with a complete mediawiki setup is available.  Fall back to these instructions in case we ever need to set it up from scratch.

This assumes the host OS is Ubuntu 14.04LTS or 16.04.

### Install and Set Up MySQL ###

This assumes MySQL will be running on the wiki host, which is fine for our simple, non-redundant setup.  Note that we won't open MySQL's default port to the public Internet--it will only be accessible locally on the wiki host.

Run the following command to install MySQL:
```
$ sudo apt-get install mysql-server
```

This will ask you to set a root password, which (for easy maintenance) should be the same password used for other MySQL instances.  Once the MySQL server is installed and running, create a DB and a mediawiki user.  Use the same `mediawiki` user password as used in other installations (again for easy maintenance).

(Note: I'm doing this from memory, so some syntactic fixes might be necessary.)
```
$ mysql -u root -p
# Enter password when prompted
mysql> create database 20n_wiki;
mysql> create user 'mediawiki'@'localhost';
mysql> set password for 'mediawiki'@'localhost' = PASSWORD('<put password here>');
mysql> grant all privileges on 20n_wiki.* to 'mediawiki'@'localhost';
mysql> flush privileges;
```

The `mediawiki` user now has the access it requires to create all the tables it needs.

### Install PHP ###

Run this command to install all the required PHP packages:
```
pkgs='php php-cli php-common php-fpm php-gd php-json php-mbstring php-mysql php-readline php-wikidiff2 php-xml'
$ echo $pkgs | xargs sudo apt-get install -y
```

This will start a PHP 7.0 FPM service that will accept traffic on a UNIX domain socket in /var/run.  Ensure `/var/run/php/php7.0-fpm.sock` exists or the wiki's PHP processing requests will fail.

The default PHP-FPM configuration should be sufficient for our purposes.  TODO: do we need to harden this?

### Install and Configure NGINX ###

Install NGINX using apt.  **Note that if the firewall or security group rules allow public access to port 80, NGINX will be immediately visible to the public Internet, which we definitely do not want yet.**

```
$ sudo apt-get install nginx
```

We'll enable access to the wiki using the `site-wiki-ssl` file in the `services` directory of this project, but we need to tweak one of the configuration files to get PHP processing working correctly.  Open `/etc/nginx/fastcgi_params` in an editor (as root) and make it look like this if it doesn't already:
```
fastcgi_param  QUERY_STRING       $query_string;
fastcgi_param  REQUEST_METHOD     $request_method;
fastcgi_param  CONTENT_TYPE       $content_type;
fastcgi_param  CONTENT_LENGTH     $content_length;

fastcgi_param  SCRIPT_FILENAME    $request_filename;
fastcgi_param  SCRIPT_NAME        $fastcgi_script_name;
fastcgi_param  REQUEST_URI        $request_uri;
fastcgi_param  DOCUMENT_URI       $document_uri;
fastcgi_param  DOCUMENT_ROOT      $document_root;
fastcgi_param  SERVER_PROTOCOL    $server_protocol;
fastcgi_param  REQUEST_SCHEME     $scheme;

fastcgi_param  GATEWAY_INTERFACE  CGI/1.1;
fastcgi_param  SERVER_SOFTWARE    nginx/$nginx_version;

fastcgi_param  REMOTE_ADDR        $remote_addr;
fastcgi_param  REMOTE_PORT        $remote_port;
fastcgi_param  SERVER_ADDR        $server_addr;
fastcgi_param  SERVER_PORT        $server_port;
fastcgi_param  SERVER_NAME        $server_name;

fastcgi_param  HTTPS              $https if_not_empty;

# PHP only, required if PHP was built with --enable-force-cgi-redirect
fastcgi_param  REDIRECT_STATUS    200;
```

For a reason I don't understand, Ubuntu's NGINX ships without a `SCRIPT_FILENAME` parameter in its `fastcgi_params` file, which results in blank pages appearing when trying to access the wiki.

### Install SSL Certificates for NGINX ###

The instructions for installing SSL certificates for NGINX live in the [service README file](service/README.md#installing-ssl-certificates-for-nginx).  Follow these instructions to allow connections to be protected with SSL.  Note that the `server_name` parameter is changed on a per-instance basis, so there is no need to change it for the default setup.

### Unpack and Set Up Mediawiki ###

First, install imagemagick, which the wiki will use for image manipulation:
```
$ sudo apt-get install imagemagick
```

Mediawiki distributes its software in an easy to install package, so this part is pretty easy.  Download and verify (if you can) a mediawiki distribution and move it to `/var/www/mediawiki`:
```
$ tar zxvf mediawiki-1.27.1.tar.gz
# Make sure /var/www/mediawiki doesn't already exist before doing this: we want to rename mediawiki-1.27.1.
$ sudo mv mediawiki-1.27.1 /var/www/mediawiki
$ sudo chown -R www-data:www-data /var/www/mediawiki
```

You'll also need to install the following extensions into `/var/www/mediawiki/extensions` (and make `www-data` the owner).  I recommend just copying these directories from another wiki instance, as the source code should be identical:
```
GraphViz
iDisplay
Tabs
```
Note that the `Graphviz` extension depends on `ImageMap`, which is installed by default in recent mediawiki distributions.


Now the wiki source is in place, but NGINX doesn't know how to serve it yet.  Follow `site-wiki-ssl` installation instructions in [service/README.md](service/README.md#enabling-reverse-proxy-endpoints-in-nginx).  Once NGINX has reloaded its config, you should be able to get to the wiki in a web browser (at `/`), preferably over a tunnel.  Better still, do the *entire* wiki services setup process now, as everything will work by the time the wiki is up and ready to go.

Mediawiki installation is mostly self explanatory, but make sure to do the following things:
* Specify `20n_wiki` as the DB, or whatever you created during MySQL setup.  `localhost` is the correct DB hostname.
* Use `mediawiki` as the user and the password you set while setting up MySQL.
* Use the default settings on `Database settings`
* Use `20n Wiki` or something similar as the name of the wiki.  This doesn't matter all that much.
* Set a `wiki_admin` user as the administrator with the password used in other wiki installations.  Don't bother with an email address.
* On the `Options` page:
    * Select `Authorized editors only` as the `User rights profile`.
    * Disable the `Enable outbound email` checkbox.
    * In the "Extensions" section, check the boxes next to the three extensions above plus `ImageMap`.
    * **Disable** file uploads, we won't need them.

At the end of the installation process, you'll be asked to download a `LocalSettings.php` file that needs to be dropped into `/var/www/mediawiki`.  Before you copy and move it in place, make the following edits:

Set `$wgLogo` to this value (around line 39):
```php
$wgLogo = "$wgResourceBasePath/resources/assets/20n_small.png";
```
Don't forget to put the image file in place:
```
$ sudo cp /var/www/mediawiki/assets/img/20n_small.png /var/www/mediawiki/resources/assets
sudo chown -R www-data:www-data  /var/www/mediawiki/resources/assets
```

Append the following code to the end of `LocalSettings.php`:
```php
# Prevent file uploads as a hardening measure.
$wgEnableUploads = false;
$wgUseImageMagick = true;
$wgImageMagickConvertCommand = "/usr/bin/convert";

$wgGraphVizSettings -> defaultImageType = "png";
$wgGraphVizSettings -> createCategoryPages = "no";

$wgFileExtensions[] = 'svg';
$wgAllowTitlesInSVG = true;
# Enable this to convert SVGs to PNGs, which isn't always desired.
#$wgSVGConverter = 'ImageMagick';

# Enable these for debugging info
#$wgDebugToolbar = true;
#$wgShowDebug = true;

$wgMaxShellMemory = 33554432;

$wgFileExtensions[] = 'txt';

$wgTrustedMediaFormats[] = 'text/plain';

# Restrict editing and account creation
# See https://www.mediawiki.org/wiki/Manual:Preventing_access

# Disable anonymous editing
$wgGroupPermissions['*']['edit'] = false;

# Prevent new user registrations except by sysops
$wgGroupPermissions['*']['createaccount'] = false;


# Allow for very large images
$wgMaxImageArea = $wgMaxImageArea * 10;

# Don't write parser limit reports in rendered HTML
$wgEnableParserLimitReporting = false;
```

With the security settings added in the above code block, only the administrator can make accounts, and only the administrator (I think?) can make edits--public edits are definitely not allowed.

Now you should be ready to move `LocalSettings.php` to `/var/www/mediawiki/LocalSettings.php` and change its owner to `www-data`.
```
$ sudo mv LocalSettings.php /var/www/mediawiki
$ sudo chown www-data:www-data /var/www/mediawiki/LocalSettings.php
```

One more change needs to be made: in order to make the logo point to `20n.com`, change the logo link in `/var/www/mediawiki/skins/Vector/VectorTemplate.php` (around line 191):
```php
echo htmlspecialchars( 'http://20n.com' )
```
Here's the full patch:
```diff
--- VectorTemplate.php.orig	2017-01-04 18:42:16.872660024 +0000
+++ VectorTemplate.php	2017-01-04 18:42:43.721636871 +0000
@@ -186,11 +186,11 @@
                                         <?php $this->renderNavigation( [ 'VIEWS', 'ACTIONS', 'SEARCH' ] ); ?>
                                 </div>
                         </div>
                         <div id="mw-panel">
                                 <div id="p-logo" role="banner"><a class="mw-wiki-logo" href="<?php
-                                        echo htmlspecialchars( $this->data['nav_urls']['mainpage']['href'] )
+                                        echo htmlspecialchars( 'http://20n.com' )
                                         ?>" <?php
                                         echo Xml::expandAttributes( Linker::tooltipAndAccesskeyAttribs( 'p-logo' ) )
                                         ?>></a></div>
                                 <?php $this->renderPortals( $this->data['sidebar'] ); ?>
                         </div>
```

Now you should be able to access the wiki over an ssh tunnel:
```
$ ssh -L8080:localhost:80 <my-wiki-host>
```
Navigate to `http://localhost:8080/index.php?title=Main_Page` in a web browser and make sure things look sane.

#### Exploring the Wiki Via a Tunnel ####

The default linking mechanism used by mediawiki (frustratingly) rewrites URLs to include the fully qualified hostname.  This can make exploration of the wiki over a tunnel challenging.  You can always access a specific page by entering `http://localhost:8080/index.php?title=<Page Name>` in your browser, substituting `<Page Name>` with the name of the page you're trying to reach.

## A3. Hosting In Azure ##

We take advantage of our Azure sponsorship by running our wiki VMs there.  Note that we still use AWS for DNS, but rely on Azure for all the hosting.

### Creating New Wiki Images ###

While the Azure instance instantiation protocol is fairly straightforward, creating images from scratch is an involved process.  At a high level, the steps are:

1.  Configure an instance with all software and configuration bits in place.
1.  "De-provision" that instance to remove environment-specific configuration data.  **Important:** this does not make the instance ready for use outside of 20n, it just forgets its name and location.  This also renders it impossible to log back into the instance, so make sure things are *really* how you want them.
1.  Deallocate, "generalize," and "capture" the instance to create a template OS disk.
1.  Update our JSON template file with values that reference the newly created OS disk.

So once we have a template wiki set up, the Azure-specific bits are:
* `de-provision`: so the VM forgets who/where it is
* `deallocate+generalize+capture`: so the VM's OS disk image is prepared for reuse
* `update JSON templates`: so `act/scripts/azure/spawn_vm` can make more using the correct disk image.

These instructions will omit the first step, as wiki host setup procedures were documented [in an earlier section](#mediawiki-setup-from-scratch).  For now, we'll assume that a fully configured and functioning wiki instance exists at `private-1-wiki-west2` in the resource group `twentyn-azure-west-us-2`, and we'll proceed from step 2 (de-provisioning) above.

**Hopefully you will never need to create a fresh image, and can instead just reuse the current one.**

Full, official instructions live [here](https://docs.microsoft.com/en-us/azure/virtual-machines/virtual-machines-linux-capture-image).

#### De-provision the Template Instance ####

**Note: the next step makes the host inaccessible via ssh.  Make very sure things are perfect before you start the image creation process.**

Log into the host you wish to replicate and run:
```
$ sudo waagent -deprovision
```
Read and respond to the prompt.  This will make the host forget all of its DNS settings, which makes provisioning as an instance template possible.  Note that you can alternatively run:
```
$ sudo waagent -deprovision+user
```
This will **also eliminate your home directory and user entry.**  Maybe you want to do this for some reason, but for our internal use it's fine to leave your home directory as part of the image.

#### Deallocate, Generalize, Capture ####

We'll run three Azure CLI commands to shut the host down, prep its OS disk for reuse, store that OS disk in a reusable image, and capture the disk's parameters so that we can save it to our own template file.

On your laptop (see the login instructions above if needed):
```
# Shut the host down.
$ azure vm deallocate twentyn-azure-west-us-2 private-1-wiki-west2
# Important: after generalization, you will not be able to boot this host again.  But if you de-provisioned it, you can't log in anyway.
$ azure vm generalize twentyn-azure-west-us-2 private-1-wiki-west2
# Create a `twentyn-wiki` image in Azure's default location for images, and write the configuration info to `twentyn-wiki-image-template.json`.
$ azure vm capture twentyn-azure-west-us-2 private-1-wiki-west2 -p twentyn-wiki -t twentyn-wiki-image-template.json
```

#### Update the Reachables Wiki Template File ####

The file `twentyn-wiki-image-template.json` should now exist, but is likely an un-readable mess of JSON.  But that's okay--we'll use `jq` to extract the bits we need and update our host template accordingly!  This assumes you're in the `act` directory.
```
$ image_name=$(jq '.resources[0].properties.storageProfile.osDisk.name' twentyn-wiki-image-template.json)
$ image_uri=$(jq '.resources[0].properties.storageProfile.osDisk.image.uri' twentyn-wiki-image-template.json)
$ jq ".resources[0].properties.storageProfile.osDisk.name = ${image_name} | .resources[0].properties.storageProfile.osDisk.image.uri = ${image_uri}" scripts/azure/reachables_wiki/template.json > scripts/azure/reachables_wiki/template.json.new
$ mv scripts/azure/reachables_wiki/template.json{.new,}
```

If `jq` complains about any of these steps, stop before you overwrite `scripts/azure/reachables_wiki/template.json`.

Once this is complete, you can commit `scripts/azure/reachables_wiki/template.json` to GH.  Subsequence instances created using `spawn_vm` and the `reachables_wiki` host type should use your new image.

## A4. Alternative Hosting Provider: AWS ##

We started hosting our wikis in EC2, though have since moved to Azure to reduce costs.  This section remains in case we ever want/need to move back to EC2.  Most of AWS's services are fairly self-explanatory; just the same, here is a brief overview of the AWS facilities we're using and how they're configured.

### EC2 ###

We're currently using `t2.medium` instances, which have enough memory to run MySQL, NGINX, and our Java web services fairly comfortably.  Our instances started out with a vanilla Ubuntu 16.04 AMI, but snapshots now exist that should provide a fully configured base wiki image that can be used to create new private, per-customer wiki instances.  Specifically, a new instance created with AMI `reachables-wiki-20161229T1800` should have a full wiki + supporting software stack installed but no reachables data populated: you'll need to upload wiki pages, images (**important**: molecule renderings need to be uploaded to the wiki **and** copied to `/var/www/mediawiki/assets/img`), and a reachables list (see `service/README.md`), but the rest should already be in place.

### Security Groups, Elastic IPs, and DNS ###

By default, EC2 instances will only be accessible from within their VPC (virtual private cloud), a group of instances that AWS groups together for security purposes.  In order to grant network access from other locations, instances must be enrolled in security groups.  During set up, only port 22 (ssh) should be open to anything outside the VPC; the `office-ssh` security group in `us-west-2` opens port 22 only to the office's static IP.  Once setup is complete, the poorly named `wiki-group` security group can be used instead.  This grants public access to port 80 (http) and 22, though the latter can be restricted to specific IPs if necessary.

Security groups can also be used for IP-based whitelisting of clients.  I strongly recommend, however, that whitelisting not be the exclusive access protection mechanism: either basic or certificate-based authentication should also be used to prevent unauthorized access in the event of client IP reallocation.  Also, we should purchase and install SSL certificates on our wiki hosts to protect in-flight traffic--SSL + client certificate authentication is the strongest authentication mechanism we can realistically employ.

Each wiki host is assigned an Elastic IP address, which is static.  Once an instance has been assigned an Elastic IP, an entry can be added in our Route 53 configuration to assign a DNS name to that host.  Create an `A` record that points to that Elastic IP in the `20n.com` record set; in a few hours, the name should propagate to all major public DNS servers.

**Important**: once a DNS name has been assigned to a wiki server, update `$wgServer` in `LocalSettings.php` to reference that name.  Mediawiki has a tendency to rewrite the current URL with its canonical hostname, which may result in unexpected connection failures if the hostname is not updated before clients access the wiki.

### SNS ###

We use AWS's simple notification service (SNS) to send emails when users submit pathway order requests.  These are sent via a single message topic whose ARN (resource id) is stored in `orders_config.json`.  Only the `wiki-order-notification-user` user has privileges to publish to this topic, and its AWS access/secret key must be used when setting up the orders service.  The current wiki AMI already contains credentials for this user, but they are not checked into GitHub.

Users who wish to receive order notification emails must subscribe to the `wiki_order_notifications` topic.  Subscription requests can be sent through the SNS dashboard, and must be confirmed/accepted by each user before further emails will be sent.

## NGINX ##

While the default mediawiki install uses Apache as its web server, our custom setup uses NGINX, a lighter-weight, easy to configure HTTP server and reverse proxy.  The Ubuntu NGINX installation uses a slightly non-standard configuration, where configuration files for virtual servers live in `/etc/nginx/sites-available` and are symlinked into `/etc/nginx/sites-enabled` to activate them.  The `site-wiki-ssl` configuration file in the `services` directory should be copied to `/etc/nginx/sites-available` and symlinked into `/etc/nginx/sites-enabled`; `/etc/nginx/sites-enabled/default` should then be removed (as root) and NGINX reloaded/restarted with `/etc/init.d/nginx reload` to update the configuration.

The `site-wiki-ssl` configuration file enables request rate limiting.  This has not been tested in our setup, but follows the instructions on NGINX's website.

### Basic Authentication ###

By default, the `site-wiki-ssl` configuration enables HTTP basic authorization for any access to the wiki or supporting services.  A default username and password has been established to prevent unauthorized access in the event that a wiki VM is made publicly accessible before it is prepared for client use:
```
username: wiki_test
password: dogwood563{Della
```

This username and password are considered non-sensitive, as they should be replaced before the wiki is made accessible to any client.  If you followed the [setup steps above](#set-an-nginx-password), you would have changed this value before assigning a public IP address to the host.

#### From-scratch Set up ####

Setting up simple username and password authentication in NGINX is very straightforward.  This sort of authentication only makes sense if you protecting in-flight traffic with SSL.  The setup process is for a wiki that has no authentication enabled at all.
```
# Install the htpasswd utility.
$ sudo apt-get install apache2-utils
# Create a password file that nginx will read.
# Note that this doesn't live in /var/www/mediawiki so that it's not publicly accessible.
$ sudo htpasswd -c /etc/nginx/htpasswd <username>
# Enter and confirm a password when prompted
```

The `site-wiki-ssl` NGINX config file should already have basic auth enabled; the change to enable it is outlined in the diff below.  You'll need to choose an *authentication realm* that identifies this wiki so that users who might be looking at multiple wikis won't have the credentials accidentally reused.  Here, the realm is `20n WIki 1`, though you could use anything (like a UUID or some random ASCII identifier).
```diff
--- site-wiki-ssl.orig	2017-01-11 21:24:42.583678198 +0000
+++ site-wiki-ssl	2017-01-11 00:48:05.254290466 +0000
@@ -60,10 +60,13 @@
   root /var/www/mediawiki;

   client_max_body_size 5m;
   client_body_timeout 60;

+  auth_basic "20n Wiki 1";
+  auth_basic_user_file /etc/nginx/htpasswd;
+
   # Substructure search service
   location = /search {
     proxy_set_header   Host             $host:$server_port;
     proxy_set_header   X-Real-IP        $remote_addr;
     proxy_set_header   X-Forwarded-For  $proxy_add_x_forwarded_for;
```

Here's just the text to be added to the `server` config block in `/etc/nginx/sites-available/site-wiki-ssl`:
```
      auth_basic "20n Wiki 1";
      auth_basic_user_file /etc/nginx/htpasswd;
```

Now we'll check that our modification was correct and tell NGINX to reload it's configuration file.
```
$ sudo /etc/init.d/nginx configtest
 * Testing nginx configuration                    [ OK ]
$ echo $?
0
$ sudo /etc/init.d/nginx reload
```

If you now navigate to any wiki page (including over a tunnel) you should not be prompted for a username and password.

### Adding/Updating a Password ###

To change or add a password, just omit the `-c` option to `htpasswd`:
```
$ sudo htpasswd /etc/nginx/htpasswd <username>
```
A password change should not require an NGINX config reload.

### SSL ###

We have purchased a wildcard SSL certificate for `*.bioreachables.com` that allows us to protect in-flight HTTP traffic to our wiki VMs.  The `site-wiki-ssl` config file contains parameters that enable SSL and redirect insecure connections to the SSL virtual server.  The VM image should have SSL certificates/keys and DH parameters installed or created as needed during setup.  Instructions for SSL installation live in the [service README](service/README.md#installing-ssl-certificates-for-nginx).  Note that certificate and DH parameter installation on a VM that will be used as an image for replication does *not* require changes to `site-wiki-ssl`.

:moyai:
