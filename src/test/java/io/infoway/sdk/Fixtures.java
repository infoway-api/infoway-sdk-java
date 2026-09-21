package io.infoway.sdk;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads verbatim production captures from src/test/resources/fixtures.
 * See fixtures/SOURCES.md for how each file was captured.
 */
final class Fixtures {

    private Fixtures() {}

    static String load(String relativePath) {
        String resource = "/fixtures/" + relativePath;
        try (InputStream in = Fixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
