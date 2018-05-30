package org.sandroproxy.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.sandrop.webscarab.httpclient.HTTPClient;
import org.sandrop.webscarab.httpclient.HTTPClientFactory;
import org.sandrop.webscarab.model.HttpUrl;
import org.sandrop.webscarab.model.NamedValue;
import org.sandrop.webscarab.model.Request;
import org.sandrop.webscarab.model.Response;
import org.sandroproxy.webscarab.store.sql.SqlLiteStore;

import dnsutils.DNS;
import dnsutils.DNSQuery;
import dnsutils.DNSRR;
import dnsutils.record.Address;


import android.content.Context;
import android.util.Log;

/**
 * DNS Proxy
 *
 * @original author biaji
 */
public class DNSProxy implements Runnable {
    
  private static Logger _logger = Logger.getLogger(DNSProxy.class.getName());
  
  private static boolean LOGD = true;
  
  public static String getHostNameFromIp(String ip){
      String hostName = null;
      if (dnsHostCache != null){
          if (dnsHostCache.containsKey(ip)){
              hostName =  dnsHostCache.get(ip);
              if (LOGD) Log.d(TAG, "returning " + hostName + " for " + ip);
          }
      }
      return hostName;
  }

  public static byte[] int2byte(int res) {
    byte[] targets = new byte[4];

    targets[0] = (byte) (res & 0xff);
    targets[1] = (byte) ((res >> 8) & 0xff);
    targets[2] = (byte) ((res >> 16) & 0xff);
    targets[3] = (byte) (res >>> 24);
    return targets;
  }

  private static final String TAG = DNSProxy.class.getSimpleName();

  private final static int MAX_THREAD_NUM = 5;
  private final static int LOCAL_DNS_PORT = 53;
  private final ExecutorService mThreadPool = Executors.newFixedThreadPool(MAX_THREAD_NUM);

  private Map<String, DNSResponseDto> dnsResponseCache;
  private static List<String> dnsLocalServers;
  
  private static Map<String, String> dnsHostCache = null;
  

  private DatagramSocket srvSocket;

  private int srvPort = 8153;
  private String providerId;
  final protected int DNS_PKG_HEADER_LEN = 12;
  final private int[] DNS_HEADERS = {0, 0, 0x81, 0x80, 0, 0, 0, 0, 0, 0, 0,
      0};
  final private int[] DNS_PAYLOAD = {0xc0, 0x0c, 0x00, 0x01, 0x00, 0x01,
      0x00, 0x00, 0x00, 0x3c, 0x00, 0x04};

  final private int IP_SECTION_LEN = 4;

  private boolean inService = false;

  /**
   * DNS Proxy upper stream ip's
   */
  
//  private static String dnsRelayMyhostsSinappHostName = "myhosts.sinaapp.com";
//  private static String dnsRelayMyhostsSinappIp = "220.181.136.37";
//      
//  private static String dnsRelayGeaHostName = "gaednsproxy.appspot.com";
//  private static String dnsRelayGaeIp = "173.194.70.141";
  
  private static String dnsRelayPingEuHostName = "ping.eu";
  private static String dnsRelayPingEuIp = "88.198.46.60";
  
  private static String dnsRelayWwwIpCnHostName = "www.ip.cn";
  private static String dnsRelayWwwIpCnIp = "216.157.85.151";
  
  private static String dnsRelayHostName;
  private static String dnsRelayIp;
  private static String dnsRelayUrl;
  
  public static String dnsRelayCustomId = "Custom";
  
  private boolean localProvider = true;

  private static final String CANT_RESOLVE = "Error";

  private SqlLiteStore database;

  
  private static void getDnsServers() throws Exception{
      Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
      Method method = SystemProperties.getMethod("get", new Class[] { String.class });
      ArrayList<String> servers = new ArrayList<String>();
      for (String name : new String[] { "net.dns1", "net.dns2", "net.dns3", "net.dns4" }) {
          String value = (String) method.invoke(null, name);
          if (value != null && !"".equals(value) && !servers.contains(value))
              servers.add(value);
      }
      if (servers.size() == 0){
          Log.e(TAG, "No local dns servers for net.dnsx system properties");
      }
      dnsLocalServers = servers;
  }

