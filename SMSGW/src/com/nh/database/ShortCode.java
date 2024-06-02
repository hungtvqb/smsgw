/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.database;

import java.io.StringReader;
import java.util.Properties;
import com.nh.main.MyLog;

/**
 *
 * @author thuzpt
 */
public class ShortCode {
    private String mShortCode, mIP, mUserName, mPassword, mProperties;
    private int mPort, mEnquireLinkTime, mNumberOfInterface, mLongSMSDelay, mLimitTPS, mStatus;
    private String id; //haind25 add for 1 shortcode - n smsc
    
    public ShortCode(String mShortCode, String mIP, String mUserName, String mPassword, String mProperties, 
                     int mPort, int mEnquireLinkTime, int mNumberOfInterface, int mLongSMSDelay, int mLimitTPS, int mStatus, String id) {
        this.mShortCode = mShortCode;
        this.mIP = mIP;
        this.mUserName = mUserName;
        this.mPassword = mPassword;
        this.mProperties = mProperties;
        this.mPort = mPort;
        this.mEnquireLinkTime = mEnquireLinkTime;
        this.mNumberOfInterface = mNumberOfInterface;
        this.mLongSMSDelay = mLongSMSDelay;
        this.mLimitTPS = mLimitTPS;
        this.mStatus = mStatus;
        this.id = id;
    }

    public String getShortCode() {
        return mShortCode;
    }

    public String getIP() {
        return mIP;
    }

    public String getUserName() {
        return mUserName;
    }

    public String getPassword() {
        return mPassword;
    }

    public String getProperties() {
        return mProperties;
    }
    
    public Properties getConnectionPropeties() {
        Properties p = new Properties();
        try {
            p.load(new StringReader(mProperties));
        } catch (Exception ex) {
            MyLog.Error(ex);
        }
        return p;
    }

    public int getPort() {
        return mPort;
    }

    public int getEnquireLinkTime() {
        return mEnquireLinkTime;
    }

    public int getNumberOfInterface() {
        return mNumberOfInterface;
    }

    public int getLongSMSDelay() {
        return mLongSMSDelay;
    }

    public int getLimitTPS() {
        return mLimitTPS;
    }

    public int getStatus() {
        return mStatus;
    }
    
     public String getId() {
        return id;
    }
    
    @Override
    public String toString() {
        return "ShortCode{id="+id +", mShortCode=" + mShortCode + ", mIP=" + 
                mIP + ", mUserName=" + mUserName + ", mPassword=" 
                + mPassword + ", mProperties=" + mProperties + ", mPort=" 
                + mPort + ", mEnquireLinkTime=" + mEnquireLinkTime 
                + ", mNumberOfInterface=" + mNumberOfInterface 
                + ", mLongSMSDelay=" + mLongSMSDelay 
                + ", mLimitTPS=" + mLimitTPS + ", mStatus=" + mStatus + '}';
    }
    
    
}
