## Mediawiki Web Services ##

This directory contains source and config files for web services that support our mediawiki installation.  These should only be enabled on **private** wiki installations, not on the public preview wiki.  Private wikis should be created by launching new instances of existing wiki AMIs (in EC2).  If complete installation instructions are required, see `service/README.md`.  Please read that document for additional information about resource placement.

Still TODO:
* Authentication, basic or certificate based
* Serving multiple host names per instance, with proper URL hostname rewriting in nginx
* Monitoring
* Backups/disaster recovery
* Anything but trivial ordering capabilities (we just send an email for now)

## New Wiki Instance Setup Steps ##

### Update LocalSettings.php ###

The `reachables-wiki` AMIs in EC2 (TODO: add more info for Azure) contain a full Mediawiki, MySQL, nginx, and web services stack necessary to run a private wiki instance for a single client.  Only a few configuration changes are need to be made to prepare a wiki for client use; most of the work will involve loading data into the wiki--see the section on [Loading Data into the Wiki](#loading-data-into-the-wiki).

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

Another *important manual step* is to set `$wgServer` to the appropriate base URL for all wiki links.  Once a DNS name has been assigned to a wiki server, update `$wgServer` in `LocalSettings.php` to reference that name.  Mediawiki has a tendency to rewrite the current URL with its canonical hostname, which may result in unexpected connection failures if the hostname is not updated before clients access the wiki.

Note that if you are using SSL to encrypt traffic to the wiki, use `https` as the protocol for `$wgServer`.  This will ensure all URL rewrites force secure HTTP.

### Page Generation and Loading Workflow ###

TODO: complete this once the remaining wiki workflow PRs are merged.

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
echo $pkgs | xargs sudo apt-get install -y
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

Now the wiki source is in place, but nginx doesn't know how to serve it yet.  Follow the `site-wiki` installation instructions in `service/README.md` (under the heading "Enabling Reverse-Proxy Endpoints in Nginx").  Once nginx has reloaded its config, you should be able to get to the wiki in a web browser (at `/`), preferably over a tunnel.  Better still, do the *entire* wiki services setup process now, as everything will work by the time the wiki is up and ready to go.

Mediawiki installation is mostly self explanatory, but make sure to do the following things:
* Specify `20n_wiki` as the DB, or whatever you created during MySQL setup.  `localhost` is the correct DB hostname.
* Use `mediawiki` as the user and the password you set while setting up MySQL.
* Use the default settings on `Database settings`
* Use `20n Wiki` or something similar as the name of the wiki.  This doesn't matter all that much.
* Set a `wiki_admin` user as the administrator with the password used in other wiki installations.  Don't bother with an email address.
* On the `Options` page:
** Select `Authorized editors only` as the `User rights profile`.
** Disable the `Enable outbound email` checkbox.
** In the "Extensions" section, check the boxes next to the three extensions above plus `ImageMap`.
** **Disable** file uploads, we won't need them.

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


### Loading Data into the Wiki ###

All of the content in the wiki will be uploaded using maintenance scripts.  These scripts are easy to use and fairly quick to run.

#### Loading Images ####

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

## Azure ##



## AWS ##

We started hosting our wiis in EC2, though have since moved to Azure to reduce costs.  This section remains in case we ever want/need to move back to EC2.  Most of AWS's services are fairly self-explanatory; just the same, here is a brief overview of the AWS facilities we're using and how they're configured.

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
