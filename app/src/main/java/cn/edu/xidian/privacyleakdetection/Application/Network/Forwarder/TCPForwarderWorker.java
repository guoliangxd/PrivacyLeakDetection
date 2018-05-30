package cn.edu.xidian.privacyleakdetection.Application.Network.Forwarder;

import cn.edu.xidian.privacyleakdetection.Application.PrivacyLeakDetection;
import cn.edu.xidian.privacyleakdetection.Application.Logger;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 作为tcp转发器和LocalServer之间的中间层
 */
public class TCPForwarderWorker extends Thread {
    private static final String TAG = TCPForwarderWorker.class.getSimpleName();
    private static final boolean DEBUG = false;
    private final int limit = 1368;
    private Socket socket;
    private TCPForwarder forwarder;
    private ByteBuffer msg = ByteBuffer.allocate(limit);
    private LinkedBlockingQueue<byte[]> requests = new LinkedBlockingQueue<>();
    private Sender sender;
    private int src_port;

    public TCPForwarderWorker(Socket socket, TCPForwarder forwarder, int src_port) {
        this.forwarder = forwarder;
        this.socket = socket;
        this.src_port = src_port;
    }

    public boolean isValid() {
        return true;
    }


    @Override
    // 从连接到LocalServer的套接字中读取响应，并将它们传递给tcp发器。
    public void run() {

        try {
            byte[] buff = new byte[limit];
            int got;
            InputStream in = socket.getInputStream();
            while ((got = in.read(buff)) > -1) {
                if (DEBUG) Logger.d(TAG, got + " response bytes to be written to " + src_port);
                PrivacyLeakDetection.tcpForwarderWorkerRead += got;
                byte[] temp = new byte[got];
                System.arraycopy(buff, 0, temp, 0, got);
                forwarder.forwardResponse(temp);
            }
        } catch (IOException e) {
        }
    }

    public void close() {

        if (sender != null && sender.isAlive()) {
            sender.interrupt();
        }
        try {
            socket.close();
                Logger.d(TAG, "closed socket for port " + src_port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 读取由tcp发器队列中的有效负载，并将它们放入连接到LocalServer的套接字中。
    public class Sender extends Thread {
        public void run() {
            try {
                byte[] temp;
                OutputStream stream = socket.getOutputStream();
                while (!isInterrupted() && !socket.isClosed()) {
                        temp = requests.take();
                            stream.write(temp);
                            stream.flush();
                            Logger.d(TAG, temp.length + " bytes forwarded to LocalServer from port: " + src_port);
                            PrivacyLeakDetection.tcpForwarderWorkerWrite += temp.length;
                    }
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}