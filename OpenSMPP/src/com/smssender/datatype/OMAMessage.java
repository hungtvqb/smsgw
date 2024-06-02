/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender.datatype;

import com.smssender.SMSLog;
import com.wbxml.Wbxml13;
import com.wbxml.WbxmlLibs;
import java.io.ByteArrayOutputStream;

/**
 *
 * @author hoand
 */
public class OMAMessage implements BinarySMS {
    private Wbxml13 mConverter;
    private byte[] mData;
    private int mSrcPort, mDstPort;

    public OMAMessage(String xmlFile) {
        mConverter = Wbxml13.getInstance();
        mData = mConverter.convertToWbxml(xmlFile);

        mSrcPort = 9200; // 0x23F0
        mDstPort = 2948; // 0x0B84
    }

    public OMAMessage(byte[] xmldata) {
        mConverter = Wbxml13.getInstance();

        mData = mConverter.convertToWbxml(xmldata);

        mSrcPort = 9200; // 0x23F0
        mDstPort = 2948; // 0x0B84
    }

    public ByteArrayOutputStream getData() {
        try {
            ByteArrayOutputStream data = getWSPHeader();
            data.write(mData);
            return data;
        } catch (Exception ex) {
            SMSLog.Error(ex.getMessage());
            ex.printStackTrace();
        }

        return null;
    }

    public int getSMSType() {
        return BinarySMS.SMS_OMA_MESSAGE;
    }

   /**
    * get Source Port when send to Handset
    */
    public int getSrcPort() {
        return mSrcPort; // 0xC002
    }

   /**
    * get Dest Port when send to Handset
    */
    public int getDestPort() {
        return mDstPort; //0xc34f
    }

    public void setSrcPort(int val) {
        mSrcPort = val;
    }

    public void setDstPort(int val) {
        mDstPort = val;
    }


    public ByteArrayOutputStream getWSPHeader() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(WbxmlLibs.toByteArr( "FC" + // Message ID
                                           "06" + // WAP PUSH
                                           "2F" + // Header length ->  need to calculate
                                           "1F2D" + // Header format, need to see specification
                                           "B6" + // Content-Type - application/vnd.wap.connectivity-wbxml
                                           "9181" + // USER PIN Security
                                           "92")); // -> start HMAC);
            byte[] hMAC = WbxmlLibs.getMac(mData, "1111");
            out.write(hMAC);
            out.write(WbxmlLibs.toByteArr("00")); // end header.

            return out;
        } catch (Exception ex) {
            SMSLog.Error(ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }
}
