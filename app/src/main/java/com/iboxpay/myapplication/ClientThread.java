package com.iboxpay.myapplication;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSocket;

/**
 * Created by yinlijun on 16-11-28.
 */

public class ClientThread implements Runnable {
    private Handler handler;
    private Socket socket;
    public Handler mReceiveHandler;
    private OutputStream os;
    private BufferedReader br;

    public ClientThread(Handler handler, Socket socket) {
        this.handler = handler;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            os = socket.getOutputStream();
            new Thread() {
                @Override
                public void run() {
                    String content = null;
                    try {
                        while((content = br.readLine()) != null) {
                            Message msg = new Message();
                            msg.what = MainActivity.MSG_RECEIVER_CONTENT;
                            msg.obj = content;
                            Log.d("received", content);
                            handler.sendMessage(msg);

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();

            Looper.prepare();
            mReceiveHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MainActivity.MSG_SEND_CONTENT:
                            try {
                                os.write(((msg.obj.toString() + "\r\n").getBytes()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case MainActivity.MSG_DISCONNECT_SERVER:
                            try {
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                handler.sendEmptyMessage(MainActivity.MSG_DISCONNECT_SERVER_DONE);
                            }
                            break;
                    }
                }
            };
            Looper.loop();
        } catch (SocketTimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
