package cn.edu.xidian.privacyleakdetection.Application.Network;

import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN.FakeVpnService;
import cn.edu.xidian.privacyleakdetection.Plugin.IPlugin;
import cn.edu.xidian.privacyleakdetection.Plugin.LeakReport;
import cn.edu.xidian.privacyleakdetection.Plugin.TrafficRecord;
import cn.edu.xidian.privacyleakdetection.Plugin.TrafficReport;

import java.util.concurrent.LinkedBlockingQueue;

public class FilterThread extends Thread {
    private static final String TAG = FilterThread.class.getSimpleName();
    private static final boolean DEBUG = false;
    private LinkedBlockingQueue<FilterMsg> toFilter = new LinkedBlockingQueue<>();
    private FakeVpnService vpnService;
    ConnectionMetaData metaData;

    public FilterThread(FakeVpnService vpnService) {
        this.vpnService= vpnService;
    }

    public FilterThread(FakeVpnService vpnService, ConnectionMetaData metaData) {
        this.vpnService = vpnService;
        this.metaData = metaData;
    }

    public void offer(String msg, ConnectionMetaData metaData) {
        FilterMsg filterData = new FilterMsg(msg, metaData);
        toFilter.offer(filterData);
    }

    public void filter(String msg) {
        filter(msg, metaData);
    }

    public void filter(String msg, ConnectionMetaData metaData) {

        TrafficReport traffic;
        TrafficRecord record = vpnService.getTrafficRecord();
        traffic = record.handle(msg);

        if(traffic != null){
            traffic.metaData = metaData;
            vpnService.addtotraffic(traffic);
        }

        if(metaData.outgoing) {

            Logger.logTraffic(metaData, msg);

            for (IPlugin plugin : vpnService.getNewPlugins()) {
                LeakReport leak = plugin.handleRequest(msg);
                if (leak != null) {
                    leak.metaData = metaData;
                    vpnService.notify(msg, leak);
                    if (DEBUG) Logger.v(TAG, metaData.appName + " is leaking " + leak.category.name());
                    Logger.logLeak(leak.category.name());
                }
            }
        }
    }

    public void run() {
        try {
            while (!interrupted()) {
                FilterMsg temp = toFilter.take();
                filter(temp.msg, temp.metaData);
            }
        } catch (InterruptedException e) {
        }
    }

    class FilterMsg {
        ConnectionMetaData metaData;
        String msg;

        FilterMsg(String msg, ConnectionMetaData metaData) {
            this.msg = msg;
            this.metaData = metaData;
        }
    }
}
