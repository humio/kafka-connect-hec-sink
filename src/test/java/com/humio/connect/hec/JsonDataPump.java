package com.humio.connect.hec;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import static com.humio.connect.hec.TestUtils.*;

public class JsonDataPump {
    private static final Logger log = LogManager.getLogger(JsonDataPump.class);

    public static String KAFKA_TOPIC;
    public static final String KAFKA_BROKER;
    public static final int KAFKA_BROKER_PORT;
    public static final String SCHEMA_REGISTRY;
    public static final int SCHEMA_REGISTRY_PORT;

    private static KafkaProducer<String, String> PRODUCER;

    static {
        Map composeFile = null;
        try {
            composeFile = (Map)new YamlReader(new FileReader(DOCKER_COMPOSE_FILE)).read();

            KAFKA_BROKER = extractHostnameFromDockerCompose(composeFile,"kafkabroker");
            KAFKA_BROKER_PORT = extractHostPortFromDockerCompose(composeFile,"kafkabroker");

            SCHEMA_REGISTRY = extractHostnameFromDockerCompose(composeFile,"schemaregistry");
            SCHEMA_REGISTRY_PORT = extractHostPortFromDockerCompose(composeFile,"schemaregistry");
        } catch (YamlException | FileNotFoundException e) {
            throw new RuntimeException("error: problem parsing " + DOCKER_COMPOSE_FILE, e);
        }

        try {
            JsonObject config = new JsonParser().parse(
                    new String(Files.readAllBytes(Paths.get(SINK_CONNECTOR_CONFIG))))
                    .getAsJsonObject()
                    .getAsJsonObject("config");
            KAFKA_TOPIC = config.get("topics").getAsString();
        } catch (IOException e) {
            throw new RuntimeException("error: problem parsing " + SINK_CONNECTOR_CONFIG, e);
        }
    }

    public static void main(final String[] args) throws Exception {

        if (args.length > 0) {
            KAFKA_TOPIC = args[0];
            log.info("set kafka topic to '" + KAFKA_TOPIC + "'");
        }

        Properties props = new Properties();
        props.put("bootstrap.servers","localhost:"+KAFKA_BROKER_PORT);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("schema.registry.url","http://localhost:"+ SCHEMA_REGISTRY_PORT);
        PRODUCER = new KafkaProducer<>(props);

        int batchSize = 10000;
        int ct = 0;
        long t0 = System.currentTimeMillis();
        while(true) {
            JsonObject rec = new JsonObject();
            rec.addProperty("num", ct);
            rec.addProperty("message",
                    "message #" + ct + ": testing kafka, tap tap tap, is this thing on?");
            ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, rec.toString());

            PRODUCER.send(record, (RecordMetadata r, Exception exc) -> {
                if (exc != null) {
                    exc.printStackTrace();
                    System.exit(-1);
                }
            });
            ct++;
            if (ct % batchSize == 0) {
                long dif = System.currentTimeMillis() - t0;
                log.info((dif) + "ms per " + batchSize + " messages (" + ct + " sent)");
                t0 = System.currentTimeMillis();
            }
        }

    }
}
