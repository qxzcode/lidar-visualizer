#!/bin/zsh
clear
rm bin/*.class
$JAVA_HOME/bin/javac -d bin/ src/*.java && cp -r data/ bin/ && cd bin/ && java Display
