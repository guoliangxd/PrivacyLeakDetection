package cn.edu.xidian.privacyleakdetection.Application;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cn.edu.xidian.privacyleakdetection.Application.Database.DatabaseHandler;


public class ActionReceiver extends BroadcastReceiver {
    public ActionReceiver() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        int notifyId = intent.getIntExtra("notificationId", 0);

        // 取消通知
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        manager.cancel(notifyId);

        DatabaseHandler db = DatabaseHandler.getInstance(context);

        //TODO: set ignore
        db.setIgnoreAppCategory(notifyId, true);
    }
}
