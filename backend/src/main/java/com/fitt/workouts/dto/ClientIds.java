package com.fitt.workouts.dto;

import java.util.Locale;

// The id a client makes for a start, an added exercise or a logged set, and sends
// again on every retry of that one action. A UUID in its usual 36 character form.
public final class ClientIds {

    public static final String PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public static final String MESSAGE = "Client id must be a UUID";

    private ClientIds() {}

    // Stored and compared in lower case, so the same id matches whichever case the
    // client writes it in. Swift writes upper case, most other platforms lower.
    public static String normalize(String clientId) {
        return clientId == null ? null : clientId.toLowerCase(Locale.ROOT);
    }
}
