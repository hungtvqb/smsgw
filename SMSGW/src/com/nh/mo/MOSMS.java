/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mo;

/**
 *
 * @author thuzpt
 */
public class MOSMS {
    private String mSource, mDest, mContent; 
    private WSEndPoint mAddress; // Webservice to call
    private long mCreatedTime;

    public MOSMS(String source, String dest, String content, WSEndPoint address) {
        this.mSource = source;
        this.mDest = dest;
        this.mContent = content;
        mAddress = address;
        mCreatedTime = System.currentTimeMillis();
    }

    public long getCreatedTime() {
        return mCreatedTime;
    }
    
    public String getSource() {
        return mSource;
    }

    public String getDest() {
        return mDest;
    }

    public String getContent() {
        return mContent;
    }

    public WSEndPoint getWSAddress() {
        return mAddress;
    }
    
    
    @Override
    public String toString() {
        return "(From: " + mSource + "; " +
               "To: " + mDest + "; " + 
               "WS: " + mAddress + "; " + 
               "Content: " + mContent + ")";
    }    
}
