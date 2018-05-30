package cn.edu.xidian.privacyleakdetection.Application.Network.Protocol;

import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPDatagram;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.IP.IPHeader;
import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TransportHeader;

import java.net.InetAddress;


public class ConnectionInfo {
  protected InetAddress clientAddress, serverAddress;
  protected int clientPort, serverPort;
  protected int protocol;

  protected IPHeader responseIPHeader;
  protected TransportHeader responseTransHeader;

  public ConnectionInfo(IPDatagram ipDatagram) {
    reset(ipDatagram);
  }

  public IPHeader getIPHeader() {
    return responseIPHeader;
  }

  public TransportHeader getTransHeader() {
    return responseTransHeader;
  }

  public InetAddress getDstAddress() { return serverAddress; }

  public void reset(IPDatagram ipDatagram) {
    this.clientAddress = ipDatagram.header().getSrcAddress();
    this.serverAddress = ipDatagram.header().getDstAddress();
    this.clientPort = ipDatagram.payLoad().getSrcPort();
    this.serverPort = ipDatagram.payLoad().getDstPort();
    this.protocol = ipDatagram.header().protocol();
    this.responseIPHeader = ipDatagram.header().reverse();
    this.responseTransHeader = ipDatagram.payLoad().header().reverse();
  }

}
