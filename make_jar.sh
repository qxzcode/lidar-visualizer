#!/bin/zsh
clear
rm bin/*.class
mkdir tmp
$JAVA_HOME/bin/javac -d bin/ src/*.java &&
cp -r bin/* tmp/ &&
cd tmp &&
$JAVA_HOME/bin/jar cvfe ../program.jar Display data/*.txt *.class

cd ..
rm -r tmp/
