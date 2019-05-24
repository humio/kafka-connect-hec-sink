/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec.converter;

import com.humio.connect.hec.Record;
import org.apache.kafka.connect.sink.SinkRecord;

public interface RecordConverter {
    Record convert(SinkRecord record);
}
