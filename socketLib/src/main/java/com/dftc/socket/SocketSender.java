package com.dftc.socket;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * Created by xuqiqiang on 2017/5/12.
 */

public class SocketSender {
    private static final String TAG = SocketSender.class.getSimpleName();
    private String mHost;
    private int mPort;

    private static final int ERROR_COMPLETE = 0;
    private static final int ERROR_File_ERROR = 1;
    private static final int ERROR_SERVER_EXIST = 2;
    private static final int ERROR_SERVER_DISK_ERROR = 3;
    private static final int ERROR_UNKNOWN = 4;

    private ExecutorService mExecutorService = Executors.newFixedThreadPool(3);

    /**
     * 发送文件的监听
     */
    public interface OnSocketSendListener {
        void onProgress(long fileSize, long sendSize);

        void onSendComplete(int errorCode);
    }

    /**
     * 发送文件夹的监听
     */
    public interface OnSocketSendDirListener {
        void onProgress(String filePath, long fileSize, long sendSize);

        void onSendComplete(String filePath, int errorCode);
    }

    public SocketSender(String host, int port) {
        mHost = host;
        mPort = port;
    }

    /**
     * 外部调用send
     * @param filePath
     * @param listener
     */
    public void send(final String filePath, final OnSocketSendListener listener) {
        send("", filePath, listener);
    }

//    private String relativePath = "";

    public void send(final String relativePath,final String filePath, final OnSocketSendListener listener) {
        mExecutorService.execute(new Thread() {
            public void run() {
                int errorCode = ERROR_UNKNOWN;
                File file = new File(filePath);
                if (file.isFile()) {     //文件存在，不是文件夹，执行发送文件
                    try {
                        errorCode = sendFile(relativePath, file, listener);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    errorCode = ERROR_File_ERROR;
                    Log.e(TAG, filePath + " is not a file!");    //要发送的文件不存在
                }
                if (listener != null)
                    listener.onSendComplete(errorCode);
            }
        });

    }

    public void sendDir(final String dirPath, final OnSocketSendDirListener listener) {
        File dir = new File(dirPath);
        if(!dir.isDirectory())
            return;
        sendDir(dir.getName(), dirPath, listener);
    }

    private void sendDir(final String relativePath, final String dirPath, final OnSocketSendDirListener listener) {
        File dir = new File(dirPath);
        File[] list = dir.listFiles();
        for (final File file : list) {
            if (file.isFile()) {
                send(relativePath, file.getAbsolutePath(), new OnSocketSendListener() {

                    @Override
                    public void onProgress(long fileSize, long sendSize) {
                        listener.onProgress(file.getAbsolutePath(), fileSize, sendSize);
                    }

                    @Override
                    public void onSendComplete(int errorCode) {
                        listener.onSendComplete(file.getAbsolutePath(), errorCode);
                    }
                });
            } else if (file.isDirectory()) {
                sendDir(relativePath + File.separator + file.getName(), file.getAbsolutePath(), listener);
            }
        }
    }


    /**
     * 确认文件存在，就发送文件
     * @param relativePath
     * @param file
     * @param listener
     * @return
     * @throws Exception
     */
    private int sendFile(String relativePath, File file, OnSocketSendListener listener) throws Exception {
        int errorCode = ERROR_COMPLETE;
        Log.e(TAG, "sendFile: =================" );
        Socket sock = new Socket(mHost, mPort);       //在此创建Socket客户端对象
        Log.e(TAG, "sendFile: =================" );

        FileInputStream fis = new FileInputStream(file);
        try {
            OutputStream sockOut = sock.getOutputStream();

            if (!sendFileInfo(sockOut, relativePath, file))   //由sockout输出流把文件信息发送出去，没发送出去就返回"ERROR_UNKNOWN"
                return ERROR_UNKNOWN;

            long fileSize = file.length();
            long sendSize = 0;

            String serverInfo = getServInfoBack(sock);    //得到socket的输入流，也就是服务端的发送的信息
            if (SocketReceiver.MESSAGE_SEND_NOW.equals(serverInfo)) {   //如果和接收端发送的信息一致
                byte[] bufFile = new byte[1024];
                int len;
                try {
                    while (true) {
                        len = fis.read(bufFile);    //读取文件输入流
                        if (len != -1) {
                            sockOut.write(bufFile, 0, len);   //由输出流写入
                            sendSize += len;                  //由每次写入的字节长度累加得出总发送的文件长度
                            if (listener != null)
                                listener.onProgress(fileSize, sendSize);    //回调进度接口，文件总长度，发送出的长度
                            if (sendSize >= fileSize)
                                break;
                        } else {
                            break;
                        }
                    }
                } finally {
                    sock.shutdownOutput();
                }

            } else {                    //接收的服务端发送的信息不是MESSAGE_SEND_NOW
                Log.e(TAG, "Server info:" + serverInfo);
                if (SocketReceiver.MESSAGE_EXIST.equals(serverInfo))    //接收的退出信息
                    errorCode = ERROR_SERVER_EXIST;
                else if (SocketReceiver.MESSAGE_DISK_ERROR.equals(serverInfo))   //接收的是硬盘错误的信息
                    errorCode = ERROR_SERVER_DISK_ERROR;
            }

            Log.d(TAG, "Server info:" + getServInfoBack(sock));
        } finally {
            fis.close();
            sock.close();
        }

        return errorCode;
    }

    /**
     * 把文件信息包装成JsonObject对象发送出去
     * @param sockOut
     * @param relativePath
     * @param file
     * @return
     */
    private boolean sendFileInfo(OutputStream sockOut, String relativePath, File file) {
        String fileName = file.getName();

        JSONObject json = new JSONObject();
        try {
            json.put("fileName", fileName);
            json.put("relativePath", relativePath);
            json.put("fileSize", file.length());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String jsonStr = json.toString();

        Log.d(TAG, "send:" + jsonStr);
        try {
            sockOut.write(jsonStr.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 根据客户端socket对象得到输入流，读取
     * @param sock
     * @return
     * @throws Exception
     */
    public String getServInfoBack(Socket sock) throws Exception {
        InputStream sockIn = sock.getInputStream();
        byte[] bufIn = new byte[1024];
        int lenIn = sockIn.read(bufIn);
        String info = new String(bufIn, 0, lenIn);
        return info;
    }

}