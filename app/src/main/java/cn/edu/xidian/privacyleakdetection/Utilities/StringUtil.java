package cn.edu.xidian.privacyleakdetection.Utilities;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;


public class StringUtil {

    public static String EmptyString = "";

    public static String typeFromMsg(String msg) {
        msg = msg.replace("is leaking", "");
        return msg.trim();
    }

    public static String locationFromMsg(String msg) {
        msg = msg.replace("is leaking", "");
        int endIndex = msg.lastIndexOf(":");
        if (endIndex > 0) {
            msg = msg.substring(0, endIndex-1);
        }
        return msg.trim();
    }

    public static String encodeAt(String emailAddress) {
        return emailAddress.replace("@", "%40");
    }

    public static String encodeColon(String macAddress) {
        return macAddress.replace(":", "%3A");
    }
}
