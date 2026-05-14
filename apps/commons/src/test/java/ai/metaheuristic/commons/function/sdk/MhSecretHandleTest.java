/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.commons.function.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Pure-unit tests for {@link MhSecretHandle}. No sockets, no IO.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class MhSecretHandleTest {

    @Test
    public void test_bytes_returnsTheBuffer() {
        byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
        try (MhSecretHandle h = new MhSecretHandle(payload.clone())) {
            assertEquals(3, h.length());
            assertEquals('a', h.bytes()[0]);
        }
    }

    @Test
    public void test_asString_decodesUtf8() {
        try (MhSecretHandle h = new MhSecretHandle("sk-abc".getBytes(StandardCharsets.UTF_8))) {
            assertEquals("sk-abc", h.asString());
        }
    }

    @Test
    public void test_close_zerosTheBuffer() {
        byte[] inner = "abc".getBytes(StandardCharsets.UTF_8);
        MhSecretHandle h = new MhSecretHandle(inner);
        assertEquals('a', inner[0]);
        h.close();
        assertEquals(0, inner[0]);
        assertEquals(0, inner[1]);
        assertEquals(0, inner[2]);
    }

    @Test
    public void test_bytes_afterClose_throws() {
        MhSecretHandle h = new MhSecretHandle("abc".getBytes(StandardCharsets.UTF_8));
        h.close();
        IllegalStateException ex = assertThrows(IllegalStateException.class, h::bytes);
        assertTrue(ex.getMessage().contains("0667.030"));
    }

    @Test
    public void test_close_isIdempotent() {
        MhSecretHandle h = new MhSecretHandle("abc".getBytes(StandardCharsets.UTF_8));
        h.close();
        // second close should not throw
        h.close();
    }
}
