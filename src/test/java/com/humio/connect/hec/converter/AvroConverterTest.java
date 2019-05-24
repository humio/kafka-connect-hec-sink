/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import com.google.gson.JsonObject;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.humio.connect.hec.converter.ConverterTestUtils.makeSinkRecord;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AvroConverterTest {
    private AvroConverter converter = new AvroConverter();

    @Test
    void convertBasicTest() {
        Schema valueSchema = SchemaBuilder.struct()
                .name("com.humio.connect.hec.schema.DateMsg")
                .version(1)
                .doc("A calendar date including month, day, and year.")
                .field("month", Schema.STRING_SCHEMA)
                .field("day", Schema.INT8_SCHEMA)
                .field("year", Schema.INT16_SCHEMA)
                .build();

        Struct struct = new Struct(valueSchema)
                .put("month", "january")
                .put("day", (byte) 8)
                .put("year", (short) 1977);

        SinkRecord sinkRecord = makeSinkRecord(valueSchema, struct);
        Record record = converter.convert(sinkRecord);
        JsonObject obj = (JsonObject) record.value;

        assertAll(
                () -> assertEquals("january", obj.get("month").getAsString()),
                () -> assertEquals(8, obj.get("day").getAsByte()),
                () -> assertEquals(1977, obj.get("year").getAsShort())
        );
    }

    @Test
    void convertTypesTest() {
        Schema schema = SchemaBuilder.struct()
            .field("int8",
                    SchemaBuilder
                            .int8()
                            .defaultValue((byte) 2)
                            .doc("int8 field")
                            .build())
            .field("int16", Schema.INT16_SCHEMA)
            .field("int32", Schema.INT32_SCHEMA)
            .field("int64", Schema.INT64_SCHEMA)
            .field("float32", Schema.FLOAT32_SCHEMA)
            .field("float64", Schema.FLOAT64_SCHEMA)
            .field("boolean", Schema.BOOLEAN_SCHEMA)
            .field("string", Schema.STRING_SCHEMA)

            // NOTE: we do not support the BYTES type
            //.field("bytes", Schema.BYTES_SCHEMA)

            .field("array", SchemaBuilder.array(Schema.STRING_SCHEMA).build())
            .field("map", SchemaBuilder.map(Schema.STRING_SCHEMA, Schema.INT32_SCHEMA).build())

            // NOTE: we do not support maps with non-string keys
            //.field("mapNonStringKeys",
            //        SchemaBuilder.map(Schema.INT32_SCHEMA, Schema.INT32_SCHEMA).build())

            .build();

        Struct struct = new Struct(schema)
            .put("int8", (byte) 12)
            .put("int16", (short) 12)
            .put("int32", 12)
            .put("int64", 12L)
            .put("float32", 12.2f)
            .put("float64", 12.2)
            .put("boolean", true)
            .put("string", "foo")
            .put("array", Arrays.asList("a", "b", "c"))
            .put("map", Collections.singletonMap("field", 1));

        Record record = converter.convert(makeSinkRecord(schema, struct));
        JsonObject obj = (JsonObject) record.value;

        // primitive types
        assertAll(
                () -> assertEquals(struct.get("int8"), obj.get("int8").getAsByte()),
                () -> assertEquals(struct.get("int16"), obj.get("int16").getAsShort()),
                () -> assertEquals(struct.get("int32"), obj.get("int32").getAsInt()),
                () -> assertEquals(struct.get("int64"), obj.get("int64").getAsLong()),
                () -> assertEquals(struct.get("float32"), obj.get("float32").getAsFloat()),
                () -> assertEquals(struct.get("float64"), obj.get("float64").getAsDouble()),
                () -> assertEquals(struct.get("boolean"), obj.get("boolean").getAsBoolean()),
                () -> assertEquals(struct.get("string"), obj.get("string").getAsString())
        );

        // array
        assertAll(
                () -> assertEquals("a", obj.getAsJsonArray("array").get(0).getAsString()),
                () -> assertEquals("b", obj.getAsJsonArray("array").get(1).getAsString()),
                () -> assertEquals("c", obj.getAsJsonArray("array").get(2).getAsString())
        );

        // map
        assertEquals(1, obj.getAsJsonObject("map").get("field").getAsInt());
    }

}