#!/bin/sh

if [ "$#" -ne 1 ] || [ -d "$1" ]; then
    echo "Usage: $0 dst_dir (dst_dir cannot be an existing dir)" >&2
    exit -1
fi

# create root directory of project and copy the build.sbt to it
mkdir $1
DST_DIR=$1
TEMPLATE_DIR=`dirname $0`
cp $TEMPLATE_DIR/build.sbt.template $DST_DIR/build.sbt
echo "Please modify $DST_DIR/build.sbt to your project's name"

# create directory structure (from http://www.scala-sbt.org/release/docs/Getting-Started/Directories.html)
mkdir $DST_DIR/src/main/resources/
mkdir $DST_DIR/src/main/scala/
mkdir $DST_DIR/src/main/java/

mkdir $DST_DIR/src/test/resources/
mkdir $DST_DIR/src/test/scala/
mkdir $DST_DIR/src/test/java/

mkdir $DST_DIR/lib/
mkdir $DST_DIR/target/
mkdir $DST_DIR/project/

echo "You can now put your sources under $DST_DIR/src/main/scala/ or java/"
echo "    and your tests under $DST_DIR/src/test/scala/ or java/"
echo "    and either batch mode 'sbt clean compile \"dotest argx argy\"'"
echo "    or continuous build test 'sbt -> ~ ;compile ;run arg'"

