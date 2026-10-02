package com.stratasmp.stratateams;

import java.util.UUID;

public record PendingInvite(UUID teamId, long expiresAt) {
}
