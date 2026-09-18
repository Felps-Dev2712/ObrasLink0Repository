#!/bin/sh
set -eu
classpath=$(cat target/classpath.txt)
jshell --class-path "target/classes:$classpath" scripts/normalizar-cadastros.jsh
