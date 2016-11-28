package com.iboxpay.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    public static final int MSG_CONNECT_SERVER = 0;
    public static final int MSG_DISCONNECT_SERVER = 1;
    public static final int MSG_SEND_CONTENT = 2;
    public static final int MSG_RECEIVER_CONTENT = 3;
    public static final int MSG_CONNECT_ERROR = 4;
    public static final int MSG_DISCONNECT_SERVER_DONE = 5;
    public static final int MSG_CONNECTION_STATE_CHANGE = 6;

    public static final int CONNECTION_STATE_IDLE = 0;
    public static final int CONNECTION_STATE_CONNECTING = 1;
    public static final int CONNECTION_STATE_CONNECTED = 2;
    public static final int CONNECTION_STATE_DISCONNECTING = 3;

    //views
    private TextView mStatusText;
    private TextView mReceiveText;
    private EditText mIPEdit;
    private EditText mPortEdit;
    private EditText mContentEdit;

    private SSLSocketFactory  mSSLSocketFactory;
    private SSLSocket mSSLSocket;
    private SSLContext mSSLContext;
    private ClientThread mClientThread;

    private int connectionState = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStatusText = (TextView)findViewById(R.id.status);
        mReceiveText = (TextView)findViewById(R.id.receive);
        mIPEdit = (EditText) findViewById(R.id.ip);
        mPortEdit = (EditText)findViewById(R.id.port);
        mContentEdit = (EditText)findViewById(R.id.content);
    }

    public void sendContent(View view) {
        String content = mContentEdit.getText().toString();
        if(mClientThread == null) {
            sendErrorMessage("Connection has disconnected");
            return;
        }
        if(content.length() > 0) {
            Message msg = new Message();
            msg.what = MSG_SEND_CONTENT;
            msg.obj = content;
            mClientThread.mReceiveHandler.sendMessage(msg);
        }
    }

    private void onConnectionStateChange(int state) {
        if(connectionState != state) {
            connectionState = state;
            switch (state) {
                case CONNECTION_STATE_CONNECTED:
                    mStatusText.setText("Connected");
                    break;
                case CONNECTION_STATE_CONNECTING:
                    mStatusText.setText("Connecting");
                    break;
                case CONNECTION_STATE_DISCONNECTING:
                    mStatusText.setText("Disconnecting");
                    break;
                case CONNECTION_STATE_IDLE:
                    mStatusText.setText("IDLE");
                    break;
            }
        }
    }

    public void disconnectServer(View view) {
        mHandler.sendEmptyMessage(MSG_DISCONNECT_SERVER);
    }

    private void showAlertDialog(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(msg)
                .show();
        return;
    }

    private void sendErrorMessage(String str) {
        Message msg = new Message();
        msg.what = MSG_CONNECT_ERROR;
        msg.obj = str;
        mHandler.sendMessage(msg);
    }

    private void makeSSLSocket() {
        try {
            KeyStore trustStore = KeyStore.getInstance("bks");
            InputStream tsIn = getResources().getAssets().open("server.bks");

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            InputStream ksIn = getResources().getAssets().open("client.p12");

            try {
                keyStore.load(ksIn, "123456".toCharArray());
                trustStore.load(tsIn, "123456".toCharArray());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    ksIn.close();
                } catch (Exception ignore) {
                }
                try {
                    tsIn.close();
                } catch (Exception ignore) {
                }
            }
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
            keyManagerFactory.init(keyStore, "123456".toCharArray());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            mSSLContext = SSLContext.getInstance("TLS");
            mSSLContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

            mSSLSocket = (SSLSocket) mSSLContext.getSocketFactory().createSocket(mIPEdit.getText().toString(), Integer.valueOf(mPortEdit.getText().toString()));
            mSSLSocket.startHandshake();
            Log.d(TAG, "mSSLSocket:" + mSSLSocket);
            mClientThread = new ClientThread(mHandler, mSSLSocket);
            new Thread(mClientThread).start();

            Message msg = new Message();
            msg.what = MSG_CONNECTION_STATE_CHANGE;
            msg.arg1 = CONNECTION_STATE_CONNECTED;
            mHandler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
            Message msg = new Message();
            msg.obj = e.getMessage();
            mHandler.sendMessage(msg);
        }
        /*
        catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }
        */
    }

    public void connetServer(View view) {
        if(mSSLSocket != null) {
            sendErrorMessage("Already connected");
            return;
        }
        mHandler.sendEmptyMessage(MSG_CONNECT_SERVER);
    }

    private void tryConnectServer() {
        new Thread() {
            @Override
            public void run() {
                makeSSLSocket();
            }
        }.start();
    }

    private void tryDisconnectServer() {
        mClientThread.mReceiveHandler.sendEmptyMessage(MSG_DISCONNECT_SERVER);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECT_SERVER:
                    tryConnectServer();
                    onConnectionStateChange(CONNECTION_STATE_CONNECTING);
                    break;
                case MSG_DISCONNECT_SERVER:
                    tryDisconnectServer();
                    onConnectionStateChange(CONNECTION_STATE_DISCONNECTING);
                    break;
                case MSG_RECEIVER_CONTENT:
                    String content = msg.obj.toString();
                    mReceiveText.setText(content);
                    break;
                case MSG_CONNECT_ERROR:
                    content = msg.obj.toString();
                    showAlertDialog(content);
                    break;
                case MSG_DISCONNECT_SERVER_DONE:
                    mClientThread = null;
                    mSSLSocket = null;
                    onConnectionStateChange(CONNECTION_STATE_IDLE);
                    break;
                case MSG_CONNECTION_STATE_CHANGE:
                    onConnectionStateChange(msg.arg1);
            }
        }
    };
}