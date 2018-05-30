package cn.edu.xidian.privacyleakdetection.Plugin;

import android.content.Context;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;


public interface IPlugin {
    // 可以修改请求与响应的内容。
    public LeakReport handleRequest(String request);
    public LeakReport handleResponse(String response);
    public String modifyRequest(String request);
    public String modifyResponse(String response);
    public void setContext(Context context);
}
