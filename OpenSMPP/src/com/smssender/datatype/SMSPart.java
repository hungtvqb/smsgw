/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender.datatype;

import org.smpp.pdu.DeliverSM;
import com.smssender.SMSLog;
import com.wbxml.WbxmlLibs;
import java.io.ByteArrayInputStream;

/**
 *
 * @author Hoa
 */
public class SMSPart {
    public int mTotalPart;
    public int mPart;
    public int mSrcPort, mDstPort;

    public byte mData[];
    public int mReferenceNumber;

    public SMSPart() {
        mSrcPort = mDstPort = Integer.MAX_VALUE;
    }

    public static SMSPart getSMSPart(DeliverSM sms) {
        byte data[] = sms.getOriginalMessage();
        ByteArrayInputStream inp = new ByteArrayInputStream(data);

        SMSLog.Debug("Message Length: " + inp.available());
        int headerLen = inp.read(); // 0
        int identifier = inp.read(); // 1

        SMSLog.Debug("NEW ConcatenatedSms: (total headerlen: " + headerLen + ") (identifier: " + identifier + ")");
        if (identifier == 0x0000) {
            // Concatenated Text SMS
            return getTextSMS(sms);
//            SMSLog.Debug("Reference No: " + mReferenceNo);
        } else {
            return getBinaryPart(sms);
        }
    }

//    private static SMSPart getTextSMS(DeliverSM sms) {
//        String data = sms.getShortMessage();
//        int headerLen = data.charAt(0);
//        int identifier = data.charAt(1);
//
//        int totalPart = 0;
//        int partNo = 0;
//        int refNo = 0;
//
//        SMSLog.Debug("NEW Concatenated Text Sms: " + headerLen + " " + identifier);
//        if (identifier == 0x0000) {
//            // Concatenated Text SMS
//            int aHeaderLen = data.charAt(2); // len
//            SMSLog.Debug("Header Len: " + aHeaderLen);
//
//            if (aHeaderLen == 3) {
//                // 8 bit refnumber
//                refNo = data.charAt(3);
//                totalPart = data.charAt(4);
//                partNo = data.charAt(5);
//            } else {
//                // 16 bit ref
//                String ref = data.substring(3, 5);
//                refNo = Integer.parseInt(ref, 16);
//                totalPart = data.charAt(5);
//                partNo = data.charAt(6);
//            }
//
//            SMSLog.Debug("Reference No: " + refNo);
//        }
//
//        SMSLog.Debug("Total part: " + totalPart);
//        SMSLog.Debug("Got partNo: " + partNo);
//
//        SMSPart aPart = new SMSPart();
//        aPart.mData = data.substring(headerLen + 1, data.length()).getBytes();
//        SMSLog.Infor("DATA '" + (new String(aPart.mData)) + "'");
//        aPart.mPart =  partNo;
//        aPart.mReferenceNumber = refNo;
//        aPart.mTotalPart = totalPart;
//
//        return aPart;
//    }

    private static SMSPart getTextSMS(DeliverSM sms) {
        SMSPart result = new SMSPart();

        byte data[] = sms.getOriginalMessage();
        ByteArrayInputStream inp = new ByteArrayInputStream(data);

        SMSLog.Debug("Message Length: " + inp.available());
        int headerLen = inp.read(); // 0
        int identifier = inp.read(); // 1

        SMSLog.Debug("NEW Concatenated Text SMS: (total headerlen: " + headerLen + ") (identifier: " + identifier + ")");
        SMSLog.Debug(WbxmlLibs.printBytes(data));
        // skip end char
        int aHeaderLen = inp.read(); //data.charAt(2 + portLen + 1); // len
        SMSLog.Debug("Header Len: " + aHeaderLen);

        if (aHeaderLen == 3) {
            // 8 bit refnumber
            result.mReferenceNumber = inp.read(); //data.charAt(3);
            result.mTotalPart = inp.read(); //data.charAt(4);
            result.mPart = inp.read(); // data.charAt(5);
        } else {
            // 16 bit ref
//                String ref = data.substring(3, 5);
            result.mReferenceNumber = getNextInt(inp);//Integer.parseInt(ref, 16);
            result.mTotalPart = inp.read(); // data.charAt(5);
            result.mPart = inp.read(); // data.charAt(6);
        }

        SMSLog.Debug("Reference No: " + result.mReferenceNumber);
        SMSLog.Debug("Total part: " + result.mTotalPart);
        SMSLog.Debug("Got partNo: " + result.mPart);
        SMSLog.Debug("Data Length: " + inp.available());

        result.mData = new byte[inp.available()];
//        aPart.mData = data.substring(headerLen + 1, data.length());
        inp.read(result.mData, 0, inp.available());

        SMSLog.Debug("Data: ");
        SMSLog.Infor(WbxmlLibs.printBytes(result.mData));
//        SMSLog.Debug("Data: '" + (new String(aPart.mData)) + "'");
        return result;
    }
    
