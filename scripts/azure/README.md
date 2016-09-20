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

Add this to your ssh config to enable transparent ssh-ing through the bastion host:
```
Host twentyn-worker-*
  ProxyCommand ssh 13.89.34.25 -W %h:%p
  ServerAliveInterval 30
  ForwardAgent Yes
```

Note that if the bastion host's public IP changes, this will need to
be updated.

## Basic VM organization

Azure VMs are organized into resource groups, which are arbitrary
collections of machines.  We currently use only one resource group:
`twentyn-azure-central-us`.  Each resource group is confined to a
location; our resource group lives in `centralus`.  Locations
determine the cost of VM time, as well as the size of the
pool of available hardware resources.

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
