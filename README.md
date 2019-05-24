# Humio HEC Kafka Connector

## Introduction

This guide provides step-by-step guidance on how to build, integrate and operate the Humio HEC connector within the Kafka platform.

The purpose of the Humio HEC Sink connector is to read messages from a Kafka topic and submit them as events to the [HTTP event collector endpoint](https://docs.humio.com/integrations/data-shippers/hec/) of a running Humio system.

## Resources

* [HEC Sink Connector GitHub Repository](https://github.com/humio/kafka-connect-hec-sink)
* [Confluent Kafka Connect Documentation](https://docs.confluent.io/current/connect/index.html)
* [Humio HEC Endpoint Documentation](https://docs.humio.com/integrations/data-shippers/hec/)

## Installation

### Version Support

The Humio HEC connector uses maven to build and test itself.  The version of Kafka to build for is indicated in the `pom.xml` file by the line:

```
<kafka.version>2.2.0</kafka.version>
```

Out of the box, Kafka 2.2.0 is supported.  This can (and should) be changed to match your current Kafka or Confluent Platform version; to check which version this is, refer to the [Confluent Platform Versions](https://docs.confluent.io/current/installation/versions-interoperability.html) page.

Scripts are provided to automatically build and package the connector jar.  `bin/compile.sh` automatically compiles and packages the connector, with the resulting "uber jar" located at `target/kafka-connect-hec-sink-1.0-SNAPSHOT-jar-with-dependencies.jar`.  Alternatively, you can run:

```
mvn -DskipTests=true clean install
mvn -DskipTests=true assembly:assembly -DdescriptorId=jar-with-dependencies
```

### Installing on "plain Kafka"

To install the connector for "plain Kafka", copy the uber JAR `kafka-connect-hec-sink-1.0-SNAPSHOT-jar-with-dependencies.jar` into `KAFKA_HOME/libs/` folder.  Set your configuration properties in `KAFKA_HOME/config/connect-distributed.properties` and `KAFKA_HOME/config/connect-standalone.properties`.

### Installing on the Confluent Platform

To install the connector for the Confluent platform, build the uber JAR and copy it into the proper directory, e.g.,

```
mkdir /CONFLUENT_HOME/share/java/kafka-connect-hec-sink
cp target/kafka-connect-hec-sink-1.0-SNAPSHOT-jar-with-dependencies.jar /CONFLUENT_HOME/share/java/kafka-connect-hec-sink/.
```

See the [Install Connectors](https://docs.confluent.io/current/connect/managing/install.html) Confluent page for more information.

## Configuration

For an example configuration using standalone mode, refer to `config/HECSinkConnector.properties`, and for distributed mode, refer to `src/test/resources/config/json_sink_connector.json`.

### Configuration Properties

Property | Description
-------- | -----------
`humio.hec.url` |  URL to the Humio HEC endpoint, e.g., `http://machine:3000/api/v1/ingest/hec`.  This configuration element must be a non-empty string and is __required__.
`humio.repo` | name of the Humio repo you wish to send data to, e.g., `sandbox`.  This configuration element must be a non-empty string and is __required__.
`humio.hec.ingest_token` | The ingest token as supplied by your Humio installation, e.g., `2dWGywwQOtnQIgrMfbH0CrHna7zcDPt8c41ccycfWcWG`.  You can find this value within the Humio interface for a specific repo under "Settings".  This configuration element must be a non-empty string and is __required__.
`humio.hec.buffer_size` | Maximum number of events to send per call the HEC endpoint.  This configuration element must be an integer greater than zero and is __required__.
`humio.hec.fields.topic` | When set, defines the name of the field which will automatically be set to hold the kafka topic name the event originated from.  It may be useful to use a tag, e.g., `#httpd`.  This configuration element must be a non-empty string and is _optional_.
`humio.hec.fields.partition` | When set, defines the name of the field which will automatically be set to the partition of the kafka topic the event originated from.  This configuration element must be a non-empty string and is _optional_.
`humio.hec.fields.use_kafka_timestamp` |  When `true`, will automatically set the `time` field of every event sent to the HEC endpoint to the kafka message time value.  This configuration element must be one of `true` or `false` and is _optional_.
`humio.hec.retry.max` | Maximum number of times a call to the HEC endpoint will be retried before failing (and throwing an exception).  This configuration element is _optional_, with a default value of 10 retries.
`humio.hec.retry.delay_sec` | Related to `humio.hec.retry.max`, retries use an exponential backoff strategy with an initial delay of `humio.hec.retry.delay_sec` seconds and is _optional_.  Default value is 10 seconds.

## Data & Schema

### Keys & Values

Kafka message _keys_ are currently ignored.  Values are converted to HEC-compatible JSON based on connector configuration (see below examples).

### Schema Configuration

Connectors require key and value converters, e.g.

```
value.converter=org.apache.kafka.connect.json.JsonConverter
key.converter=org.apache.kafka.connect.storage.StringConverter
```

These determine the method by which the HEC Sink handles each message and are described below.

Converter | Description
--------- | -----------
`org.apache.kafka.connect.storage.StringConverter` | With `StringConverter`, messages are placed in the HEC event as a raw string.  If messages on your kafka topic are, e.g., JSON, you should use `JsonConverter`, otherwise you will lose any structured data in your events.
`org.apache.kafka.connect.json.JsonConverter` | With `JsonConverter`, messages are placed in the HEC event as the given JSON object without modification.
`io.confluent.connect.avro.AvroConverter` | With `AvroConverter`, messages are converted to JSON and placed in the HEC event.  Currently the connector handles `SinkRecord` records (i.e., Structs) with support for the following types: `INT8`, `INT16`, `INT32`, `INT64`, `FLOAT32`, `FLOAT64`, `BOOLEAN`, `STRING`, `ARRAY`, `MAP`, & `STRUCT`, with full support for nested value structures.  `BYTES` is _not_ currently supported.  We also _do not_ currently support maps with non-string keys.

## Metrics

Metrics are exposed via JMX as well as dumped to standard out every 30 seconds.

More information about the method used to generate these metrics can be found [here](https://metrics.dropwizard.io/4.0.0/manual/core.html).

### Counters

Name | Description
---- | -----------
`com.humio.connect.hec.HECSinkTask.active-tasks` | Number of active sink tasks.
`com.humio.connect.hec.HECSinkTask.flushes` | Number of flushes requested by Connect.
`com.humio.connect.hec.HECSinkTask.put-records` | Number of records put.
`com.humio.connect.hec.HECSinkTask.task-starts` | Number of connector task starts.
`com.humio.connect.hec.HECSinkTask.task-stops` | Number of connector task stops.
`com.humio.connect.hec.client.HECClientImpl.failed-hec-requests` | Failed HTTP requests to Humio HEC endpoint.
`com.humio.connect.hec.client.HECClientImpl.posted-records` | Number of records posted to Humio HEC endpoint.
`com.humio.connect.hec.service.HECServiceImpl.flush-lock-waits` | Number of times connector had to wait for a batch to post to Humio as a result of a requested flush from Connect.

### Histograms

Name | Description
---- | -----------
`com.humio.connect.hec.HECSinkTask.put-batch-sizes` | Batch sizes received from Kafka.
`com.humio.connect.hec.client.HECClientImpl.posted-batch-sizes` | Batch sizes submitted to Humio HEC endpoint.

### Timers

Name | Description
---- | -----------
`com.humio.connect.hec.HECSinkTask.put-calls` | Full end-to-end processing time.
`com.humio.connect.hec.client.HECClientImpl.hec-http-requests` | HEC endpoint POST time.

### Notes

* If the metric `com.humio.connect.hec.HECSinkTask.put-batch-sizes` reflects values consistently smaller than the configuration property `humio.hec.buffer_size` and you are sure there is sufficient throughput on the assigned topic partition to fill the buffer, check the Kafka configuration property `max.poll.records` (its default is 500), you may have to increase it.

## HEC Event Field Support & Techniques

The Humio HEC endpoint supports several more fields which are not explicitly handled by this connector.  The techniques outlined below for each field may give you some ideas on how to use these fields by way of Kafka Connector's [Single Message Transformations](https://docs.confluent.io/current/connect/transforms/index.html).  Alternatively, these fields may also be set (or modified) by way of Humio's [Parsers](https://docs.humio.com/parsers/).

### SMT rename example: `time`

You can use the below technique to rename a field using SMT, e.g., if your messages already have a timestamp:

```
transforms=replacefield
transforms.replacefield.renames=other_time_field:time
transforms.replacefield.type=org.apache.kafka.connect.transforms.ReplaceField$Value
```

### SMT field insertion example: `timezone`, `sourcetype`, `source` and `host`

You can use the below technique to leverage SMT to insert a field with a static value.  e.g., if you wish to configure events to use a specific time zone, you can set a static value:

```
transforms=insert_tz
transforms.insert_tz.static.field=timezone
transforms.insert_tz.static.value=Europe/Copenhagen
transforms.insert_tz.type=org.apache.kafka.connect.transforms.InsertField$Value
```

## Testing

We have created a docker compose setup to streamline testing of the Humio HEC sink connector.  There are two ways you can use it: completely integrated with and managed by the test framework, _or_ running the docker compose environment separately and running the supplied tests and testing the connector itself is standalone mode.

### Unit tests

Unit tests currently cover the internal `Record` object functionality, `HECSinkConnectorConfig` instantiation, as well as each of `AvroConverter`, `JsonRawStringRecordConverter` & `JsonSchemalessRecordConverter` for schema and data conversion functionality.

### End-to-end test

The _end-to-end test_ performs the following steps (assuming _managed_ docker):

* extracts the host names and ports for all required services (Kafka Broker, Kafka Connect, Schema Registry, and Humio) from the `src/test/resources/docker-compose.yml` Docker Compose file;
* constructs the relevant URLs, property objects and other identifiers to start the system and perform the tests;
* starts the Docker containers described in `src/test/resources/docker-compose.yml` and waits for them to successfully start;
* extracts the Humio ingest token from the `humio-data` directory mounted from within the running docker container;
* registers (or updates) the sink connector with Kafka Connect;
* pushes 10 unique messages to the configured Kafka topic, flushes the Kafka producer, and waits for them to have been processed;
* performs a `count()` query against the Humio docker instance, verifying the correct number of messages has been received;
* performs a check to ensure a unique token placed in the messages is present in the output of each message;
* shuts down the docker containers.

If any of the above steps fail, the test as a whole fails.

### Prerequisites

* Docker
* Maven
* Java 8+

### Distributed testing with managed Docker Compose

In this context, "managed Docker Compose" means the test framework handles the work of starting, managing and stopping the docker compose element, as well as automatically configure all dependencies based on data in `src/test/resources/docker/docker-compose.yml` (e.g., hostnames, ports, ingest token, etc.)

__Unit & end-to-end integration test__:

* Inspect `src/test/resources/docker/docker-compose.yml` and verify the port assignments are unused and available on your machine.  If they are not, be sure to ensure any ports that were changed are reflected in references made by other services.
* Inspect `src/test/resources/config/json_sink_connector.json`, editing configuration properties if necessary.
* execute `bin/compile.sh` if you have not already; this will build the "uber jar" required for the managed instance of Connect.
* execute `mvn test` to run the full test suite (including unit tests).

Results specific to distributed testing will be contained within the `EndToEndJsonTest` test output.  For further insight as to its mechanics and what/how everything is being tested, refer to [the source](https://github.com/humio/kafka-connect-hec-sink/blob/master/src/test/java/com/humio/connect/hec/EndToEndJsonTest.java).

### Standalone testing

In this context, the assumption will be that you are managing the docker compose element yourself, with the tests assuming it's running.  This is generally only useful if you want to run the connector in standalone mode and generate some load with `JsonDataPump`, or test it by some other means to suit your needs.

__Things to know__:

* Killing docker and restarting it "can" retain state, depending on how it [was] stopped.  When in doubt, run `bin/stop-docker-instance.sh`, and restart it.  This will ensure you start with a blank slate.
* _Before running the connector in standalone mode_, but _after_ docker has been started, run the utility `bin/get-ingest-token.sh`.  Ouptut will look similar to this:

```
$ bin/get-ingest-token.sh
defaulting to humio index (repo) sandbox
extracting sandbox ingest key from global data snapshot...
located sandbox key: sandbox_e5W4sRju9jCXqMsEULfKvZnc
LaBtqQmFXSOrKhG4HuYyk4JZiov2BGhuyB2GitW6dgNi
```

The last line of the output (e.g., `LaBtqQmFXSOrKhG4HuYyk4JZiov2BGhuyB2GitW6dgNi`) should be placed in the file `config/HECSinkConnector.properties` as the value for the `humio.hec.ingest_token` property.  If you see errors here, it probably cannot find data in `./humio-data`, which is mounted from the humio service running in docker; stop and restart the docker services with the scripts provided in `bin` (see _things to know_ above).

__Unit and end-to-end integration test__

* Start docker with `bin/start-docker-instance.sh`.
* Execute tests with the environment variable `MANAGE_DOCKER=false`, e.g.,
```
$ MANAGE_DOCKER=false mvn test
```

* Stop docker with `bin/stop-docker-instance.sh`.

__Testing the HEC connector with `JsonDataPump`__:

* Start docker with `bin/start-docker-instance.sh`.
* Inspect `config/HECStandaloneSinkConnector.properties`, editing port assignments if necessary.
* Inspect connector properties in `config/HECSinkConnector.properties`.  If you have not updated the ingest token (see _things to know_ above), do so now.
* Inspect the standalone worker properties in `config/HECStandaloneSinkConnector.properties`, ensuring the Kafka broker port is correct (if you haven't edited the `docker-compose.yml` file, you're good to go), _and_ ensure that the `rest.port` port is not already in use on your machine.
* Start the connector in standalone mode with `bin/start-connector-standalone.sh`.
* Generate messages for the configured topic (e.g., `hectopic`, the value in the configuration properties if you've not changed it) by running `bin/json-data-pump.sh hectopic`.  __Note__: this utility will run until killed!
* Stop docker with `bin/stop-docker-instance.sh`.
