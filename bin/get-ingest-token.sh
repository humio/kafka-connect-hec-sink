#!/usr/bin/env bash
java -cp target/kafka-connect-hec-sink-1.0-SNAPSHOT-jar-with-dependencies.jar:target/test-classes com.humio.connect.hec.IngestTokenExtractor ${1}
