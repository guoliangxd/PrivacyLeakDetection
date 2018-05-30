package cn.edu.xidian.privacyleakdetection.Utilities;

import android.content.Intent;
import android.security.KeyChain;

import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN.FakeVpnService;

import org.sandrop.webscarab.plugin.proxy.SSLSocketFactoryFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;


public class CertificateManager {
    private static String TAG = "CertificateManager";
    static SSLSocketFactoryFactory mFactoryFactory;

    // generate CA certificate but return a ssl socket factory factory which use this certificate
    public static SSLSocketFactoryFactory initiateFactory(String dir, String caName, String certName, String KeyType, char[] password) {
        if (mFactoryFactory == null) {
            try {
                mFactoryFactory = new SSLSocketFactoryFactory(dir + "/" + caName, dir + "/" + certName, KeyType, password);
            } catch (GeneralSecurityException | IOException e) {
                Logger.e(TAG, "Error initiating SSLSocketFactoryFactory", e);
            }
        }
        return mFactoryFactory;
    }

    public static SSLSocketFactoryFactory getSSLSocketFactoryFactory() {
        return mFactoryFactory;
    }

    // check whether we already trust fake root CA; if not generate intent to add its certificate to trust store
    public static Intent trustfakeRootCA(String dir, String caName) {
        try {
            X509Certificate fileCACert = getCACertificate(dir, caName);
            if (fileCACert == null)
                return null;
            //Logger.d(TAG, fileCACert.toString());

            KeyStore ks = KeyStore.getInstance("AndroidCAStore");
            if (ks == null)
                return null;

            ks.load(null, null);
            X509Certificate rootCACert = null;
            Enumeration aliases = ks.aliases();
            boolean found = false;
            while (aliases.hasMoreElements()) {
                String alias = (String) aliases.nextElement();
                rootCACert = (X509Certificate) ks.getCertificate(alias);

                if (rootCACert.getIssuerDN().getName().contains(caName) &&
                        rootCACert.equals(fileCACert)) {
                    //Logger.d(TAG, rootCACert.toString());
                    found = true;
                    break;
                }
            }

            if (!found) {
                Logger.d(TAG, "Fake root CA is not yet trusted");

                Intent intent = KeyChain.createInstallIntent();
                intent.putExtra(KeyChain.EXTRA_CERTIFICATE, fileCACert.getEncoded());
                intent.putExtra(KeyChain.EXTRA_NAME, FakeVpnService.CAName);
                return intent;
            }

            Logger.d(TAG, "Fake root CA is already trusted");
            return null;

        } catch(IOException e){
            e.printStackTrace();
        } catch(KeyStoreException e){
            e.printStackTrace();
        } catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        } catch(java.security.cert.CertificateException e){
            e.printStackTrace();
        }

        return null;
    }

    // 通过路径获取CA证书。
    private static X509Certificate getCACertificate(String dir, String caName) {
        String CERT_FILE = dir + "/" + caName + "_export.crt";
        InputStream inStream = null;
        try {
            inStream = new FileInputStream(CERT_FILE);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(inStream);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }
}
