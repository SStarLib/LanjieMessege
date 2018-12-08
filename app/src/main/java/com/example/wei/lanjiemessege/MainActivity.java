package com.example.wei.lanjiemessege;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.wei.lanjiemessege.HttpUtils;


public class MainActivity extends ActionBarActivity {

    private ListView mListView;
    private SimpleAdapter sa;
    private List<Map<String, Object>> data;
    public static final int REQ_CODE_CONTACT = 1;

    private TextView vSms;//短信内容TextView
    private TextView vAddress;
    private SMSContent smsObsever;//短信观察者


    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_PROGRESS = 2;
    public static final int TYPE_BIG_TEXT = 3;
    public static final int TYPE_INBOX = 4;
    public static final int TYPE_BIG_PICTURE = 5;
    public static final int TYPE_HANGUP = 6;

    private boolean delFlag = false;
    private Button btnDel;
    private String preDelBody;
    private boolean isSpam = false;

    String[] array = null;
    String post_result = null;

    private Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            Bundle bundle = msg.getData();
            String body = bundle.getString("body");
            String address = bundle.getString("address");

            vSms.setText(body);
            vAddress.setText(address);
        }

        ;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        vSms = (TextView) this.findViewById(R.id.tx_sms);//短信内容显示

        vAddress = (TextView) this.findViewById(R.id.textView2);//短信内容显示

        smsObsever = new SMSContent(handler);//实例化短信观察者
        //注册短信观察者
        getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, smsObsever);


    }


    private void hangUpNotify(final String body) {
        final NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle("垃圾短信提示");
        builder.setContentText("通知：" + body);
        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.contact));
        Intent intent = new Intent(this, NotificationActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, 1, intent, 0);
        builder.setContentIntent(pIntent);
        //这句是重点
        builder.setFullScreenIntent(pIntent, true);
        builder.setAutoCancel(true);
        Notification notification = builder.build();
        manager.notify(TYPE_HANGUP, notification);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                manager.cancel(TYPE_HANGUP);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this);
                builder.setContentTitle("垃圾短信提示");
                builder.setContentText("通知： " + body);
                builder.setSmallIcon(R.mipmap.ic_launcher);
                builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.contact));
                Intent intent = new Intent(MainActivity.this, NotificationActivity.class);
                PendingIntent pIntent = PendingIntent.getActivity(MainActivity.this, 1, intent, 0);
                builder.setContentIntent(pIntent);
                builder.setAutoCancel(true);
                Notification notification = builder.build();
                manager.notify(TYPE_HANGUP, notification);
            }
        }, 2000);
    }


    /**
     * @author Administrator
     * @description 短信观察者
     */
    class SMSContent extends ContentObserver {
        private Handler mHandler;

        public SMSContent(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Cursor cursor = null;
            String body = null;
            String address=null;
            try {
                cursor = getContentResolver().query(
                        Uri.parse("content://sms/inbox"), null, null, null,
                        "date desc");
                if (cursor != null) {
                    if (cursor.moveToNext()) {//不遍历只拿当前最新的一条短信
                        //获取当前的短信内容
                        body = cursor.getString(cursor.getColumnIndex("body"));

                        //判断是否为垃圾短信；
                        String [] data=new String[2];
                        data[0]=body;
                        postData(data);
                        if (isSpam){
                            address = cursor.getString(cursor.getColumnIndex("address"));
                            Message msg = Message.obtain();
                            Bundle bundle = new Bundle();
                            bundle.putString("body", body);
                            bundle.putString("address", address);

                            if (!delFlag) {
                                preDelBody = body;
                                hangUpNotify("收到一条 "+address+" 发来的垃圾短信： "+body);
                            }
                            msg.setData(bundle);
                            mHandler.sendMessage(msg);
                        }

                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }

            }
        }
    }
    //将短信内容传入服务器
    //*******************************************************************************************************************
    private void postData(final String[] data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String sdata;
                String j;
//                Map<String, String> params = new HashMap<String, String>();
                Map params = new LinkedHashMap();

                for (int i = 0;i<data.length;i++)
                {
//                    sdata = Integer.toString(data[i]);
                    sdata = data[i];
                    j = Integer.toString(i);
                    params.put(j,sdata);
                }
                try {
                    post_result = HttpUtils.submitPostData(params, "utf-8");
                    if(!post_result.equals("0")){
                        isSpam=true;
                    }
                    Log.i("POST_RESULT", post_result);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //取消注册
        getContentResolver().unregisterContentObserver(smsObsever);
    }

}
