/***********************************************************************
 *
 * This file is part of SandroProxy, 
 * For details, please see http://code.google.com/p/sandrop/
 *
 * Copyright (c) 2012 supp.sandrob@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Getting Source
 * ==============
 *
 * Source for this application is maintained at
 * http://code.google.com/p/sandrop/
 *
 * Software is build from sources of WebScarab project
 * For details, please see http://www.sourceforge.net/projects/owasp
 *
 */

package org.sandrop.webscarab.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.InflaterOutputStream;

import org.sandrop.webscarab.httpclient.ChunkedInputStream;
import org.sandrop.webscarab.httpclient.ChunkedOutputStream;
import org.sandrop.webscarab.httpclient.FixedLengthInputStream;

import android.util.Log;

/** Message is a class that is used to represent the bulk of an HTTP message, namely
 * the headers, and (possibly null) body. Messages should not be instantiated
 * directly, but should rather be created by a derived class, namely Request or
 * Response.
 * @author rdawes
 */
public class Message {
    
    private ArrayList<NamedValue> _headers = null;
    private NamedValue[] NO_HEADERS = new NamedValue[0];
    
    private static final byte[] NO_CONTENT = new byte[0];
    private static final byte[] CONTENT_TOO_BIG = "Content to big to parse".getBytes();
    
    private static boolean LOGD = false;
    private static String TAG = Message.class.getName();
    
    private InputStream _contentStream = null;
    private MessageOutputStream _content = null;
    private boolean _chunked = false;
    private boolean _gzipped = false;
    private boolean _deflate = false;
    private int _length = -1;
    protected Logger _logger = Logger.getLogger(this.getClass().getName());
    
    /** Message is a class that is used to represent the bulk of an HTTP message, namely
     * the headers, and (possibly null) body. Messages should not be instantiated
     * directly, but should rather be created by a derived class, namely Request or
     * Response.
     */
    public Message() {
    }
    
    
    
