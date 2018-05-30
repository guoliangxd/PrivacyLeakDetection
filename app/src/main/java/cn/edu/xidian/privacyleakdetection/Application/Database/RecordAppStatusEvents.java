package cn.edu.xidian.privacyleakdetection.Application.Database;

import android.annotation.TargetApi;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import cn.edu.xidian.privacyleakdetection.Application.Helpers.PermissionsHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;


@TargetApi(22)
public class RecordAppStatusEvents extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals("android.intent.action.DATE_CHANGED")) {
            // 测试。
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().putLong("date_changed", sp.getLong("date_changed", 0) + 1).apply();
        }

        if (intent.getAction().equals("android.intent.action.ACTION_SHUTDOWN")) {
            // 测试。
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().putLong("shutdown_action", sp.getLong("shutdown_action", 0) + 1).apply();
        }

        if (intent.getAction().equals("android.intent.action.QUICKBOOT_POWEROFF")) {
            // 测试。
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().putLong("quick_boot", sp.getLong("quick_boot", 0) + 1).apply();
        }

        // 测试。
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putLong("service_called", sp.getLong("service_called", 0) + 1).apply();

        // 要运行此服务，构建版本必须是有效的，并且必须授予使用访问权限。
        if (!PermissionsHelper.validBuildVersionForAppUsageAccess() ||
                !PermissionsHelper.hasUsageAccessPermission(context)) {
            return;
        }

        DatabaseHandler databaseHandler = DatabaseHandler.getInstance(context);

        List<AppSummary> apps = databaseHandler.getAllApps();
        HashSet<String> appPackageNames = new HashSet<>();
        for (AppSummary summary : apps) {
            appPackageNames.add(summary.getPackageName());
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
        long currentTime = (new Date()).getTime();

        // 每次电话关机或在电话中有日期变化时，这个接收器就会被调用。
        //因此，在过去的1天里，没有记录的状态事件可以存在。
        //回顾过去的两天，以确保所有状态事件都被记录下来。
        UsageEvents usageEvents = usageStatsManager.queryEvents(currentTime - TimeUnit.DAYS.toMillis(2), currentTime);

        List<UsageEvents.Event> appUsageEvents = new ArrayList<>();
        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            usageEvents.getNextEvent(event);
            if (appPackageNames.contains(event.getPackageName()) &&
                    (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                     event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND)) {
                appUsageEvents.add(event);
            }
        }

        HashSet<AppStatusEvent> databaseStatusEvents = new HashSet<>();
        databaseStatusEvents.addAll(databaseHandler.getAppStatusEvents());

        for (UsageEvents.Event event : appUsageEvents) {
            int foreground = event.getEventType() ==
                    UsageEvents.Event.MOVE_TO_FOREGROUND ? DatabaseHandler.FOREGROUND_STATUS : DatabaseHandler.BACKGROUND_STATUS;
            AppStatusEvent temp = new AppStatusEvent(event.getPackageName(), event.getTimeStamp(), foreground);
            if (!databaseStatusEvents.contains(temp)) {
                databaseHandler.addAppStatusEvent(event.getPackageName(), event.getTimeStamp(), foreground);
            }
        }
    }
}
