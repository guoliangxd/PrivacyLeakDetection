package cn.edu.xidian.privacyleakdetection.Application.Database;

import android.annotation.TargetApi;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.AsyncTask;

import cn.edu.xidian.privacyleakdetection.Application.Helpers.PermissionsHelper;

import java.util.Date;
import java.util.concurrent.TimeUnit;


@TargetApi(22)
public class UpdateLeakForegroundStatus extends AsyncTask<Long, Void, Void> {
    private Context context;

    public UpdateLeakForegroundStatus(Context context) {
        super();
        this.context = context;
    }

    @Override
    protected Void doInBackground(Long... params) {
        if (!PermissionsHelper.validBuildVersionForAppUsageAccess() ||
                !PermissionsHelper.hasUsageAccessPermission(context)) {
            return null;
        }

        long id = params[0];
        DatabaseHandler databaseHandler = DatabaseHandler.getInstance(context);
        DataLeak leak = databaseHandler.getLeakById(id);

        long leakTime = leak.getTimestampDate().getTime();

        UsageStatsManager usageStatsManager = (UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
        long currentTime = (new Date()).getTime();

        UsageEvents usageEvents = usageStatsManager.queryEvents(currentTime - TimeUnit.DAYS.toMillis(1), currentTime);

        UsageEvents.Event lastEvent = null;
        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            usageEvents.getNextEvent(event);
            if (event.getTimeStamp() > leakTime) break;

            if (event.getPackageName().equals(leak.getPackageName()) &&
                    (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                     event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND)) {
                lastEvent = event;
            }
        }

        if (lastEvent == null) {
            // 有些应用程序会在没有用户打开应用程序的情况下泄漏信息。
            //例如，有些应用程序会监听互联网连接，然后在后台运行服务，而不需要打开应用程序。
            //在这种情况下，将不会有状态事件来对泄漏进行分类，并将其分类为后台。
            databaseHandler.setDataLeakStatus(id, DatabaseHandler.BACKGROUND_STATUS);
        }
        else if (lastEvent.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            databaseHandler.setDataLeakStatus(id, DatabaseHandler.FOREGROUND_STATUS);
        }
        else if (lastEvent.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            databaseHandler.setDataLeakStatus(id, DatabaseHandler.BACKGROUND_STATUS);
        }
        else {
            throw new RuntimeException("A leak's status should always be classified by this task.");
        }

        return null;
    }
}
