package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.repositories.AuditEventRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditEventRepository events;
    private final KycProperties properties;

    public AuditRetentionJob(AuditEventRepository events, KycProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void purge() {
        purge(Instant.now());
    }

    int purge(Instant now) {
        int months = properties.audit().retentionMonths();
        if (months < 1) {
            log.warn("audit retention skipped: KYC_AUDIT_RETENTION_MONTHS must be at least 1, was {}", months);
            return 0;
        }
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(months).toInstant();
        int deleted = events.deleteByCreatedAtBefore(cutoff);
        log.info("audit retention deleted={} months={} cutoff={}", deleted, months, cutoff);
        return deleted;
    }
}
