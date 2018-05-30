/*
 * Abstract class for all forwarders
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

import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN.FakeVpnService;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPHeader;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPPayLoad;


public abstract class AbsForwarder {
    private static final String TAG = AbsForwarder.class.getSimpleName();
    private static final boolean DEBUG = false;
    protected FakeVpnService vpnService;
    //protected boolean closed = true;
    protected int port;
    public AbsForwarder(FakeVpnService vpnService, int port) {
        this.vpnService = vpnService;
        this.port = port;
    }


    public abstract void release();

    public int getPort() { return port; }

    public abstract boolean hasExpired();

    public abstract void forwardRequest(IPDatagram ip);

    public abstract void forwardResponse(byte[] response);

    public int forwardResponse(IPHeader ipHeader, IPPayLoad datagram) {
        if(ipHeader == null || datagram == null) return 0;
        datagram.update(ipHeader); // set the checksum
        IPDatagram newIpDatagram = new IPDatagram(ipHeader, datagram); // set the ip datagram, will update the length and the checksum
        if (DEBUG) Logger.d("AbsForwarder",newIpDatagram.headerToString());
        vpnService.fetchResponse(newIpDatagram.toByteArray());
        return datagram.virtualLength();
    }

}
