package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private Instant createdAt;

    public Organization(UUID id, String name, String slug, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.createdAt = createdAt;
    }
}
