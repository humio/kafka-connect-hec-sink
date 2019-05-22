#!/usr/bin/env bash
rm -rf humio-data
docker-compose -f src/test/resources/docker/docker-compose.yml up
