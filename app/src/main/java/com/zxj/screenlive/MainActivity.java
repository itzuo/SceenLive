package com.zxj.screenlive;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {


    private ScreenLive mScreenLive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mScreenLive.onActivityResult(requestCode,resultCode,data);
    }

    public void startLive(View view) {
        mScreenLive = new ScreenLive();
        mScreenLive.startLive(this,
                "rtmp://139.224.136.101/myapp");
//                "rtmp://192.168.20.104/myapp/mystream");
//                "rtmp://txy.live-push.bilivideo.com/live-bvc/?streamname=live_592442300_47961724&key=259dd282e59e23a3836939dd41d3cb5a&schedule=rtmp&pflag=1");
    }

    public void stopLive(View view) {
        mScreenLive.stopLive();
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            }, 1);

        }
        return false;
    }
}
