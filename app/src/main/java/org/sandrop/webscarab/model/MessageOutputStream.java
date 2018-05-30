package org.sandrop.webscarab.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

public class MessageOutputStream extends OutputStream implements java.io.Closeable{

    private ByteArrayOutputStream memoryStream;
    private FileOutputStream fileStream;
    private boolean useFileStream = false;
    private boolean deleteOnClean = true;
    private File file;
    
    private static boolean LOGD = false;
    private static String TAG = MessageOutputStream.class.getName();
    
    public static int LARGE_CONTENT_SIZE = 1024 * 1024;
    private static long SUM_MEMORY_CONTENT_ALL = 0;
    
    public static void resetActiveMemorySize(){
        SUM_MEMORY_CONTENT_ALL = 0;
    }
    
    public static synchronized void addRemoveActiveContentSum(int dataSize, boolean remove){
        String action = "";
        if (!remove){
            SUM_MEMORY_CONTENT_ALL = SUM_MEMORY_CONTENT_ALL + dataSize;
            action = "add";
        }else{
            SUM_MEMORY_CONTENT_ALL = SUM_MEMORY_CONTENT_ALL - dataSize;
            action = "remove";
        }
        if (LOGD) Log.d(TAG, "Memory content. " + action + " " + dataSize + " size is :" + SUM_MEMORY_CONTENT_ALL);
    } 
    
    
    public MessageOutputStream(){
        memoryStream = new ByteArrayOutputStream();
    }
    
    public MessageOutputStream(String fileName) throws FileNotFoundException{
        file = new File(fileName);
        useFileStream = true;
        deleteOnClean = false;
    }
    
    public boolean moveContentToFile(File newFile) throws Exception{
        try{
            if (useFileStream){
                String newAbsolutePath = newFile.getAbsolutePath();
                String oldAbsolutePath = file.getAbsolutePath();
                if (newAbsolutePath.equalsIgnoreCase(oldAbsolutePath)){
                    return false;
                }
                if (LOGD) Log.d(TAG, " start rename file storing content to file " + newAbsolutePath);
                file.renameTo(newFile);
                return true;
            }else{
                FileOutputStream fs = new FileOutputStream(newFile);
                byte[] buffer = new byte[4096]; // Adjust if you want
                int bytesRead;
                InputStream is = getInputStream();
                while ((bytesRead = is.read(buffer)) != -1)
                {
                    fs.write(buffer, 0, bytesRead);
                }
                if (LOGD) Log.d(TAG, " byte buffer storing content to file " + newFile.getAbsolutePath());
                fs.flush();
                fs.close();
                return true;
            }
        }catch(Exception ex){
            ex.printStackTrace();
            throw ex;
        }
        
    }
    
    public String getFileName(){
        String result = null;
        if (useFileStream){
            result = file.getAbsolutePath();
        }else{
            if (memoryStream != null){
                int size = memoryStream.size();
                useFileStream = true;
                try {
                    file = File.createTempFile("SandroProxy", ".tmp");
                    if (LOGD) Log.d(TAG, "Memory content. Creating temp file:"  + file.getAbsoluteFile());
                    fileStream = new FileOutputStream(file);
                    byte[] data = memoryStream.toByteArray();
                    fileStream.write(data, 0, data.length);
                    fileStream.flush();
                    memoryStream = null;
                    addRemoveActiveContentSum(size, true);
                    result = file.getAbsolutePath();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    @Override
    public void write(int oneByte) throws IOException {
        write(new byte[]{(byte)oneByte}, 0 ,1);
    }
    
    @Override
    public void write(byte[] buffer, int offset, int len) throws IOException {
        if (!useFileStream &&  SUM_MEMORY_CONTENT_ALL > LARGE_CONTENT_SIZE){
            int size = memoryStream.size();
            useFileStream = true;
            file = File.createTempFile("SandroProxy", ".tmp");
            if (LOGD) Log.d(TAG, "Memory content. Creating temp file:"  + file.getAbsoluteFile());
            fileStream = new FileOutputStream(file);
            byte[] data = memoryStream.toByteArray();
            fileStream.write(data, 0, data.length);
            fileStream.flush();
            memoryStream = null;
            addRemoveActiveContentSum(size, true);
        }
        if (useFileStream){
            fileStream.write(buffer, offset, len);
        }else{
            memoryStream.write(buffer, offset, len);
            addRemoveActiveContentSum(len, false);
        }
    }
    
    public InputStream getInputStream(){
        InputStream result = null;
        if (!useFileStream){
            byte[] contentByteArray = memoryStream.toByteArray();
            result = new ByteArrayInputStream(contentByteArray);
        }else{
            try {
                result = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    
    public int size(){
        int result = 0;
        if (!useFileStream){
            if (memoryStream != null){
                result = memoryStream.size();
            }
       }else{
            try {
                if (fileStream != null){
                    result = (int)fileStream.getChannel().size();
                }else{
                    FileInputStream fis = new FileInputStream(file);
                    result = (int)fis.getChannel().size();
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    
    @Override
    public void close() throws IOException {
        if (useFileStream && deleteOnClean){
            if (fileStream != null){
                fileStream.close();
                fileStream = null;
            }
            if (file != null && file.exists()){
                if (LOGD) Log.d(TAG, "Memory content. Deleting temp file:"  + file.getAbsoluteFile());
                file.delete();
            }
        }else{
            if (memoryStream != null){
                addRemoveActiveContentSum(size(), true);
                memoryStream.close();
                memoryStream = null;
            }
        }
    }
}
