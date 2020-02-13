/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import org.apache.kafka.common.config.ConfigDef;
import org.junit.Assert;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HECSinkConnectorConfigTest {

    private Map<String,String> props = null;

    @BeforeEach
    public void setup() {
        props = new HashMap<>();
        props.put(HECSinkConnectorConfig.INDEX_NAME, "sandbox");
        props.put(HECSinkConnectorConfig.INGEST_TOKEN, "ingest_token");
        props.put(HECSinkConnectorConfig.HEC_ENDPOINT, "http://localhost:8000/hec/test/endpoint");
    }

    @Test
    public void testDefaultConfigurationBuild() {
        System.out.println(HECSinkConnectorConfig.conf().toRst());
        assertTrue(true);
    }

    @Test
    public void testDocumentation() {
        ConfigDef config = HECSinkConnectorConfig.conf();
        for (String key : config.names()){
            assertFalse("Configuration property " + key + " should be documented",
                    config.configKeys().get(key).documentation == null ||
                            "".equals(config.configKeys().get(key).documentation.trim()));
        }
    }

    @Test
    public void testDefaultBufferSize() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(config.getInt(HECSinkConnectorConfig.BUFFER_SIZE), (Integer) 500);
    }

    @Test
    public void testDefaultHecRetryMax() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(config.getInt(HECSinkConnectorConfig.HEC_RETRY_MAX), (Integer) 10);
    }

    @Test
    public void testDefaultHecRetryDelay() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(config.getInt(HECSinkConnectorConfig.HEC_RETRY_DELAY_S), (Integer) 10);
    }

    @Test
    public void testDefaultUseKafkaTimestamp() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(config.getBoolean(HECSinkConnectorConfig.USE_KAFKA_TIMESTAMP), false);
    }

    @Test
    public void testDefaultTopicField() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(config.getString(HECSinkConnectorConfig.TOPIC_FIELD), null);
    }

    @Test
    public void testDefaultPartitionField() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(config.getString(HECSinkConnectorConfig.PARTITION_FIELD), null);
    }

    @Test
    public void testIgnoreParsingErrorsOption() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(false, config.ignoreParsingErrors());
    }

    @Test
    public void testLogParsingErrorsOption() {
        HECSinkConnectorConfig config = new HECSinkConnectorConfig(props);
        Assert.assertEquals(true, config.logParsingErrors());
    }
}