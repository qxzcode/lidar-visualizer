#!/bin/zsh
clear
rm *.class
$JAVA_HOME/bin/javac Display.java && $JAVA_HOME/bin/jar cvfe program.jar Display *_proc.txt *.class
