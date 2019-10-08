/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.header.Header;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.shaded.okhttp3.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static com.humio.connect.hec.TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@RunWith(JUnitPlatform.class)
public class EndToEndJsonTest {
    private static final Logger log = LogManager.getLogger(EndToEndJsonTest.class);

    public static final String DEFAULT_COMPOSE_SERVICE_SUFFIX = "_1";
    public static boolean dockerRunning = false;

    public static final String KAFKA_TOPIC;
    public static final String HUMIO_INDEX;

    public static final String KAFKA_BROKER;
    public static final int KAFKA_BROKER_PORT;

    public static final String KAFKA_CONNECT;
    public static final int KAFKA_CONNECT_PORT;

    public static final String SCHEMA_REGISTRY;
    public static final int SCHEMA_REGISTRY_PORT;

    public static final String HUMIO;
    public static final int HUMIO_PORT;

    private static final String HUMIO_CLIENT_URI;

    private static String HUMIO_INGEST_TOKEN;

    private static boolean manageDocker =
            System.getenv("MANAGE_DOCKER") == null ||
            System.getenv("MANAGE_DOCKER").equals("") ||
            System.getenv("MANAGE_DOCKER").equalsIgnoreCase("true");

    private static KafkaProducer<String, String> PRODUCER;

    static {
        Map composeFile = null;
        try {
            composeFile = (Map)new YamlReader(new FileReader(DOCKER_COMPOSE_FILE)).read();

            KAFKA_BROKER = extractHostnameFromDockerCompose(composeFile,"kafkabroker");
            KAFKA_BROKER_PORT = extractHostPortFromDockerCompose(composeFile,"kafkabroker");

            KAFKA_CONNECT = extractHostnameFromDockerCompose(composeFile,"kafkaconnect");
            KAFKA_CONNECT_PORT = extractHostPortFromDockerCompose(composeFile,"kafkaconnect");

            SCHEMA_REGISTRY = extractHostnameFromDockerCompose(composeFile,"schemaregistry");
            SCHEMA_REGISTRY_PORT = extractHostPortFromDockerCompose(composeFile,"schemaregistry");

            HUMIO = extractHostnameFromDockerCompose(composeFile,"humio");
            HUMIO_PORT = extractHostPortFromDockerCompose(composeFile,"humio");
            HUMIO_CLIENT_URI = "http://" + HUMIO + ":" + HUMIO_PORT + "/api/v1/ingest/hec";

        } catch (YamlException | FileNotFoundException e) {
            throw new RuntimeException("error: problem parsing " + DOCKER_COMPOSE_FILE, e);
        }

        try {
            JsonObject config = new JsonParser().parse(
                    new String(Files.readAllBytes(Paths.get(SINK_CONNECTOR_CONFIG))))
                    .getAsJsonObject()
                    .getAsJsonObject("config");
            KAFKA_TOPIC = config.get("topics").getAsString();
            HUMIO_INDEX = config.get(HECSinkConnectorConfig.INDEX_NAME).getAsString();
        } catch (IOException e) {
            throw new RuntimeException("error: problem parsing " + SINK_CONNECTOR_CONFIG, e);
        }
    }

    @ClassRule
    public static DockerComposeContainer CONTAINER_ENV =
            new DockerComposeContainer(new File(DOCKER_COMPOSE_FILE))
                    .withExposedService(KAFKA_BROKER+DEFAULT_COMPOSE_SERVICE_SUFFIX,KAFKA_BROKER_PORT)
                    .withExposedService(HUMIO+DEFAULT_COMPOSE_SERVICE_SUFFIX,HUMIO_PORT);

    @BeforeAll
    public static void setup() throws IOException, InterruptedException {

        System.out.println("removing any existing humio-data volume...");

        if (manageDocker) {
            removeExistingHumioData();
            System.out.println("starting container...");
            CONTAINER_ENV.start();
        }

        Properties props = new Properties();
        props.put("bootstrap.servers","localhost:"+KAFKA_BROKER_PORT);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("schema.registry.url","http://localhost:"+ SCHEMA_REGISTRY_PORT);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                "io.confluent.monitoring.clients.interceptor.MonitoringProducerInterceptor");
        PRODUCER = new KafkaProducer<>(props);

        if (manageDocker) {
            System.out.println("waiting for containers...");
            Thread.sleep(30000);
        }

        // get humio token
        // NOTE: humio-data is the humio data directory exposed by the docker instance.  if it's not found,
        //  for some reason your docker container is not running!

