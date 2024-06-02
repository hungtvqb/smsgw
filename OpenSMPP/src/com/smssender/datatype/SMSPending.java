/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.smssender.datatype;

import java.io.ByteArrayOutputStream;
import java.util.Date;

/**
 *
 * @author hoand
 */
public class SMSPending {
    private long mSendTime; // Send time
    private String mDest;
    private ByteArrayOutputStream mSMSData;
    private String mAlias;
    private byte mDataEnconding;
    private byte mParESMClass;

    public SMSPending(long sendTime, String dest, ByteArrayOutputStream SMSData, String alias, 
                      byte dataEncoding, byte parESMClass) {
        this.mSendTime = sendTime;
        this.mDest = dest;
        this.mSMSData = SMSData;
        this.mAlias = alias;
        mDataEnconding = dataEncoding;
        mParESMClass = parESMClass;
    }

    public long getSendTime() {
        return mSendTime;
    }

    public String getDest() {
        return mDest;
    }

    public ByteArrayOutputStream getSMSData() {
        return mSMSData;
    }

    public String getAlias() {
        return mAlias;
    }

    public byte getDataEnconding() {
        return mDataEnconding;
    }

    public byte getParESMClass() {
        return mParESMClass;
    }
    
    public String getDebugStr() {
        return "(Dest: " + mDest + "; " + 
               "Alias: " + mAlias + "; " + 
               "Encode: " + getDataEnconding() + "; " + 
               "Date: " + (new Date(mSendTime)) + ")";
    }
}
