#!/usr/bin/env bash
set -e
rm -rf build
chmod +x gradlew
./gradlew distZip --refresh-dependencies
cp build/distributions/*.zip ../../files/advantech.zip
