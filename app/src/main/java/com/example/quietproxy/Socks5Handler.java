package com.example.quietproxy;

import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Socks5Handler implements Runnable {
    private static final String TAG = "socks5";

    private static final byte SOCKS_VERSION = 5;
    private static final byte AUTH_NOAUTH = 0;
    private static final byte CMD_CONNECT = 1;
    private static final byte ADDR_IPV4 = 1;
    private static final byte ADDR_DOMAINNAME = 3;
    private static final byte CONN_SUCCEEDED = 0;
    private static final byte CONN_FAILED = 1;

    public interface Logger {
        void log(String msg);
    }

    private final org.quietmodem.Quiet.Socket clientSocket;
    private final Logger logger;

    public Socks5Handler(org.quietmodem.Quiet.Socket clientSocket, Logger logger) {
        this.clientSocket = clientSocket;
        this.logger = logger;
    }

    private void uiLog(String msg) {
        Log.i(TAG, msg);
        if (logger != null) logger.log("[socks5] " + msg);
    }

    @Override
    public void run() {
        try {
            handle();
        } catch (Exception e) {
            Log.e(TAG, "handler error", e);
            uiLog("error: " + e.getMessage());
        } finally {
            close(clientSocket);
            uiLog("connection closed");
        }
    }

    private void handle() throws IOException {
        InputStream cIn = clientSocket.getInputStream();
        OutputStream cOut = clientSocket.getOutputStream();
        DataInputStream dis = new DataInputStream(cIn);

        // --- Auth negotiation ---
        int ver = dis.readUnsignedByte();
        if (ver != SOCKS_VERSION) {
            uiLog("bad socks version: " + ver);
            return;
        }
        int nmethods = dis.readUnsignedByte();
        byte[] methods = new byte[nmethods];
        dis.readFully(methods);
        boolean noAuthOk = false;
        for (byte m : methods) {
            if (m == AUTH_NOAUTH) { noAuthOk = true; break; }
        }
        if (!noAuthOk) {
            uiLog("no acceptable auth method");
            return;
        }
        cOut.write(new byte[]{SOCKS_VERSION, AUTH_NOAUTH});
        cOut.flush();
        uiLog("auth ok");

        // --- Connect request ---
        ver = dis.readUnsignedByte();
        if (ver != SOCKS_VERSION) return;
        int cmd = dis.readUnsignedByte();
        if (cmd != CMD_CONNECT) {
            uiLog("unsupported cmd: " + cmd);
            return;
        }
        dis.readUnsignedByte(); // reserved
        int addrType = dis.readUnsignedByte();

        String host;
        if (addrType == ADDR_IPV4) {
            byte[] ipv4 = new byte[4];
            dis.readFully(ipv4);
            host = (ipv4[0] & 0xFF) + "." + (ipv4[1] & 0xFF) + "."
                 + (ipv4[2] & 0xFF) + "." + (ipv4[3] & 0xFF);
        } else if (addrType == ADDR_DOMAINNAME) {
            int len = dis.readUnsignedByte();
            byte[] domain = new byte[len];
            dis.readFully(domain);
            host = new String(domain, "UTF-8");
        } else {
            uiLog("unsupported addr type: " + addrType);
            return;
        }
        int port = dis.readUnsignedShort();

        uiLog("connecting to " + host + ":" + port);

        // --- Connect to remote via native socket ---
        Socket remoteSocket;
        try {
            remoteSocket = new Socket(host, port);
        } catch (IOException e) {
            Log.e(TAG, "connect failed: " + host + ":" + port, e);
            uiLog("connect failed: " + host + ":" + port + " (" + e.getMessage() + ")");
            sendReply(cOut, CONN_FAILED);
            return;
        }

        sendReply(cOut, CONN_SUCCEEDED);
        uiLog("connected to " + host + ":" + port + ", relaying...");

        // --- Relay data bidirectionally ---
        relay(clientSocket, remoteSocket);
    }

    private void sendReply(OutputStream out, byte status) throws IOException {
        byte[] reply = new byte[10];
        reply[0] = SOCKS_VERSION;
        reply[1] = status;
        reply[2] = 0; // reserved
        reply[3] = ADDR_IPV4;
        // bind addr + port left as 0.0.0.0:0
        out.write(reply);
        out.flush();
    }

    private void relay(final org.quietmodem.Quiet.Socket client,
                       final Socket remote) throws IOException {
        final InputStream cIn = client.getInputStream();
        final OutputStream cOut = client.getOutputStream();
        final InputStream rIn = remote.getInputStream();
        final OutputStream rOut = remote.getOutputStream();

        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                close(client);
                close(remote);
            }
        };

        Thread t1 = new Thread(new RelayTask(cIn, rOut, cleanup), "relay-c2r");
        Thread t2 = new Thread(new RelayTask(rIn, cOut, cleanup), "relay-r2c");
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ignored) {
        }
    }

    private static void close(org.quietmodem.Quiet.Socket s) {
        try { s.close(); } catch (Exception ignored) {}
    }

    private static void close(Socket s) {
        try { s.close(); } catch (Exception ignored) {}
    }
}
