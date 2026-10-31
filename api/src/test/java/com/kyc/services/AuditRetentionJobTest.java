package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kyc.config.KycProperties;
import com.kyc.repositories.AuditEventRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditRetentionJobTest {

    private final AuditEventRepository events = mock(AuditEventRepository.class);

    @Test
    void deletesRowsOlderThanTheConfiguredMonths() {
        when(events.deleteByCreatedAtBefore(any())).thenReturn(4);
        AuditRetentionJob job = job(6);
        Instant now = Instant.parse("2026-10-31T03:00:00Z");

        int deleted = job.purge(now);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(events).deleteByCreatedAtBefore(cutoff.capture());
        assertEquals(4, deleted);
        assertEquals(now.atZone(ZoneOffset.UTC).minusMonths(6).toInstant(), cutoff.getValue());
    }

    @Test
    void skipsWhenRetentionIsNotPositive() {
        AuditRetentionJob job = job(0);

        int deleted = job.purge(Instant.parse("2026-10-31T03:00:00Z"));

        assertEquals(0, deleted);
        verify(events, never()).deleteByCreatedAtBefore(any());
    }

    private AuditRetentionJob job(int months) {
        KycProperties properties = mock(KycProperties.class);
        when(properties.audit()).thenReturn(new KycProperties.AuditRetention(months));
        return new AuditRetentionJob(events, properties);
    }
}
