# Azure Utilities

This collection of scripts and config files can be used to automate the setup of azure VMs.

Get started by running the following commands on your mac:
```Shell
$ brew install azure-cli
$ azure login
$ azure config mode arm
```
Follow the prompts to authenticate your host via a web browser.

Each of the script contains documentation regarding its usage.


## SSH Configuration

The `twentyn-worker` VM cluster is protected behind a "bastion" host,
which obviates the need for machines with potentially sensitive data
and code to be directly accessible via the public Internet (we do a
similar thing with the office network, where only orval can be
accessed from the Internet).  Bastions are common in large host
deployments, and become completely transparent with the addition of
some simple ssh configuration directives.

The bastion for the `twentyn-worker` cluster is named
`twentyn-bastion-1`.  However, this hostname does not appear in any
public DNS records (this is intentional: only we need to know what
this host is and what it does).  We can ssh to the bastion by IP, and
then connect to any of the worker nodes by name--azure's internal DNS
registers our worker hosts automatically as they are created.  We can
also tell ssh to connect to the hosts in azure via a proxy: ssh will
connect to the bastion and then make a second "hop" to the destination
host based on the name of the final target.

Add this to your ssh config to enable transparent ssh-ing through the bastion hosts:
```
Host twentyn-*
  ProxyCommand ssh 13.89.34.25 -W %h:%p
  ServerAliveInterval 30
  ForwardAgent Yes

Host *-west2
  ProxyCommand ssh 13.66.211.16 -W %h:%p
  ServerAliveInterval 30
  ForwardAgent Yes

Host *-scus
  ProxyCommand ssh 13.65.25.6 -W %h:%p
  ServerAliveInterval 30
  ForwardAgent Yes
```

If your local username is not the same as the one you use on remote
servers (which is usually the same as your email address), add a
`User <username>` directive to each of these config blocks with the
correct value set for `<username>`.

Note that if the bastion host's public IP changes, this will need to
be updated.

### Naming conventions

With the exception of `twentyn-` hosts, I've adopted the convention of
suffixing each host's names with a region identifier for easier ssh
connectivity through a bastion.  I've selected shortened region names
as the suffixes (i.e. `west2` for `west-us-2` and `scus` for the
incredibly verbose `south-central-us`).  The ssh configuration can
pattern-match on the host names and select the appropriate tunneling
command.  This is a commonly used convention, though it's usually done
with separating the name and region/subnet rather than dashes; alas,
azure does not allow the use of dots in hostnames when using their
internal DNS.

## Web browser proxy configuration

Connecting to azure via ssh is made easy with ssh config files, but
doing the same with a browser is slightly more complicated.  We would
like to be able to transparently connect to any host in a particular
Azure region without having to create a new ssh tunnel for every host
and port.  Fortunately, we can open an ssh tunnel that points at a
remote HTTP proxy process, which can perform DNS lookups and traffic
proxying on the *remote* side of a tunnel.  We can convince our
browser to direct traffic through this tunnel by installing a
proxy-autoconfig file on our local machines.

On OS X, navigate to `System Preferences -> Network -> Advanced -> Proxies`,
check the box next to "Automatic Proxy Configuration," and input a URL
(i.e. an absolute path beginning with `file:///`) to the `proxy.pac`
file in this repository.  Once you click `OK` and `Apply`, your
browser will attempt to pattern match host names against the same
naming conventions used for ssh connectivity, and will direct traffic
to a known port as appropriate.  Now all we need is a tunnel!

**Important**: Once you've set your proxy configuration to use
`proxy.pac`, *don't move/rename/delete that file!* Your machine will
rely on the absolute path of `proxy.pac` being stable and the file
being consistently available in order to enforce correct proxying
rules.

As specified in the `proxy.pac` file, your browser will use certain
ports to attempt to proxy traffic into Azure.  You can set up tunnels
to each region like so (assuming you have acces to our private DNS
server, such as when you are in the office or connected to the VPN):
```
# Open a tunnel to central-us, for connecting to twentyn-* hosts
$ ssh -L 20141:127.0.0.1:3128 azure-central-us
# Open a tunnel to west-us-2 hosts, for connecting to Spark
$ ssh -L 20142:127.0.0.1:3128 azure-west-us-2
# Open a tunnel to south-central-us hosts, for connecting to a
# GPU-enabled host
$ ssh -L 20143:127.0.0.1:3128 azure-south-central-us
```

