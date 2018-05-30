package cn.edu.xidian.privacyleakdetection.Plugin;

import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.provider.ContactsContract;

import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Utilities.StringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ContactDetection implements IPlugin {
    private final String TAG = "ContactDetection";
    private final boolean DEBUG = false;

    private static boolean init = false;

    private static final HashSet<String> emailList = new HashSet<>();
    private static final HashSet<String> phoneList = new HashSet<>();

    @Override
    @Nullable
    public LeakReport handleRequest(String request) {
        ArrayList<LeakInstance> leaks = new ArrayList<>();

        // 不要做基于正则表达式的电子邮件/电话搜索，因为这将假定 a）可以定义这样的正则表达式和b）应用程序实现者确保他们的电话号码/电子邮件地址遵循这些正则表达式
        for (String phoneNumber: phoneList) {
            if (request.contains(phoneNumber)) {
                leaks.add(new LeakInstance("Contact Phone Number", phoneNumber));
            }
        }
        for (String email: emailList) {
            if (request.contains(email)) {
                leaks.add(new LeakInstance("Contact Email Address", email));
            }
        }

        if(leaks.isEmpty()){
            return null;
        }
        LeakReport rpt = new LeakReport(LeakReport.LeakCategory.CONTACT);
        rpt.addLeaks(leaks);
        return rpt;
    }

    @Override
    public LeakReport handleResponse(String response) {
        return null;
    }

    @Override
    public String modifyRequest(String request) {
        return request;
    }

    @Override
    public String modifyResponse(String response) {
        return response;
    }

    @Override
    public void setContext(Context context) {
        synchronized (emailList) {
            if (init) return;
            init = true;
            getNumber(context.getContentResolver());
            getEmail(context.getContentResolver());
        }
    }


    public void getNumber(ContentResolver cr)
    {
        Cursor phones = null;
        try {
            phones = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            int phoneNumberIdx = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            if(phones.moveToFirst()){
                do {
                    String phoneNumber = phones.getString(phoneNumberIdx);
                    if (DEBUG) Logger.d(TAG, "contact phone number: " + phoneNumber);
                    phoneList.add(phoneNumber);
                } while (phones.moveToNext());
            }
        } catch (Exception e) {
            Logger.e(TAG, e.getMessage());
        } finally {
            if (phones != null) {
                phones.close();
            }
        }
    }
    public void getEmail(ContentResolver cr)
    {
        Cursor emails = null;
        try {
            emails = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null, null, null, null);
            int emailAddressIdx = emails.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);

            if(emails.moveToFirst()){
                do {
                    String email = emails.getString(emailAddressIdx);
                    if (DEBUG) Logger.d(TAG, "contact email address: " + email);
                    emailList.add(email);
                    if (DEBUG) Logger.d(TAG, "contact email address: " + StringUtil.encodeAt(email));
                    emailList.add(StringUtil.encodeAt(email));
                } while (emails.moveToNext());
            }

        } catch (Exception e) {
            Logger.e(TAG, e.getMessage());
        } finally {
            if (emails != null) {
                emails.close();
            }
        }
    }

}
