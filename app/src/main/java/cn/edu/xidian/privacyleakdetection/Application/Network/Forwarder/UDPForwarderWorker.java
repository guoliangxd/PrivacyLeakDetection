package cn.edu.xidian.privacyleakdetection.Application.Network.Forwarder;

import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPHeader;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.UDP.UDPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.UDP.UDPHeader;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 从套接字读取UDP响应数据包的线程，添加header，并将它们放入队列中，通过tunwrite，每个UDP请求源端口都有一个这样的线程
 */
public class UDPForwarderWorker extends Thread {
    private static final String TAG = UDPForwarderWorker.class.getSimpleName();
    private static final boolean DEBUG = false;
    private final int LIMIT = 32767;
    private UDPForwarder forwarder;
    private IPHeader newIPHeader;
    private UDPHeader newUDPHeader;
    private int srcPort;
    private DatagramSocket socket;

    // ip数据报是一个请求UDP数据包，它将被转发到套接字上，这个线程将等待对这个请求的响应（或者稍后请求来自同一个源端口的UDP数据包）
    public UDPForwarderWorker(IPDatagram ipDatagram, DatagramSocket socket, UDPForwarder forwarder) {
        this.socket = socket;
        this.forwarder = forwarder;
        this.newIPHeader = ipDatagram.header().reverse();
        UDPDatagram udpDatagram = (UDPDatagram)ipDatagram.payLoad();
        this.newUDPHeader = (UDPHeader)udpDatagram.header().reverse();
    }

    @Override
    public void interrupt(){
        super.interrupt();
        if (socket != null) socket.close();
    }

    public void run() {
        ByteBuffer packet = ByteBuffer.allocate(LIMIT);
        DatagramPacket datagram = new DatagramPacket(packet.array(), LIMIT);

        try {
            while (!isInterrupted()) {
                packet.clear();
                socket.receive(datagram);

                byte[] received = Arrays.copyOfRange(datagram.getData(), 0, datagram.getLength());
                if (received != null) {
                    newIPHeader.updateSrcAddress(datagram.getAddress());
                    newUDPHeader.updateSrcPort(datagram.getPort());
                    UDPDatagram response = new UDPDatagram(newUDPHeader, received);
                    if (DEBUG) Logger.d(TAG, "response is " + response.debugString());
                    forwarder.forwardResponse(newIPHeader, response);
                }
            }
        } catch (SocketException e) {
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}
