package com.example.wei.lanjiemessege;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by wei on 2018/11/25.
 */

public class SmsReceiver extends BroadcastReceiver {

    private String uriParse;
    @Override
    public void onReceive(Context context, Intent intent) {
        Object[] pduses = (Object[]) intent.getExtras().get("pdus");
        for (Object puds:pduses){
            //获取短信
            byte[] pdusmessage = (byte[]) puds;
            SmsMessage sms = SmsMessage.createFromPdu(pdusmessage);
            String mobile = sms.getOriginatingAddress();
            String content = sms.getMessageBody();
            Date date = new Date(sms.getTimestampMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String time = format.format(date);
            //根据号码删除短信
            int res = context.getContentResolver().delete(Uri.parse("content://sms"), "address like '" + mobile + "'", null);
        }
    }

    public String getUriParse() {
        return uriParse;
    }

    public void setUriParse(String uriParse) {
        this.uriParse = uriParse;
    }
}