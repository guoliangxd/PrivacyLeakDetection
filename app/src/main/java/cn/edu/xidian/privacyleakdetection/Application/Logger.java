package cn.edu.xidian.privacyleakdetection.Application;

import android.location.Location;
import android.os.Environment;
import android.util.Log;

import cn.edu.xidian.privacyleakdetection.BuildConfig;
import cn.edu.xidian.privacyleakdetection.Application.Network.ConnectionMetaData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class Logger {
    private static final String TIME_STAMP_FORMAT = "MM-dd HH:mm:ss.SSS";
    private static final SimpleDateFormat df = new SimpleDateFormat(TIME_STAMP_FORMAT, Locale.CANADA);//TODO: auto detect locale
    private static File logFile = new File(getDiskCacheDir(), "Log");
    private static File trafficFile = new File(getDiskCacheDir(), "NetworkTraffic");
    private static File locationFile = new File(getDiskCacheDir(), "LastLocations");


    public static File getDiskCacheDir() {
        File cacheDir = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cacheDir = PrivacyLeakDetection.getAppContext().getExternalCacheDir();
        }
        if (cacheDir == null) {
            if (BuildConfig.DEBUG) {
                Log.d("LoggerManager", "External Cache Directory not available.");
            }
            cacheDir = PrivacyLeakDetection.getAppContext().getCacheDir();
        }
        if (BuildConfig.DEBUG) {
            Log.d("LoggerManager", "Logging to " + cacheDir);
        }
        return cacheDir;
    }

    public static File getDiskFileDir() {
        File fileDir = null;
        if (BuildConfig.DEBUG && (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
               || !Environment.isExternalStorageRemovable())) {
            fileDir = PrivacyLeakDetection.getAppContext().getExternalFilesDir(null);
        }
        if (fileDir == null) {
            if (BuildConfig.DEBUG) {
                Log.d("LoggerManager", "External Cache Directory not available.");
            }
            fileDir = PrivacyLeakDetection.getAppContext().getFilesDir();
        }
        if (BuildConfig.DEBUG) {
            Log.d("LoggerManager", "Storing files in " + fileDir);
        }
        return fileDir;
    }

    public static void logToFile(String tag, String msg) {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
            out.println("Time : " + df.format(new Date()));
            out.println(" [ " + tag + " ] ");
            out.println(msg);
            out.println("");
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void i(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, msg);
        } else {
            logToFile(tag, msg);
        }
    }

    // ignore debug if release
    public static void d(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void e(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg);
        } else {
            logToFile(tag, msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, msg,tr);
        } else {
            logToFile(tag, msg + '\n' + Log.getStackTraceString(tr));
        }
    }

    // ignore verbose if release
    public static void v(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, msg);
        } else {
            logToFile(tag, msg);
        }
    }

    public static void logTraffic(ConnectionMetaData metaData, String msg) {
        if (BuildConfig.DEBUG) {

            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(trafficFile, true)));
                out.println("=========================");
                out.println("Time : " + df.format(new Date()));
                out.println(" [ " + metaData.appName + " ]  " + metaData.packageName + "  src port: " + metaData.srcPort);
                out.println(" [ " + metaData.destHostName + " ] " + metaData.destIP + ":" + metaData.destPort);
                out.println("");
                out.println("Message:");
                out.println(msg);
                out.println("");
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void logLeak(String category) {
        //log network traffic ONLY in debug build
        if (BuildConfig.DEBUG) {

            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(trafficFile, true)));
                out.println("Leaking: " + category);
                out.println("");
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void logLastLocations(Map<String, Location> locations, boolean firstTime) {
        //log network traffic ONLY in debug build
        if (BuildConfig.DEBUG) {

            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(locationFile, true)));
                if (firstTime) {
                    out.println("Initial Location Information");
                } else {
                    out.println("Active Location Update");
                }
                out.println("Time : " + df.format(new Date()));
                for (Map.Entry<String, Location> locationEntry : locations.entrySet()) {
                    out.println(locationEntry.getKey() + " : lon = " + locationEntry.getValue().getLongitude() + ", lat = " + locationEntry.getValue().getLatitude());
                }
                out.println("");
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void logLastLocation(Location loc) {
        //log network traffic ONLY in debug build
        if (BuildConfig.DEBUG) {

            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(locationFile, true)));
                out.println("Passive Location Update");
                out.println("Time : " + df.format(new Date()));
                out.println(loc.getProvider() + " : lon = " + loc.getLongitude() + ", lat = " + loc.getLatitude());
                out.println("");
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
