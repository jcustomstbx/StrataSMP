package com.stratasmp.strataduels.challenge;

import java.util.UUID;

public record PendingChallenge(UUID challenger, int challengerKit, UUID target, long expiresAtMillis) {
}