    public static void setLargeContentSize(String size){
        try{
            int largeSize = Integer.parseInt(size);
            MessageOutputStream.LARGE_CONTENT_SIZE = largeSize;
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }
    
    public static long getLargeContentSize(String size){
        return MessageOutputStream.LARGE_CONTENT_SIZE;
    }
    
    /**
     * Instructs the class to read the headers from the InputStream, and assign the
     * InputStream as the contentStream, from which the body of the message can be
     * read.
     * @throws IOException Propagates any IOExceptions thrown by the InputStream read methods
     * @param is the InputStream to read the Message headers and body from
     */
    public void read(InputStream is) throws IOException {
        _headers = null;
        String previous = null;
        String line = null;
        do {
            line=readLine(is);
            _logger.finer("Header: " + line);
            if (line.startsWith(" ")) {
                if (previous == null) {
                    _logger.severe("Got a continuation header but had no previous header line");
                } else {
                    previous = previous.trim() + " " + line.trim();
                }
            } else {
                if (previous != null) {
                    String[] pair = previous.split(":", 2);
                    if (pair.length == 2) {
                        addHeader(new NamedValue(pair[0], pair[1].trim()));
                    } else {
                        // _logger.finest("Error parsing header: '" + previous + "'");
                    }
                }
                previous = line;
            }
        } while (!line.equals(""));
        
        _contentStream = is;
        if (_chunked) {
            _contentStream = new ChunkedInputStream(_contentStream);
        } else if (_length > -1) {
            _contentStream = new FixedLengthInputStream(_contentStream, _length);
        }
    }
    
    public InputStream getContentStream(){
        if (_contentStream != null){
            return _contentStream;
        }
        _contentStream = _content.getInputStream();
        return _contentStream;
    }
    
    public boolean isCompressed(){
        return _gzipped;
    }
    
    public boolean isDeflated(){
        return _deflate;
    }
    
    public boolean isChunked(){
        return _chunked;
    }
    
    public int getContentSize(){
        if (_content != null){
            return _content.size();
        }else{
            return 0;
        }
    }
    
    public void clean(){
        try {
            if (_content != null){
                _content.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
//    private boolean createRandomFileName() {
//        String rootDirName = Preferences.getPreference(PreferenceUtils.dataStorageKey, null);
//        String tempDirName = null;
//        if (rootDirName != null && !rootDirName.equals("")){
//            
//            File dataDir = new File(rootDirName);
//            if (!PreferenceUtils.IsDirWritable(dataDir)){
//                return false;
//            }
//            tempDirName = rootDirName + "/tempfiles/";
//            File tempDir = new File(tempDirName);
//            if (!tempDir.exists()){
//                tempDir.mkdir();
//            }
//        }
//        String prefix = tempDirName;
//        int randRange = 100000;
//        String fileName = ((Long)Math.round(Math.random() * randRange)).toString() + ".webscarab.temp";
//        String fullName = prefix + fileName;
//        while (new File(fullName).exists()){
//            fileName = ((Long)Math.round(Math.random() * randRange)).toString() + ".webscarab.temp";
//            fullName = prefix + fileName;
//        }
//        contentFileName = fullName;
//        return true;
//    }
    
    public void setContentFileName(String fileName) throws FileNotFoundException{
        if (fileName != null){
            _content = new MessageOutputStream(fileName);
        }
    }
    
    public String getContentFileName(){
        String result = null;
        if (_content != null){
            result = _content.getFileName();
        }
        return result;
    }

    private InputStream getContentInputStream() throws IOException{
        InputStream result = null;
        if (_content != null){
            result =  _content.getInputStream();
        }
        return result;
    }
    
    private void cleanContentInputStream() throws IOException{
        if (_content != null){
            _content.close();
        }
    }
    
    public boolean moveContentToFile(File file) throws Exception{
        if (_content != null){
            return _content.moveContentToFile(file);
        }
        return false;
    }
    
    /**
     * Writes the Message headers and content to the supplied OutputStream
     * @param os The OutputStream to write the Message headers and content to
     * @throws IOException any IOException thrown by the supplied OutputStream, or any IOException thrown by the InputStream from which this Message was originally read (if any)
     */
    public void write(OutputStream os) throws Exception {
        write(os, "\r\n");
    }
    
    /**
     * Writes the Message headers and content to the supplied OutputStream
     * @param os The OutputStream to write the Message headers and content to
     * @throws IOException any IOException thrown by the supplied OutputStream, or any IOException thrown by the InputStream from which this Message was originally read (if any)
     */
    public void writeHeaders(OutputStream os) throws Exception {
        writeHeaders(os, "\r\n");
    }
    
    public void writeHeaders(OutputStream os, String crlf) throws IOException {
        if (_headers != null) {
            for (int i=0; i<_headers.size(); i++) {
                NamedValue nv = _headers.get(i);
                os.write(new String(nv.getName() + ": " + nv.getValue() + crlf).getBytes());
                _logger.finest("Header: " + nv);
            }
        }
        os.write(crlf.getBytes());
        _logger.finer("wrote headers");
    }
    
    /**
     * Writes the Message headers and content to the supplied OutputStream
     * @param os The OutputStream to write the Message headers and content to
     * @throws IOException any IOException thrown by the supplied OutputStream, or any IOException thrown by the InputStream from which this Message was originally read (if any)
     * @param crlf the line ending to use for the headers
     */
    public void write(OutputStream os, String crlf) throws IOException {
        if (_headers != null) {
            for (int i=0; i<_headers.size(); i++) {
                NamedValue nv = _headers.get(i);
                os.write(new String(nv.getName() + ": " + nv.getValue() + crlf).getBytes());
                _logger.finest("Header: " + nv);
            }
        }
        os.write(crlf.getBytes());
        _logger.finer("wrote headers");
        if (_chunked) {
            os = new ChunkedOutputStream(os);
        }
        if (_contentStream != null) {
            _logger.finer("Flushing contentStream");
            flushContentStream(os);
            _logger.finer("Done flushing contentStream");
        } else if (_content != null && getContentSize() > 0) {
            _logger.finer("Writing content bytes");
            byte[] buff = new byte[1024];
            int got;
            InputStream is = getContentInputStream();
            while ((got = is.read(buff))>-1) {
                os.write(buff, 0, got);
            }
            
            is.close();
            _logger.finer("Done writing content bytes");
        }
        
        if (_chunked) {
            ((ChunkedOutputStream) os).writeTrailer();
        }
    }
    
    /**
     * Instructs the class to read the headers and content from the supplied StringBuffer
     * N.B. The "Content-length" header is updated automatically to reflect the true size
     * of the content, if one exists
     * @param buffer The StringBuffer to parse the headers and content from. This buffer is "consumed" i.e. characters are removed from the buffer as the Message is parsed.
     * @throws ParseException if there is an error parsing the Message structure
     */
    public void parse(StringBuffer buffer) throws ParseException {
        _headers = null;
        String previous = null;
        String line = null;
        do {
            line=getLine(buffer);
            if (line.startsWith(" ")) {
                if (previous == null) {
                    _logger.severe("Got a continuation header but had no previous header line");
                } else {
                    previous = previous.trim() + " " + line.trim();
                }
            } else {
                if (previous != null) {
                    String[] pair = previous.split(":", 2);
                    if (pair.length == 2) {
                        addHeader(new NamedValue(pair[0], pair[1].trim()));
                    } else {
                        _logger.warning("Error parsing header: '" + previous + "'");
                    }
                }
                previous = line;
            }
        } while (!line.equals(""));
        
        _content = new MessageOutputStream();
        try {
            _content.write(buffer.toString().getBytes());
        } catch (IOException ioe) {} // can't fail
        String cl = getHeader("Content-length");
        if (cl != null) {
            setHeader(new NamedValue("Content-length", Integer.toString(getContentSize())));
        }
    }
    
    /** Returns a String representation of the message, *including* the message body.
     * @return The string representation of the message
     */
    public String toString() {
        return toString("\r\n");
    }
    
    /** Returns a String representation of the message, *including* the message body.
     * Lines of the header are separated by the supplied "CarriageReturnLineFeed" string.
     * @return a String representation of the Message.
     * @param crlf The required line separator string
     */
    public String toString(String crlf) {
        StringBuffer buff = new StringBuffer();
        if (_headers != null) {
            for (int i=0; i<_headers.size(); i++) {
                NamedValue nv = _headers.get(i);
                if (nv.getName().equalsIgnoreCase("Transfer-Encoding") && nv.getValue().indexOf("chunked")>-1) {
                    buff.append("X-" + nv.getName() + ": " + nv.getValue() + crlf);
                } else if (nv.getName().equalsIgnoreCase("Content-Encoding") && nv.getValue().indexOf("gzip")>-1) {
                    buff.append("X-" + nv.getName() + ": " + nv.getValue() + crlf);
                } else {
                    buff.append(nv.getName() + ": " + nv.getValue() + crlf);
                }
            }
        }
        byte[] content = getContent();
        if (_chunked && content != null) {
            buff.append("Content-length: " + Integer.toString(content.length) + crlf);
        }
        buff.append(crlf);
        if (content != null) {
            try {
                buff.append(new String(content, "UTF-8"));
            } catch (UnsupportedEncodingException uee) {}; // must support UTF-8
        }
        return buff.toString();
    }
    
    private void updateFlagsForHeader(NamedValue header) {
        if (header.getName().equalsIgnoreCase("Transfer-Encoding")) {
            if (header.getValue().indexOf("chunked")>-1) {
                _chunked = true;
            } else {
                _chunked = false;
            }
        } else if (header.getName().equalsIgnoreCase("Content-Encoding")) {
            if (header.getValue().indexOf("gzip")>-1) {
                _gzipped = true;
            } else {
                _gzipped = false;
            }
            if (header.getValue().indexOf("deflate")>-1) {
                _deflate = true;
            } else {
                _deflate = false;
            }
        } else if (header.getName().equalsIgnoreCase("Content-length")) {
            try {
                _length = Integer.parseInt(header.getValue().trim());
            } catch (NumberFormatException nfe) {
                _logger.warning("Error parsing the content-length '" + header.getValue() + "' : " + nfe);
            }
        }
    }
    
    /**
     * sets the value of a header. This overwrites any previous values of headers with the same name.
     * @param name the name of the header (without a colon)
     * @param value the value of the header
     */
    public void setHeader(String name, String value) {
        setHeader(new NamedValue(name, value.trim()));
    }
    
    public void setHeader(NamedValue header) {
        updateFlagsForHeader(header);
        if (_headers == null) {
            _headers = new ArrayList<NamedValue>(1);
        } else {
            for (int i=0; i<_headers.size(); i++) {
                NamedValue nv = _headers.get(i);
                if (nv.getName().equalsIgnoreCase(header.getName())) {
                    _headers.set(i,header);
                    return;
                }
            }
        }
        _headers.add(header);
    }
    
    /**
     * Adds a header with the specified name and value. This preserves any previous
     * headers with the same name, and adds another header with the same name.
     * @param name the name of the header (without a colon)
     * @param value the value of the header
     */
    public void addHeader(String name, String value) {
        addHeader(new NamedValue(name, value.trim()));
    }
    
    public void addHeader(NamedValue header) {
        updateFlagsForHeader(header);
        if (_headers == null) {
            _headers = new ArrayList<NamedValue>(1);
        }
        _headers.add(header);
    }
    
    /**
     * Removes a header
     * @param name the name of the header (without a colon)
     * @return the value of the header that was deleted
     */
    public String deleteHeader(String name) {
        if (_headers == null) {
            return null;
        }
        for (int i=0; i<_headers.size(); i++) {
            NamedValue nv = _headers.get(i);
            if (nv.getName().equalsIgnoreCase(name)) {
                _headers.remove(i);
                updateFlagsForHeader(new NamedValue(name, ""));
                return nv.getValue();
            }
        }
        return null;
    }
    
    /**
     * Returns an array of header names
     * @return an array of the header names
     */
    public String[] getHeaderNames() {
        if (_headers == null || _headers.size() == 0) {
            return new String[0];
        }
        String[] names = new String[_headers.size()];
        for (int i=0; i<_headers.size(); i++) {
            NamedValue nv = _headers.get(i);
            names[i] = nv.getName();
        }
        return names;
    }
    
    /**
     * Returns the value of the requested header
     * @param name the name of the header (without a colon)
     * @return the value of the header in question (null if the header did not exist)
     */
    public String getHeader(String name) {
        if (_headers == null) {
            return null;
        }
        for (int i=0; i<_headers.size(); i++) {
            NamedValue nv = _headers.get(i);
            if (nv.getName().equalsIgnoreCase(name)) {
                return nv.getValue();
            }
        }
        return null;
    }
    
    /**
     * Returns all the values of the requested header, if there are multiple items
     * @param name the name of the header (without a colon)
     * @return the values of the header in question (null if the header did not exist)
     */
    public String[] getHeaders(String name) {
        if (_headers == null) 
            return null;
        ArrayList<String> values = new ArrayList<String>();
        for (int i=0; i<_headers.size(); i++) {
            NamedValue nv = _headers.get(i);
            if (nv.getName().equalsIgnoreCase(name)) {
                values.add(nv.getValue());
            }
        }
        if (values.size() == 0) 
            return null;
        return values.toArray(new String[0]);
    }
    
    /**
     * returns the header names and their values
     * @return an array of NamedValue's representing the names and values 
     * of the headers
     */
    public NamedValue[] getHeaders() {
        if (_headers == null || _headers.size() == 0) {
            return new NamedValue[0];
        }
        return _headers.toArray(NO_HEADERS);
    }
    
    /**
     * sets the headers
     * @param table a two dimensional array of Strings, where table[i][0] is the header name and
     * table[i][1] is the header value
     */
    public void setHeaders(NamedValue[] headers) {
        if (_headers == null) {
            _headers = new ArrayList<NamedValue>();
        } else {
            _headers.clear();
        }
        for (int i=0; i<headers.length; i++) {
            addHeader(headers[i]);
        }
    }
    
    /**
     * a protected method to read a line up to and including the CR or CRLF.
     *
     * We don't use a BufferedInputStream so that we don't read further than we should
     * i.e. into the message body, or next message!
     * @param is The InputStream to read the line from
     * @throws IOException if an IOException occurs while reading from the supplied InputStream
     * @return the line that was read, WITHOUT the CR or CRLF
     */
    protected String readLine(InputStream is) throws IOException {
        if (is == null) {
            NullPointerException npe = new NullPointerException("InputStream may not be null!");
            npe.printStackTrace();
            throw npe;
        }
        StringBuffer line = new StringBuffer();
        int i;
        char c=0x00;
        i = is.read();
        if (i == -1) return null;
        while (i > -1 && i != 10 && i != 13) {
            // Convert the int to a char
            c = (char)(i & 0xFF);
            line = line.append(c);
            i = is.read();
        }
        if (i == 13) { // 10 is unix LF, but DOS does 13+10, so read the 10 if we got 13
            i = is.read();
        }
        _logger.finest(line.toString());
        return line.toString();
    }
    
    /**
     * a protected method to read a line up to and including the CR or CRLF.
     * Removes the line from the supplied StringBuffer.
     * @param buffer the StringBuffer to read the line from
     * @return the line that was read, WITHOUT the CR or CRLF
     */
    protected String getLine(StringBuffer buffer) {
        int lf = buffer.indexOf("\n");
        if (lf > -1) {
            int cr = buffer.indexOf("\r");
            if (cr == -1 || cr > lf) {
                cr = lf;
            }
            String line = buffer.substring(0,cr);
            buffer.delete(0, lf+1);
            _logger.finest("line is '" + line + "'");
            return line;
        } else if (buffer.length() > 0) {
            String line = buffer.toString();
            buffer.setLength(0);
            _logger.finest("line is '" + line + "'");
            return line;
        } else {
            return "";
        }
    }
    
    /** getContent returns the message body that accompanied the request.
     * if the message was read from an InputStream, it reads the content from
     * the InputStream and returns a copy of it.
     * If the message body was chunked, or gzipped (according to the headers)
     * it returns the unchunked and unzipped content.
     *
     * @return Returns a byte array containing the message body
     */
    public byte[] getContent() {
        try {
            flushContentStream(null);
            if (getContentSize() > MessageOutputStream.LARGE_CONTENT_SIZE){
                return CONTENT_TOO_BIG;
            }
        } catch (IOException ioe) {
            _logger.info("IOException flushing the contentStream: " + ioe);
        }
        if (_content != null && _gzipped) {
            try {
                InputStream is = getContentInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPInputStream gzis = new GZIPInputStream(is);
                byte[] buff = new byte[1024];
                int got;
                while ((got = gzis.read(buff))>-1) {
                    baos.write(buff, 0, got);
                }
                return baos.toByteArray();
            } catch (Exception ioe) {
                _logger.info("Exception unzipping content : " + ioe.getMessage());
                return NO_CONTENT;
            }
        } else if (_content != null && _deflate){
            try {
                InputStream is = getContentInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InflaterOutputStream iflos = new InflaterOutputStream(baos, new Inflater(true));
                byte[] buff = new byte[1024];
                int got;
                while ((got = is.read(buff))>-1) {
                    iflos.write(buff, 0, got);
                }
                return baos.toByteArray();
            } catch (Exception ioe) {
                _logger.info("Exception unzipping content : " + ioe.getMessage());
                return NO_CONTENT;
            }
        }
        if ((_content != null)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            try {
                InputStream is;
                if (_gzipped){
                    is = new GZIPInputStream(getContentInputStream());
                }else if (_deflate){
                    is = new InflaterInputStream(getContentInputStream(), new Inflater(true));
                }else{
                    is = getContentInputStream();
                }
                byte[] buff = new byte[1024];
                int got;
                while ((got = is.read(buff))>-1) {
                    baos.write(buff, 0, got);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return NO_CONTENT;
            }
            return baos.toByteArray();
        } else {
            return NO_CONTENT;
        }
    }
    
    
    /**
     * reads all content from the content stream if one exists. Bytes read are stored internally, and returned via getContent()
     */
    public void flushContentStream() {
        try {
            flushContentStream(null);
        } catch (IOException ioe) {
            _logger.info("Exception flushing the contentStream " + ioe);
        }
    }
    
    /** reads all the bytes in the contentStream into a local ByteArrayOutputStream
     * where they can be retrieved by the getContent() methods.
     * Optionally writes the bytes read to the supplied outputstream
     * This method immediately throws any IOExceptions that occur while reading
     * the contentStream, but defers any exceptions that occur writing to the
     * supplied outputStream until the entire contentStream has been read and
     * saved.
     */
    private void flushContentStream(OutputStream os) throws IOException {
        IOException ioe = null;
        if (_contentStream == null) return;
        _content = new MessageOutputStream();
        byte[] buf = new byte[4096];
        _logger.finest("Reading initial bytes from contentStream " + _contentStream);
        
        int got = _contentStream.read(buf);
        int sum = got; 
        _logger.finest("Got " + got + " bytes");
        while (got > 0) {
            sum += got;
            _content.write(buf,0, got);
            if (os != null) {
                try {
                    os.write(buf,0,got);
                } catch (IOException e) {
                    _logger.info("IOException ioe writing to output stream : " + e);
                    // _logger.info("Had seen " + (_content.size()-got) + " bytes, was writing " + got);
                    ioe = e;
                    os = null;
                }
            }else{
                if (LOGD) Log.d(TAG, "output Stream is null");
            }
            got = _contentStream.read(buf);
            _logger.finest("Got " + got + " bytes");
        }
        _content.flush();
        _contentStream = null;
        if (ioe != null) throw ioe;
    }
    
    /**
     * sets the message to not have a body. This is typical for a CONNECT request or
     * response, which should not read any body.
     */
    public void setNoBody() {
        _content = null;
        _contentStream = null;
    }
    
    
    /**
     * Sets the content of the message body. If the message headers indicate that the
     * content is gzipped, the content is automatically compressed
     * @param bytes a byte array containing the message body
     */
    public void setContent(byte[] bytes) {
        // discard whatever is pending in the content stream
        try {
            flushContentStream(null);
        } catch (IOException ioe) {
            _logger.info("IOException flushing the contentStream " + ioe);
        }
        if (_gzipped) {
            try {
                _content = new MessageOutputStream();
                GZIPOutputStream gzos = new GZIPOutputStream(_content);
                gzos.write(bytes);
                // gzos.close(); // http://code.google.com/p/sandrop/issues/detail?id=100
                gzos.finish(); // http://code.google.com/p/sandrop/issues/detail?id=100#c6
            } catch (IOException ioe) {
                _logger.info("IOException gzipping content : " + ioe);
            }
        } else if (_deflate){
            try {
                _content = new MessageOutputStream();
                DeflaterOutputStream zos = new DeflaterOutputStream(_content, new Deflater(Deflater.BEST_COMPRESSION, true));
                zos.write(bytes);
                // zos.close(); // http://code.google.com/p/sandrop/issues/detail?id=100
                zos.finish(); // http://code.google.com/p/sandrop/issues/detail?id=100#c6
            } catch (IOException ioe) {
                _logger.info("IOException gzipping content : " + ioe);
            }
        } else {
            _content = new MessageOutputStream();
            try {
                if (bytes != null)
                    _content.write(bytes);
            } catch (IOException ioe) {} // can't fail
        }
        String cl = getHeader("Content-length");
        if (cl != null) {
            setHeader(new NamedValue("Content-length", Integer.toString(getContentSize())));
        }
    }
    
    public boolean equals(Object obj) {
        if (! (obj instanceof Message)) return false;
        Message mess = (Message) obj;
        NamedValue[] myHeaders = getHeaders();
        NamedValue[] thoseHeaders = mess.getHeaders();
        if (myHeaders.length != thoseHeaders.length) return false;
        for (int i=0; i<myHeaders.length; i++) {
            if (!myHeaders[i].getName().equalsIgnoreCase(thoseHeaders[i].getName())) return false;
            if (!myHeaders[i].getValue().equals(thoseHeaders[i].getValue())) return false;
        }
        byte[] myContent = getContent();
        byte[] thatContent = mess.getContent();
        return Arrays.equals(myContent, thatContent);
    }
    
}
