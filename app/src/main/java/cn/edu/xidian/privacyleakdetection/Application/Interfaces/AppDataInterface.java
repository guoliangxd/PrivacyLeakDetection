package cn.edu.xidian.privacyleakdetection.Application.Interfaces;

import cn.edu.xidian.privacyleakdetection.Application.Database.DataLeak;
import cn.edu.xidian.privacyleakdetection.Application.Database.Traffic;
import cn.edu.xidian.privacyleakdetection.Plugin.LeakReport;

import java.util.List;


public interface AppDataInterface {
    String getAppName();
    String getAppPackageName();
    List<DataLeak> getLeaks(LeakReport.LeakCategory category);
    List<Traffic> getTraffics(boolean encrypted, boolean outgoing);
}
