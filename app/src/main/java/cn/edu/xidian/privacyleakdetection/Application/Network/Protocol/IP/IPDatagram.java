package cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP;

import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TCP.TCPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.UDP.UDPDatagram;
import cn.edu.xidian.privacyleakdetection.Utilities.ByteOperations;
import cn.edu.xidian.privacyleakdetection.Application.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;


public class IPDatagram {
    public final static String TAG = "IPDatagram";
    public static final int TCP = 6, UDP = 17;
    IPHeader header;
    IPPayLoad data;

    public IPDatagram(IPHeader header, IPPayLoad data) {
        this.header = header;
        this.data = data;
        int totalLength = header.headerLength() + data.length();
        if (this.header.length() != totalLength) {
            this.header.setLength(totalLength);
            this.header.setCheckSum(new byte[]{0, 0});
            byte[] toComputeCheckSum = this.header.toByteArray();
            this.header.setCheckSum(ByteOperations.computeCheckSum(toComputeCheckSum));
        }
    }

    public static IPDatagram create(ByteBuffer packet) {
        IPHeader header = IPHeader.create(packet.array());
        IPPayLoad payLoad;
        if (header.protocol() == TCP) {
            payLoad = TCPDatagram.create(packet.array(), header.headerLength(), packet.limit(), header.getDstAddress());
        } else if (header.protocol() == UDP) {
            payLoad = UDPDatagram.create(Arrays.copyOfRange(packet.array(), header.headerLength(), packet.limit()));
        } else return null;
        return new IPDatagram(header, payLoad);
    }

    public IPHeader header() {
        return header;
    }

    public IPPayLoad payLoad() {
        return data;
    }

    public byte[] toByteArray() {
        return ByteOperations.concatenate(header.toByteArray(), data.toByteArray());
    }

    public void debugInfo() {
        Logger.d(TAG, "SrcAddr=" + header.getSrcAddress() + " DstAddr=" + header.getDstAddress());
    }

    public String headerToString()
    {
        StringBuffer sb = new StringBuffer("SrcAddr=");
        sb.append(header.getSrcAddress());
        sb.append(" DstAddr=");
        sb.append(header.getDstAddress());
        sb.append(" ");
        return sb.toString();
    }
}