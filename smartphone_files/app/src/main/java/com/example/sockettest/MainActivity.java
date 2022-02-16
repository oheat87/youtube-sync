package com.example.sockettest;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

//import fragment inflater

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private Handler mHandler;
    private Socket socket;
    private BufferedWriter networkWriter;
    private String msg_content_string;
    private String debug_content_string;

    //for soft keyboard handling
    InputMethodManager imanager;

    private String ip = "192.168.56.1";
    private final int port=3000;
    private final int this_server_port=3800;
    private final String QUIT_MSG= "QUIT_MSG";
    private  final String SHUT_SERVER = "SHUT_SERVER";
    private final String RTT_REPLY = "RTT_REPLY";

    // rtt measuring
    private Boolean rtt_value_valid;
    private final int rtt_number=5;
    private long cur_rtt;
    private double avg_rtt;

    // youtube app video share intent extra keys
    private final String video_URL_key= "android.intent.extra.TEXT";
    private final String video_subject_key = "android.intent.extra.SUBJECT";

    // file IO for storing app information
    private static final String app_info_file_name= "app_info.txt";
    private static File app_info_file;
    private static final int NUM_BYTES_NEEDED_FOR_MY_APP=300;

    // call video control activity
    public static final String EXTRA_VIDEO_URL = "com.example.sockettest.VIDEO_URL";
    public static final String EXTRA_SERVER_IP = "com.example.sockettest.SERVER_IP";
    public static final String EXTRA_SERVER_PORT = "com.example.sockettest.SERVER_PORT";

    EditText ip_box;
    EditText port_box;
    EditText chatbox;
    EditText num_picker_minute;
    EditText num_picker_second;
    EditText num_picker_under;
    TextView result_text;
    Button msg_send_button;
    Button predefined_button;
    Button rtt_measure;
    Button end_button;
    Button show_video_button;
    Button hide_video_button;
    Button set_video_button;
    Button display_video_button;
    Button set_server_button;
    Button play_video_button;
    Button pause_video_button;
    Button seek_video_button;
    TextView debug_console;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        // handler
        mHandler = new Handler();
        //input method manager
        imanager= (InputMethodManager) getSystemService(MainActivity.INPUT_METHOD_SERVICE);

        //measuring rtt btw this phone and websocket in browser of device
        rtt_value_valid=false;

        // widgets
        ip_box= (EditText) findViewById(R.id.ip_box);
        port_box= (EditText) findViewById(R.id.port_box);
        chatbox= (EditText) findViewById(R.id.chatbox);
        num_picker_minute= (EditText) findViewById(R.id.num_picker_minute);
        num_picker_second= (EditText) findViewById(R.id.num_picker_second);
        num_picker_under= (EditText) findViewById(R.id.num_picker_under);
        result_text= (TextView) findViewById(R.id.result_text);
        predefined_button= (Button) findViewById(R.id.predefined_button);
        msg_send_button= (Button) findViewById(R.id.msg_send_button);
        rtt_measure= (Button) findViewById(R.id.rtt_measure);
        end_button= (Button) findViewById(R.id.end_button);

        // widget for debugging
        debug_console= (TextView) findViewById(R.id.debug_console);
        debug_console.setMovementMethod(new ScrollingMovementMethod());

        // widget for video control in laptop computer
        show_video_button= (Button) findViewById(R.id.show_video_button);
        hide_video_button= (Button) findViewById(R.id.hide_video_button);
        set_video_button= (Button) findViewById(R.id.set_video_button);
        display_video_button= (Button) findViewById(R.id.display_video_button);
        set_server_button= (Button) findViewById(R.id.set_server_button);
        play_video_button= (Button) findViewById(R.id.play_video_button);
        pause_video_button= (Button) findViewById(R.id.pause_video_button);
        seek_video_button= (Button) findViewById(R.id.seek_video_button);

        /*// intent extra data handling
        try{
            Intent intent = getIntent();
            if (intent!=null){
                Bundle intent_extra_data= intent.getExtras();
                if (intent_extra_data!=null){
                    CharSequence cs;
                    for(String key : intent_extra_data.keySet()){

                        debug_console.append(key+": ");
                        cs= intent_extra_data.getCharSequence(key);
                        if (cs!=null){
                            debug_console.append(cs.toString());
                        }
                        debug_console.append("\n");
                    }
                    // put video url to chat box
                    cs= intent_extra_data.getCharSequence(video_URL_key);
                    if (cs!=null){
                        chatbox.setText(cs.toString());
                    }
                }
            }
        } catch(Exception e){
            debug_console.append(e.toString());
        }*/

        // run adnroid server thread
        /*new ServerThread("",this_server_port).start();*/


        msg_send_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                String chatbox_content= chatbox.getText().toString();
                // first check if chat box is empty or not
                if (chatbox_content.isEmpty()){
                    Toast.makeText(MainActivity.this,"input message to send to server!",Toast.LENGTH_SHORT).show();
                    return;
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");
                // if chat box has content(not empty), send msg to server
                // new Thread(getSendMsgRunnable(tmp_ip,tmp_port,chatbox_content,"user msg sent to server")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,chatbox_content)).start();
                chatbox.setText("");
                // hide soft keyboard
                imanager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
            }
        });
        predefined_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                //new Thread(getSendMsgRunnable(tmp_ip,tmp_port,"hello from android phone","predefined message sent")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,"hello from android phone")).start();
            }
        });
        /*rtt_measure.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());

                new RttMeasureThread(tmp_ip,tmp_port,this_server_port).start();
            }
        });*/
        /*rtt_measure.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","rtt_measure");
                } catch (JSONException e){
                    debug_content_string+=e.toString()+"\n";
                    mHandler.post(showDebug);
                }

                // get ip and port from boxes
                String tmp_ip = ip_box.getText().toString();
                int tmp_port = Integer.parseInt(port_box.getText().toString());

                new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"measuring rtt...")).start();
            }
        });*/
        rtt_measure.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view) {
                                // get ip and port from boxes
                String tmp_ip = ip_box.getText().toString();
                int tmp_port = Integer.parseInt(port_box.getText().toString());

                //new MeasureAvgRtt(tmp_ip,tmp_port).start();
                new SocketHandler.MeasureAvgRtt().start();
            }
        });
        end_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                //new Thread(getSendMsgRunnable(tmp_ip,tmp_port,QUIT_MSG,"server program ended")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,QUIT_MSG)).start();

                //due to server close, close socket also
                try{
                    SocketHandler.closeSocket();
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        show_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","show_hide");
                    jsonObject.put("command","show");
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                // new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"show video")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString())).start();
            }
        });
        hide_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","show_hide");
                    jsonObject.put("command","hide");
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                //new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"hide video")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString())).start();
            }
        });
        set_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                //send set video msg
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","set_video");
                    jsonObject.put("url",chatbox.getText().toString());
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                // set server ip and port
                // this code should be added to before all threads using socket
                try{
                    SocketHandler.setServerIP(ip_box.getText().toString());
                    SocketHandler.setServerPort(Integer.parseInt(port_box.getText().toString()));
                } catch (Exception e){
                    e.printStackTrace();
                }

                try{
                    //Thread socketThread= new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"set video"));
                    Thread socketThread= new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString()));
                    socketThread.start();
                    socketThread.join();
                }
                catch(Exception e){
                    debug_console.append(e.toString());
                }

                /*// play video in desktop browser
                jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","play");
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"play video")).start();
*/

                // start video control activity
                Context context= getApplicationContext();
                Intent intent = new Intent(context, VideoControlActivity.class);
                intent.putExtra(EXTRA_VIDEO_URL, chatbox.getText().toString());
                intent.putExtra(EXTRA_SERVER_IP, ip_box.getText().toString());
                intent.putExtra(EXTRA_SERVER_PORT, Integer.parseInt(port_box.getText().toString()));
                startActivity(intent);
            }
        });
        display_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                Context context= getApplicationContext();
                Intent intent = new Intent(context, VideoControlActivity.class);
                intent.putExtra(EXTRA_VIDEO_URL, chatbox.getText().toString());
                startActivity(intent);
            }
        });
        set_server_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                try{
                    SocketHandler.setServerIP(ip_box.getText().toString());
                    SocketHandler.setServerPort(Integer.parseInt(port_box.getText().toString()));
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });
        play_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","play");
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                //new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"play video")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString())).start();
            }
        });
        pause_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","pause");
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                //new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"pause video")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString())).start();
            }
        });
        seek_video_button.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view){
                JSONObject jsonObject = new JSONObject();
                String time_string= getVideoSeekTime();
                if(time_string.equals("")){
                    //
                    return;
                }
                try{
                    jsonObject.put("type","seek");
                    jsonObject.put("time",time_string);
                } catch (JSONException e){
                    debug_console.append(e.toString());
                }
                // get ip and port from boxes
                String tmp_ip=ip_box.getText().toString();
                int tmp_port=Integer.parseInt(port_box.getText().toString());
                debug_console.setText("ip: "+tmp_ip+"\nport num: "+Integer.toString(tmp_port)+"\n");

                //new Thread(getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString(),"seek time of video")).start();
                new Thread(SocketHandler.getSendMsgRunnable(tmp_ip,tmp_port,jsonObject.toString())).start();
            }
        });
    }

    @RequiresApi(26)
    @Override
    protected void onStart() {
        super.onStart();
        Context context= getApplicationContext();
        /*// check if app info file is already exists
        String[] files= context.fileList();
        boolean file_exist=false;
        for(String file_name:files){
            if(file_name.equals(app_info_file_name)){
                file_exist=true;
                break;
            }
        }*/
        try {
            //open info file and read
            FileInputStream fis = context.openFileInput(app_info_file_name);
            InputStreamReader inputStreamReader =
                    new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                String line = reader.readLine();
                while (line != null) {
                    stringBuilder.append(line).append('\n');
                    line = reader.readLine();
                }
            } catch (IOException e) {
                // Error occurred when opening raw file for reading.
            } finally {
                String contents = stringBuilder.toString();
                if(contents.equals("")) {
                    return;
                }
                String[] arguments= contents.split("\n");
                if(arguments.length<2){
                    return;
                }
                ip_box.setText(arguments[0]);
                port_box.setText(arguments[1]);
            }
        }
        catch (java.io.FileNotFoundException e) {
            debug_console.append(e.toString());
        }
        // intent extra data handling
        try{
            Intent intent = getIntent();
            if (intent!=null){
                Bundle intent_extra_data= intent.getExtras();
                if (intent_extra_data!=null){
                    CharSequence cs;
                    for(String key : intent_extra_data.keySet()){
                        /*debug_console.append(key+": ");
                        ArrayList<String> value_array= intent_extra_data.getStringArrayList(key);
                        if(value_array!=null) {
                            debug_console.append(value_array.toString());
                            for (String value : value_array) {
                                debug_console.append(value + ", ");
                            }
                        }*/
                        debug_console.append(key+": ");
                        cs= intent_extra_data.getCharSequence(key);
                        if (cs!=null){
                            debug_console.append(cs.toString());
                        }
                        debug_console.append("\n");
                    }
                    // put video url to chat box
                    cs= intent_extra_data.getCharSequence(video_URL_key);
                    if (cs!=null){
                        chatbox.setText(cs.toString());
                    }
                    else {
                        debug_console.append("No URL given");
                    }
                }
            }
        } catch(Exception e){
            debug_console.append(e.toString());
        }
    }

    /*@Override
    protected void onResume(){
        super.onResume();

    }*/

    @RequiresApi(19)
    @Override
    protected void onStop() {
        super.onStop();
        Context context= getApplicationContext();
        try{
            String filename = "myfile";
            String fileContents = "Hello world!";
            try (FileOutputStream fos = context.openFileOutput(app_info_file_name, Context.MODE_PRIVATE)) {
                fos.write((ip_box.getText().toString()+"\n").getBytes(StandardCharsets.UTF_8));
                fos.write((port_box.getText().toString()+"\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (Exception e){
            debug_console.append(e.toString());
        }
        // close socket
        if(SocketHandler.isSocketValid()){
            try{
                SocketHandler.closeSocket();
            } catch (Exception e){
                System.out.println(e);
                e.printStackTrace();
            }
        }
    }

    private Thread make_socket_thread = new Thread(){
        public void run(){
            try{
                socket= new Socket(ip,port);
                networkWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                //result_text.setText("socket creation and connection successful");
                msg_content_string= new String("socket creation and connection successful");
                mHandler.post(showUpdate);
            }catch(Exception e){
                System.out.println(e);
                e.printStackTrace();
            }
        }
    };
    private Thread send_msg_thread = new Thread() {
      public void run(){
          PrintWriter out= new PrintWriter(networkWriter, true);
          out.println("hello from android phone");
          //result_text.setText("message sent from phone");
          msg_content_string= new String("message sent from phone");
          mHandler.post(showUpdate);
      }
    };
    private Thread close_socket_thread = new Thread() {
      public void run(){
          try{
              if(socket!=null){
                  PrintWriter out= new PrintWriter(networkWriter, true);
                  out.println(QUIT_MSG);
                  socket.close();
              }
          } catch(Exception e){
              e.printStackTrace();
          }
          msg_content_string = new String("socket closed successfully");
          mHandler.post(showUpdate);
      }
    };
    private Runnable showUpdate = new Runnable() {
        @Override
        public void run() {
            result_text.setText(msg_content_string);
        }
    };
    private Runnable showDebug = new Runnable() {
        @Override
        public void run() { debug_console.setText(debug_content_string);}
    };
    private Runnable showToast(String toast_msg){
        return new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,toast_msg,Toast.LENGTH_SHORT).show();
            }
        };
    }

    private Runnable getSendMsgRunnable(String tmp_ip, int tmp_port, String msg, String textView_prompt) {
        return new Runnable() {
            @Override
            public void run() {
                // make socket part
                try {
                    socket = new Socket(tmp_ip, tmp_port);
                    networkWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    //result_text.setText("socket creation and connection successful");
                    //msg_content_string= new String("socket creation and connection successful");
                    //mHandler.post(showUpdate);
                } catch (Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }

                // send predefined message part
                try {
                    networkWriter.write(msg);
                    networkWriter.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // close socket and buffered writer part
                try {
                    if (socket != null) {
                        networkWriter.close();
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // update content of text view
                msg_content_string = textView_prompt;
                mHandler.post(showUpdate);
            }
        };
    }
    private Runnable getRttMeasureRunnable(String tmp_ip, int tmp_port){
        return new Runnable() {
            @Override
            public void run() {
                // make rtt measuring json
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("type","rtt_measure");
                } catch (JSONException e){
                    debug_content_string+=e.toString()+"\n";
                    mHandler.post(showDebug);
                }

                long start_time= System.currentTimeMillis();
                // network writer and network reader with read buffer
                BufferedWriter networkWriter;
                InputStreamReader networkReader;
                char[] msg_buffer= new char[1024];
                Arrays.fill(msg_buffer,0,msg_buffer.length,'\0');
                try {
                    // make socket part
                    socket = new Socket(tmp_ip, tmp_port);
                    networkWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    networkReader = new InputStreamReader(socket.getInputStream());

                    // send and receive predefined message part
                    networkWriter.write(jsonObject.toString());
                    networkWriter.flush();
                    int read_bytes= networkReader.read(msg_buffer,0,msg_buffer.length-1);
                    String received_msg= new String(new String(msg_buffer,0,read_bytes).getBytes(),"UTF-8");

                    // print received packet content
                    debug_content_string=received_msg+"\n";
                    mHandler.post(showDebug);

                    // close socket and buffered writer part
                    networkWriter.close();
                    networkReader.close();
                    socket.close();
                } catch (Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }

                long elapsed_time= System.currentTimeMillis()-start_time;
                cur_rtt= elapsed_time;

                // show rtt
                debug_content_string += "single rtt: "+elapsed_time+"\n";
                mHandler.post(showDebug);

                // update content of text view
                msg_content_string = "rtt measuring done";
                mHandler.post(showUpdate);
            }
        };
    }
    private class ServerThread extends Thread {
        String server_ip;
        int server_port;
        public ServerThread(String ip_arg, int port_arg){
            server_ip=ip_arg;
            server_port=port_arg;
        }
        @Override
        public void run() {
            // message buffer
            char[] msg_buffer= new char[1024];
            try {
                ServerSocket server_socket = new ServerSocket(server_port);
                //Debug
                mHandler.post(showToast("server successfully started with port num "+server_port));

                while(true){
                    Arrays.fill(msg_buffer,0,msg_buffer.length,'\0');
                    Socket receive_socket = server_socket.accept();
                    InputStreamReader networkReader = new InputStreamReader(receive_socket.getInputStream());
                    int read_bytes= networkReader.read(msg_buffer,0,msg_buffer.length-1);
                    long current_time= System.currentTimeMillis();

                    String received_msg= new String(new String(msg_buffer,0,read_bytes).getBytes(),"UTF-8");

                    debug_content_string+="get packet at: "+current_time+"\n";
                    debug_content_string+="content: "+received_msg+"\n";
                    mHandler.post(showDebug);

                    receive_socket.close();

                    if (msg_buffer.toString()==SHUT_SERVER){
                        debug_content_string+="android server shut\n";
                        mHandler.post(showDebug);
                        break;
                    }
                    else if(msg_buffer.toString()==RTT_REPLY){
                        cur_rtt=current_time;
                    }
                }

                server_socket.close();
            } catch(Exception e){
                debug_content_string+=e.toString();
                mHandler.post(showDebug);
            }
        }
    }
    private class MeasureAvgRtt extends Thread {
        String target_ip;
        int target_port;
        public MeasureAvgRtt(String ip_arg, int port_arg){
            target_ip=ip_arg;
            target_port=port_arg;
        }
        @Override
        public void run(){


            double rtt_sum=0;

            for(int i=0; i<rtt_number; i++){
                Thread rtt_thread= new Thread(getRttMeasureRunnable(target_ip,target_port));
                rtt_thread.start();
                try{
                    rtt_thread.join();
                }
                catch (InterruptedException e){
                    debug_content_string=e.toString()+"\n";
                    mHandler.post(showDebug);
                }
                rtt_sum+=cur_rtt;
            }
            avg_rtt=rtt_sum/rtt_number;

            // show what's happening
            msg_content_string="measuring average rtt";
            mHandler.post(showUpdate);

            // show avg rtt on console box
            debug_content_string+="avg rtt: "+avg_rtt+"\n";
            mHandler.post(showDebug);
        }
    }
    private class RttMeasureThread extends Thread {
        String target_ip;
        int target_port;
        int this_server_port;
        public RttMeasureThread(String target_ip_arg, int target_port_arg, int this_port_arg){
            target_ip=target_ip_arg;
            target_port=target_port_arg;
            this_server_port=this_port_arg;
        }
        @Override
        public void run(){
            int rtt_number=5;
            double rtt_tmp=0;

            // empty debug console box
            debug_content_string="";
            mHandler.post(showDebug);

            // make json for measuring rtt
            JSONObject jsonObject = new JSONObject();
            try{
                jsonObject.put("type","rtt_measure");
            } catch (JSONException e){
                debug_content_string+=e.toString()+"\n";
                mHandler.post(showDebug);
            }
/*            // get ip and port from boxes
            String tmp_ip=ip_box.getText().toString();
            int tmp_port=Integer.parseInt(port_box.getText().toString());*/

            // message buffer
            char[] msg_buffer= new char[1024];
            try {
                ServerSocket server_socket = new ServerSocket(this_server_port);
                debug_content_string+="server opened with port num "+this_server_port+"\n";
                mHandler.post(showDebug);

                for(int i=0; i<rtt_number; i++){
                    Arrays.fill(msg_buffer,0,msg_buffer.length,'\0'); //clear the msg buffer first
                    long start_time= System.currentTimeMillis();
                    new Thread(getSendMsgRunnable(target_ip,target_port,jsonObject.toString(),"measuring rtt...")).start();

                    debug_content_string+="before sending RTT packet\n";
                    mHandler.post(showDebug);

                    Socket receive_socket = server_socket.accept();

                    debug_content_string+="after sending RTT packet\n";
                    mHandler.post(showDebug);

                    InputStreamReader networkReader = new InputStreamReader(receive_socket.getInputStream());
                    int read_bytes= networkReader.read(msg_buffer,0,msg_buffer.length-1);
                    long rtt= System.currentTimeMillis()-start_time;
                    rtt_tmp+=(double)rtt;

                    String received_msg= new String(new String(msg_buffer,0,read_bytes).getBytes(),"UTF-8");

                    if(received_msg.equals(RTT_REPLY)){
                        debug_content_string+="RTT reply message received\n";
                    } else{
                        debug_content_string+="received reply: "+received_msg+"\n";
                    }
                    mHandler.post(showDebug);

                    receive_socket.close();
                }

                server_socket.close();

                avg_rtt= rtt_tmp/rtt_number;
                rtt_value_valid=true;

                debug_content_string+="RTT measurement process completed\n";
                debug_content_string+="avg RTT: "+avg_rtt+"\n";
                mHandler.post(showDebug);
            } catch(Exception e){
                debug_content_string=e.toString();
                mHandler.post(showDebug);
            } finally {

            }
        }
    }
    /*private Runnable getSendJSONRunnable(JSONObject jsonObject){
        return new Runnable() {
            @Override
            public void run() {
                // make socket part
                OutputStreamWriter networkWriter;
                byte[] json_byte_array= jsonObject.toString().getBytes(Charset.forName("UTF-8"));
                try{
                    socket= new Socket(ip,port);
                    networkWriter = new OutputStreamWriter(socket.getOutputStream());
                    //result_text.setText("socket creation and connection successful");
                    //msg_content_string= new String("socket creation and connection successful");
                    //mHandler.post(showUpdate);
                }catch(IOException e){
                    System.out.println(e);
                    e.printStackTrace();
                }

                // send predefined message part
                try{
                    networkWriter.write(json_byte_array);
                    networkWriter.write(json_byte_array);
                    networkWriter.flush();
                }
                catch (IOException e){
                    e.printStackTrace();
                }

                // close socket and buffered writer part
                try{
                    if(socket!=null){
                        networkWriter.close();
                        socket.close();
                    }
                } catch(IOException e){
                    e.printStackTrace();
                }

                // update content of text view
                msg_content_string=textView_prompt;
                mHandler.post(showUpdate);
            }
        };
    }*/
    private String getVideoURLFromIntent(){
        Intent intent= getIntent();
        if (intent!=null){
            Bundle extra_data= intent.getExtras();
            if(extra_data!=null){
                CharSequence cs= extra_data.getCharSequence(video_URL_key);
                if(cs!=null){
                    return cs.toString();
                }
            }
        }
        return "";
    }
    private String getVideoSeekTime(){
        try {
            int sec_part= Integer.parseInt(num_picker_second.getText().toString());
            sec_part+= Integer.parseInt(num_picker_minute.getText().toString())*60;
            return String.valueOf(sec_part)+"."+num_picker_under.getText().toString();
        }
        catch(Exception e) {
            return new String("");
        }
    }
    private class serializedSocket implements Serializable{

    }
}