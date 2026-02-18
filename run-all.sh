#!/bin/bash

set -e

cd backend
SERVER_PORT=8081 ./gradlew bootRun &
SERVER_PORT=10010 ./gradlew bootRun &