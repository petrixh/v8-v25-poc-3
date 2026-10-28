/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.ui.artifacts.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * The bounded read of what is left of a refused or failed upload
 * ({@link AbstractFileTransferHandler#drain}): no servlet is needed, it is a
 * function of an {@link InputStream}.
 */
class DrainRefusedUploadBodyTest {

    private static final long NO_TIME_LIMIT = 60_000;

    /** A stream of {@code size} bytes that counts what was read from it. */
    private static final class CountingStream extends InputStream {

        private final long size;
        private final AtomicLong read = new AtomicLong();

        CountingStream(final long size) {
            this.size = size;
        }

        @Override
        public int read() {
            return read.get() < size ? (int) (read.getAndIncrement() & 0x7f) : -1;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) {
            final long left = size - read.get();
            if (left <= 0) {
                return -1;
            }
            final int n = (int) Math.min(len, left);
            read.addAndGet(n);
            return n;
        }
    }

    @Test
    void readsAStreamThatEndsBeforeTheBoundsToItsEnd() {
        final ByteArrayInputStream in = new ByteArrayInputStream(new byte[300_000]);

        assertThat(AbstractFileTransferHandler.drain(in, 1_000_000, NO_TIME_LIMIT)).isEqualTo(300_000);
        assertThat(in.available()).isZero();
    }

    @Test
    void stopsAtTheByteCapAndLeavesTheRestUnread() {
        final CountingStream in = new CountingStream(10_000_000);

        final long read = AbstractFileTransferHandler.drain(in, 1_000_000, NO_TIME_LIMIT);

        // whole buffers are read, so the cap may be passed by less than one buffer
        assertThat(read).isBetween(1_000_000L, 1_000_000L + 64 * 1024);
        assertThat(in.read.get()).isEqualTo(read).isLessThan(10_000_000);
    }

    @Test
    void stopsAtTheTimeCapOfAStreamThatNeverEnds() {
        final long[] reads = { 0 };
        final InputStream trickle = new InputStream() {

            @Override
            public int read() {
                return 0;
            }

            @Override
            public int read(final byte[] b, final int off, final int len) {
                reads[0]++;
                try {
                    Thread.sleep(20);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                return 1;
            }
        };

        final long start = System.nanoTime();
        final long read = AbstractFileTransferHandler.drain(trickle, Long.MAX_VALUE, 200);
        final long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isBetween(200L, 5_000L);
        assertThat(read).isEqualTo(reads[0]).isPositive();
    }

    @Test
    void survivesAnIoExceptionAndReturnsWhatWasRead() {
        final long read = AbstractFileTransferHandler.drain(failingAfter(5, new IOException("connection reset")),
                1_000_000, NO_TIME_LIMIT);

        assertThat(read).isEqualTo(5);
    }

    @Test
    void survivesARuntimeException() {
        final long read = AbstractFileTransferHandler.drain(failingAfter(7, new IllegalStateException("recycled")),
                1_000_000, NO_TIME_LIMIT);

        assertThat(read).isEqualTo(7);
    }

    @Test
    void aNullStreamReadsNothing() {
        assertThat(AbstractFileTransferHandler.drain(null, 1_000_000, NO_TIME_LIMIT)).isZero();
    }

    @Test
    void theBoundsAreOneHundredMegabytesAndSixtySeconds() {
        assertThat(AbstractFileTransferHandler.MAX_DRAIN_BYTES).isEqualTo(100L * 1024 * 1024);
        assertThat(AbstractFileTransferHandler.MAX_DRAIN_MILLIS).isEqualTo(60_000);
    }

    private static InputStream failingAfter(final int bytes, final Exception failure) {
        return new InputStream() {

            private int delivered;

            @Override
            public int read() {
                return 0;
            }

            @Override
            public int read(final byte[] b, final int off, final int len) throws IOException {
                if (delivered >= bytes) {
                    if (failure instanceof IOException io) {
                        throw io;
                    }
                    throw (RuntimeException) failure;
                }
                delivered += bytes;
                return bytes;
            }
        };
    }
}