    private static SMSPart getBinaryPart(DeliverSM sms) {
        SMSPart result = new SMSPart();

        byte data[] = sms.getOriginalMessage();
        ByteArrayInputStream inp = new ByteArrayInputStream(data);

        SMSLog.Debug("Message Length: " + inp.available());
        int headerLen = inp.read(); // 0
        int identifier = inp.read(); // 1
        int partNo = 0;

        SMSLog.Debug("NEW ConcatenatedSms: (total headerlen: " + headerLen + ") (identifier: " + identifier + ")");
        SMSLog.Infor(WbxmlLibs.printBytes(data));
        // 0x05, port number
        int portLen = inp.read();
        // skip read port
//            mDstPort = Integer.parseInt(data.substring(3, 3 + portLen/2), 16);
//            mSrcPort = Integer.parseInt(data.substring(3 + portLen/2, 3 + portLen), 16);
        result.mDstPort = getNextInt(inp);
        result.mSrcPort = getNextInt(inp);
        // ----------
        SMSLog.Debug("Port: " + portLen + " " + result.mDstPort + " " + result.mSrcPort);
        // skip end char
        int aHeaderLen = inp.read(); //data.charAt(2 + portLen + 1); // len
        SMSLog.Debug("Header Len: " + aHeaderLen);

        if (aHeaderLen == 3) {
            // 8 bit refnumber
            result.mReferenceNumber = inp.read(); //data.charAt(3 + portLen + 1);
            result.mTotalPart = inp.read(); //data.charAt(4 + portLen + 1);
            partNo = inp.read(); // data.charAt(5 + portLen + 1);
        } else {
            // 16 bit ref
//                String ref = data.substring(3 + portLen + 1, 5 + portLen + 1);
            result.mReferenceNumber = getNextInt(inp);//Integer.parseInt(ref, 16);
            result.mTotalPart = inp.read(); // data.charAt(5 + portLen + 1);
            partNo = inp.read(); // data.charAt(6 + portLen + 1);
        }

        SMSLog.Debug("Reference No: " + result.mReferenceNumber);
        SMSLog.Debug("Total part: " + result.mTotalPart);
        SMSLog.Debug("Got partNo: " + partNo);
        SMSLog.Debug("Data Length: " + inp.available());

        result.mData = new byte[inp.available()];
//        aPart.mData = data.substring(headerLen + 1, data.length());
        inp.read(result.mData, 0, inp.available());
        result.mPart =  partNo;

        SMSLog.Debug("Data: ");
        SMSLog.Infor(WbxmlLibs.printBytes(result.mData));

//        SMSLog.Debug("Data: '" + (new String(aPart.mData)) + "'");

        return result;
    }
        
    private static int getNextInt(ByteArrayInputStream inp) {
        byte tmp[] = new byte[2];
        inp.read(tmp, 0, 2);
//                String refNo = data.substring(3, 5);
        return Integer.parseInt(WbxmlLibs.byteArrToHexStr(tmp), 16);
    }

}
