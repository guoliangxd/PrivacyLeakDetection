package cn.edu.xidian.privacyleakdetection.Application;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import cn.edu.xidian.privacyleakdetection.Utilities.AndroidLoggingHandler;
import java.util.logging.Level;


public class PrivacyLeakDetection extends Application {
    public final static String EXTRA_DATA = "PrivacyLeakDetection.DATA";
    public final static String EXTRA_ID = "PrivacyLeakDetection.id";
    public final static String EXTRA_APP_NAME = "PrivacyLeakDetection.appName";
    public final static String EXTRA_PACKAGE_NAME = "PrivacyLeakDetection.packageName";
    public final static String EXTRA_CATEGORY = "PrivacyLeakDetection.category";
    public final static String EXTRA_IGNORE = "PrivacyLeakDetection.ignore";
    public final static String EXTRA_SIZE = "PrivacyLeakDetection.SIZE";
    public final static String EXTRA_DATE_FORMAT = "PrivacyLeakDetection.DATE";
    public static boolean doFilter = true;
    public static boolean asynchronous = true;
    public static int tcpForwarderWorkerRead = 0;
    public static int tcpForwarderWorkerWrite = 0;
    public static int socketForwarderWrite = 0;
    public static int socketForwarderRead = 0;

    private static Application sApplication;

    public static Application getApplication() {
        return sApplication;
    }

    public static Context getAppContext() {
        return getApplication().getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;

        AndroidLoggingHandler loggingHandler = new AndroidLoggingHandler();
        AndroidLoggingHandler.reset(loggingHandler);
        loggingHandler.setLevel(Level.FINEST);
    }
}
