package com.example.sockettest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class SocketHandler {
    private static Socket socket;
    private static BufferedWriter networkWriter;
    private static InputStreamReader networkReader;
    private static boolean socket_valid=false;

    //socket timeout
    private static final int socket_timeout_millis=10;

    //ip and port num of server
    private static String server_ip;
    private static int server_port;

    // rtt measuring
    private static Boolean rtt_value_valid;
    private static final int rtt_number=10;
    private static long cur_rtt;
    private static double avg_rtt;

    public static synchronized Socket getSocket(){
        return SocketHandler.socket;
    }
    public static synchronized void setSocket(Socket socket){
        SocketHandler.socket= socket;
    }
    public static synchronized void setSocketValid(){
        SocketHandler.socket_valid=true;
    }
    public static synchronized void setSocketInvalid(){
        SocketHandler.socket_valid=false;
    }
    public static synchronized boolean isSocketValid(){
        return SocketHandler.socket_valid;
    }
    public static synchronized void setServerIP(String ip){
        SocketHandler.server_ip= ip;
    }
    public static synchronized String getServerIP(){
        return SocketHandler.server_ip;
    }
    public static synchronized void setServerPort(int port_num){
        SocketHandler.server_port= port_num;
    }
    public static synchronized int getServerPort(){
        return SocketHandler.server_port;
    }

    public static synchronized void makeSocket(){
        try{
            SocketHandler.socket = new Socket(SocketHandler.server_ip, SocketHandler.server_port);
            //socket.setSoTimeout(socket_timeout_millis);
            SocketHandler.networkWriter = new BufferedWriter(new OutputStreamWriter(SocketHandler.socket.getOutputStream()));
            SocketHandler.networkReader = new InputStreamReader(SocketHandler.socket.getInputStream());
            SocketHandler.socket_valid=true;
        } catch (Exception e){
            System.out.println(e);
            e.printStackTrace();
            SocketHandler.socket_valid=false;
        }
    }
    public static synchronized void closeSocket(){
        try{
            SocketHandler.socket.close();
            SocketHandler.networkReader.close();
            SocketHandler.networkWriter.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        finally {
            SocketHandler.socket=null;
            SocketHandler.networkReader=null;
            SocketHandler.networkWriter=null;
            SocketHandler.socket_valid=false;
        }
    }
    public static Runnable getSendMsgRunnable(String tmp_ip, int tmp_port, String msg) {
        return new Runnable() {
            @Override
            public void run() {
                // make socket part: if necessary
                if(!SocketHandler.isSocketValid()) {
                    SocketHandler.makeSocket();
                    if(!SocketHandler.isSocketValid()){
                        return;
                    }
                }

                // send predefined message part
                try {
                    SocketHandler.networkWriter.write(msg);
                    SocketHandler.networkWriter.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                    synchronized (SocketHandler.class){
                        SocketHandler.socket_valid=false;
                    }
                }
            }
        };
    }
    public static class MeasureAvgRtt extends Thread {
        public MeasureAvgRtt(){
        }
        @Override
        public void run(){
            double rtt_sum=0;
            synchronized (SocketHandler.class){
                SocketHandler.rtt_value_valid=false;
            }

            for(int i=0; i<rtt_number; i++){
                Thread rtt_thread= new Thread(getRttMeasureRunnable(SocketHandler.server_ip,SocketHandler.server_port));
                rtt_thread.start();
                try{
                    rtt_thread.join();
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                    return;
                }
                rtt_sum+=SocketHandler.cur_rtt;
            }
            synchronized (SocketHandler.class){
                SocketHandler.avg_rtt=rtt_sum/SocketHandler.rtt_number;
                SocketHandler.rtt_value_valid=true;
            }
        }
    }
    private static Runnable getRttMeasureRunnable(String tmp_ip, int tmp_port){
        return new Runnable() {
            @Override
            public void run() {
                // make rtt measuring json
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","rtt_measure");
                } catch (JSONException e){
                    e.printStackTrace();
                }

                long start_time= System.currentTimeMillis();
                // network writer and network reader with read buffer
                char[] msg_buffer= new char[1024];
                Arrays.fill(msg_buffer,0,msg_buffer.length,'\0');
                try {
                    if(!SocketHandler.isSocketValid()){
                        // make socket part
                        SocketHandler.makeSocket();
                        if(!SocketHandler.isSocketValid()){
                            return;
                        }
                    }

                    // send and receive predefined message part
                    SocketHandler.networkWriter.write(jsonObject.toString());
                    SocketHandler.networkWriter.flush();
                    int read_bytes= SocketHandler.networkReader.read(msg_buffer,0,msg_buffer.length-1);
                    String received_msg= new String(new String(msg_buffer,0,read_bytes).getBytes(),"UTF-8");
                } catch (Exception e) {
                    synchronized (SocketHandler.class){
                        SocketHandler.socket_valid=false;
                        SocketHandler.rtt_value_valid=false;
                    }
                    e.printStackTrace();
                    return;
                }
                long elapsed_time= System.currentTimeMillis()-start_time;
                synchronized (SocketHandler.class){
                    SocketHandler.cur_rtt= elapsed_time;
                }
            }
        };
    }
    public static class ServerListenThread implements Runnable{
        private Socket socket;
        private VideoControlActivity va;
        private InputStreamReader networkReader;
        private boolean exit=false;
        private char[] msg_buffer;
        ServerListenThread(Socket s, VideoControlActivity _va){
            socket= s;
            va=_va;
            msg_buffer= new char[1024];
        }
        @Override
        public void run() {
            int read_bytes;
            boolean buffer_cleared=false;
            try{
                synchronized (SocketHandler.class){
                    socket.setSoTimeout(SocketHandler.socket_timeout_millis);
                }
                networkReader=new InputStreamReader(socket.getInputStream());
            } catch (Exception e){
                e.printStackTrace();
                return;
            }

            while(!exit){
                //clear buffer
                if(!buffer_cleared){
                    Arrays.fill(msg_buffer,0,msg_buffer.length,'\0');
                    buffer_cleared=true;
                }

                // try to receive message from server
                try{
                    read_bytes= networkReader.read(msg_buffer,0,msg_buffer.length-1);
                    String received_msg= new String(new String(msg_buffer,0,read_bytes).getBytes(),"UTF-8");
                    va.handleMsg(received_msg);
                    buffer_cleared=false;
                } catch (SocketTimeoutException e){
                    try{
                        Thread.sleep(5);
                    } catch (InterruptedException ie){
                        break;
                    }
                    continue;
                } catch (IOException e){
                    SocketHandler.setSocketInvalid();
                    break;
                }
            }
            va.setListenThreadStopped();
        }
        public void stop() {
            exit=true;
        }
    }
}
