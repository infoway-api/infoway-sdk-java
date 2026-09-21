package io.infoway.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ApiKeysTest {

    @Test
    void resolveKeepsExplicitKey() {
        assertEquals("explicit", ApiKeys.resolve("explicit"));
    }

    @Test
    void requireThrowsWhenBlankAndEnvMissing() {
        String env = System.getenv("INFOWAY_API_KEY");
        assumeTrue(env == null || env.isBlank(), "INFOWAY_API_KEY is set in this environment");
        assertThrows(IllegalArgumentException.class, () -> ApiKeys.require(""));
        assertThrows(IllegalArgumentException.class, () -> ApiKeys.require(null));
    }
}
