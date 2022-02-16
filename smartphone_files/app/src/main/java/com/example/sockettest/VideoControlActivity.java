package com.example.sockettest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class VideoControlActivity extends YouTubeBaseActivity
        //implements YouTubePlayer.OnFullscreenListener
{

    YouTubePlayerView yt_view;
    YouTubePlayer yt_player;
    Button load_button;
    Button minus_minute_button;
    Button cur_time_button;
    Button plus_minute_button;
    TextView text_view;
    YouTubePlayer.OnInitializedListener init_listener;

    TextViewCleaner tvc;

    //fullscreen handling
    //boolean fullscreen;
    LinearLayout otherViews;
    LinearLayout baseLayout;

    //variables for socket transmission
    String server_ip;
    int server_port;

    // rtt measuring
    private Boolean rtt_value_valid;
    private final int rtt_number=10;
    private long cur_rtt;
    private double avg_rtt;

    //socket transmission
    private Socket socket;
    private int socket_timeout_millis=10;
    private BufferedWriter networkWriter;
    private InputStreamReader networkReader;
    private boolean socket_valid=false;

    //socket listening thread
    private SocketHandler.ServerListenThread socket_listen_runnable=null;
    private Thread socket_listen_thread=null;
    private boolean listen_thread_stop=true;

    //thread handling
    private Handler mHandler;

    //video synchronizing
    boolean iframe_video_state=false; // should be synchronized
    boolean phone_video_state=false; // should be synchronized
    boolean play_probe_mode=true; // should be synchronized
    boolean ad_playing=false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_control_layout);
        load_button= (Button) findViewById(R.id.load_button);
        minus_minute_button= (Button) findViewById(R.id.minus_min_button);
        cur_time_button= (Button) findViewById(R.id.cur_time_button);
        plus_minute_button= (Button) findViewById(R.id.plus_min_button);
        TextView text_view= (TextView) findViewById(R.id.text_view);
        yt_view= (YouTubePlayerView) findViewById(R.id.yt_view);

        otherViews= (LinearLayout) findViewById(R.id.otherViews);
        baseLayout= (LinearLayout) findViewById(R.id.baseLayout);

        // handler initialization
        mHandler = new Handler();

        // Get server ip & port and make & connect socket
        Intent intent = getIntent();
        server_ip= intent.getStringExtra(MainActivity.EXTRA_SERVER_IP);
        server_port= intent.getIntExtra(MainActivity.EXTRA_SERVER_PORT,3000);
        socket_valid=false;

        // Get the Intent that started this activity and extract the string
        String VIDEO_URL= intent.getStringExtra(MainActivity.EXTRA_VIDEO_URL);
        String VIDEO_ID= getVideoIdFromURL(VIDEO_URL);

        // measure avg rtt and share the value to server
        try{
            //rtt measure
            //Thread rtt_measure_thread= new VideoControlActivity.MeasureAvgRtt(server_ip,server_port);
            Thread rtt_measure_thread= new SocketHandler.MeasureAvgRtt();
            rtt_measure_thread.start();
            rtt_measure_thread.join();
            //avg rtt value share
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type","avg_rtt_value");
            jsonObject.put("value",(int)avg_rtt);

            String msg= "avg rtt: ";
            Toast.makeText(VideoControlActivity.this,msg+(int)avg_rtt,Toast.LENGTH_SHORT).show();

            //Thread rtt_share_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
            Thread rtt_share_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
            rtt_share_thread.start();
            rtt_share_thread.join();
        } catch (Exception e){
            System.out.println(e);
            e.printStackTrace();
        }

        // make and start socket listening thread
        // !! this thread should be started after measuring average rtt value
        //if(socket_valid){
        if(SocketHandler.isSocketValid()){
            runListenThread();
        }

        //video synchronizing flag
        synchronized (VideoControlActivity.class){
            iframe_video_state=false; // should be synchronized
            phone_video_state=false; // should be synchronized
            play_probe_mode=true; // should be synchronized
        }
        ad_playing=false;


        init_listener= new YouTubePlayer.OnInitializedListener() {
            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youtubePlayer, boolean b){
                yt_player=youtubePlayer;
                yt_player.setPlaybackEventListener(new YouTubePlayer.PlaybackEventListener() {
                    @Override
                    public void onPlaying() {
                        String msg= "onPlaying Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();
                        if(!ad_playing){
                            boolean prev_play_probe_mode;
                            synchronized (VideoControlActivity.class){
                                prev_play_probe_mode=play_probe_mode;
                            }
                            if(prev_play_probe_mode){
                                yt_player.pause();
                                // first of all, check if listening thread is running
                                // if not, run it
                                if(listen_thread_stop){
                                    //first, check if socket is valid
                                    if(!SocketHandler.isSocketValid()){
                                        SocketHandler.makeSocket();
                                        //if cannot make socket, shut this routine
                                        if(!SocketHandler.isSocketValid()){
                                            return;
                                        }
                                    }
                                    runListenThread();
                                }
                                // send "play" msg to server
                                // server probe play-ability of iframe video
                                try{
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put("type","play");
                                    //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    signal_thread.start();
                                } catch (Exception e){
                                    System.out.println(e);
                                    e.printStackTrace();
                                }
                                // check if iframe video is play-able
                                boolean prev_iframe_video_state;
                                boolean prev_phone_video_state;
                                synchronized (VideoControlActivity.class){
                                    prev_iframe_video_state=iframe_video_state;
                                    prev_phone_video_state=phone_video_state;
                                    phone_video_state=true;
                                }
                                mHandler.post(showDebug("phone flag set"));
                                // send "phone video can play" msg to server
                                try{
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put("type","phone_can_play");
                                    //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    signal_thread.start();
                                } catch (Exception e){
                                    System.out.println(e);
                                    e.printStackTrace();
                                }
                                if(prev_iframe_video_state){
                                    try{
                                        Thread.sleep((long)avg_rtt/2);
                                    } catch (InterruptedException e){
                                        System.out.println(e);
                                        e.printStackTrace();
                                    }
                                    synchronized (VideoControlActivity.class){
                                        play_probe_mode=false;
                                        yt_player.play();
                                    }
                                }
                            }
                            else{
                                //set play probe mode flag and video states flags
                                /*synchronized (VideoControlActivity.class){
                                    iframe_video_state=false;
                                    phone_video_state=false;
                                    play_probe_mode=true;
                                }*/
                            }
                        }
                    }

                    @Override
                    public void onPaused() {
                        String msg= "onPaused Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();
                        boolean prev_play_probe_mode;
                        synchronized (VideoControlActivity.class){
                            prev_play_probe_mode= play_probe_mode;
                        }
                        if(!ad_playing){
                            if(prev_play_probe_mode){
                                /*synchronized (VideoControlActivity.class){
                                    play_probe_mode=false;
                                }*/
                            }
                            else{
                                // send "pause" msg to server
                                /*try{
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put("type","pause");
                                    //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    signal_thread.start();
                                } catch (Exception e){
                                    System.out.println(e);
                                    e.printStackTrace();
                                }*/
                                // send "seek" msg to server
                                try{
                                    int cur_video_time= yt_player.getCurrentTimeMillis();
                                    JSONObject jsonObject = new JSONObject();
                                    jsonObject.put("type","seek");
                                    jsonObject.put("time",Integer.toString(cur_video_time/1000) +
                                            "." + Integer.toString(cur_video_time%1000));
                                    //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                                    signal_thread.start();
                                } catch (Exception e){
                                    System.out.println(e);
                                    e.printStackTrace();
                                }
                                // set play-probe-mode flag and reset video states flags
                                synchronized (VideoControlActivity.class){
                                    iframe_video_state=false;
                                    phone_video_state=false;
                                    play_probe_mode=true;
                                }
                            }
                        }
                    }

                    @Override
                    public void onStopped() {
                        String msg= "onStopped Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();
                        /*boolean prev_iframe_video_state;
                        boolean prev_phone_video_state;
                        synchronized (VideoControlActivity.class){
                            prev_iframe_video_state=iframe_video_state;
                            prev_phone_video_state=phone_video_state;
                            phone_video_state=false;
                        }
                        // send "phone video paused" msg to server
                        new VideoControlActivity.MeasureAvgRtt(server_ip,server_port).start();
                        JSONObject jsonObject = new JSONObject();
                        try{
                            jsonObject.put("type","phone_paused");
                        } catch (JSONException e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                        new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString())).start();*/
                    }

                    @Override
                    public void onBuffering(boolean b) {
                        String msg= "onBuffering Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSeekTo(int i) {
                        String msg= "onSeekTo Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();
                        // reset video states and probe mode flag
                        // + pause video
                        synchronized (VideoControlActivity.class){
                            iframe_video_state=false;
                            phone_video_state=false;
                            play_probe_mode=true;
                        }
                        // send "seek" msg to server
                        try{
                            int cur_video_time= yt_player.getCurrentTimeMillis();
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("type","seek");
                            jsonObject.put("time",Integer.toString(cur_video_time/1000) +
                                    "." + Integer.toString(cur_video_time%1000));
                            //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                            Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                            signal_thread.start();
                        } catch (Exception e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                    }
                });
                yt_player.setPlayerStateChangeListener(new YouTubePlayer.PlayerStateChangeListener() {
                    @Override
                    public void onLoading() {
                        /*String msg= "onLoading Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();*/
                    }

                    @Override
                    public void onLoaded(String s) {
                        /*String msg= "onLoaded Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();*/
                        /*//should it be paused first here?: yes
                        yt_player.pause(); // first pause player
                        boolean prev_iframe_video_state;
                        boolean prev_phone_video_state;
                        synchronized (VideoControlActivity.class){
                            prev_iframe_video_state=iframe_video_state;
                            prev_phone_video_state=phone_video_state;
                            phone_video_state=true;
                        }
                        // send "phone video can play" msg to server
                        new VideoControlActivity.MeasureAvgRtt(server_ip,server_port).start();
                        JSONObject jsonObject = new JSONObject();
                        try{
                            jsonObject.put("type","phone_can_play");
                        } catch (JSONException e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                        new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString())).start();
                        if(prev_iframe_video_state){
                            try{
                                Thread.sleep((long)avg_rtt/2);
                            } catch (InterruptedException e){
                                System.out.println(e);
                                e.printStackTrace();
                            }
                            yt_player.play();
                        }*/
                    }

                    @Override
                    public void onAdStarted() {
                        /*String msg= "onAdStarted Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();*/
                        ad_playing=true;
                        // send "pause" msg to server
                        try{
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("type","pause");
                            //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                            Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                            signal_thread.start();
                        } catch (Exception e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                        /*boolean prev_iframe_video_state;
                        boolean prev_phone_video_state;
                        synchronized (VideoControlActivity.class){
                            prev_iframe_video_state=iframe_video_state;
                            prev_phone_video_state=phone_video_state;
                            phone_video_state=false;
                        }*/
                        //what message should be sent here?
                    }

                    @Override
                    public void onVideoStarted() {
                        /*String msg= "onVideoStarted Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();*/
                        ad_playing=false;
                    }

                    @Override
                    public void onVideoEnded() {
                        /*String msg= "onVideoEnded Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();*/
                    }

                    @Override
                    public void onError(YouTubePlayer.ErrorReason errorReason) {
                        String msg= "onError Listener invoked";
                        Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();
                        // send "pause" msg to server
                        try{
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("type","pause");
                            //Thread signal_thread= new Thread(getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                            Thread signal_thread= new Thread(SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()));
                            signal_thread.start();
                        } catch (Exception e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                    }
                });
                yt_player.loadVideo(VIDEO_ID);
                //yt_player.cueVideo(VIDEO_ID,0);
            }
            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult result){

            }
        };

        yt_view.initialize("AIzaSyBMIuGKRsKaqyvcB8yZK0EEWTReTBeCetY",init_listener);

        load_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                yt_view.initialize("AIzaSyBMIuGKRsKaqyvcB8yZK0EEWTReTBeCetY",init_listener);
            }
        });

        cur_time_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                int video_cur_time= yt_player.getCurrentTimeMillis();
                int video_cur_time_sec=video_cur_time/1000;
                int video_hour=video_cur_time_sec/3600;
                int video_min=(video_cur_time_sec/60)%60;
                int video_sec=video_cur_time_sec%60;

                if (video_hour>0){
                    text_view.setText(Integer.toString(video_hour)+":"+Integer.toString(video_min)+":"+Integer.toString(video_sec));
                } else {
                    text_view.setText(Integer.toString(video_min)+":"+Integer.toString(video_sec));
                }

                text_view.setTextColor(Color.parseColor("#000000"));
