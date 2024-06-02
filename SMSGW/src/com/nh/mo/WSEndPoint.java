/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mo;

import com.smssender.TextSecurity;
import java.util.Properties;
import com.nh.main.MyLog;

/**
 *
 * @author thuzpt
 */
public class WSEndPoint {
    private String mUsername, mPassword, mAddress, mSoapAction, mRawXml, mReturnTag;
    private boolean mConvertToACSII, mUseHexToSend;

    public WSEndPoint(String mUsername, String mPassword, String mAddress, 
                      String mSoapAction, String mRawXml, String mReturnTag, 
                      boolean mConvertToACSII, boolean mUseHexToSend) {
        this.mUsername = mUsername;
        this.mPassword = mPassword;
        this.mAddress = mAddress;
        this.mSoapAction = mSoapAction;
        this.mRawXml = mRawXml;
        this.mReturnTag = mReturnTag;
        this.mConvertToACSII = mConvertToACSII;
        this.mUseHexToSend = mUseHexToSend;
    }
    
    public static WSEndPoint createWSEndPoint(Properties p) {
        WSEndPoint result = null;
        try {
            String username = p.getProperty("ws_user");
            String password = TextSecurity.getInstance().Decrypt(p.getProperty("ws_password"));
            String url = p.getProperty("ws_url");
            String soapAction = p.getProperty("ws_soap_action");
            String rawXml = p.getProperty("ws_raw_xml");
            String returnTag = p.getProperty("ws_return_tag");
            boolean convertToASCII = true;
            try {
                convertToASCII = Boolean.parseBoolean(p.getProperty("convert_to_ascii"));
            } catch (Exception ex) {}
            
            boolean useUseHex = false;
            try {
                useUseHex = Boolean.parseBoolean(p.getProperty("use_hex_for_content"));
            } catch (Exception ex) {}
            
            result = new WSEndPoint(username, password, url, soapAction, rawXml, returnTag, convertToASCII, useUseHex);
        } catch (Exception ex) {
            MyLog.Error(ex);
        }
        
        return result;
    }

    public String getUsername() {
        return mUsername;
    }

    public String getPassword() {
        return mPassword;
    }

    public String getAddress() {
        return mAddress;
    }

    public String getSoapAction() {
        return mSoapAction;
    }

    public String getRawXml() {
        return mRawXml;
    }

    public String getReturnTag() {
        return mReturnTag;
    }

    public boolean isConvertToACSII() {
        return mConvertToACSII;
    }

    public boolean isUseHexToSend() {
        return mUseHexToSend;
    }
    
    
    @Override
    public String toString() {
        return "(URL: " + mAddress + "; " +
               "SoapAction: " + mSoapAction + "; " +
               "User: " + mUsername + "; " +
               "Pass: " + "****; " + 
               "RawXML: " + mRawXml + "; " +
               "ReturnTag: " + mReturnTag + "; " +
               "ConverACSII: " + mConvertToACSII + "; " +
               "UseHex: " + mUseHexToSend + 
               ")";
    }
}
