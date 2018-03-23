package cn.edu.xidian.privacyleakdetection.utils;

/**
 * Created by 99544 on 2018/3/23/023.
 * 辅助类，用于查询权限是否属于危险权限，具体属于哪个危险权限组
 */

public class PermissionGroup {
    //private String[][] dangers = new String[9][];
    private static String[] dangers = {
            "android.permission.READ_CALENDER",
            "android.permission.WRITE_CALENDER",
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.BODY_SENSORS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };
    private static String[] calender = {
            "android.permission.READ_CALENDER",
            "android.permission.WRITE_CALENDER"
    };
    private static String[] camera = {
            "android.permission.CAMERA"
    };
    private static String[] location = {
            "andorid.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"
    };
    private static String[] microphone = {
            "android.permission.RECORD_AUDIO"
    };
    private static String[] phone = {
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.PROCESS_OUTGOING_CALLS"
    };
    private static String[] sensors = {
            "android.permission.BODY_SENSORS"
    };
    private static String[] sms = {
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS"
    };
    private static String[] storage = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };
    //TODO：查找权限
    /*
    查询危险权限数量
     */
    public static int queryDangersPermission(String[] permissions){
        int i = 0;
        for(int j = 0; j < permissions.length; j ++ ){
           for(int k = 0; k < dangers.length; k ++){
               if(permissions[j].equals(dangers[k])){
                   i ++;
                   break;
               }
           }
        }
        return i;
    }

}
