package com.example.liyang.udpboardcastclient;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class MainActivity extends Activity implements View.OnClickListener {
    public TextView text1;
    public EditText input;
    ProgressDialog progressDialog;


    //发送消息线程
    private Thread sendThread = null;
    //监听的端口号
    private static int PORT = 4444;
    private StringBuffer strContent = new StringBuffer();
    private Socket socket = null;
    private String content = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        try {
////            socket = new DatagramSocket(PORT);
////            socket = new Socket(PORT);
//
//        } catch (SocketException e) {
//            e.printStackTrace();
//        }

        initUI();
        clientThread = new UDPClientThread();

    }

    private void initUI() {
        showProgress();
        text1 = (TextView) findViewById(R.id.txt_show);
        input = (EditText) findViewById(R.id.et_input);
        Button btn = (Button) findViewById(R.id.btn_send);
        btn.setOnClickListener(this);

    }

    private void showProgress() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("提示");
        progressDialog.setMessage("建立连接中,请稍后");
        progressDialog.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_send: {
                if (!input.getText().toString().isEmpty()) {
                    new Thread(new Client(ip)).start();
                } else {
                    Toast.makeText(MainActivity.this, "发送内容不能为空", Toast.LENGTH_LONG).show();
                }
                break;

            }
        }
    }

    InetAddress inetAddress = null;
    UDPClientThread clientThread = null;
    //服务端的局域网IP,或动态获得IP.
    private static String ip;

    private class UDPClientThread extends Thread {

        //创建一个 InetAddress .要使用多点广播,需要让一个数据报标有一组目标主机地址,
        //其思想便是设置一组特殊网络地址作为多点广播地址,第一个多点广播地址都被看作是一个组,
        //当客户端需要发送.接收广播信息时,加入该组就可以了.IP协议为多点广播提供这批特殊的IP地址,
        //这些IP地址范围是224.0.0.0---239.255.255.255,其中224.0.0.0为系统自用.
        //下面BROADCAST_IP是自己声明的一个String类型的变量,其范围但是前面所说的IP范围,
        //比如BROADCAST_IP="224.224.224.224"
        static final String BROADCAST_IP = "224.0.0.1";
        //监听的端口号
        static final int BROADCAST_PORT = 1234;

        public UDPClientThread() {
            /*开启线程*/
            start();
        }

        @Override
        public void run() {
            MulticastSocket multicastSocket = null;//多点广播套接字
            try {

                /**
                 * 1.实例化MulticastSocket对象,并指定端口
                 * 2.加入广播地址，MulticastSocket使用public void joinGroup(InetAddress mcastaddr)
                 * 3.开始接收广播
                 * 4.关闭广播
                 */

                multicastSocket = new MulticastSocket(BROADCAST_PORT);
                inetAddress = InetAddress.getByName(BROADCAST_IP);
                multicastSocket.joinGroup(inetAddress);
                byte buf[] = new byte[1024];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);


                while (true) {
                    multicastSocket.receive(dp);
                    Thread.sleep(3000);
                    ip = new String(buf, 0, dp.getLength());
                    multicastSocket.leaveGroup(inetAddress);
                    multicastSocket.close();


                    //接收到服务端的广播，通知UI更新
                    Message msg = new Message();
                    msg.what = 0x000001;
                    msg.obj = ip;
                    mHandler.sendMessage(msg);

                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /*更新UI*/
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0x000001:
                    new Thread() {
                        @Override
                        public void run() {
                            super.run();
                            try {
                                socket = new Socket(ip, PORT);
                                new ReceiveThread().start();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                    progressDialog.dismiss();
                    ip = (String) msg.obj;
                    clientThread.interrupt();
                    Toast.makeText(MainActivity.this, "通信连接已经成功，可以发消息了", Toast.LENGTH_SHORT).show();

                    break;
                case 0x1234:
                    //客户端发送消息为空
                    Toast.makeText(MainActivity.this, "发送内容不能为空", Toast.LENGTH_SHORT).show();
                    break;
                case 0x1235:
                    //客户端发送消息成功提示
                    strContent.append("我:" + msg.getData().getString("content") + "\n");
                    text1.setText(strContent.toString());
                    input.setText("");
                    break;
                case 0x1236:
                    //客户端发送失败提示
                    Toast.makeText(MainActivity.this, "客户端发送失败", Toast.LENGTH_SHORT).show();
                    break;
                case 0x1237:
                    //服务器连接失败提示
                    Toast.makeText(MainActivity.this, "服务器连接失败", Toast.LENGTH_SHORT).show();
                case 0x1238:
                    //服务器端拦截发送的消息
                    strContent.append(msg.getData().getString("content") + "\n");
                    text1.setText(strContent.toString());
                    break;
            }
        }
    };

    /**
     * UDP与服务器主机通讯
     */

    public class Client implements Runnable {
        private String IPAddress;

        public Client(String IPAddress) {
            this.IPAddress = IPAddress;
        }

        @Override
        public void run() {
            try {
//                Thread.sleep(500);
                InetAddress serverAddr = InetAddress.getByName(IPAddress);
                Socket s = new Socket(serverAddr, PORT);
                s.setSoTimeout(2000);
                // outgoing stream redirect to socket
                OutputStream out = s.getOutputStream();
                // 注意第二个参数据为true将会自动flush，否则需要需要手动操作out.flush()
                PrintWriter output = new PrintWriter(out, true);
                output.println(input.getText().toString());
                BufferedReader in = new BufferedReader(new InputStreamReader(s
                        .getInputStream()));
                String message = in.readLine();
                Bundle bundle = new Bundle();
                bundle.putString("content", message);
                Message msg = new Message();
                msg.setData(bundle);
                msg.what = 0x1235;
                mHandler.sendMessage(msg);
                s.close();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
//            catch (InterruptedException e) {
//                e.printStackTrace();
//            } finally {
//                mHandler.sendEmptyMessage(0x1236);
//                Thread.interrupted();
//            }


            /**
             * 1.实例化DatagramSocket对象 指定服务器端IP地址及响应端口
             * 2.创建DatagramPacket对象,将发送的的内容放置在DatagramPacket对象中
             * 3.调用DatagramSocket对象的send()对象 将报文发出
             * 4.关闭DatagramSocket对象
             */

//                InetAddress serverAddr = InetAddress.getByName(IPAddress);
//                DatagramSocket socket = new DatagramSocket();
//                byte[] buf;
//                if (!input.getText().toString().isEmpty()) {
//                    buf = input.getText().toString().getBytes();
//                    DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, PORT);
//                    socket.send(packet);
//                    socket.close();
//
//                    //通知UI数据已经发送，但是服务端是否收到未知
//                    Bundle bundle = new Bundle();
//                    bundle.putString("content", new String(input.getText().toString()));
//                    Message msg = new Message();
//                    msg.setData(bundle);
//                    msg.what = 0x1235;
//                    mHandler.sendMessage(msg);
//                } else {
//                    mHandler.sendEmptyMessage(0x1234);
//                }
//            } catch (Exception e) {
//                mHandler.sendEmptyMessage(0x1236);
//            } finally {
//                Thread.interrupted();
//            }
        }
    }

    public class ReceiveThread extends Thread {
        @Override
        public void run() {
            super.run();
//
//            try {
//                /**
//                 * 1.实例化的端口号要和发送时的socket一致，否则收不到data；
//                 * 2.使用new DatagramPacket(data, data.length)接受服务器端发过来的消息，参数一:要接受的data 参数二：data的长度；
//                 * 3.将服务端接过来的报文转为字符串；
//                 * 4.关闭DatagramSocket对象
//                 */
//                byte data[] = new byte[4 * 1024];
//                DatagramPacket packet = new DatagramPacket(data, data.length);
//                while (true) {
//                    socket.receive(packet);
//                    String result = new String(packet.getData(), packet.getOffset(),
//                            packet.getLength());
//
//                    //根据服务端的响应消息更新UI
//                    Bundle bundle = new Bundle();
//                    bundle.putString("content", packet.getAddress().getHostAddress() + ":" + result);
//                    Message msg = new Message();
//                    msg.setData(bundle);
//                    msg.what = 0x1238;
//                    mHandler.sendMessage(msg);
//                }
//
//            } catch (SocketException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }


            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket
                        .getInputStream()));
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream())), true);

                while (true) {
                    if (!socket.isClosed()) {
                        if (socket.isConnected()) {
                            if (!socket.isInputShutdown()) {
                                if ((content = in.readLine()) != null) {
                                    content += "\n";
                                    mHandler.sendMessage(mHandler.obtainMessage());
                                } else {

                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}
