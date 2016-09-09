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

## Starting and stopping VMs.