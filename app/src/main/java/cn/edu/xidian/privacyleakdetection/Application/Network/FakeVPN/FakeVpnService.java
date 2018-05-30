/*
 * Vpnservice, build the virtual network interface
 * Copyright (C) 2014  Yihang Song

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NotificationCompat;

import cn.edu.xidian.privacyleakdetection.Application.ActionReceiver;
import cn.edu.xidian.privacyleakdetection.Application.Activities.AppSummaryActivity;
import cn.edu.xidian.privacyleakdetection.Application.PrivacyLeakDetection;
import cn.edu.xidian.privacyleakdetection.R;
import cn.edu.xidian.privacyleakdetection.Application.Database.DatabaseHandler;
import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Application.Network.FilterThread;
import cn.edu.xidian.privacyleakdetection.Application.Network.Forwarder.ForwarderPools;
import cn.edu.xidian.privacyleakdetection.Application.Network.LocalServer;
import cn.edu.xidian.privacyleakdetection.Application.Network.Resolver.MyClientResolver;
import cn.edu.xidian.privacyleakdetection.Application.Network.Resolver.MyNetworkHostNameResolver;
import cn.edu.xidian.privacyleakdetection.Plugin.ContactDetection;
import cn.edu.xidian.privacyleakdetection.Plugin.IPlugin;
import cn.edu.xidian.privacyleakdetection.Plugin.KeywordDetection;
import cn.edu.xidian.privacyleakdetection.Plugin.LeakReport;
import cn.edu.xidian.privacyleakdetection.Plugin.LocationDetection;
import cn.edu.xidian.privacyleakdetection.Plugin.DeviceDetection;
import cn.edu.xidian.privacyleakdetection.Plugin.TrafficRecord;
import cn.edu.xidian.privacyleakdetection.Plugin.TrafficReport;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class FakeVpnService extends VpnService implements Runnable {
    public static final String CADir = Logger.getDiskFileDir().getAbsolutePath();
    // also update SSLSocketFactoryFactory.java if CAName is modified
    public static final String CAName = "PrivacyLeakDetection Custom CA";
    public static final String CertName = "PrivacyLeakDetection_Cert";
    public static final String KeyType = "PKCS12";
    public static final String Password = "";

    private static final String TAG = "FakeVpnService";
    private static final boolean DEBUG = true;
    private static boolean running = false;
    private static boolean started = false;
    private static HashMap<String, Integer[]> notificationMap = new HashMap<String, Integer[]>();

    // 虚拟网络接口，获取并返回数据包
    private ParcelFileDescriptor mInterface;
    private TunWriteThread writeThread;
    private TunReadThread readThread;
    private Thread uiThread;
    // 转发器池。
    private ForwarderPools forwarderPools;

    // 网络。
    private MyNetworkHostNameResolver hostNameResolver;
    private MyClientResolver clientAppResolver;
    private LocalServer localServer;

    // 插件。
    private Class pluginClass[] = {
            LocationDetection.class,
            DeviceDetection.class,
            ContactDetection.class,
            KeywordDetection.class
    };
    private ArrayList<IPlugin> plugins;

    // 如果过滤是异步完成的，那么查找泄漏的线程。
    private FilterThread filterThread;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    public static boolean isRunning() {
        /** http://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android */
        return running;
    }

    public static boolean isStarted() {
        return started;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(TAG, "onStartCommand");
        uiThread = new Thread(this);
        uiThread.start();
        return START_STICKY_COMPATIBILITY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MyVpnServiceBinder();
    }

    @Override
    public void onRevoke() {
        Logger.d(TAG, "onRevoke");
        stop();
        super.onRevoke();
    }


    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy");
        stop();
        super.onDestroy();

    }

    @Override
    public void run() {
        if (!(setup_network())) {
            return;
        }

        started = false;
        running = true;

        // 通知主活动，VPN现在正在运行。
        Intent i = new Intent(getString(R.string.vpn_running_broadcast_intent));
        sendBroadcast(i);

        setup_workers();
        wait_to_close();
    }

    private boolean setup_network() {
        Builder b = new Builder();
        b.addAddress("10.8.0.1", 32);
        b.addDnsServer("8.8.8.8");
        b.addRoute("0.0.0.0", 0);
        b.setMtu(1500);
        b.setBlocking(true);

        try {
            b.addDisallowedApplication("com.android.vending");
            b.addDisallowedApplication("com.android.providers.downloads.ui");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        mInterface = b.establish();
        if (mInterface == null) {
            Logger.d(TAG, "Failed to establish Builder interface");
            return false;
        }
        forwarderPools = new ForwarderPools(this);

        return true;
    }

    private void setup_workers() {
        hostNameResolver = new MyNetworkHostNameResolver(this);
        clientAppResolver = new MyClientResolver(this);

        localServer = new LocalServer(this);
        localServer.start();
        readThread = new TunReadThread(mInterface.getFileDescriptor(), this);
        readThread.start();
        writeThread = new TunWriteThread(mInterface.getFileDescriptor(), this);
        writeThread.start();

        if (PrivacyLeakDetection.asynchronous) {
            filterThread = new FilterThread(this);
            // 降低过滤线程的优先级，因为它是异步运行的
            filterThread.setPriority(filterThread.getPriority() - 1);
            filterThread.start();
        }
    }

    private void wait_to_close() {
        // 等待所有线程停止。
        try {
            while (writeThread.isAlive())
                writeThread.join();

            while (readThread.isAlive())
                readThread.join();

            while (localServer.isAlive())
                localServer.join();

            if (PrivacyLeakDetection.asynchronous && filterThread.isAlive())
                filterThread.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void fetchResponse(byte[] response) {
        writeThread.write(response);
    }

    public MyNetworkHostNameResolver getHostNameResolver() {
        return hostNameResolver;
    }

    public MyClientResolver getClientAppResolver() {
        return clientAppResolver;
    }

    public ForwarderPools getForwarderPools() {
        return forwarderPools;
    }

    public FilterThread getFilterThread() { return filterThread; }

    public ArrayList<IPlugin> getNewPlugins() {
        ArrayList<IPlugin> ret = new ArrayList<IPlugin>();
        try {
            for (Class c : pluginClass) {
                IPlugin temp = (IPlugin) c.newInstance();
                temp.setContext(this);
                ret.add(temp);
            }
            return ret;
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }



    public TrafficRecord getTrafficRecord(){
        TrafficRecord trafficRecord = new TrafficRecord();
        trafficRecord.setContext(this);
        return trafficRecord;
    }

    public void addtotraffic(TrafficReport traffic) {

        DatabaseHandler db = DatabaseHandler.getInstance(this);

        db.addtraffic(traffic);
    }


    public void notify(String request, LeakReport leak) {

        DatabaseHandler db = DatabaseHandler.getInstance(this);
        int notifyId = db.findNotificationId(leak);
        if (notifyId < 0) {
            return;
        }
        int frequency = db.findNotificationCounter(notifyId, leak.category.name());

        buildNotification(notifyId, frequency, leak);

    }

    void buildNotification(int notifyId, int frequency, LeakReport leak) {

        int idx = leak.metaData.destHostName.lastIndexOf('.');
        String destNetwork;
        if (idx > 0)
            idx = leak.metaData.destHostName.lastIndexOf('.', idx-1);
        if (idx > -1)
            destNetwork = leak.metaData.destHostName.substring(idx+1);
        else
            destNetwork = leak.metaData.destHostName;

        String msg = "Leaking " + leak.category.name() + " to " + destNetwork;
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_spam)
                        .setContentTitle(leak.metaData.appName)
                        .setContentText(msg).setNumber(frequency)
                        .setTicker(msg)
                        .setAutoCancel(true);

        Intent ignoreIntent = new Intent(this, ActionReceiver.class);
        ignoreIntent.setAction("Ignore");
        ignoreIntent.putExtra("notificationId", notifyId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), (int) System.currentTimeMillis(), ignoreIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.addAction(R.drawable.ic_cancel, "Ignore " + leak.category.name() + " leaks for this app", pendingIntent);

        Intent resultIntent = new Intent(this, AppSummaryActivity.class);
        resultIntent.putExtra(PrivacyLeakDetection.EXTRA_PACKAGE_NAME, leak.metaData.packageName);
        resultIntent.putExtra(PrivacyLeakDetection.EXTRA_APP_NAME, leak.metaData.appName);
        resultIntent.putExtra(PrivacyLeakDetection.EXTRA_IGNORE, 0);

        // stack builder对象将包含一个用于启动活动的返回栈。
        //这就确保了从活动中向后导航会导致主屏幕的出现
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // 为Intent添加返回栈（但不是Intent本身）
        stackBuilder.addParentStack(AppSummaryActivity.class);
        // 添加将活动启动到返回栈顶的Intent。
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(notifyId, mBuilder.build());

    }


    public void deleteNotification(int id) {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.cancel(id);
    }

    private void stop() {
        running = false;
        if (mInterface == null) return;
        Logger.d(TAG, "Stopping");
        try {
            readThread.interrupt();
            writeThread.interrupt();
            localServer.interrupt();
            if (PrivacyLeakDetection.asynchronous) filterThread.interrupt();
            mInterface.close();
        } catch (IOException e) {
            Logger.e(TAG, e.toString() + "\n" + Arrays.toString(e.getStackTrace()));
        }
        mInterface = null;
    }

    public void startVPN(Context context) {
        Intent intent = new Intent(context, FakeVpnService.class);
        context.startService(intent);
        started = true;
    }

    public void stopVPN() {
        stop();
        stopSelf();
    }

    public class MyVpnServiceBinder extends Binder {
        public FakeVpnService getService() {
            // 返回FakeVpnService实例，以便clients调用公共方法。
            return FakeVpnService.this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == IBinder.LAST_CALL_TRANSACTION) {
                onRevoke();
                return true;
            }
            return false;
        }
    }
}
