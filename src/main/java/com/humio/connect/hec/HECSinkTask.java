/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import com.humio.connect.hec.converter.AvroConverter;
import com.humio.connect.hec.converter.JsonRawStringRecordConverter;
import com.humio.connect.hec.converter.JsonSchemalessRecordConverter;
import com.humio.connect.hec.converter.RecordConverter;
import com.humio.connect.hec.service.HECService;
import com.humio.connect.hec.service.HECServiceImpl;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.codahale.metrics.MetricRegistry.name;

public class HECSinkTask extends SinkTask {
    private static final Logger log = LogManager.getLogger(HECSinkTask.class);

    private HECService hecService;
    private HECSinkConnectorConfig hecConfig;

    private static final AtomicLong instanceCounter = new AtomicLong(0);
    private long inst = -1;

    // converters

    private final RecordConverter structConverter = new AvroConverter();
    private final RecordConverter jsonConverter = new JsonSchemalessRecordConverter();
    private final RecordConverter stringConverter = new JsonRawStringRecordConverter();

    // metrics

    private final Counter putRecords =
            Metrics.metrics.counter(name(this.getClass(), "put-records"));
    private final Counter taskStarts =
            Metrics.metrics.counter(name(this.getClass(), "task-starts"));
    private final Counter taskStops =
            Metrics.metrics.counter(name(this.getClass(), "task-stops"));
    private final Counter activeTasks =
            Metrics.metrics.counter(name(this.getClass(), "active-tasks"));
    private final Counter flushes =
            Metrics.metrics.counter(name(this.getClass(), "flushes"));
    private final Histogram putBatchSizes =
            Metrics.metrics.histogram(name(this.getClass(), "put-batch-sizes"));
    private final Timer putCalls =
            Metrics.metrics.timer(name(this.getClass(), "put-calls"));
    private final Counter parsingErrors =
            Metrics.metrics.counter(name(this.getClass(), "parsing-errors"));

    @Override
    public String version() {
        return VersionUtil.getVersion();
    }

    @Override
    public void start(Map<String, String> config) {
        inst = instanceCounter.incrementAndGet();
        hecConfig = new HECSinkConnectorConfig(config);
        log.debug("hec: starting instance " + inst);
        hecService = new HECServiceImpl(null, new HECSinkConnectorConfig(config));
        taskStarts.inc();
        activeTasks.inc();
    }

    @Override
    public void put(Collection<SinkRecord> collection) {
        final Timer.Context context = putCalls.time();
        try {
            if (collection.size() == 0) return;

            log.debug(inst + " hec: HECSinkTask: put(collection) size = " + collection.size());

            Collection<Record> records = collection
                    .stream()
                    .map(r -> {
                        try {
                            Object data = r.value();
                            Schema schema = r.valueSchema();
                            if (schema != null && data instanceof Struct) {
                                return structConverter.convert(r);
                            } else if (data instanceof Map) {
                                return jsonConverter.convert(r);
                            } else if (data instanceof String) {
                                return stringConverter.convert(r);
                            } else {
                                throw new DataException("error: no converter present due to unexpected object type "
                                        + data.getClass().getName());
                            }
                        } catch (Exception ex) {
                            parsingErrors.inc();
                            if (hecConfig.logParsingErrors()) {
                                System.out.println("Unable to parse message: " +
                                        "topic = " + r.topic() +
                                        ", partition = " + r.kafkaPartition() +
                                        ", offset = " + r.kafkaOffset() +
                                        "\n\nrecord.toString = " + r.toString() +
                                        "\n\nrecord.value = " + r.value() + "\n\n");
                            }
                            if (!hecConfig.ignoreParsingErrors()) {
                                System.out.println("Ignored parsing error, " +
                                        "topic = " + r.topic() +
                                        ", partition = " + r.kafkaPartition() +
                                        ", offset = " + r.kafkaOffset());
                                return null;
                            } else {
                                throw new DataException(ex);
                            }
                        }
                    })
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            hecService.process(records);
            putRecords.inc(records.size());
            putBatchSizes.update(records.size());
        } finally {
            context.stop();
        }
    }

    // NOTE: exactly-once is not currently supported
    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> map) {
        log.debug(inst + " flush: map = " + map);
        try {
            hecService.flushWait();
        } catch (InterruptedException e) {
            // TODO can't rethrow InterruptedException, how to handle this?
            e.printStackTrace();
        }
        flushes.inc();
    }

    @Override
    public void stop() {
        try {
            log.debug(inst + " hec: STOP()");
            hecService.closeClient();
            taskStops.inc();
            activeTasks.dec();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
