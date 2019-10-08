/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.humio.connect.hec.HECSinkConnectorConfig;
import com.humio.connect.hec.Metrics;
import com.humio.connect.hec.Record;
import com.humio.connect.hec.client.HECClient;
import com.humio.connect.hec.client.HECClientImpl;
import com.humio.connect.hec.converter.AvroConverter;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.header.Header;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.codahale.metrics.MetricRegistry.name;

public class HECServiceImpl implements HECService {
    private static final Logger log = LogManager.getLogger(HECServiceImpl.class);
    private final ReentrantLock sinkLock = new ReentrantLock();
    private final HECClient hecClient;

    private final String indexName;
    private final String ingestToken;
    private final String hecEndpoint;
    private final int bufferSize;
    private final boolean useKafkaTimestamp;
    private final String topicField;
    private final String partitionField;
    private final String kafkaOffsetField;
    private final String messageKeyField;
    private final String messageHeadersField;

    private final static AvroConverter avroConverter = new AvroConverter();

    private final Counter flushLockWaits =
            Metrics.metrics.counter(name(this.getClass(), "flush-lock-waits"));
    private final Timer processTimer =
            Metrics.metrics.timer(name(this.getClass(), "process"));

    public HECServiceImpl(HECClient hecClient, HECSinkConnectorConfig config) {
        indexName = config.getIndexName();
        ingestToken = config.getIngestToken();
        hecEndpoint = config.getHecEndpoint();
        bufferSize = config.getBufferSize();
        useKafkaTimestamp = config.useKafkaTimestamp();
        topicField = config.getTopicField();
        partitionField = config.getPartitionField();
        kafkaOffsetField = config.getKafkaOffsetField();
        messageKeyField = config.getMessageKeyField();
        messageHeadersField = config.getMessageHeadersField();

        int hecRetryMax = config.getHecRetryMax();
        int hecRetryDelay = config.getHecRetryDelay();

        if(hecClient == null) {
            hecClient = new HECClientImpl(hecEndpoint, ingestToken, hecRetryMax, hecRetryDelay);
        }

        this.hecClient = hecClient;
    }

    @Override
    public void process(Collection<Record> records) {
        final Timer.Context context = processTimer.time();
        sinkLock.lock();
        try {
            log.debug("## process: " + records.size() + " records: ");

            Collection<Record> recordBuffer = new ArrayList<>();

            records.forEach(record -> {
                record.message.addProperty("index", indexName);

                if (useKafkaTimestamp) {
                    record.message.addProperty("time", record.ts);
                }

                JsonObject fields = new JsonObject();

                // kafka topic
                if (topicField != null && !topicField.equals(""))
                    fields.addProperty(topicField, record.topic);

                // kafka topic partition
                if (partitionField != null && !partitionField.equals(""))
                    fields.addProperty(partitionField, record.partition);

                // kafka offset
                if (kafkaOffsetField != null && !kafkaOffsetField.equals(""))
                    fields.addProperty(kafkaOffsetField, record.kafkaOffset);

                // kafka record key
                if (record.sinkRecord.key() != null && messageKeyField != null && !messageKeyField.equals("")) {
                    if (record.sinkRecord.keySchema() != null) {
                        switch(record.sinkRecord.keySchema().type()) {
                            case BOOLEAN:
                                fields.addProperty(messageKeyField, (Boolean) record.sinkRecord.key());
                                break;
                            case FLOAT32:
                            case FLOAT64:
                            case INT8:
                            case INT16:
                            case INT32:
                            case INT64:
                                fields.addProperty(messageKeyField, (Number) record.sinkRecord.key());
                                break;
                            case STRING:
                                fields.addProperty(messageKeyField, (String) record.sinkRecord.key());
                                break;
                            default:
                                fields.addProperty(messageKeyField, String.valueOf(record.sinkRecord.key()));
                                fields.addProperty(messageKeyField + "__error",
                                        "unknown key schema data type!  check connector documentation.");
                        }
                    } else {
                        fields.addProperty(messageKeyField, new String((byte[]) record.sinkRecord.key()));
                    }
                }

                // kafka record headers
                if (record.sinkRecord.headers() != null && record.sinkRecord.headers().size() > 0 &&
                        messageHeadersField != null && !messageHeadersField.equals("")) {
                    JsonObject headers = new JsonObject();
                    for(Header header : record.sinkRecord.headers()) {
                        String key = header.key();
                        // in the case of multiple values for the same key, repackage the data
                        //  into an array; if there are multiple fields that repeat and are
                        //  related, they'll line up 1-for-1 on the other end
                        if (headers.has(key) && !headers.get(key).isJsonArray()) {
                            JsonArray valueArray = new JsonArray();
                            valueArray.add(headers.get(key));
                            headers.remove(key);
                            headers.add(key, valueArray);
                        }
                        if (header.schema() != null) {
                            JsonElement el = null;
                            switch(header.schema().type()) {
                                case BOOLEAN:
                                    el = new JsonPrimitive((Boolean) header.value());
                                    break;
                                case FLOAT32:
                                case FLOAT64:
                                case INT8:
                                case INT16:
                                case INT32:
                                case INT64:
                                    el = new JsonPrimitive((Number) header.value());
                                    break;
                                case STRING:
                                    el = new JsonPrimitive((String) header.value());
                                    break;
                                default:
                                    el = new JsonPrimitive(String.valueOf(header.value()));
                                    log.debug("error in header key schema: " +
                                            "unknown key schema data type!  check connector documentation.");
                            }

                            if (headers.has(key)) {
                                headers.getAsJsonArray(key).add(el);
                            } else {
                                headers.add(key, el);
                            }

                        } else {
                            if (headers.has(key)) {
                                headers.getAsJsonArray(key).add(new String((byte[]) header.value()));
                            } else {
                                headers.addProperty(key, new String((byte[]) header.value()));
                            }
                        }
                    }

                    // flatten our headers since HEC fields only accept (key, primitive)
                    for(Map.Entry<String, JsonElement> entry : headers.entrySet()) {
                        String key = messageHeadersField + "." + entry.getKey();
                        if (entry.getValue().isJsonArray()) {
                            JsonArray ar = entry.getValue().getAsJsonArray();
                            for(int i=0; i<ar.size(); i++) {
                                String arKey = key + "[" + i + "]";
                                fields.add(arKey, ar.get(i));
                            }
                        } else {
                            fields.add(key, entry.getValue());
                        }
                    }
                }

                if (fields.entrySet().size() > 0)
                    record.message.add("fields", fields);

                recordBuffer.add(record);
                if (recordBuffer.size() == bufferSize) {
                    try {
                        hecClient.bulkSend(recordBuffer);
                        recordBuffer.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new ConnectException(e);
                    }
                }
            });
            try {
                if (recordBuffer.size() > 0) {
                    hecClient.bulkSend(recordBuffer);
                    recordBuffer.clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new ConnectException(e);
            }

            log.debug("## process complete");
        } finally {
            sinkLock.unlock();
            context.stop();
        }
    }

    @Override
    public void closeClient() throws IOException {
        hecClient.close();
        log.debug("closeClient()");
    }

    // this simply acquires the lock, ensuring any in-flight collection of records was processed.  this is only used
    //  by HECSinkTask's flush implementation.
    @Override
    public void flushWait() throws InterruptedException {
        while(!sinkLock.tryLock(1, TimeUnit.SECONDS)) {
            log.warn("waiting for sink lock for flush...");
            flushLockWaits.inc();
        }
        sinkLock.unlock();
    }
}
