package com.example.quietproxy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.quietmodem.Quiet.FrameReceiverConfig;
import org.quietmodem.Quiet.FrameTransmitterConfig;
import org.quietmodem.Quiet.InetAddress;
import org.quietmodem.Quiet.NetworkInterface;
import org.quietmodem.Quiet.NetworkInterfaceConfig;
import org.quietmodem.Quiet.ServerSocket;
import org.quietmodem.Quiet.Socket;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    public static final String ACTION_LOG = "com.example.quietproxy.LOG";
    public static final String EXTRA_MSG = "msg";

    private static final String PROFILE = "cable-64k";
    private static final String LOCAL_IP = "192.168.0.8";
    private static final String NETMASK = "255.255.255.0";
    private static final String GATEWAY = "192.168.0.1";
    private static final int PORT = 1080;

    private static final int SAMPLE_RATE = 48000;

    private volatile boolean running;
    private NetworkInterface networkInterface;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        log("service starting...");
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runServer();
            }
        }, "proxy-accept");
        acceptThread.start();
        return START_NOT_STICKY;
    }

    private void runServer() {
        try {
            log("initializing audio modem (profile: " + PROFILE + ")");
            FrameTransmitterConfig txConf =
                new FrameTransmitterConfig(this, PROFILE);
            txConf.setSampleRate(SAMPLE_RATE);
            FrameReceiverConfig rxConf =
                new FrameReceiverConfig(this, PROFILE);
            rxConf.setSampleRate(SAMPLE_RATE);

            log("creating network interface...");
            NetworkInterfaceConfig netConf = new NetworkInterfaceConfig(
                rxConf, txConf,
                InetAddress.getByName(LOCAL_IP),
                InetAddress.getByName(NETMASK),
                InetAddress.getByName(GATEWAY));

            networkInterface = new NetworkInterface(netConf);
            log("network interface up: " + LOCAL_IP);

            serverSocket = new ServerSocket(PORT, 5,
                InetAddress.getByName(LOCAL_IP));
            log("SOCKS5 proxy listening on " + LOCAL_IP + ":" + PORT);

            sendStatus(true);

            while (running) {
                Socket client = serverSocket.accept();
                log("accepted connection");
                new Thread(new Socks5Handler(client), "socks5").start();
            }
        } catch (Exception e) {
            if (running) {
                Log.e(TAG, "server error", e);
                log("error: " + e.getMessage());
            }
        } finally {
            running = false;
            cleanup();
            sendStatus(false);
            stopSelf();
        }
    }

    private void cleanup() {
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
        if (networkInterface != null) {
            try { networkInterface.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        log("service stopping...");
        running = false;
        cleanup();
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void log(String msg) {
        Log.i(TAG, msg);
        Intent i = new Intent(ACTION_LOG);
        i.putExtra(EXTRA_MSG, msg);
        sendBroadcast(i);
    }

    private void sendStatus(boolean isRunning) {
        Intent i = new Intent(ACTION_LOG);
        i.putExtra("status", isRunning);
        i.putExtra("ip", LOCAL_IP);
        i.putExtra("port", PORT);
        sendBroadcast(i);
    }
}
