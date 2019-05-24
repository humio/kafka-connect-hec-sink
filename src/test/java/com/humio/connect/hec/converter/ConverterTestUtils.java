/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;

import java.util.Map;

public class ConverterTestUtils {

    public static SinkRecord makeSinkRecord(Schema valueSchema, Struct value) {
        return new SinkRecord(
                "test-topic",
                0,
                SchemaBuilder.string(),
                "record key",
                valueSchema,
                value,
                0,
                System.currentTimeMillis()/1000,
                TimestampType.NO_TIMESTAMP_TYPE);
    }

    public static SinkRecord makeSinkRecord(Map value) {
        return new SinkRecord(
                "test-topic",
                0,
                SchemaBuilder.string(),
                "record key",
                null,
                value,
                0,
                System.currentTimeMillis()/1000,
                TimestampType.NO_TIMESTAMP_TYPE);
    }

    public static SinkRecord makeSinkRecord(String value) {
        return new SinkRecord(
                "test-topic",
                0,
                SchemaBuilder.string(),
                "record key",
                null,
                value,
                0,
                System.currentTimeMillis()/1000,
                TimestampType.NO_TIMESTAMP_TYPE);
    }
}
