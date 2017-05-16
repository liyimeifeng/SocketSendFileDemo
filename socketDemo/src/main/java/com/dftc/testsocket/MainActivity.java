package com.dftc.testsocket;

import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.dftc.socket.SocketReceiver;
import com.dftc.socket.SocketSender;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private SocketReceiver mSocketReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                testSendDir();
                Log.e(TAG, "点击" );
                testSend();
            }
        });
        testRecv();
    }

    void testRecv() {
        mSocketReceiver = new SocketReceiver(8888,
                Environment.getExternalStorageDirectory().getPath() + File.separator + "SocketReceiver",
                new SocketReceiver.OnSocketReceiveListener() {

                    @Override
                    public void onNetworkError() {
                        Log.e(TAG, "onNetworkError");
                    }

                    @Override
                    public void onReceiveStart(String fileName, String senderAddr) {
                        Log.e(TAG, "onReceiveStart fileName:" + fileName + ",senderAddr:" + senderAddr);
                    }

                    @Override
                    public void onProgress(String fileName, long fileSize, long recvSize) {
                        Log.e(TAG, "onProgress fileName:" + fileName + ",fileSize:" + fileSize + ",recvSize:" + recvSize);
                    }

                    @Override
                    public void onReceiveComplete(String fileName, int errorCode) {
                        Log.e(TAG, "onReceiveComplete fileName:" + fileName + ",errorCode:" + errorCode);
                    }
                });
        mSocketReceiver.startServer();
    }

    void testSend() {
        SocketSender mSocketSender = new SocketSender("192.168.3.15", 8888);
        mSocketSender.send(
                Environment.getExternalStorageDirectory().getPath() + File.separator + "sender/QQ.apk",
                new SocketSender.OnSocketSendListener() {

                    @Override
                    public void onProgress(long fileSize, long sendSize) {
                        Log.d(TAG, "onProgress fileSize:" + fileSize + ",sendSize:" + sendSize);
                    }

                    @Override
                    public void onSendComplete(int errorCode) {
                        Log.d(TAG, "onSendComplete errorCode:" + errorCode);
                    }
                });
    }

    void testSendDir() {
        SocketSender mSocketSender = new SocketSender("192.168.3.15", 8888);
        mSocketSender.sendDir(
                Environment.getExternalStorageDirectory().getPath() + File.separator + "sender",
                new SocketSender.OnSocketSendDirListener() {

                    @Override
                    public void onProgress(String filePath, long fileSize, long sendSize) {
                        Log.d(TAG, "onProgress filePath:" + filePath + "fileSize:" + fileSize + ",sendSize:" + sendSize);
                    }

                    @Override
                    public void onSendComplete(String filePath, int errorCode) {
                        Log.d(TAG, "onSendComplete filePath:" + filePath + "errorCode:" + errorCode);
                    }

                });
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocketReceiver.onDestroy();
    }
}
