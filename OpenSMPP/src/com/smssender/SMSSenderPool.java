/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

import org.smpp.pdu.DeliverSM;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

/**
 *
 * @author hoand
 */
public class SMSSenderPool implements SMSListener {
    private String mServerIp;
    private String mUserName;
    private String mPassword;
    private String mShortCode;
    private String mConfFile;
    private Properties mConnectionPropeties;
    private int mServerPort;
    private int mPoolSize;
    private SMSSender[] mSenderPool;
    private int mCurrentIdx;

    private SMSListener mListener;
    private DecodeUDHSms mDecodeSms;
    private SMSConfirmQueue mConfirmQueue;
    private int mEnquireLink;
    private int mSepradeBetweenLongSMS;
    private int mLimitTPS;

    public SMSSenderPool(String shortCode, String ip, int port, String username, String password, 
                        int enqireLinkTime, int longSMSDelay, int numberOfInterface, int limitTPS, Properties connectionProperties) {
        mShortCode = shortCode;
        mServerIp = ip;
        mServerPort = port;
        mUserName = username;
        mPassword = password;
        mEnquireLink = enqireLinkTime;
        mSepradeBetweenLongSMS = longSMSDelay;
        
        mPoolSize = numberOfInterface;
        mConfFile = null;
        mLimitTPS = limitTPS;
        
        mConnectionPropeties = connectionProperties;
        
        mListener = null;
        SMSLog.Debug(showVersion());
    }
    
    public SMSSenderPool(String configFile) {
        loadConfig(configFile);
        mListener = null;
        SMSLog.Debug(showVersion());
    }

    public String showVersion() {
        StringBuilder result = new StringBuilder();
        
        try {
            InputStream is = getClass().getResourceAsStream("version.properties");
            Properties p = new Properties();
            p.load(is);
            
            Enumeration<Object> key = p.keys();
            result.append("SMSLibrary Build Information: \n");
            while (key.hasMoreElements()) {
                String k = (String) key.nextElement();
                result.append(k).append(": ").append(p.getProperty(k)).append("\n");
            }
            
            is.close();
        } catch (Exception ex) {
            
        }
        
        return result.toString();
    }
    
    private void loadConfig(String configFile) {
        try {
            Properties prop = new Properties();
            FileInputStream gencfgFile = new FileInputStream(configFile);
            prop.load(gencfgFile);

            // SMSC Server
            try {
                mServerIp = prop.getProperty("smscserver");
                mServerPort = Integer.parseInt(prop.getProperty("smscport"));
                mUserName = prop.getProperty("smscuser");
                String tmp = prop.getProperty("smscpass");
                TextSecurity sec = TextSecurity.getInstance();
                
                mPassword = sec.Decrypt(tmp);
                mConfFile = prop.getProperty("smscconf");

                mShortCode = prop.getProperty("smssrc");
                try {
                    mPoolSize = Integer.parseInt(prop.getProperty("smspoolsize"));
                } catch (Exception ex) {
                    mPoolSize = 1;
                }
                
                try {
                    mLimitTPS = Integer.parseInt(prop.getProperty("limit_tps"));
                } catch (Exception ex) {
                    mLimitTPS = 25;
                }
                
                try {
                    mSepradeBetweenLongSMS = Integer.parseInt(prop.getProperty("long_sms_delay"));
                } catch (Exception ex) {
                    mSepradeBetweenLongSMS = 2000;
                }
            } catch (Exception e) {}

            SMSLog.Infor("SMSC: (" + mShortCode + ") " + mServerIp + ", " + mServerPort + ", " +
                                      mUserName + ", ***************, " + mConfFile + " (Size: " + mPoolSize + ")");
            SMSLog.Infor("SMSC: (" + mShortCode + ") Delay when send long SMS: " + mSepradeBetweenLongSMS + " (ms)");
            SMSLog.Infor("SMSC: (" + mShortCode + ") Limit TPS: " + mLimitTPS + " (ms)");

            try {
                mEnquireLink = Integer.parseInt(prop.getProperty("enquire_link_time")) * 1000;
            } catch (Exception e) {
                mEnquireLink = 30 * 1000;
            }

            SMSLog.Infor("SMSC Enquire Link time: " + mEnquireLink);
            gencfgFile.close();
        } catch (Exception ex) {
            SMSLog.Error("Error while load config file: " + ex.getMessage());
        }
    }
    
    public void start() {
        mSenderPool = new SMSSender[mPoolSize];

        mDecodeSms = new DecodeUDHSms(this);
        mDecodeSms.start();

        mConfirmQueue = new SMSConfirmQueue(this);
        mConfirmQueue.start();
        
        if (mLimitTPS == 0) {
            mLimitTPS = 25; // default;
        }
        
        for (int i=0; i<mSenderPool.length; i++) {
            try {
                if (mConfFile != null && !mConfFile.isEmpty()) {
                    mSenderPool[i] = new SMSSender(i, mServerIp, mServerPort, mUserName, mPassword, mShortCode, mConfFile);
                    System.out.println("i, mServerIp, mServerPort, mUserName, mPassword, mShortCode, mConfFile");
                    System.out.println("" +i +"----"+ mServerIp+"----"+ mServerPort+"----"+ mUserName+"----"+ mPassword+"----"+ mShortCode+"----"+ mConfFile);
                } else {
                    System.out.println("i, mServerIp, mServerPort, mUserName, mPassword, mShortCode, mConfFile");
                    System.out.println("" +i +"----"+ mServerIp+"----"+ mServerPort+"----"+ mUserName+"----"+ mPassword+"----"+ mShortCode+"----"+ mConfFile);
                    
                    mSenderPool[i] = new SMSSender(i, mServerIp, mServerPort, mUserName, mPassword, mShortCode, mConnectionPropeties);
                }
                mSenderPool[i].setDecodeSMS(mDecodeSms);
                mSenderPool[i].setConfirmQueue(mConfirmQueue);
                mSenderPool[i].setTextListener(this);
                mSenderPool[i].setLongSMSSepradeTime(mSepradeBetweenLongSMS);
                mSenderPool[i].setMaxTPS(mLimitTPS);

                mSenderPool[i].startSMSListener();
            } catch (Exception ex) {
                SMSLog.Error("SMSSenderPool: Error while create Sender", ex);
                SMSLog.Error("SMSSenderPool: Error while create Sender " + i + " (" + ex.getMessage() + ")");
                mSenderPool[i] = null;
            }
        }
    }

    public void stop() {
        mDecodeSms.stop();
        mConfirmQueue.stop();

        for (int i=0; i<mSenderPool.length; i++) {
            mSenderPool[i].stop();
        }
        mSenderPool = null;
    }
    
    synchronized public SMSSender getSender() {
        if (mSenderPool == null || mSenderPool.length == 0) {
            return null;
        }

        mCurrentIdx ++;
        if (mCurrentIdx >= mSenderPool.length) {
            mCurrentIdx = 0;
        }

        return mSenderPool[mCurrentIdx];
    }
    
    public void onTextSMSArrive(String src, String dst, DeliverSM sms) {
        if (mListener != null) {
            mListener.onTextSMSArrive(src, dst, sms);
        }
    }
    
    public void onLongTextSMSArrive(String src, String dst, DeliverSM firstSMS, byte[] data) {
        if (mListener != null) {
            mListener.onLongTextSMSArrive(src, dst, firstSMS, data);
        }
    }

    public void setListener(SMSListener l) {
        mListener = l;
    }

    public String getShortCode() {
        return mShortCode;
    }

    public int getLimitTPS() {
        return mLimitTPS;
    }
    
    
}
