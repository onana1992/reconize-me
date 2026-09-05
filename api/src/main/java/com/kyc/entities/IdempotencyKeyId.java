package com.kyc.entities;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IdempotencyKeyId implements Serializable {

    private UUID organizationId;
    private String keyValue;
}
