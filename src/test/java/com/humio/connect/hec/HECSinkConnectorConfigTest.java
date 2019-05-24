/*
    Copyright (c) Humio, 2019
    See file "LICENSE.md" for terms of usage and
    redistribution.
*/

package com.humio.connect.hec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitPlatform.class)
class HECSinkConnectorConfigTest {

    @Test
    @DisplayName("build config doc (no test)")
    public void doc() {
        System.out.println(HECSinkConnectorConfig.conf().toRst());
        assertTrue(true);
    }

}