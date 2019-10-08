/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import java.util.Map;


public class HECSinkConnectorConfig extends AbstractConfig {

    public static final String INDEX_NAME = "humio.repo";
    public static final String INDEX_NAME_DOC = "Humio repository name.";

    public static final String INGEST_TOKEN = "humio.hec.ingest_token";
    public static final String INGEST_TOKEN_DOC = "Humio ingest token.";

    public static final String HEC_ENDPOINT = "humio.hec.url";
    public static final String HEC_ENDPOINT_DOC = "Humio HEC endpoint URL.";

    public static final String HEC_RETRY_MAX = "humio.hec.retry.max";
    public static final String HEC_RETRY_MAX_DOC = "Number of times to retry a failed call to the HEC endpoint, -1 for infinite (default 10).";

    public static final String HEC_RETRY_DELAY_S = "humio.hec.retry.delay_sec";
    public static final String HEC_RETRY_DELAY_S_DOC = "Initial delay for exponential backoff for failed HEC endpoint call retry (default 10).";

    public static final String BUFFER_SIZE = "humio.hec.buffer_size";
    public static final String BUFFER_SIZE_DOC = "Number of messages to buffer per HEC call (default 500).";

    public static final String USE_KAFKA_TIMESTAMP = "humio.hec.fields.use_kafka_timestamp";
    public static final String USE_KAFKA_TIMESTAMP_DOC = "When true, the time field on all messages will be set to the kafka record timestamp.";

    public static final String TOPIC_FIELD = "humio.hec.fields.topic";
    public static final String TOPIC_FIELD_DOC = "When set, this events field will be set to the topic the message was received from.";

    public static final String PARTITION_FIELD = "humio.hec.fields.partition";
    public static final String PARTITION_FIELD_DOC = "When set, this events field will be set to the partition of the topic the message was received from.";

    public static final String KAFKA_OFFSET_FIELD = "humio.hec.fields.kafka_offset";
    public static final String KAFKA_OFFSET_FIELD_DOC = "When set, this events field will be set to the kafka offset for the partition of the topic the message was received from.";

    public static final String MESSAGE_KEY_FIELD = "humio.hec.fields.message_key";
    public static final String MESSAGE_KEY_FIELD_DOC = "When set, this events field will be set to the key of the kafka message, if one exists.";

    public static final String MESSAGE_HEADERS_FIELD = "humio.hec.fields.message_headers";
    public static final String MESSAGE_HEADERS_FIELD_DOC = "When set, this events field will be set to the headers of the kafka message, if any exist.";

    public static final String IGNORE_PARSING_ERRORS = "humio.hec.ignore_parsing_errors";
    public static final String IGNORE_PARSING_ERRORS_DOC = "When set, this will ignore messages which fail parsing (default false).  Use with humio.hec.log_parsing_errors for debug information.";

    public static final String LOG_PARSING_ERRORS = "humio.hec.log_parsing_errors";
    public static final String LOG_PARSING_ERRORS_DOC = "When set, this will log messages which failed to parse (default true).";

    public HECSinkConnectorConfig(ConfigDef config, Map<String, String> parsedConfig) {
        super(config, parsedConfig);
    }

    public HECSinkConnectorConfig(Map<String, String> parsedConfig) {
    this(conf(), parsedConfig);
    }

    public static ConfigDef conf() {
      return new ConfigDef()
        .define(INDEX_NAME, Type.STRING, null,
                new ConfigDef.NonEmptyString(),
                Importance.HIGH, INDEX_NAME_DOC)
        .define(INGEST_TOKEN, Type.STRING, null,
                new ConfigDef.NonEmptyString(),
                Importance.HIGH, INGEST_TOKEN_DOC)
        .define(HEC_ENDPOINT, Type.STRING, null,
                new ConfigDef.NonEmptyString(),
                Importance.HIGH, HEC_ENDPOINT_DOC)
        .define(HEC_RETRY_MAX, Type.INT, 10,
                ConfigDef.Range.between(-1, Integer.MAX_VALUE),
                Importance.HIGH, HEC_RETRY_MAX_DOC)
        .define(HEC_RETRY_DELAY_S, Type.INT, 10,
                ConfigDef.Range.between(0, Integer.MAX_VALUE),
                Importance.HIGH, HEC_RETRY_DELAY_S_DOC)
        .define(BUFFER_SIZE, Type.INT, 500,
                ConfigDef.Range.between(1, Integer.MAX_VALUE),
                Importance.HIGH, BUFFER_SIZE_DOC)
        .define(USE_KAFKA_TIMESTAMP, Type.BOOLEAN, false,
                Importance.HIGH, USE_KAFKA_TIMESTAMP_DOC)
        .define(TOPIC_FIELD, Type.STRING, null,
                Importance.HIGH, TOPIC_FIELD_DOC)
        .define(PARTITION_FIELD, Type.STRING, null,
                Importance.HIGH, PARTITION_FIELD_DOC)
        .define(KAFKA_OFFSET_FIELD, Type.STRING, null,
                Importance.HIGH, KAFKA_OFFSET_FIELD_DOC)
        .define(MESSAGE_KEY_FIELD, Type.STRING, null,
                Importance.HIGH, MESSAGE_KEY_FIELD_DOC)
        .define(MESSAGE_HEADERS_FIELD, Type.STRING, null,
                Importance.HIGH, MESSAGE_HEADERS_FIELD_DOC)
        .define(IGNORE_PARSING_ERRORS, Type.BOOLEAN, false,
                Importance.HIGH, IGNORE_PARSING_ERRORS_DOC)
        .define(LOG_PARSING_ERRORS, Type.BOOLEAN, true,
                Importance.HIGH, LOG_PARSING_ERRORS_DOC);
    }

    public String getIndexName(){
        return this.getString(INDEX_NAME);
    }

    public String getIngestToken() { return this.getString(INGEST_TOKEN); }

    public String getHecEndpoint() { return this.getString(HEC_ENDPOINT); }

    public int getHecRetryMax() { return this.getInt(HEC_RETRY_MAX); }

    public int getHecRetryDelay() { return this.getInt(HEC_RETRY_DELAY_S); }

    public int getBufferSize() { return this.getInt(BUFFER_SIZE); }

    public boolean useKafkaTimestamp() { return this.getBoolean(USE_KAFKA_TIMESTAMP); }

    public String getTopicField() { return this.getString(TOPIC_FIELD); }

    public String getPartitionField() { return this.getString(PARTITION_FIELD); }

    public String getKafkaOffsetField() { return this.getString(KAFKA_OFFSET_FIELD); }

    public String getMessageKeyField() { return this.getString(MESSAGE_KEY_FIELD); }

    public String getMessageHeadersField() { return this.getString(MESSAGE_HEADERS_FIELD); }

    public boolean ignoreParsingErrors() { return this.ignoreParsingErrors(); }

    public boolean logParsingErrors() { return this.logParsingErrors(); }

}
