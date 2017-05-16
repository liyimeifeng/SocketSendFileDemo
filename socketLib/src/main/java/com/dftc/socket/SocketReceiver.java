package com.dftc.socket;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Created by xuqiqiang on 2017/5/12.
 */

public class SocketReceiver {
    private static final String TAG = SocketReceiver.class.getSimpleName();

    public static final String MESSAGE_EXIST = "EXIST";
    public static final String MESSAGE_DISK_ERROR = "DISK_ERROR";
    public static final String MESSAGE_SEND_NOW = "SEND_NOW";
    public static final String MESSAGE_COMPLETE = "COMPLETE";

    public static final int ERROR_COMPLETE = 0;
    public static final int ERROR_UNKNOWN = 1;

    private int mPort;
    private String mDirPath;
    private OnSocketReceiveListener mListener;

    public interface OnSocketReceiveListener {
        void onNetworkError();

        void onReceiveStart(String fileName, String senderAddr);

        void onProgress(String fileName, long fileSize, long recvSize);

        void onReceiveComplete(String fileName, int errorCode);
    }

    public SocketReceiver(int port, String dirPath, OnSocketReceiveListener listener) {
        mPort = port;
        mDirPath = dirPath;
        mListener = listener;
    }

    /**
     * 打开服务端
     */
    public void startServer() {
        new Thread() {
            public void run() {
                ServerSocket serverSocket;
                try {
                    serverSocket = new ServerSocket(mPort);
                } catch (IOException e) {
                    e.printStackTrace();
                    if (mListener != null)
                        mListener.onNetworkError();    //回调网络异常接口
                    return;
                }
                while (true) {
                    Socket sock = null;
                    try {
                        Log.e(TAG, "等待客户端链接......."  );
                        sock = serverSocket.accept();   //阻塞，直到客户端连进来，获得Socket对象
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (mListener != null)
                            mListener.onNetworkError();
                    }

                    Log.e(TAG, "开始检测" );
                    if (checkHost(sock)) {   //如果本地Ip和远程Ip相同，关闭退出
                        Log.d(TAG, "Exit!");
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return;
                    }

                    new Thread(new SocketReceiveTask(sock)).start();
                }
            }
        }.start();
    }

    /**
     * 检查socket对象的服务端和本地的Ip地址，相同就是同一台主机，
     * @param sock
     * @return
     */
    private boolean checkHost(Socket sock) {
        String ip = sock.getInetAddress().getHostAddress();  //获得Inetaddress对象后得到另一端Ip地址
        Log.d(TAG, "ip: " + ip);
        String localHost = null;
        try {
            localHost = InetAddress.getLocalHost().getHostAddress();  //获得本地的Ip地址
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "localHost: " + localHost);
        return TextUtils.equals(ip, localHost);   //对比本地Ip和远程Ip
    }

    class SocketReceiveTask implements Runnable {
        private Socket sock;
        private File mFile;
        private long mFileSize;

        SocketReceiveTask(Socket sock) {
            this.sock = sock;
        }

        public void run() {
            String ip = sock.getInetAddress().getHostAddress();   //得到远程Ip
            int errorCode = ERROR_COMPLETE;
            try {
                Log.d(TAG, "Receive from: " + ip);
                InputStream sockIn = sock.getInputStream();
                mFile = getClientFile(sockIn);
                if (mFile == null) {
                    return;
                }
                if (mListener != null)
                    mListener.onReceiveStart(mFile.getName(), ip);

                long recvSize = 0;
                FileOutputStream fos = new FileOutputStream(mFile);
                byte[] bufFile = new byte[1024 * 1024];
                int len;
                try {
                    while (true) {
                        len = sockIn.read(bufFile);
                        if (len != -1) {
                            fos.write(bufFile, 0, len);
                            recvSize += len;
                            if (mListener != null)
                                mListener.onProgress(mFile.getName(), mFileSize, recvSize);
                            if (recvSize >= mFileSize)
                                break;
                        } else {
                            break;
                        }
                    }
                } finally {
                    fos.close();
                }
                errorCode = ERROR_COMPLETE;
                writeOutInfo(sock, MESSAGE_COMPLETE);
                Log.d(TAG, "Receive complete!");
            } catch (Exception e) {
                e.printStackTrace();
                if (mListener != null && mFile != null) {
                    mListener.onNetworkError();
                }
                errorCode = ERROR_UNKNOWN;

            } finally {
                try {
                    sock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mListener != null && mFile != null)
                    mListener.onReceiveComplete(mFile.getName(), errorCode);
            }
        }

        public void writeOutInfo(Socket sock, String infoStr) throws Exception {
            OutputStream sockOut = sock.getOutputStream();
            sockOut.write(infoStr.getBytes());
        }

        public File getClientFile(InputStream sockIn) throws Exception {
            byte[] bufFile = new byte[1024];
            int lenInfo = sockIn.read(bufFile);
            String jsonStr = new String(bufFile, 0, lenInfo);   //把输入流包装成String类型再包装成JsonObject对象从中得出文件名字等
            String fileName = null;
            String relativePath = null;
            try {
                JSONObject json = new JSONObject(jsonStr);
                fileName = json.getString("fileName");
                mFileSize = json.getLong("fileSize");
                relativePath = json.getString("relativePath");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            File dir = new File(mDirPath);  //文件目录不存在就创建
            if (!dir.exists())
                dir.mkdirs();
            if (!TextUtils.isEmpty(relativePath)) {
                dir = new File(mDirPath + File.separator + relativePath);
                String[] dirNames = relativePath.split(File.separator);
                String path = mDirPath;
                for (String name : dirNames) {
                    new File(path += File.separator + name).mkdirs();
                }
            }

            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    if (!f.isDirectory() && f.getName().equals(fileName)) {
                        Log.d(TAG, f.getName() + " exist!");
                        writeOutInfo(sock, MESSAGE_EXIST);
                        return null;
                    }
                }
            }
            Log.d(TAG, "dir:" + dir.getPath());
            File file = new File(dir, fileName);
            if (file.createNewFile()) {
                Log.d(TAG, "Save file " + fileName + " to " + dir.getAbsolutePath());
                writeOutInfo(sock, MESSAGE_SEND_NOW);
                return file;
            } else {
                writeOutInfo(sock, MESSAGE_DISK_ERROR);
                return null;
            }
        }
    }

    public void onDestroy() {
        new Thread() {
            public void run() {
                try {
                    Socket sock = new Socket(InetAddress.getLocalHost(), mPort);
                    sock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
