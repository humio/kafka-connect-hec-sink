package com.humio.connect.hec;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkConnector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HECSinkConnector extends SinkConnector {
    private static Logger log = LogManager.getLogger(HECSinkConnector.class);
    private Map<String, String> configProperties;
    private static Metrics metrics = new Metrics();

    @Override
    public String version() {
        return VersionUtil.getVersion();
    }

    @Override
    public void start(Map<String, String> props) {
      try {
          log.debug("hec: starting HECSinkConnector => " + props);
          configProperties = props;
          new HECSinkConnectorConfig(props);
      } catch (ConfigException e) {
          throw new ConnectException("Couldn't start HECSinkConnector due to configuration error",e);
      }
      metrics.startConsoleReporter();
    }

    @Override
    public Class<? extends Task> taskClass() {
        return HECSinkTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        List<Map<String, String>> taskConfigs = new ArrayList<>();
        Map<String, String> taskProps = new HashMap<>();
        taskProps.putAll(configProperties);
        for (int i = 0; i < maxTasks; i++) {
          taskConfigs.add(taskProps);
        }
        return taskConfigs;
    }

    @Override
    public void stop() {
        log.debug("stop");
    }

    @Override
    public ConfigDef config() {
        return HECSinkConnectorConfig.conf();
    }
}
