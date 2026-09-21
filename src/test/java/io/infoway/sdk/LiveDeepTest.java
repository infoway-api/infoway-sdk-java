package io.infoway.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Opt-in production deep probe. See {@link LiveDeepProbe}.
 *
 * <pre>
 *   INFOWAY_LIVE_TESTS=1 INFOWAY_API_KEY=&lt;key&gt; mvn test -Dtest=LiveDeepTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "INFOWAY_LIVE_TESTS", matches = "1")
@EnabledIfEnvironmentVariable(named = "INFOWAY_API_KEY", matches = ".+")
class LiveDeepTest {

    @Test
    void deepProbeHappyPathsBadParamsAndForgedKey() throws Exception {
        LiveDeepProbe probe = new LiveDeepProbe(
                System.getenv("INFOWAY_API_KEY"),
                System.getenv("INFOWAY_BAD_API_KEY"));
        int failed = probe.run();
        assertEquals(0, failed, failed + " live cases failed — see stdout");
    }
}
