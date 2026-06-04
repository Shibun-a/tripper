#!/usr/bin/env bash

#./scripts/support/check_env.sh


echo "Running web profile: go to http://localhost:8747"

#export SPRING_PROFILES_ACTIVE=neo
./mvnw -Dmaven.test.skip=true spring-boot:run
