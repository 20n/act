20n STYLE GUIDE
===============
http://docs.scala-lang.org/style/naming-conventions.html

Notes:
1] package com.act.*
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
Use home-brew on mac: brew install sbt. (instructions on http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
(if port needs to update: do “sudo port selfupdate”; and if that fails “sudo xcode-select —install” and agree to “sudo xcodebuild -license”)

Casbah (Scala Mongo driver wrapper)
-----------------------------------
To build.sbt: libraryDependencies += "org.mongodb" %% "casbah" % "2.7.1"

~/.bash_profile
---------------
export JAVA_HOME=$(/usr/libexec/java_home)
export EC2_HOME=/usr/local/ec2/ec2-api-tools-1.6.13.0/
export MONGO_HOME=/usr/local/mongo/mongodb-osx-x86_64-2.6.0/
export SCALA_HOME=/usr/share/scala-2.11.0/

export SBT_OPTS="-Xmx2G"

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

vim
---
https://github.com/scala/scala-dist/tree/master/tool-support/src/vim
~/.vim/ftdetect/scala.vim --- What files are scala files? (= ends with .scala)
~/.vim/indent/scala.vim --- indentation 
~/.vim/syntax/scala.vim --- Syntax highlighting

.vimrc:
    set nocompatible "Not vi compativle (Vim is king)
    
    """"""""""""""""""""""""""""""""""
    " Syntax and indent
    """"""""""""""""""""""""""""""""""
    syntax on " Turn on syntax highligthing
    set showmatch  "Show matching bracets when text indicator is over them
    
    colorscheme delek
    
    " Switch on filetype detection and loads 
    " indent file (indent.vim) for specific file types
    filetype indent on
    filetype on
    set autoindent " Copy indent from the row above
    set si " Smart indent
    
    """"""""""""""""""""""""""""""""""
    " Some other confy settings
    """"""""""""""""""""""""""""""""""
    " set nu " Number lines
    set hls " highlight search
    set lbr " linebreak
    
    " Use 2 space instead of tab during format
    set expandtab
    set shiftwidth=2
    set softtabstop=2
