/*
* TCP forwarder, implement a simple tcp protocol
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
import cn.edu.xidian.privacyleakdetection.Application.Network.LocalServer;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPPayLoad;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TCP.TCPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TCP.TCPHeader;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TCPConnectionInfo;
import cn.edu.xidian.privacyleakdetection.Application.PrivacyLeakDetection;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TCPForwarder extends AbsForwarder { //implements ICommunication {
    private static final String TAG = TCPForwarder.class.getSimpleName();
    private static final boolean DEBUG = false;
    private final int WAIT_BEFORE_RELEASE_PERIOD_AFTER_CLOSE = 60000;
    private final int WAIT_BEFORE_RELEASE_PERIOD_IF_IDLE = 300000;
    protected Status status;
    protected boolean firstData = true;
    private TCPForwarderWorker worker;
    private TCPConnectionInfo conn_info;
    protected long releaseTimeAfterClose;
    protected long releaseTimeIfIdle;
    private boolean closed = true;
    private Socket socketToLocalServer;
    private int src_port;

    public TCPForwarder(FakeVpnService vpnService, int port) {
        super(vpnService, port);
        status = Status.LISTEN;
        closed = false;
    }

    private boolean handle_LISTEN(IPDatagram ipDatagram, byte flag, int len) {
        if (flag != TCPHeader.SYN) {
            if (DEBUG) Logger.d(TAG, "LISTEN: Resetting " + ipDatagram.payLoad().header().getSrcPort() + " : " + ipDatagram.payLoad().header().getDstPort());
            close(true);
            return false;
        }
        if (DEBUG) Logger.d(TAG, "LISTEN: Accepting " + ipDatagram.payLoad().header().getSrcPort() + " : " + ipDatagram.payLoad().header().getDstPort());
        conn_info.reset(ipDatagram);
        conn_info.setup(this);
        if (worker == null || !worker.isValid()) {
            close(true);
            if (DEBUG) Logger.d(TAG, "LISTEN: Failed to set up worker for " + ipDatagram.payLoad().header().getDstPort());
            return false;
        }
        TCPDatagram response = new TCPDatagram(conn_info.getTransHeader(len, TCPHeader.SYNACK), null, conn_info.getDstAddress());
        if (DEBUG) Logger.d(TAG, "LISTEN: Responded with " + response.headerToString());
        conn_info.increaseSeq(
                forwardResponse(conn_info.getIPHeader(), response)
        );
        status = Status.SYN_ACK_SENT;
        if (DEBUG) Logger.d(TAG, "LISTEN: Switched to SYN_ACK_SENT");
        return true;
    }

/*
* step 1 : 逆向IP header；
* step 2 : 创建一个新的TCP header, 正确设置syn, ack；
* step 3 : 如果有必要，获取响应；
* step 4 : 合并响应，并创建一个新的TCP数据报；
* step 5 : 更新数据报校验和；
* step 6 : 合并IP数据报和TCP数据报，更新ip header。
*/

    private boolean handle_SYN_ACK_SENT(byte flag) {
        if(flag != TCPHeader.ACK) {
            if (DEBUG) Logger.d(TAG, "SYN_ACK_SENT: No ACK received, ignored");
            return false;
        }
        status = Status.DATA;
        if (DEBUG) Logger.d(TAG, "SYN_ACK_SENT: Switched to DATA");
        return true;
    }

    private boolean handle_DATA(IPDatagram ipDatagram, byte flag, int len, int rlen) {
        if(firstData){
            firstData = false;
        }else{
            assert ((flag & TCPHeader.ACK) == 0);
            if (((flag & TCPHeader.ACK) == 0) && ((flag & TCPHeader.RST) == 0)) {
                if (DEBUG) Logger.e(TAG, "DATA: ACK is 0 for Datagram:\nHeader: " + ipDatagram.header().toString() +"\nPayload: "+ipDatagram.payLoad().toString());
                return false;
            }
        }

        if(rlen > 0) {
            send(ipDatagram.payLoad());
            conn_info.increaseSeq(
                    forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(len, TCPHeader.ACK), null, conn_info.getDstAddress()))
            );
        } else if(flag == TCPHeader.FINACK) { // FIN
            conn_info.increaseSeq(
                    forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(len, TCPHeader.ACK), null, conn_info.getDstAddress()))
            );
            conn_info.increaseSeq(
                    forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(0, TCPHeader.FINACK), null, conn_info.getDstAddress()))
            );
            if (DEBUG) Logger.d(TAG, "DATA: FIN received, closing");
            close(false);
        } else if((flag & TCPHeader.RST) != 0) { // RST
            close(false);
            if (DEBUG) Logger.d(TAG, "DATA: RST received, closing");
        }
        // if none of the above hold, we have an empty ACK
        return true;
    }

    private boolean handle_HALF_CLOSE_BY_CLIENT(byte flag) {
        assert(flag == TCPHeader.ACK);
        if ((flag != TCPHeader.ACK)) {
//TODO: find out why this would happen
            if (DEBUG) Logger.e(TAG, "ACK is 0");
            return false;
        }
        status = Status.CLOSED;
        if (DEBUG) Logger.d(TAG, "HALF_CLOSE_BY_CLIENT close");
        close(false);
        return true;
    }

    private boolean handle_HALF_CLOSE_BY_SERVER(byte flag, int len) {
        if(flag == TCPHeader.FINACK) {
            conn_info.increaseSeq(
                    forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(len, TCPHeader.ACK), null, conn_info.getDstAddress()))
            );
            status = Status.CLOSED;
            if (DEBUG) Logger.d(TAG, "HALF_CLOSE_BY_SERVER close");
            close(false);
        }
        return true;
    }

    protected synchronized void handle_packet (IPDatagram ipDatagram) {
        if(closed) return;
        byte flag;
        int len, rlen;
        if(ipDatagram != null) {
            flag = ((TCPHeader)ipDatagram.payLoad().header()).getFlag();
            len = ipDatagram.payLoad().virtualLength();
            rlen = ipDatagram.payLoad().dataLength();
            if(conn_info == null) conn_info = new TCPConnectionInfo(ipDatagram);
        } else return;
        if (DEBUG) Logger.d(TAG, "HANDLING: " + ipDatagram.headerToString() + ((TCPDatagram)ipDatagram.payLoad()).headerToString());
        switch(status) {
            case LISTEN:
                if (DEBUG) Logger.d(TAG, "LISTEN");
                if(!handle_LISTEN(ipDatagram, flag, len)) return;
                else break;
            case SYN_ACK_SENT:
                if (DEBUG) Logger.d(TAG, "SYN_ACK_SENT");
                if(!handle_SYN_ACK_SENT(flag)) return;
                else break;
            case DATA:
                if (DEBUG) Logger.d(TAG, "DATA");
                if(!handle_DATA(ipDatagram, flag, len, rlen)) return;
                else break;
            case HALF_CLOSE_BY_CLIENT:
                if (DEBUG) Logger.d(TAG, "HALF_CLOSE_BY_CLIENT");
                if(!handle_HALF_CLOSE_BY_CLIENT(flag)) return;
                else break;
            case HALF_CLOSE_BY_SERVER:
                if (DEBUG) Logger.d(TAG, "HALF_CLOSE_BY_SERVER");
                if(!handle_HALF_CLOSE_BY_SERVER(flag, len)) return;
                else break;
            case CLOSED:
                //status = Status.CLOSED;
            default:
                break;
        }
    }

    /*
    *  methods for AbsForwarder
    */
    public boolean setup(InetAddress srcAddress, int src_port, InetAddress dstAddress, int dst_port) {
        vpnService.getClientAppResolver().setLocalPortToRemoteMapping(src_port, dstAddress.getHostAddress(), dst_port);

        try {
            //socketChannel = SocketChannel.open();
            //Socket socket = socketChannel.socket();
            socketToLocalServer = new Socket();
            vpnService.protect(socketToLocalServer);
            socketToLocalServer.setReuseAddress(true);
            socketToLocalServer.bind(new InetSocketAddress(InetAddress.getLocalHost(), src_port));
            this.src_port = src_port;
            try {
                socketToLocalServer.connect(new InetSocketAddress(LocalServer.port));
            } catch (ConnectException e) {
                e.printStackTrace();
            }

            worker = new TCPForwarderWorker(socketToLocalServer, this, src_port);
            worker.start();

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    public boolean isClosed() {
        return closed;
    }

    @Override
    public void forwardRequest(IPDatagram ipDatagram) {

        handle_packet(ipDatagram);

        releaseTimeIfIdle = System.currentTimeMillis() + WAIT_BEFORE_RELEASE_PERIOD_IF_IDLE;
    }

    @Override
    public synchronized void forwardResponse(byte[] response) {
        if (conn_info == null) return;
        conn_info.increaseSeq(
                forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(0, TCPHeader.DATA), response, conn_info.getDstAddress()))
        );
    }

    /*
    * Methods for ICommunication
    */
    public void send(IPPayLoad payLoad) {
        if(isClosed()) {
            status = Status.HALF_CLOSE_BY_SERVER;
            conn_info.increaseSeq(
                    forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(0, TCPHeader.FINACK), null, conn_info.getDstAddress()))
            );
        } else send(payLoad.data());
    }

    private void close(boolean sendRST) {
        closed = true;
        if(sendRST) forwardResponse(conn_info.getIPHeader(), new TCPDatagram(conn_info.getTransHeader(0, TCPHeader.RST), null, conn_info.getDstAddress()));
        status = Status.CLOSED;
        try {
            if (socketToLocalServer != null) socketToLocalServer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (worker != null) {
            worker.interrupt();
        }
        if (DEBUG) Logger.d(TAG, "Preparing for release of TCP forwarder for port " + port);
        releaseTimeAfterClose = System.currentTimeMillis() + WAIT_BEFORE_RELEASE_PERIOD_AFTER_CLOSE;
    }

    public void release()
    {
        if (DEBUG) Logger.d(TAG, "Releasing TCP forwarder for port " + port);
    }

    public boolean hasExpired() {
        long current = System.currentTimeMillis();

        if (closed) {
            if (releaseTimeAfterClose < current) return true;
        }
        else if (releaseTimeIfIdle < current) {
            if (DEBUG) Logger.d(TAG, "Going to release TCP forwarder for port " + port + " due to idleness");
            close(true);
        }

        return false;
    }

    public enum Status {
        DATA, LISTEN, SYN_ACK_SENT, HALF_CLOSE_BY_CLIENT, HALF_CLOSE_BY_SERVER, CLOSED
    }

    private void send(byte[] request) {

        try {
            socketToLocalServer.getOutputStream().write(request);
            if (DEBUG) Logger.d(TAG, request.length + " bytes forwarded to LocalServer from port: " + src_port);
            PrivacyLeakDetection.tcpForwarderWorkerWrite += request.length;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
