/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender.datatype;

import com.smssender.SMSLog;
import com.wbxml.Wbxml10;
import com.wbxml.WbxmlLibs;
import java.io.ByteArrayOutputStream;

/**
 *
 * @author hoand
 */
public class OTAMessage implements BinarySMS {
    private Wbxml10 mConverter;
    private byte[] mData;
    private int mSrcPort, mDstPort;

    public OTAMessage(String xmlFile) {
        mConverter = Wbxml10.getInstance();

        mData = mConverter.convertToWbxml(xmlFile);
        mSrcPort = 49154; // 0xC002
        mDstPort = 49999; //0xc34f
    }

    public OTAMessage(byte[] xmldata) {
        mConverter = Wbxml10.getInstance();

        mData = mConverter.convertToWbxml(xmldata);
        mSrcPort = 49154; // 0xC002
        mDstPort = 49999; //0xc34f
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
        return BinarySMS.SMS_OTA_MESSAGE;
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
            out.write(WbxmlLibs.hexToByte("01")); // Transaction ID / Push ID
            out.write(WbxmlLibs.hexToByte("06")); // PDU Type Push
            out.write(WbxmlLibs.hexToByte("2C")); // header length
            out.write(WbxmlLibs.hexToByte("1F")); // length ???
            out.write(WbxmlLibs.hexToByte("2A")); // Value length not set
            // application/x-wap-prov.browser-settings
            out.write(WbxmlLibs.toByteArr("6170706c69636174696f6e2f782d7761702d70726f762e62726f777365722d73657474696e6773"));
            out.write(WbxmlLibs.hexToByte("00")); // Terminated Char
            out.write(WbxmlLibs.hexToByte("81")); // Charset
            out.write(WbxmlLibs.hexToByte("EA")); // UTF-8

            return out;

        } catch (Exception ex) {
            ex.printStackTrace();
            SMSLog.Error("Error while create WSP header for OTA Message");
        }

        return null;
    }
}
