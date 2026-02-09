package com.example.quietproxy;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Bidirectional relay between two streams using blocking I/O.
 * Each direction runs in its own thread.
 */
public class RelayTask implements Runnable {
    private static final int BUF_SIZE = 8192;

    private final InputStream in;
    private final OutputStream out;
    private final Runnable onDone;

    public RelayTask(InputStream in, OutputStream out, Runnable onDone) {
        this.in = in;
        this.out = out;
        this.onDone = onDone;
    }

    @Override
    public void run() {
        byte[] buf = new byte[BUF_SIZE];
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            onDone.run();
        }
    }
}