//                TextViewCleaner tvc= new TextViewCleaner(text_view);
//                tvc.run();
            }
        });
        minus_minute_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yt_player.seekRelativeMillis(-60000);
            }
        });
        plus_minute_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yt_player.seekRelativeMillis(60000);
            }
        });
    }

    @Override
    protected void onStop(){
        super.onStop();
        //if(socket_valid){
        if(SocketHandler.isSocketValid()){
            // send disconnect msg to server
            JSONObject jsonObject = new JSONObject();
            try{
                jsonObject.put("type","disconnect");
                SocketHandler.getSendMsgRunnable(server_ip,server_port,jsonObject.toString()).run();
            } catch (JSONException e){
                System.out.println(e);
                e.printStackTrace();
            }
            // stop socket listening thread
            // + close socket
            try{
                socket_listen_runnable.stop();
                socket_listen_thread.join();
                listen_thread_stop=true;
                SocketHandler.closeSocket();
            } catch (Exception e){
                System.out.println(e);
                e.printStackTrace();
            }
        }
    }
    /*@Override
    public void onFullscreen(boolean isFullscreen) {
        int cur_time= yt_player.getCurrentTimeMillis();
        yt_player.seekToMillis(cur_time);
        //fullscreen = isFullscreen;
        //doLayout();
    }*/

    /*@Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        doLayout();
    }*/

    /*private void doLayout(){
        LinearLayout.LayoutParams playerParams =
                (LinearLayout.LayoutParams) yt_view.getLayoutParams();
        if (fullscreen) {
            // When in fullscreen, the visibility of all other views than the player should be set to
            // GONE and the player should be laid out across the whole screen.
            playerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            playerParams.height = ViewGroup.LayoutParams.MATCH_PARENT;

            otherViews.setVisibility(View.GONE);
        } else {
            // This layout is up to you - this is just a simple example (vertically stacked boxes in
            // portrait, horizontally stacked in landscape).
            otherViews.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams otherViewsParams = otherViews.getLayoutParams();
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                playerParams.width = otherViewsParams.width = 0;
                playerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                otherViewsParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                playerParams.weight = 1;
                baseLayout.setOrientation(LinearLayout.VERTICAL);
            } else {
                playerParams.width = otherViewsParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                playerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                playerParams.weight = 0;
                otherViewsParams.height = 0;
                baseLayout.setOrientation(LinearLayout.HORIZONTAL);
            }
            //setControlsEnabled();
        }
    }*/

    public String getVideoIdFromURL(String video_url){
        if(video_url.contains("youtu.be") ||
           video_url.contains("embeded")){
            String[] arr= video_url.split("/");
            return arr[arr.length-1];
        }
        else{
            int index= video_url.indexOf("v=")+2;
            return video_url.substring(index, video_url.length());
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
                    mHandler.post(showDebug(e.toString()));
                }
                rtt_sum+=cur_rtt;
            }
            avg_rtt=rtt_sum/rtt_number;

            /*// show what's happening
            msg_content_string="measuring average rtt";
            mHandler.post(showUpdate);

            // show avg rtt on console box
            debug_content_string+="avg rtt: "+avg_rtt+"\n";
            mHandler.post(showDebug);*/
        }
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
                    mHandler.post(showDebug(e.toString()));
                }

                long start_time= System.currentTimeMillis();
                // network writer and network reader with read buffer
                char[] msg_buffer= new char[1024];
                Arrays.fill(msg_buffer,0,msg_buffer.length,'\0');
                try {
                    if(!socket_valid){
                        // make socket part
                        makeSocket();
                        if(!socket_valid){
                            return;
                        }
                    }

                    // send and receive predefined message part
                    networkWriter.write(jsonObject.toString());
                    networkWriter.flush();
                    int read_bytes= networkReader.read(msg_buffer,0,msg_buffer.length-1);
                    String received_msg= new String(new String(msg_buffer,0,read_bytes).getBytes(),"UTF-8");

                    // print received packet content
                    //mHandler.post(showDebug);

                    /*// close socket and buffered writer part
                    networkWriter.close();
                    networkReader.close();
                    socket.close();*/
                } catch (Exception e) {
                    System.out.println(e);
                    e.printStackTrace();
                }

                long elapsed_time= System.currentTimeMillis()-start_time;
                cur_rtt= elapsed_time;

                // show rtt
                //mHandler.post(showDebug);

                // update content of text view
                //mHandler.post(showUpdate);
            }
        };
    }
    private class ServerListenThread implements Runnable{
        private Socket socket;
        private InputStreamReader networkReader;
        private boolean exit;
        char[] msg_buffer;
        ServerListenThread(Socket s){
            socket= s;
            exit=false;
            msg_buffer= new char[1024];

        }
        @Override
        public void run() {
            int read_bytes;
            boolean buffer_cleared=false;
            try{
                socket.setSoTimeout(socket_timeout_millis);
                networkReader=new InputStreamReader(socket.getInputStream());
            } catch (Exception e){
                System.out.println(e);
                e.printStackTrace();
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
                    handleMsg(received_msg);
                    buffer_cleared=false;
                } catch (SocketTimeoutException e){
                    try{
                        Thread.sleep(5);
                    } catch (InterruptedException ie){
                        return;
                    }
                    continue;
                } catch (IOException e){
                    socket_valid=false;
                    return;
                }
            }
        }

        private void handleMsg(String msg){
            if(msg.equals("iframe_can_play")){
                // retrieve previous states of players
                boolean prev_iframe_video_state;
                boolean prev_phone_video_state;
                boolean prev_play_probe_mode;
                synchronized (VideoControlActivity.class){
                    prev_iframe_video_state=iframe_video_state;
                    prev_phone_video_state=phone_video_state;
                    prev_play_probe_mode=play_probe_mode;
                    iframe_video_state=true;
                }
                if(prev_play_probe_mode){
                    if(prev_phone_video_state){
                        synchronized (VideoControlActivity.class){
                            play_probe_mode=false;
                            yt_player.play();
                        }
                    }
                }
            }
        }

        public void stop() {
            exit=true;
        }
    }
    private Runnable showDebug(String msg){
        return new Runnable() {
            @Override
            public void run() { Toast.makeText(VideoControlActivity.this,msg,Toast.LENGTH_SHORT).show();}
        };
    }
    public void handleMsg(String msg){
        mHandler.post(showDebug("received msg: "+msg));
        if(msg.equals("iframe_can_play")){
            mHandler.post(showDebug("iframe flag set"));
            // retrieve previous states of players
            boolean prev_iframe_video_state;
            boolean prev_phone_video_state;
            synchronized (VideoControlActivity.class){
                prev_iframe_video_state=iframe_video_state;
                prev_phone_video_state=phone_video_state;
                iframe_video_state=true;
            }
            if(prev_phone_video_state){
                synchronized (VideoControlActivity.class){
                    play_probe_mode=false;
                    yt_player.play();
                }
            }
        }
    }
    private Runnable getSendMsgRunnable(String tmp_ip, int tmp_port, String msg) {
        return new Runnable() {
            @Override
            public void run() {
                // make socket part
                if(!socket_valid) {
                    makeSocket();
                    if(!socket_valid){
                        return;
                    }
                }

                // send predefined message part
                try {
                    networkWriter.write(msg);
                    networkWriter.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                /*// close socket and buffered writer part
                try {
                    if (socket != null) {
                        networkWriter.close();
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
            }
        };
    }
    private void makeSocket(){
        try{
            socket = new Socket(server_ip, server_port);
            //socket.setSoTimeout(socket_timeout_millis);
            networkWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            networkReader = new InputStreamReader(socket.getInputStream());
            socket_valid=true;
        } catch (Exception e){
            System.out.println(e);
            e.printStackTrace();
            socket_valid=false;
        }
    }
    private void runListenThread(){
        //socket_listen_runnable= new ServerListenThread(socket);
        socket_listen_runnable= new SocketHandler.ServerListenThread(SocketHandler.getSocket(),this);
        socket_listen_thread= new Thread(socket_listen_runnable);
        socket_listen_thread.start();
        synchronized (VideoControlActivity.class){
            listen_thread_stop=false;
        }
    }
    public void setListenThreadStopped(){
        synchronized (VideoControlActivity.class){
            listen_thread_stop=true;
        }
    }
}

class TextViewCleaner implements Runnable{
    private TextView text_view;
    TextViewCleaner(TextView text_view){
        this.text_view= text_view;
    }
    public void run(){
        try{
            Thread.sleep(3000);
        }
        catch(InterruptedException e){

        }
        this.text_view.setText("");
    }
}