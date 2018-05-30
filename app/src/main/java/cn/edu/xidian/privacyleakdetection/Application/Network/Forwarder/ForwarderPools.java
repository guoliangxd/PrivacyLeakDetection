/*
 * Pool for all forwarders
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

package cn.edu.xidian.privacyleakdetection.Application.Network.Forwarder;

import android.util.Pair;

import cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN.FakeVpnService;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class ForwarderPools {
    private HashMap<Pair<Integer, Byte>, AbsForwarder> portToForwarder;
    private FakeVpnService vpnService;
    private static final String TAG = ForwarderPools.class.getSimpleName();
    private static final boolean DEBUG = false;

    public ForwarderPools(FakeVpnService vpnService) {
        this.vpnService = vpnService;
        portToForwarder = new HashMap<>();
    }

    public AbsForwarder get(int port, byte protocol) {

        releaseExpiredForwarders();
        Pair<Integer, Byte> key = new Pair<>(port, protocol);
        if (portToForwarder.containsKey(key)) { //&& !portToForwarder.get(key).isClosed()) {
            return portToForwarder.get(key);
        } else {
            AbsForwarder temp = getByProtocol(protocol, port);
            if (temp != null) {
                portToForwarder.put(key, temp);
            }
            return temp;
        }
    }

    void releaseExpiredForwarders() {
        if (DEBUG) Logger.d(TAG, "Number of forwarders: " + portToForwarder.size());
        Iterator it = portToForwarder.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            AbsForwarder fw = (AbsForwarder)pair.getValue();
            if (fw.hasExpired()) {
                fw.release();
                it.remove();
            }
        }
    }

    private AbsForwarder getByProtocol(byte protocol, int port) {
        switch (protocol) {
            case IPDatagram.TCP:
                if (DEBUG) Logger.d(TAG, "Creating TCP forwarder for src port " + port);
                return new TCPForwarder(vpnService, port);
            case IPDatagram.UDP:
                if (DEBUG) Logger.d(TAG, "Creating UDP forwarder for src port " + port);
                return new UDPForwarder(vpnService, port);
            default:
                if (DEBUG) Logger.d(TAG, "Unknown type of forwarder requested for protocol " + protocol);
                return null;
        }
    }

    public void release(UDPForwarder udpForwarder) {
        portToForwarder.values().removeAll(Collections.singleton(udpForwarder));
    }

    public void release(TCPForwarder tcpForwarder) {
        portToForwarder.values().removeAll(Collections.singleton(tcpForwarder));
    }
}
