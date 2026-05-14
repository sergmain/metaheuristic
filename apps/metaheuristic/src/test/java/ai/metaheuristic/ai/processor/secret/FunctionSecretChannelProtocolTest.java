/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Pure-unit tests for {@link FunctionSecretChannel} wire protocol. Uses
 * in-memory streams — no sockets, no Spring, no mocks.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class FunctionSecretChannelProtocolTest {

    /** Build a wire-format frame: [4-byte BE len][payload]. */
    private static byte[] frame(byte[] payload) {
        ByteBuffer bb = ByteBuffer.allocate(4 + payload.length).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(payload.length);
        bb.put(payload);
        return bb.array();
    }

    @Test
    public void test_constantTimeEquals_returnsTrueForIdenticalArrays() {
        byte[] a = "openai_api_key_value".getBytes(StandardCharsets.UTF_8);
        byte[] b = "openai_api_key_value".getBytes(StandardCharsets.UTF_8);
        assertTrue(FunctionSecretChannel.constantTimeEquals(a, b));
    }

    @Test
    public void test_constantTimeEquals_returnsFalseForDifferentContent() {
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] b = "world".getBytes(StandardCharsets.UTF_8);
        assertFalse(FunctionSecretChannel.constantTimeEquals(a, b));
    }

    @Test
    public void test_constantTimeEquals_returnsFalseForDifferentLengths() {
        byte[] a = "short".getBytes(StandardCharsets.UTF_8);
        byte[] b = "longer-string".getBytes(StandardCharsets.UTF_8);
        assertFalse(FunctionSecretChannel.constantTimeEquals(a, b));
    }

    @Test
    public void test_constantTimeEquals_emptyArrays_areEqual() {
        assertTrue(FunctionSecretChannel.constantTimeEquals(new byte[0], new byte[0]));
    }

    @Test
    public void test_readFrame_readsExactBytes() throws IOException {
        byte[] payload = "checkcode-12345".getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(frame(payload));
        byte[] got = FunctionSecretChannel.readFrame(in, 1024);
        assertArrayEquals(payload, got);
    }

    @Test
    public void test_readFrame_throwsWhenLengthExceedsMax() throws IOException {
        byte[] hugePayload = new byte[10_000];
        InputStream in = new ByteArrayInputStream(frame(hugePayload));
        IOException e = assertThrows(IOException.class,
                () -> FunctionSecretChannel.readFrame(in, 4096));
        assertTrue(e.getMessage().contains("667.040"));
    }

    @Test
    public void test_readFrame_throwsWhenLengthIsNegative() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(-1);
        InputStream in = new ByteArrayInputStream(bb.array());
        assertThrows(IOException.class,
                () -> FunctionSecretChannel.readFrame(in, 4096));
    }

    @Test
    public void test_writeFrame_roundTripsThroughReadFrame() throws IOException {
        byte[] payload = "round-trip-payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FunctionSecretChannel.writeFrame(out, payload);
        InputStream in = new ByteArrayInputStream(out.toByteArray());
        byte[] got = FunctionSecretChannel.readFrame(in, 1024);
        assertArrayEquals(payload, got);
    }

    @Test
    public void test_challengeAndWrite_releasesKeyOnMatch() throws IOException {
        String code = "the-correct-token";
        byte[] keyBytes = "sk-the-real-secret".getBytes(StandardCharsets.UTF_8);

        // Function sends the correct code.
        InputStream peerToProcessor = new ByteArrayInputStream(frame(code.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream processorToPeer = new ByteArrayOutputStream();

        FunctionSecretChannel.challengeAndWrite(peerToProcessor, processorToPeer, code, keyBytes);

        // Processor wrote the framed key back.
        byte[] reply = FunctionSecretChannel.readFrame(
                new ByteArrayInputStream(processorToPeer.toByteArray()), 4096);
        assertArrayEquals(keyBytes, reply);
    }

    @Test
    public void test_challengeAndWrite_throwsSecurityException_onMismatch_andWritesNothing() {
        String expected = "real-token";
        byte[] keyBytes = "sk-the-real-secret".getBytes(StandardCharsets.UTF_8);

        // Attacker sends the wrong code.
        InputStream peerToProcessor = new ByteArrayInputStream(
                frame("wrong-token".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream processorToPeer = new ByteArrayOutputStream();

        SecurityException ex = assertThrows(SecurityException.class,
                () -> FunctionSecretChannel.challengeAndWrite(
                        peerToProcessor, processorToPeer, expected, keyBytes));
        assertTrue(ex.getMessage().contains("667.021"));

        // Critically: the key bytes must not have been written.
        assertEquals(0, processorToPeer.size(),
                "key bytes must NOT be written on mismatch");
    }

    @Test
    public void test_challengeAndWrite_mismatch_sameLength_alsoFails() {
        // Same length, different content — exercises the constant-time path.
        String expected = "ABCDEFGH";
        byte[] keyBytes = "sk-secret".getBytes(StandardCharsets.UTF_8);

        InputStream peerToProcessor = new ByteArrayInputStream(
                frame("HGFEDCBA".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream processorToPeer = new ByteArrayOutputStream();

        assertThrows(SecurityException.class,
                () -> FunctionSecretChannel.challengeAndWrite(
                        peerToProcessor, processorToPeer, expected, keyBytes));
        assertEquals(0, processorToPeer.size());
    }
}
