## Mediawiki Web Services ##

This directory contains source and config files for web services that support our mediawiki installation.  These should only be enabled on **private** wiki installations, not on the public preview wiki.  Private wikis should be created by launching new instances of existing wiki AMIs (in EC2).  If complete installation instructions are required, see `service/README.md`.  Please read that document for additional information about resource placement.

Still TODO:
* Serving multiple host names per instance, with proper URL hostname rewriting in nginx
* Monitoring
* Backups/disaster recovery
* Anything but trivial ordering capabilities (we just send an email for now)

## Wiki Content Generation ##

If you need fresh wiki content generated prior to loading, start this process in the background while you set up a new VM--it will likely take longer than the VM setup process.

TODO: complete this once the remaining wiki workflow PRs are merged.

## New Wiki Instance Setup Steps ##

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

### Set an Nginx Password ###

Assuming you have SSL enabled, you'll want to protect the wiki with a username and password.  Full instructions are [in this section](#addingupdating-a-password), but you can just run this command:
```
$ sudo htpasswd -c /etc/nginx/htpasswd <username>
```
and input a password.  This will wipe out the existing username and password and replace it with whatever you specify--it will be the *only* username/password combination that will work.

### Update LocalSettings.php ###

The `reachables-wiki` Azure VM images and EC2 AMIs contain a full Mediawiki, MySQL, nginx, and web services stack necessary to run a private wiki instance for a single client.  Only a few configuration changes are need to be made to prepare a wiki for client use; most of the work will involve loading data into the wiki--see the section on [Loading Data into the Wiki](#loading-data-into-the-wiki).

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

Another *important manual step* is to set `$wgServer` to the appropriate base URL for all wiki links.  Update `$wgServer` in `LocalSettings.php` to reference whatever name you intend to assign to this VM in DNS.  *You need not assign the DNS name before you set this variable*: this is what mediawiki will use for its own URLs, and will be used even if you access the host via some other name or IP address.  Mediawiki has a tendency to rewrite the current URL with its canonical hostname, which may result in unexpected connection failures if the hostname is not updated before clients access the wiki.

Note that if you are using SSL to encrypt traffic to the wiki, use `https` as the protocol for `$wgServer`.  This will ensure all URL rewrites force secure HTTP (though nginx should already be doing this if SSL is configured correctly).

### Set Orders Service Client Key ###

The `/order` service endpoint uses a host-specific key to identify where an order request came from.  You'll need to update the `client_keyword` parameter in `/etc/wiki_web_services/orders_config.json` to something that represents the client for whom the wiki is being set up (could be a name or a codeword).  Here are the commands you'll run:
```
$ sudo vim /etc/wiki_web_services/orders_config.json
# Make your edits.  Then restart the service.
$ sudo /etc/init.d/orders_service restart
```
for the config change to take place.

### Moving Files to the Wiki Host ###

Now, what remains is to move data (generated locally, using [these instructions]() below) to the server, and we'll then use that data to populate the wiki. For example, for the preview wiki, the data is at `NAS/MARK_WILL_DIG_THIS_UP`

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

Note that running `rsync` from a `screen` session when copying many files is perilous: once you disconnect from `screen`, `rsync` and `ssh` will no longer have access to your `ssh agent`, and so will be unable to create new connections to the remote host.  Moving single large files (like `tar` files) is fine in screen, however.

### Create, upload, and Install a Reachables ###

The substructure search and orders services require a static TSV of reachable molecules in order to do their jobs.  This needs to be exported from the *same reachables collection* as was used to generate the wiki pages (to be in sync) using a class in the `reachables` project and installed on the wiki host.  There's some documentation in the [service README](./service#export-a-list-of-reachables), but here's a quick set of commands to run.

```
# Run this command on the server where the MongoDB instance with the Reachables collection lives.
$ sbt 'runMain act.installer.reachablesexplorer.WikiWebServicesExporter -c <reachables collection> -o reachables.out'
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

Here's the appropriate maintenance script to use when loading each type of content:
Content/directory | Maintenance Script | Extensions
----------------- | ------------------ | ---------------
Reachables | `importTextFiles.php` | N/A
Pathways | `importTextFiles.php` | N/A
DNA Designs | `importImages.php` | txt
Renderings/word clouds | `importImages.php` | png

### Loading Images ###

To load a directory of PNGs into the wiki, use this command:
```
$ sudo -u www-data php /var/www/mediawiki/maintenance/importImages.php --overwrite --extensions png <directory of images>
```

Replace `png` with a different image type/extension if you need to upload other kinds of images.

#### Loading Page Text ####

To load a directory of only pages into the wiki (no other files, please), use this command:
```
$ find <directory of page text files> -type f | sort -S1G | xargs sudo -u www-data php /var/www/mediawiki/maintenance/importTextFiles.php --overwrite
```

The Tabs extension we rely on doesn't automatically render the tab assets when using the maintenance script, so we have to force mediawiki to purge its cache and rebuild the page.  We can do this via the `api.php` endpoint:
```shell
for i in $(ls <directory of pages>); do
  echo $i;
  curl -vvv -X POST "http://localhost/api.php?action=purge&titles=${i}&format=json";
done
```

You can redirect the output of `curl` to `/dev/null` if you want, but it's good to ensure that some of the requests are working first.

Note that this must be done on the wiki host itself: public access `api.php` is blocked to all traffic sources except `localhost`.

#### Example: Loading the Wiki Front-Matter ####

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
for i in $(ls wiki_front_matter/pages); do
  echo $i;
  curl -vvv -X POST "http://localhost/api.php?action=purge&titles=${i}&format=json";
done
```

The front page should now contain our usual intro page and images.  The `All_Chemicals` list is empty, but can be populated and re-uploaded in the same way.

To edit the side bar content (i.e. to remove `Random Page` and `Recent Changes`), navigate to `/index.php?title=MediaWiki:Sidebar` and edit the source.  Use http://preview.bioreachables.20n.com/index.php?title=MediaWiki:Sidebar as an example of this.


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
$ curl -vvv http://52.183.69.103/index.php?title=Main_Page
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

Now go to Route 53 (in AWS) and create an appropriately named `A` record that points to this public IP.

### Done! ###

If things are working, *STOP HERE*! You have a wiki now, and it has data in it. If the shortcut instructions above don't work, you can setup the wiki from scratch by following the instructions below.  In all likelihood, you should not have to proceed farther than this.

:end: :hand: :stop_sign: :stop_button:

---------

## Mediawiki Setup from Scratch ##

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

Install nginx using apt.  **Note that if the firewall or security group rules allow public access to port 80, nginx will be immediately visible to the public Internet, which we definitely do not want yet.**

```
$ sudo apt-get install nginx
```

We'll enable access to the wiki using the `site-wiki` file in the `services` directory of this project, but we need to tweak one of the configuration files to get PHP processing working correctly.  Open `/etc/nginx/fastcgi_params` in an editor (as root) and make it look like this if it doesn't already:
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

For a reason I don't understand, Ubuntu's nginx ships without a `SCRIPT_FILENAME` parameter in its `fastcgi_params` file, which results in blank pages appearing when trying to access the wiki.

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


Now the wiki source is in place, but nginx doesn't know how to serve it yet.  Follow the `site-wiki` installation instructions in `service/README.md` (under the heading "Enabling Reverse-Proxy Endpoints in Nginx").  Once nginx has reloaded its config, you should be able to get to the wiki in a web browser (at `/`), preferably over a tunnel.  Better still, do the *entire* wiki services setup process now, as everything will work by the time the wiki is up and ready to go.

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

## Hosting In Azure ##

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

## Alternative Hosting Provider: AWS ##

We started hosting our wikis in EC2, though have since moved to Azure to reduce costs.  This section remains in case we ever want/need to move back to EC2.  Most of AWS's services are fairly self-explanatory; just the same, here is a brief overview of the AWS facilities we're using and how they're configured.

### EC2 ###

We're currently using `t2.medium` instances, which have enough memory to run MySQL, nginx, and our Java web services fairly comfortably.  Our instances started out with a vanilla Ubuntu 16.04 AMI, but snapshots now exist that should provide a fully configured base wiki image that can be used to create new private, per-customer wiki instances.  Specifically, a new instance created with AMI `reachables-wiki-20161229T1800` should have a full wiki + supporting software stack installed but no reachables data populated: you'll need to upload wiki pages, images (**important**: molecule renderings need to be uploaded to the wiki **and** copied to `/var/www/mediawiki/assets/img`), and a reachables list (see `service/README.md`), but the rest should already be in place.

### Security Groups, Elastic IPs, and DNS ###

By default, EC2 instances will only be accessible from within their VPC (virtual private cloud), a group of instances that AWS groups together for security purposes.  In order to grant network access from other locations, instances must be enrolled in security groups.  During set up, only port 22 (ssh) should be open to anything outside the VPC; the `office-ssh` security group in `us-west-2` opens port 22 only to the office's static IP.  Once setup is complete, the poorly named `wiki-group` security group can be used instead.  This grants public access to port 80 (http) and 22, though the latter can be restricted to specific IPs if necessary.

Security groups can also be used for IP-based whitelisting of clients.  I strongly recommend, however, that whitelisting not be the exclusive access protection mechanism: either basic or certificate-based authentication should also be used to prevent unauthorized access in the event of client IP reallocation.  Also, we should purchase and install SSL certificates on our wiki hosts to protect in-flight traffic--SSL + client certificate authentication is the strongest authentication mechanism we can realistically employ.

Each wiki host is assigned an Elastic IP address, which is static.  Once an instance has been assigned an Elastic IP, an entry can be added in our Route 53 configuration to assign a DNS name to that host.  Create an `A` record that points to that Elastic IP in the `20n.com` record set; in a few hours, the name should propagate to all major public DNS servers.

**Important**: once a DNS name has been assigned to a wiki server, update `$wgServer` in `LocalSettings.php` to reference that name.  Mediawiki has a tendency to rewrite the current URL with its canonical hostname, which may result in unexpected connection failures if the hostname is not updated before clients access the wiki.

### SNS ###

We use AWS's simple notification service (SNS) to send emails when users submit pathway order requests.  These are sent via a single message topic whose ARN (resource id) is stored in `orders_config.json`.  Only the `wiki-order-notification-user` user has privileges to publish to this topic, and its AWS access/secret key must be used when setting up the orders service.  The current wiki AMI already contains credentials for this user, but they are not checked into GitHub.

Users who wish to receive order notification emails must subscribe to the `wiki_order_notifications` topic.  Subscription requests can be sent through the SNS dashboard, and must be confirmed/accepted by each user before further emails will be sent.

## Nginx ##

While the default mediawiki install uses Apache as its web server, our custom setup uses nginx, a lighter-weight, easy to configure HTTP server and reverse proxy.  The Ubuntu nginx installation uses a slightly non-standard configuration, where configuration files for virtual servers live in `/etc/nginx/sites-available` and are symlinked into `/etc/nginx/sites-enabled` to activate them.  The `site-wiki` configuration file in the `services` directory should be copied to `/etc/nginx/sites-available` and symlinked into `/etc/nginx/sites-enabled`; `/etc/nginx/sites-enabled/default` should then be removed (as root) and nginx reloaded/restarted with `/etc/init.d/nginx reload` to update the configuration.

The `site-wiki` configuration file enables request rate limiting.  This has not been tested in our setup, but follows the instructions on nginx's website.

### Basic Authentication ###

Setting up simple username and password authentication in nginx is very straightforward.  This sort of authentication only makes sense if you protecting in-flight traffic with SSL.  The setup process is for a wiki that has no authentication enabled at all.
```
# Install the htpasswd utility.
$ sudo apt-get install apache2-utils
# Create a password file that nginx will read.
# Note that this doesn't live in /var/www/mediawiki so that it's not publicly accessible.
$ sudo htpasswd -c /etc/nginx/htpasswd <username>
# Enter and confirm a password when prompted
```

Now we'll update the nginx config file at `/etc/nginx/sites-available/site-wiki` to use require basic authentication for all wiki links.  You'll need to choose an *authentication realm* that identifies this wiki so that users who might be looking at multiple wikis won't have the credentials accidentally reused.  Here, the realm is `20n WIki 1`, though you could use anything (like a UUID or some random ASCII identifier).
```diff
--- site-wiki.orig	2017-01-06 23:46:58.199008128 +0000
+++ site-wiki	2017-01-06 23:50:33.516182634 +0000
@@ -16,20 +16,23 @@
   # http://askubuntu.com/questions/134666/what-is-the-easiest-way-to-enable-php-on-nginx
   # https://www.nginx.com/resources/wiki/start/topics/recipes/mediawiki/

   # Note that we also host some static content from the mediawiki directory.
   # This is a little sketchy, but I think it's better than
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
     proxy_pass         http://localhost:8888; # Shouldn't be publicly accessible.
     proxy_redirect     default;

     break;
     # Break required to prevent additional processing by other location blocks.
```

Here's just the text to be added to the `server` config block in `/etc/nginx/sites-available/site-wiki`:
```
      auth_basic "20n Wiki 1";
      auth_basic_user_file /etc/nginx/htpasswd;
```

Now we'll check that our modification was correct and tell nginx to reload it's configuration file.
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
A password change should not require an nginx config reload.

:moyai:
