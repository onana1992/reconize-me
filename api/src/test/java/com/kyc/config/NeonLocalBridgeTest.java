package com.kyc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NeonLocalBridgeTest {

    @Test
    void localUrlDropsSslAndKeepsTheDatabase() {
        String local = NeonLocalBridge.localJdbcUrl(
                "jdbc:postgresql://ep-example.c-6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                54321);
        assertEquals(
                "jdbc:postgresql://127.0.0.1:54321/neondb?sslmode=disable&gssEncMode=disable",
                local);
    }

    @Test
    void hostOfIgnoresMysqlAndBlank() {
        assertNull(NeonLocalBridge.hostOf(null));
        assertNull(NeonLocalBridge.hostOf("jdbc:mysql://127.0.0.1:3306/reconizme"));
        assertEquals(
                "ep-example.c-6.us-east-2.aws.neon.tech",
                NeonLocalBridge.hostOf("jdbc:postgresql://ep-example.c-6.us-east-2.aws.neon.tech:5432/neondb"));
    }
}
