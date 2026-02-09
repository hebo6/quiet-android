package com.example.quietproxy;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 1;

    private Button btnToggle;
    private TextView tvStatus;
    private TextView tvLog;
    private ScrollView scrollLog;
    private boolean serviceRunning;

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("status")) {
                boolean up = intent.getBooleanExtra("status", false);
                serviceRunning = up;
                updateUI();
                if (up) {
                    String ip = intent.getStringExtra("ip");
                    int port = intent.getIntExtra("port", 1080);
                    tvStatus.setText(getString(
                        R.string.status_running, ip, port));
                } else {
                    tvStatus.setText(R.string.status_stopped);
                }
            }
            String msg = intent.getStringExtra(ProxyService.EXTRA_MSG);
            if (msg != null) {
                appendLog(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = (Button) findViewById(R.id.btn_toggle);
        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvLog = (TextView) findViewById(R.id.tv_log);
        scrollLog = (ScrollView) findViewById(R.id.scroll_log);

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serviceRunning) {
                    stopService(new Intent(MainActivity.this,
                        ProxyService.class));
                    serviceRunning = false;
                    updateUI();
                } else {
                    startProxy();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(logReceiver,
            new IntentFilter(ProxyService.ACTION_LOG));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(logReceiver);
    }

    private void startProxy() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        doStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        if (requestCode == REQ_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            doStart();
        }
    }

    private void doStart() {
        startService(new Intent(this, ProxyService.class));
    }

    private void updateUI() {
        btnToggle.setText(serviceRunning ? R.string.stop : R.string.start);
        if (!serviceRunning) {
            tvStatus.setText(R.string.status_stopped);
        }
    }

    private void appendLog(final String msg) {
        tvLog.append(msg + "\n");
        scrollLog.post(new Runnable() {
            @Override
            public void run() {
                scrollLog.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}