If it becomes convenient to do so, we can use `autossh` to establish
and maintain these connections persistently in the background.

## Basic VM organization

Azure VMs are organized into resource groups, which are arbitrary
collections of machines.  We currently use only three resource groups:
```
twentyn-azure-central-us
twentyn-azure-west-us-2
twentyn-azure-south-central-us
```

Each resource group is confined to a location; the
`twentyn-azure-central-us` group lives in `centralus`.  Locations
determine the cost of VM time, as well as the size of the pool of
available hardware resources.

To see the set of available locations, run
```
$ azure location list
```

To see what VM sizes are available within a region and what our
resource quotas are for that region, run:
```
$ azure vm sizes --location centralus
$ azure quotas show centralus
```

Note that quotas are per region and can be increased (assuming
resources are available) within ~24 hours by contacting Azure support
through the web panel.

Currently, we use `central-us` for "bursty" allocations, where some
tens of hosts must be spun up at a time to run simple command line
utilities.  There is no regional cost savings for `central-us`, so we
use it for short-lived (on the order of hours or days) computations
only.

The `west-us-2` region offers lower prices than other regions, and so
is a good place for longer-running hosts like Spark clusters.  Hosts
in `west-us-2` should still be deallocated when not in use, but the
cost savings between this region and others is material.

`south-central-us` is the only US region that has GPU-enabled VMs.
Our quota in this region has not been raised above the default, so we
can at most have one very powerful GPU-enabled host in `south-central-us`.

## Starting and stopping existing VMs

Azure VMs have a number of operational states representing different
levels of activity and different overheads in preparing the VM for
service:

State | Description | Availability | Billed for time
--- | --- | --- | ---
Running | Host is operating. | Immediate | Yes
Stopped | Host is shutdown at the software layer | After boot cycle (somewhat fast) | Yes
Deallocated | Host has been shutdown and its resources returned to the pool | After allocation and boot (very slow) | No

The Azure CLI tools can be used to report and set these states:
```
$ azure vm list
$ azure vm start twentyn-azure-central-us twentyn-worker-2
$ azure vm stop twentyn-azure-central-us twentyn-worker-2
$ azure vm deallocate twentyn-azure-central-us twentyn-worker-2
```

**Important**: Stopping or deallocating a VM will wipe its ephemeral
drive, which lives at `/mnt`.  *Data lost on an ephemeral drive is
absolutely non-recoverable.* Use `/mnt` only for genuinely temporary
data (like Spark work dirs).  Additional disks can be created and
attached if the instance's default storage capacity is insufficient.

The boot disk for a host, however, will survive so long as the VM is
not deleted.  (We may be charged for that storage, but the boot disks
are only a few GB so the cost will be small.)  When a host is not in
use, it should be deallocated to halt billing; starting the host will
attempt to acquire the necessary resources and start the machine.

## Resizing an existing VM

Assuming capacity is available and quotas are not exceeded, a VM can
be resized, giving it more or less CPU and memory capacity as needed.

**Important**: A VM must be stopped before it can be resized.
Resizing a running host will cause it to stop, erasing all data on
`/mnt`.

The Azure CLI tools can be used to resize a VM:
```
azure vm show twentyn-azure-central-us twentyn-worker-2
azure vm stop twentyn-azure-central-us twentyn-worker-2
azure vm set -g twentyn-azure-central-us --vm-size Standard_DS12_v2 -n twentyn-worker-2
azure vm start twentyn-azure-central-us twentyn-worker-2
```

Note that these commands explicitly stop and then start the VM being
resized to make it clear what the `set` command will do implicitly.
It also seems to be slightly faster to run the commands separately
(particularly if they are to be run on many machines in parallel), but
YMMV.

## Creating and setting up VMs

TODO

For now, ask Mark for assistance.  There may already be machines available for you.

## Connecting to Azure VMs
