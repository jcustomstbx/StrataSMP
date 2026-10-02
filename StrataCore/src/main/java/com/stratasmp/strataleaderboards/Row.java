package com.stratasmp.strataleaderboards;

import java.util.UUID;

/** One line of a board. The name may be empty when only the player's id is known, the service fills it in. */
public record Row(UUID playerId, String name, long value) {

    public Row withName(String newName) {
        return new Row(playerId, newName, value);
    }
}
