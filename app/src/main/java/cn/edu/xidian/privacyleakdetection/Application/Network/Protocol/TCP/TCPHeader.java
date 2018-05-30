package cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TCP;

import cn.edu.xidian.privacyleakdetection.Application.Network.Protocol.TransportHeader;
import cn.edu.xidian.privacyleakdetection.Utilities.ByteOperations;

import java.nio.ByteBuffer;
import java.util.Arrays;


public class TCPHeader extends TransportHeader {
  public static final byte FIN = 0x01;
  public static final byte ACK = 0x10;
  public static final byte FINACK = (byte)(FIN | ACK);
  public static final byte SYN = 0x02;
  public static final byte SYNACK = (byte)(SYN | ACK);
  public static final byte PSH = 0x08;
  public static final byte DATA = (byte)(PSH | ACK);
  public static final byte RST = 0x04;
  private static final String TAG = "TCPHeader";
  private int offset, seq_num, ack_num;

  public TCPHeader(byte[] data) {
    super(data);
    offset = (data[12] & 0xF0) / 4;
    data[12] = (byte)((data[12] & 0x0F) + 0x50);
    seq_num = ByteOperations.byteArrayToInteger(data, 4, 8);
    ack_num = ByteOperations.byteArrayToInteger(data, 8, 12);
    checkSum_pos = 16;
    checkSum_size = 2;
    this.data = Arrays.copyOfRange(data, 0, 20);
  }

  public TCPHeader(byte[] data, int start) {
    super(data, start);
    offset = (data[12 + start] & 0xF0) / 4;
    data[12 + start] = (byte) ((data[12 + start] & 0x0F) + 0x50);
    seq_num = ByteOperations.byteArrayToInteger(data, 4 + start, 8);
    ack_num = ByteOperations.byteArrayToInteger(data, 8 + start, 12);
    checkSum_pos = 16;
    checkSum_size = 2;
    this.data = Arrays.copyOfRange(data, start, 20 + start);
  }

  public int offset() {
    return offset;
  }

  @Override
  public TCPHeader reverse() {
    byte[] reverseData = Arrays.copyOfRange(data, 0, data.length);
    ByteOperations.swap(reverseData, 0, 2, 2);
    return new TCPHeader(reverseData);
  }

  public int getSeq_num() {
    seq_num = ByteOperations.byteArrayToInteger(data, 4, 8);
    return seq_num;
  }

  public void setSeq_num(int seq) {
    byte[] bytes = ByteBuffer.allocate(4).putInt(seq).array();
    System.arraycopy(bytes, 0, data, 4, 4);
  }

  public int getAck_num() {
    ack_num = ByteOperations.byteArrayToInteger(data, 8, 12);
    return ack_num;
  }

  public void setAck_num(int ack) {
    byte[] bytes = ByteBuffer.allocate(4).putInt(ack).array();
    System.arraycopy(bytes, 0, data, 8, 4);
  }

  public byte getFlag() {
    return data[13];
  }

  public void setFlag(byte flag) {
    data[13] = flag;
  }
}
