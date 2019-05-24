/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.humio.connect.hec.Record;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class AvroConverter implements RecordConverter {
    private static final Logger log = LogManager.getLogger(AvroConverter.class);

    @Override
    public Record convert(SinkRecord record) {
        JsonObject el = toJsonObject(record);
        return new Record(el, record.timestamp(), record.topic(), record.kafkaPartition());
    }

    private JsonObject toJsonObject(SinkRecord record) {
        return toJsonObject(record.valueSchema(), record.value());
    }

    private JsonObject toJsonObject(Schema schema, Object value) {
        JsonObject obj = new JsonObject();
        schema.fields().forEach(f -> processField(obj, (Struct) value, f));
        return obj;
    }

    private void processField(JsonObject obj, Struct struct, Field field) {
        try {
            switch(field.schema().type()) {
                case BOOLEAN:
                case FLOAT32:
                case FLOAT64:
                case INT8:
                case INT16:
                case INT32:
                case INT64:
                case STRING:
                    handlePrimitiveField(obj, struct, field);
                    break;
                case STRUCT:
                    handleStructField(obj, struct, field);
                    break;
                case ARRAY:
                    handleArrayField(obj, struct, field);
                    break;
                case MAP:
                    handleMapField(obj, struct, field);
                    break;
                default:
                    throw new DataException("unexpected / unsupported schema type " + field.schema().type());
            }
        } catch (Exception ex) {
            throw new ConnectException(ex);
        }
    }

    private void handlePrimitiveField(JsonObject obj, Struct struct, Field field) {
        final String fieldName = field.name();
        final Object val = struct.get(field);
        if (val instanceof Number) {
            obj.addProperty(fieldName, (Number) val);
        } else if (val instanceof String) {
            obj.addProperty(fieldName, (String) val);
        } else if (val instanceof Boolean) {
            obj.addProperty(fieldName, (Boolean) val);
        } else {
            throw new DataException("unexpected / unsupported schema type " + field.schema().type() +
                " - primitive field not one of (Number, String, Boolean)");
        }
    }

    private void handleStructField(JsonObject obj, Struct struct, Field field) {
        if(struct.get(field) != null) {
            obj.add(field.name(), toJsonObject(field.schema(), struct.get(field)));
        } else {
            // no field, ignore
        }
    }

    private void handleArrayField(JsonObject obj, Struct struct, Field field) {
        if(struct.get(field) == null) {
            return;
        }

        final JsonArray array = new JsonArray();
        final String fieldName = field.name();

        for(Object val : (List) struct.get(field)) {
            if(field.schema().valueSchema().type().isPrimitive()) {
                if (val instanceof Number) {
                    array.add((Number) val);
                } else if (val instanceof String) {
                    array.add((String) val);
                } else if (val instanceof Boolean) {
                    array.add((Boolean) val);
                } else {
                    throw new DataException("unexpected / unsupported schema type " + field.schema().type() +
                            " - primitive field not one of (Number, String, Boolean)");
                }
            } else {
                array.add(toJsonObject(field.schema().valueSchema(), val));
            }
        }
        obj.add(fieldName, array);
    }

    private void handleMapField(JsonObject obj, Struct struct, Field field) {
        if(struct.get(field) == null) {
            return;
        }

        JsonObject mapObj = new JsonObject();
        final String fieldName = field.name();

        Map m = struct.getMap(fieldName);
        for(Object _key : m.keySet()) {
            String key = "" + _key;
            Object val = m.get(key);
            if (val == null) continue;
            if (val instanceof Number) {
                mapObj.addProperty(key, (Number) val);
            } else if (val instanceof String) {
                mapObj.addProperty(key, (String) val);
            } else if (val instanceof Boolean) {
                mapObj.addProperty(key, (Boolean) val);
            } else if (val instanceof Struct) {
                mapObj.add(key, toJsonObject(((Struct) val).schema(), val));
            } else {
                throw new DataException("unexpected / unsupported schema type " + field.schema().type() +
                        " - primitive field not one of (Number, String, Boolean), nor a Struct <" + val.getClass().getName() + ">");
            }
        }
        obj.add(fieldName, mapObj);
    }
}
