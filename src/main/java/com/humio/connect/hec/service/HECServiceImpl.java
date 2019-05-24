/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.google.gson.JsonObject;
import com.humio.connect.hec.HECSinkConnectorConfig;
import com.humio.connect.hec.Metrics;
import com.humio.connect.hec.Record;
import com.humio.connect.hec.client.HECClient;
import com.humio.connect.hec.client.HECClientImpl;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
                if (topicField != null && !topicField.equals(""))
                    fields.addProperty(topicField, record.topic);
                if (partitionField != null && !partitionField.equals(""))
                    fields.addProperty(partitionField, record.partition);

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
