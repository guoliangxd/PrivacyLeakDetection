package cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.UDP;

import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPPayLoad;
import cn.edu.xidian.privacyleakdetection.Utilities.ByteOperations;
import cn.edu.xidian.privacyleakdetection.Application.Logger;

import java.net.InetAddress;
import java.util.Arrays;

public class UDPDatagram extends IPPayLoad {
    private final String TAG = "UDPDatagram";
    public static UDPDatagram create(byte[] data) {
        UDPHeader header = new UDPHeader(data);
        return new UDPDatagram(header, Arrays.copyOfRange(data, 8, header.getTotal_length()));
    }

    public UDPDatagram(UDPHeader header, byte[] data) {
        this.header = header;
        this.data = data;
        if(header.getTotal_length() != data.length + header.headerLength()) {
            header.setTotal_length(data.length + header.headerLength());
        }
    }

    public void debugInfo(InetAddress dstAddress) {
        Logger.d(TAG, "DstAddr=" + dstAddress.getHostName() +
                " SrcPort=" + header.getSrcPort() + " DstPort=" + header.getDstPort() +
                " Total Length=" + ((UDPHeader)header).getTotal_length() +
                " Data Length=" + this.dataLength() +
                " Data=" + ByteOperations.byteArrayToString(this.data));
    }

    public String debugString() {
        StringBuffer sb = new StringBuffer("SrcPort=");
        sb.append(header.getSrcPort());
        sb.append(" DstPort=");
        sb.append(header.getDstPort());
        sb.append(" Total Length=");
        sb.append(((UDPHeader)header).getTotal_length());
        sb.append(" Data Length=");
        sb.append(this.dataLength());
        return sb.toString();
    }
}