  public DNSProxy(Context ctx, int port, String providerId, String customHostName, String customIp, String customUrl) {

    this.srvPort = port;
    this.providerId = providerId;
//    if (providerId.equalsIgnoreCase(dnsRelayGeaHostName)){
//        dnsRelayHostName = dnsRelayGeaHostName;
//        dnsRelayIp = dnsRelayGaeIp;
//        localProvider = false;
//  }    
    if (providerId.equalsIgnoreCase(dnsRelayPingEuHostName)){
        dnsRelayHostName = dnsRelayPingEuHostName;
        dnsRelayIp = dnsRelayPingEuIp;
        localProvider = false;
    } else if (providerId.equalsIgnoreCase(dnsRelayWwwIpCnHostName)){
        dnsRelayHostName = dnsRelayWwwIpCnHostName;
        dnsRelayIp = dnsRelayWwwIpCnIp;
        localProvider = false;
    } else if (providerId.equalsIgnoreCase(dnsRelayCustomId)){
        dnsRelayHostName = customHostName;
        dnsRelayIp = customIp;
        dnsRelayUrl = customUrl;
        localProvider = false;
    }
//    else if (providerId.equalsIgnoreCase(dnsRelayMyhostsSinappHostName)){
//        dnsRelayHostName = dnsRelayMyhostsSinappHostName;
//        dnsRelayIp = dnsRelayMyhostsSinappIp;
//        localProvider = false;
//    }
    _logger.setLevel(Level.FINEST);
    database = SqlLiteStore.getInstance(ctx, null);
    if (localProvider){
        try {
            getDnsServers();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
  }

  public int init() throws Exception {
    try {
      srvSocket = new DatagramSocket(srvPort,
          InetAddress.getByName("127.0.0.1"));
      inService = true;
      srvPort = srvSocket.getLocalPort();
      Log.e(TAG, "Start at port " + srvPort);
    } catch (SocketException e) {
      Log.e(TAG, "DNSProxy fail to init，port: " + srvPort, e);
      throw e;
    } catch (UnknownHostException e) {
      Log.e(TAG, "DNSProxy fail to init，port " + srvPort, e);
      throw e;
    }
    return srvPort;
  }

  /**
   * Add a domain name to cache.
   *
   * @param questDomainName domain name
   * @param answer fake answer
   */
  private synchronized void addToCache(String questDomainName, byte[] answer) {
    DNSResponseDto response = new DNSResponseDto(questDomainName);
    response.setDNSResponse(answer);
    try {
      if (!dnsResponseCache.containsKey(questDomainName)){
          dnsResponseCache.put(questDomainName, response);
          database.insertDnsResponse(response);
          String ip = response.getIPString();
          if (dnsHostCache.containsKey(ip)){
              dnsHostCache.remove(ip);
          }
          dnsHostCache.put(ip, questDomainName);
      }
    } catch (Exception e) {
      Log.e(TAG, "Cannot update dns cache or database", e);
    }
  }
  
  private boolean checkIfExpired(DNSResponseDto response){
      // 3 days
      if ((System.currentTimeMillis() - response.getTimestamp()) > 259200000L) {
          return true;
      }
      return false;
  }

  private synchronized DNSResponseDto queryFromCache(String questDomainName) {
    if (dnsResponseCache.containsKey(questDomainName)){
        DNSResponseDto response = dnsResponseCache.get(questDomainName);
        if (checkIfExpired(response)){
            if (LOGD) Log.d(TAG, "deleted: " + questDomainName);
            _logger.finest("delete dns response for " + questDomainName);
            dnsResponseCache.remove(questDomainName);
            dnsHostCache.remove(response.getIPString());
            database.deleteDnsProxyResponse(questDomainName);
            return null;
        }
        return response;
    }else{
        return null;
    }
  }

  public void close() throws IOException {
    inService = false;
    srvSocket.close();
    if (LOGD) Log.i(TAG, "DNS Proxy closed");
  }

  /*
    * Create a DNS response packet, which will send back to application.
    *
    * @author yanghong
    *
    * Reference to:
    *
    * Mini Fake DNS server (Python)
    * http://code.activestate.com/recipes/491264-mini-fake-dns-server/
    *
    * DOMAIN NAMES - IMPLEMENTATION AND SPECIFICATION
    * http://www.ietf.org/rfc/rfc1035.txt
    */
  protected byte[] createDNSResponse(byte[] quest, byte[] ips) {
    byte[] response = null;
    int start = 0;

    response = new byte[1024];

    for (int val : DNS_HEADERS) {
      response[start] = (byte) val;
      start++;
    }

    System.arraycopy(quest, 0, response, 0, 2); /* 0:2 */
    System.arraycopy(quest, 4, response, 4, 2); /* 4:6 -> 4:6 */
    System.arraycopy(quest, 4, response, 6, 2); /* 4:6 -> 7:9 */
    System.arraycopy(quest, DNS_PKG_HEADER_LEN, response, start,
        quest.length - DNS_PKG_HEADER_LEN); /* 12:~ -> 15:~ */
    start += quest.length - DNS_PKG_HEADER_LEN;

    for (int val : DNS_PAYLOAD) {
      response[start] = (byte) val;
      start++;
    }

    /* IP address in response */
    for (byte ip : ips) {
      response[start] = ip;
      start++;
    }
    
    byte[] result = new byte[start];
    System.arraycopy(response, 0, result, 0, start);
    if (LOGD) Log.d(TAG, "DNS Response package size: " + start);

    return result;
  }
  
  protected byte[] replayDNSResponse(DatagramPacket dnsresponse){
      byte[] result = new byte[dnsresponse.getLength()];
      System.arraycopy(dnsresponse.getData(), 0, result, 0, dnsresponse.getLength());
      return result;
  }

  /**
   * Get request domain from UDP packet.
   *
   * @param request dns udp packet
   * @return domain
   */
  protected String getRequestDomain(byte[] request) {
    String requestDomain = "";
    int reqLength = request.length;
    if (reqLength > 13) { // include packet body
      byte[] question = new byte[reqLength - 12];
      System.arraycopy(request, 12, question, 0, reqLength - 12);
      requestDomain = parseDomain(question);
      if (requestDomain.length() > 1)
        requestDomain = requestDomain.substring(0,
            requestDomain.length() - 1);
    }
    return requestDomain;
  }

  public int getServPort() {
    return this.srvPort;
  }

  public boolean isClosed() {
    return srvSocket.isClosed();
  }

  public boolean isInService() {
    return inService;
  }

  /**
   * load cache
   */
  private void loadCache() {
    try {
      dnsResponseCache = database.getDnsResponses();
      dnsHostCache = new HashMap<String, String>();
      boolean refetch = false;
      for (String  key : dnsResponseCache.keySet()) {
          DNSResponseDto resp = dnsResponseCache.get(key);
          if (checkIfExpired(resp)) {
              if (LOGD) Log.d(TAG, "deleted: " + resp.getRequest());
              _logger.finest("delete dns response for " + resp.getRequest());
              database.deleteDnsProxyResponse(key);
              refetch = true;
          }
      }
      if (refetch){
          dnsResponseCache = database.getDnsResponses();
      }
      for (String  key : dnsResponseCache.keySet()) {
          DNSResponseDto response = dnsResponseCache.get(key);
          String ip = response.getIPString();
          if (dnsHostCache.containsKey(ip)){
              dnsHostCache.remove(ip);
          }
          dnsHostCache.put(ip, response.getRequest());
      }
    } catch (Exception e) {
      Log.e(TAG, "Cannot open DAO", e);
    }
  }

  /**
   * Parse request for domain name.
   *
   * @param request dns request
   * @return domain name
   */
  private String parseDomain(byte[] request) {

    String result = "";
    int length = request.length;
    int partLength = request[0];
    if (partLength == 0)
      return result;
    try {
      byte[] left = new byte[length - partLength - 1];
      System.arraycopy(request, partLength + 1, left, 0, length
          - partLength - 1);
      result = new String(request, 1, partLength) + ".";
      result += parseDomain(left);
    } catch (Exception e) {
      Log.e(TAG, e.getLocalizedMessage());
    }
    return result;
  }

  /*
    * Parse IP string into byte, do validation.
    *
    * @param ip IP string
    *
    * @return IP in byte array
    */
  protected byte[] parseIPString(String ip) {
    byte[] result = null;
    int value;
    int i = 0;
    String[] ips = null;

    ips = ip.split("\\.");

    if (LOGD) Log.d(TAG, "Start parse ip string: " + ip + ", Sectons: " + ips.length);

    if (ips.length != IP_SECTION_LEN) {
      Log.e(TAG, "Malformed IP string number of sections is: "
          + ips.length);
      return null;
    }

    result = new byte[IP_SECTION_LEN];

    for (String section : ips) {
      try {
        value = Integer.parseInt(section);

        /* 0.*.*.* and *.*.*.0 is invalid */
        if ((i == 0 || i == 3) && value == 0) {
          return null;
        }

        result[i] = (byte) value;
        i++;
      } catch (NumberFormatException e) {
        Log.e(TAG, "Malformed IP string section: " + section);
        return null;
      }
    }

    return result;
  }
  
  @Override
  public void run() {

    Thread.currentThread().setName("DNS Proxy resolver");
    loadCache();

    byte[] qbuffer = new byte[1024];

    while (true) {
      try {
        final DatagramPacket dnsPacket = new DatagramPacket(qbuffer,
            qbuffer.length);

        srvSocket.receive(dnsPacket);
        if (LOGD) Log.d(TAG, "new request from real client: " + dnsPacket.getLength());
        

        byte[] data = dnsPacket.getData();
        int dnsqLength = dnsPacket.getLength();
        final byte[] udpreq = new byte[dnsqLength];
        System.arraycopy(data, 0, udpreq, 0, dnsqLength);

        final String questDomain = getRequestDomain(udpreq);

        if (LOGD) Log.d(TAG, "Resolving: " + questDomain);

        DNSResponseDto resp = queryFromCache(questDomain);

        if (resp != null) {
          sendDns(resp.getDNSResponse(), dnsPacket, srvSocket);
          if (LOGD) Log.d(TAG, "DNS cache hit for " + questDomain);
//        } else if (questDomain.toLowerCase().endsWith(dnsRelayGeaHostName) && providerId.toLowerCase().equals(dnsRelayGeaHostName)) {
//          byte[] ips = parseIPString(dnsRelayGaeIp);
//          byte[] answer = createDNSResponse(udpreq, ips);
//          addToCache(questDomain, answer);
//          sendDns(answer, dnsq, srvSocket);
//          if (LOGD) Log.d(TAG, "Custom DNS resolver for " + dnsRelayGeaHostName  + " to " + dnsRelayGaeIp);
        } else if (localProvider && dnsLocalServers != null && dnsLocalServers.size() > 0 && dnsLocalServers.get(0).length() > 0){
            DatagramSocket dnsSocket = new DatagramSocket();
            String dnsServer = null;
            DNSQuery dnsQuery = new DNSQuery(dnsPacket.getData(), dnsPacket.getLength());
            for (int i = 0; i < dnsLocalServers.size(); i++) {
                try{
                    dnsServer = dnsLocalServers.get(i);
                    if (dnsServer != null && dnsServer.length() > 0){
                        DatagramPacket dt = new DatagramPacket(dnsPacket.getData(), dnsPacket.getLength(), InetAddress.getByName(dnsServer), LOCAL_DNS_PORT);
                        if (LOGD) Log.d(TAG, "used local provider -> just send it up to " + dnsServer + " for " + dnsQuery.getQueryHost());
                        dnsSocket.send(dt);
                        DatagramPacket dnsPacketResponse = new DatagramPacket(qbuffer, qbuffer.length);
                        dnsSocket.receive(dnsPacketResponse);
                        try{
                            dnsQuery.receiveResponse(dnsPacketResponse.getData(), dnsPacketResponse.getLength());
                            Enumeration<DNSRR> dnsrr = dnsQuery.getAnswers();
                            if (dnsrr != null){
                                while (dnsrr.hasMoreElements()){
                                    DNSRR dnsr = dnsrr.nextElement();
                                    if (dnsr.getRRType() == DNS.TYPE_A){
                                        Address address = (Address) dnsr;
                                        byte[] answer = createDNSResponse(udpreq, address.getAddress());
                                        // TODO here we could also add time to live
                                        addToCache(questDomain, answer);
                                        // we add just first one and break;
                                        break;
                                    }
                                }
                            }
                        }catch(Exception ex){
                            ex.printStackTrace();
                        }
                        sendDns(replayDNSResponse(dnsPacketResponse), dnsPacket, srvSocket);
                        dnsSocket.close();
                        if (LOGD) Log.d(TAG, "new response relayed to real client" );
                        break;
                    }
                }catch(Exception ex){
                    ex.printStackTrace();
                }
            }
            continue;
        } else if (questDomain.toLowerCase().endsWith(dnsRelayPingEuHostName) && providerId.toLowerCase().equals(dnsRelayPingEuHostName)) {
            byte[] ips = parseIPString(dnsRelayPingEuIp);
            byte[] answer = createDNSResponse(udpreq, ips);
            addToCache(questDomain, answer);
            sendDns(answer, dnsPacket, srvSocket);
            if (LOGD) Log.d(TAG, "Custom DNS resolver " + dnsRelayWwwIpCnHostName);
        } else if (questDomain.toLowerCase().endsWith(dnsRelayWwwIpCnHostName) && providerId.toLowerCase().equals(dnsRelayWwwIpCnHostName)) {
            byte[] ips = parseIPString(dnsRelayWwwIpCnIp);
            byte[] answer = createDNSResponse(udpreq, ips);
            addToCache(questDomain, answer);
            sendDns(answer, dnsPacket, srvSocket);
            if (LOGD) Log.d(TAG, "Custom DNS resolver " + dnsRelayWwwIpCnHostName);
//        } else if (questDomain.toLowerCase().endsWith(dnsRelayMyhostsSinappHostName) && providerId.toLowerCase().equals(dnsRelayMyhostsSinappHostName)) {
//            byte[] ips = parseIPString(dnsRelayMyhostsSinappIp);
//            byte[] answer = createDNSResponse(udpreq, ips);
//            addToCache(questDomain, answer);
//            sendDns(answer, dnsq, srvSocket);
//            if (LOGD) Log.d(TAG, "Custom DNS resolver " + dnsRelayMyhostsSinappHostName);
        } else if (questDomain.toLowerCase().endsWith(dnsRelayHostName) && providerId.equals(dnsRelayCustomId)) {
          byte[] ips = parseIPString(dnsRelayIp);
          byte[] answer = createDNSResponse(udpreq, ips);
          addToCache(questDomain, answer);
          sendDns(answer, dnsPacket, srvSocket);
          if (LOGD) Log.d(TAG, "Custom DNS resolver " + dnsRelayHostName);
        } else {
          Runnable runnable = new Runnable() {
            @Override
            public void run() {
              long startTime = System.currentTimeMillis();
              try {
                byte[] answer;
                if (localProvider){
                    InetAddress addr = InetAddress.getByName(questDomain);
                    String ipValue = addr.getHostAddress();
                    byte[] ips = parseIPString(ipValue);
                    answer = createDNSResponse(udpreq, ips);
                }else{
                    answer = fetchAnswerHTTP(udpreq);
                }
                if (answer != null && answer.length != 0) {
                  addToCache(questDomain, answer);
                  sendDns(answer, dnsPacket, srvSocket);
                  if (LOGD) Log.d(TAG,
                      "Success to get DNS response for "
                          + questDomain
                          + "，length: "
                          + answer.length
                          + " "
                          + (System
                          .currentTimeMillis() - startTime)
                          / 1000 + "s");
                } else {
                  Log.e(TAG,
                      "The size of DNS packet returned is 0");
                }
              } catch (Exception e) {
                // Nothing
                Log.e(TAG, "Failed to resolve " + questDomain
                    + ": " + e.getLocalizedMessage(), e);
              }
            }
          };

          mThreadPool.execute(runnable);

        }

      } catch (SocketException e) {
        Log.e(TAG, "Socket Exception", e);
        break;
      } catch (NullPointerException e) {
        Log.e(TAG, "Srvsocket wrong", e);
        break;
      } catch (IOException e) {
        Log.e(TAG, "IO Exception", e);
      }
    }

  }

  private Request createHttpRequest(String domain) throws Exception{
      Request request = new Request();
//      if (providerId.toLowerCase().equals(dnsRelayGeaHostName)){
//          String url = "http://" + dnsRelayHostName + "/?d=" + URLEncoder.encode(Base64.encodeBytes(Base64.encodeBytesToBytes(domain.getBytes())));
//          HttpUrl base = new HttpUrl(url);
//          request.setMethod("GET");
//          request.setHeader(new NamedValue("Host", dnsRelayHostName));
//          request.setURL(base);
//          request.setNoBody();
//      } else 
      if (providerId.toLowerCase().equals(dnsRelayPingEuHostName)){
          String url = "http://" + dnsRelayHostName + "/action.php?atype=3";
          HttpUrl base = new HttpUrl(url);
          request.setMethod("POST");
          request.setHeader(new NamedValue("Host", dnsRelayHostName));
          request.setHeader(new NamedValue("Content-Type", "application/x-www-form-urlencoded"));
          request.setContent(new String("host=www.google.com&go=Go").getBytes());
          request.setURL(base);
      } else if (providerId.toLowerCase().equals(dnsRelayWwwIpCnHostName)){
          String url = "http://" + dnsRelayHostName + "/getip.php?action=queryip&ip_url=" + URLEncoder.encode(domain) + "&from=web";
          HttpUrl base = new HttpUrl(url);
          request.setMethod("GET");
          request.setHeader(new NamedValue("Host", dnsRelayHostName));
          request.setURL(base);
//      } else if (providerId.toLowerCase().equals(dnsRelayMyhostsSinappHostName)){
//          String url = "http://" + dnsRelayHostName + "/lookup.php?host=" + URLEncoder.encode(Base64.encodeBytes(Base64.encodeBytesToBytes(domain.getBytes())));
//          HttpUrl base = new HttpUrl(url);
//          request.setMethod("GET");
//          request.setHeader(new NamedValue("Host", dnsRelayHostName));
//          request.setURL(base);
      } else if (providerId.equals(dnsRelayCustomId)){
        String url = String.format(dnsRelayUrl, URLEncoder.encode(Base64.encodeBytes(Base64.encodeBytesToBytes(domain.getBytes()))));
        HttpUrl base = new HttpUrl(url);
        request.setMethod("GET");
        request.setHeader(new NamedValue("Host", dnsRelayHostName));
//        request.setHeader(new NamedValue("User-agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:29.0) Gecko/20100101 Firefox/29.0"));
//        request.setHeader(new NamedValue("Accept-Encoding", "gzip, deflate"));
//        request.setHeader(new NamedValue("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"));
        request.setURL(base);
      }
      
      return request;
  }
  
  private String parseResponse(Response response) throws Exception{
      String ip = null;
//      if (providerId.toLowerCase().equals(dnsRelayGeaHostName)){
//          InputStream is;
//          is = response.getContentStream();
//          BufferedReader br = new BufferedReader(new InputStreamReader(is));
//          ip = br.readLine();
//      } else if (providerId.toLowerCase().equals(dnsRelayMyhostsSinappHostName)){
//          InputStream is;
//          is = response.getContentStream();
//          BufferedReader br = new BufferedReader(new InputStreamReader(is));
//          ip = br.readLine();
//      } else 
      if (providerId.toLowerCase().equals(dnsRelayPingEuHostName)){
          InputStream is;
          is = response.getContentStream();
          BufferedReader br = new BufferedReader(new InputStreamReader(is));
          String line = br.readLine();
          // TODO missing parser for tcip
          while (line != null){
              Log.d(TAG, "line retrived is " + line);
              line = br.readLine();
          }
      } else if (providerId.toLowerCase().equals(dnsRelayWwwIpCnHostName)){
          InputStream is;
          is = response.getContentStream();
          BufferedReader br = new BufferedReader(new InputStreamReader(is));
          String line = br.readLine();
          if (line != null){
              String marker1 = "<code>";
              String marker2 = "</code>";
              int pos1 = line.indexOf(marker1);
              int pos2 = line.indexOf(marker2);
              ip = line.substring(pos1 + marker1.length(), pos2);
          }
      } else if (providerId.equals(dnsRelayCustomId)){
        response.flushContentStream();
        byte[] content = response.getContent();
        ip = new String(content);
      }
      return ip;
  }
  
  /*
    * Resolve host name by access a DNSRelay server
    */
  private String resolveDomainName(String domain) {
    String ip = null;
    
    try {
      HTTPClient httpClient = HTTPClientFactory.getValidInstance().getHTTPClient();
      Request request = createHttpRequest(domain);
      Response response = httpClient.fetchResponse(request);
      ip = parseResponse(response);
      if (LOGD) Log.d(TAG, "ip retrived is " + ip);
    } catch (Exception e) {
      Log.e(TAG, "Failed to request", e);
    }
    return ip;
  }

  /*
    * Implement with http based DNS.
    */

  public byte[] fetchAnswerHTTP(byte[] quest) {
    byte[] result = null;
    String domain = getRequestDomain(quest);
    String ip = null;

    DomainValidator dv = DomainValidator.getInstance();

    /* Not support reverse domain name query */
    if (domain.endsWith("ip6.arpa") || domain.endsWith("in-addr.arpa")
        || !dv.isValid(domain)) {
      return createDNSResponse(quest, parseIPString("127.0.0.1"));
    }

    ip = resolveDomainName(domain);

    if (ip == null) {
      Log.e(TAG, "Failed to resolve domain name: " + domain);
      return null;
    }

    if (ip.equals(CANT_RESOLVE)) {
      return null;
    }

    byte[] ips = parseIPString(ip);
    if (ips != null) {
      result = createDNSResponse(quest, ips);
    }

    return result;
  }

  /**
   * Send dns response
   *
   * @param response  response packet
   * @param dnsq      request packet
   * @param srvSocket server socket
   */
  private void sendDns(byte[] response, DatagramPacket dnsq,
                       DatagramSocket srvSocket) {

    System.arraycopy(dnsq.getData(), 0, response, 0, 2);

    DatagramPacket resp = new DatagramPacket(response, 0, response.length);
    resp.setPort(dnsq.getPort());
    resp.setAddress(dnsq.getAddress());

    try {
      srvSocket.send(resp);
    } catch (IOException e) {
      Log.e(TAG, "", e);
    }
  }

}
