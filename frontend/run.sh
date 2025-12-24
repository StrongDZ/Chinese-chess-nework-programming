#!/bin/bash

# Script to run the Chinese Chess frontend application

cd "$(dirname "$0")"

echo "Building and running Chinese Chess Frontend..."
mvn clean javafx:run

