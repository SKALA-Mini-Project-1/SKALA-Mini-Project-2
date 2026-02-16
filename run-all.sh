#!/bin/bash

docker-compose up -d

SERVER_PORT=8081 ./gradlew bootRun &
SERVER_PORT=10010 ./gradlew bootRun &
SERVER_PORT=10011 ./gradlew bootRun &
SERVER_PORT=10012 ./gradlew bootRun &

