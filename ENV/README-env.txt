20n STYLE GUIDE
===============
http://docs.scala-lang.org/style/naming-conventions.html

Notes:
1] package com.c20n.act.* — the extra “c” is so that scala does not complain about numeral start
2] 2 space, expand tab 


BUILD SETUP:
============
Homebrew
--------
http://brew.sh/

Scala
------
Download and unzip. sudo mv ~/Downloads/scala-2.11.0 /usr/share/
~/.bash_profile as below

SBT (Scala Build Tool)
----------------------
Use home-brew on maci: brew install sbt. (instructions on http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
(if port needs to update: do “sudo port selfupdate”; and if that fails “sudo xcode-select —install” and agree to “sudo xcodebuild -license”)

~/.bash_profile
---------------
export JAVA_HOME=$(/usr/libexec/java_home)
export EC2_HOME=/usr/local/ec2/ec2-api-tools-1.6.13.0/
export MONGO_HOME=/usr/local/mongo/mongodb-osx-x86_64-2.6.0/
export SCALA_HOME=/usr/share/scala-2.11.0/

export PATH=$PATH:$SCALA_HOME/bin
export PATH=$PATH:$EC2_HOME/bin
export PATH=$PATH:$MONGO_HOME/bin

# These are root keys to our AWS account.
# For other users have them use another account with permissions using AWS IAM
# See imp-sw-infra for details on AWS
export AWS_ACCESS_KEY=XXXXXXXXXXXXXXXXXXXX
export AWS_SECRET_KEY=XXXXXXXXXxxxXXXXXXXXxxxXXXXXXXXXXxxxXXxX

# EC2 US West (Northern California) Region
export EC2_URL=https://ec2.us-west-1.amazonaws.com
