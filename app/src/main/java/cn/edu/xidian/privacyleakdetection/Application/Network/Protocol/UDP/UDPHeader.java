package cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.UDP;

import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TransportHeader;
import cn.edu.xidian.privacyleakdetection.Utilities.ByteOperations;

import java.util.Arrays;

public class UDPHeader extends TransportHeader {
  private int total_length = 0;
  public UDPHeader(byte[] data) {
    super(data);
    this.data = Arrays.copyOfRange(data, 0, 8);
    this.total_length = ((data[4] & 0xFF) << 8) + (data[5] & 0xFF);
    this.checkSum_size = 2;
    this.checkSum_pos = 6;
  }

  @Override
  public UDPHeader reverse() {
    byte[] reverseData = Arrays.copyOfRange(data, 0, data.length);
    ByteOperations.swap(reverseData, 0, 2, 2);
    return new UDPHeader(reverseData);
  }

  public int getTotal_length() {
    return total_length;
  }

  public void setTotal_length(int l) {
    total_length = l;
    data[4] = (byte)((l & 0xFF00) >> 8);
    data[5] = (byte)(l & 0xFF);
  }
}