        int tries = 0;
        while(true) {
            try {
                System.out.println("reading humio admin token...");
                HUMIO_INGEST_TOKEN = getIngestToken();
                dockerRunning = true;
                break;
            } catch (IOException ex) {
                tries++;
                if (tries == 4) {
                    throw new RuntimeException("docker container failed to start.");
                }
                System.out.println("waiting for docker to come online...");
                Thread.sleep(5000);
            }
        }
        System.out.println("humio ingest token: " + HUMIO_INGEST_TOKEN);

        // sink config: replacement variables -> docker values + token
        String config = new String(Files.readAllBytes(Paths.get(SINK_CONNECTOR_CONFIG)));
        config = config.replaceAll("_SCHEMA_REG_HOST_", SCHEMA_REGISTRY)
                .replaceAll("_SCHEMA_REG_PORT_", ""+SCHEMA_REGISTRY_PORT)
                .replaceAll("_HEC_ENDPOINT_", HUMIO_CLIENT_URI)
                .replaceAll("_INGEST_TOKEN_", HUMIO_INGEST_TOKEN);

        // register connector
        System.out.println("registering sink connector...");
        registerHumioHECSinkConnector(config);

        System.out.println("waiting for connector...");
        Thread.sleep(10000);

        System.out.println("test setup complete");
    }

    @Test
    void endToEndTest() throws IOException, InterruptedException {
        System.out.println("starting integration test...");
        assert(dockerRunning);

        String uuid = UUID.randomUUID().toString();

        // NOTE: there will be numTestRecords*2 records created, one for JSON-formatted messages and one for
        //  raw string messages for each of numTestRecords; this is to test the JsonRawStringRecordConverter.
        int numTestRecords = 10;

        // find our partition, just in case it's not what one might expect :)
        int partition = 0;
        for(PartitionInfo partitionInfo : PRODUCER.partitionsFor(KAFKA_TOPIC)) {
            partition = partitionInfo.partition();
            System.out.println("#### partition set to " + partition);
            break;
        }

        for (int tid = 0; tid < numTestRecords; tid++) {
            JsonObject rec = new JsonObject();
            rec.addProperty("num", tid);
            rec.addProperty("message",
                    "uuid is " + uuid + " and hello " + tid + "! testing kafka is fun :D");

            ProducerRecord<String, String> record = new ProducerRecord(
                    KAFKA_TOPIC,
                    partition,
                    "key-" + tid,
                    rec.toString(),
                    new ArrayList<Header>());
            record.headers()
                    .add("header-key-1", "header key one value".getBytes())
                    .add("header-key-1", "header key one value 2".getBytes())
                    .add("header-key-2", "header key two value".getBytes());

            System.out.println(LocalDateTime.now() + " (JSON) producer sending -> " + record);

            PRODUCER.send(record, (RecordMetadata r, Exception exc) -> {
                assertNull(exc, () -> "unexpected error while sending JSON record: " + rec
                        + " | exc: " + exc.getMessage()
                );
            });

            ProducerRecord<String, String> rawStringRecord = new ProducerRecord(
                    KAFKA_TOPIC,
                    partition,
                    "raw-key-" + tid,
                    "raw string record #" + tid + ": " + System.currentTimeMillis(),
                    new ArrayList<Header>());
            rawStringRecord.headers()
                    .add("raw-header-key-1", "header key one value".getBytes())
                    .add("raw-header-key-1", "header key one value 2".getBytes())
                    .add("raw-header-key-2", "header key two value".getBytes());

            System.out.println(LocalDateTime.now() + " (raw string) producer sending -> " + rawStringRecord);

            PRODUCER.send(rawStringRecord, (RecordMetadata r, Exception exc) -> {
                assertNull(exc, () -> "unexpected error while sending rawStringRecord: " + rec
                        + " | exc: " + exc.getMessage()
                );
            });
        }
        System.out.println("\n\nflushing producer...");
        PRODUCER.flush();

        System.out.println("\n\nwaiting for connector processing...");
        Thread.sleep(10000);

        System.out.println("\n\nquerying for " + (numTestRecords*2) + " records...");

        System.out.println("\n\ncount query...");
        List<JsonObject> results = queryHumio("count()");
        String countResult = results.get(0).get("_count").getAsString();
        assertEquals("20", countResult);

        System.out.println("\n\nuuid check...");
        results = queryHumio(uuid);
        assertEquals(10, results.size());
        for(JsonObject res : results) {
            String msg = res.get("message").getAsString();
            if (msg.indexOf(uuid) == -1) {
                throw new RuntimeException("did not find unique uuid in query results!");
            }
        }
    }

    @AfterAll
    public static void teardown() {
        System.out.println("stopping kafka producer...");
        PRODUCER.close();

        if (manageDocker) {
            System.out.println("stopping container...");
            CONTAINER_ENV.stop();
        }

        System.out.println("test complete.");
    }

    // utils

    private static List<JsonObject> queryHumio(String query) throws IOException {
        JsonObject q = new JsonObject();
        q.addProperty("queryString", query);
        q.addProperty("start", "30s");
        q.addProperty("end", "now");
        q.addProperty("isLive", false);
        return queryHumio(q);
    }

    private static List<JsonObject> queryHumio(JsonObject queryObj) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), queryObj.toString()
        );

        Request request = new Request.Builder()
                .url("http://localhost:"+HUMIO_PORT+"/api/v1/dataspaces/" + HUMIO_INDEX + "/query")
                .header("Accept", "application/x-ndjson")
                .post(body)
                .build();

        Response response = new OkHttpClient().newCall(request).execute();
        List<JsonObject> results = new ArrayList<>();

        String[] strings = response.body().string().split("\n");
        for(String s : strings) {
            System.out.println("#### " + s);
            JsonObject obj = new JsonParser().parse(s).getAsJsonObject();
            results.add(obj);
        }
        return results;
    }

    private static String getIngestToken() throws IOException {
        System.out.println("extracting " + HUMIO_INDEX + " ingest key from global data snapshot...");
        String s = new String(Files.readAllBytes(Paths.get("humio-data/global-data-snapshot.json")));
        JsonObject data = new JsonParser().parse(s).getAsJsonObject();
        JsonObject dataspaces = data.getAsJsonObject("dataspaces");
        Set<Map.Entry<String, JsonElement>> entries = dataspaces.entrySet();
        for(Map.Entry<String, JsonElement> entry : entries) {
            String key = entry.getKey();
            JsonObject obj = entry.getValue().getAsJsonObject();
            if (key.startsWith(HUMIO_INDEX) && key.indexOf("LocalHostRoot") == -1) {
                System.out.println("located " + HUMIO_INDEX + " key: " + key);
                JsonObject sandbox = dataspaces.getAsJsonObject(key);
                JsonObject entity = sandbox.getAsJsonObject("entity");
                JsonArray tokens = entity.getAsJsonArray("ingestTokens");
                JsonObject token = tokens.get(0).getAsJsonObject();
                return token.get("token").getAsString();
            }
        }
        throw new RuntimeException("cannot extract " + HUMIO_INDEX + " key from humio-data/global-data-snapshot.json");
    }

    private static void registerHumioHECSinkConnector(String configuration) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), configuration
        );

        Request request = new Request.Builder()
                .url("http://localhost:"+KAFKA_CONNECT_PORT+"/connectors")
                .post(body)
                .build();

        System.out.println("sending -> " + configuration);
        Response response = new OkHttpClient().newCall(request).execute();
        System.out.println("response code = " + response.code());
        System.out.println("response body = " + response.body().string());

        // if it already exists, let's update it as configuration may have changed between runs
        if (response.code() == 409) {
            response.close();
            System.out.println("connector already exists, updating configuration...");

            JsonObject config = new JsonParser().parse(configuration).getAsJsonObject();
            String connectorName = config.get("name").getAsString();
            JsonObject configData = config.getAsJsonObject("config");

            body = RequestBody.create(
                    MediaType.parse("application/json"), configData.toString()
            );

            request = new Request.Builder()
                    .url("http://localhost:"+KAFKA_CONNECT_PORT+"/connectors/" + connectorName + "/config")
                    .put(body)
                    .build();
            response = new OkHttpClient().newCall(request).execute();
            if (response.code() != 200) {
                throw new RuntimeException("unable to update connector configuration, response = " +
                    response.code() + ": " + response.body().string());
            }
            response.close();
        } else if (response.code() == 201) {
            response.close();
        } else {
            throw new RuntimeException("invalid response from kafka connect while registering connector: " +
                    response.code() + ": " + response.body().string());
        }
    }

    private static boolean removeExistingHumioData() {
        return deleteDirectory(new File("./humio-data"));
    }


}
