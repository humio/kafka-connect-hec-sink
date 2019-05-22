package com.humio.connect.hec.converter;

import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;

public interface RecordConverter {
    Record convert(SinkRecord record);
}
