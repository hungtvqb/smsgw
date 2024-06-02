/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender.datatype;

import org.smpp.pdu.DeliverSM;
import com.smssender.SMSLog;
import com.smssender.SMSSender;
import com.smssender.SMSSenderPool;
import java.io.ByteArrayOutputStream;
import java.util.Vector;

/**
 *
 * @author Hoa
 */
public class ConcatenatedSms {
    private int mReferenceNo;
//    private int mTotalPart;
    
    private Vector<SMSPart> mListSMS;
    private int mReceivedPart;
    private String mSrcNo;
    private String mDstNo;
    
    private SMSSenderPool mSender;
//    private int mSrcPort, mDstPort;
    private long mCreateTime;
    private DeliverSM mFirstSMS;

    public ConcatenatedSms(DeliverSM firstSMS, SMSPart sms, SMSSenderPool sender, String srcNo, String dstNo) {
        mSender = sender;
        mListSMS = new Vector<SMSPart>();
        mListSMS.add(sms);
        mReceivedPart = 1;
        mSrcNo = srcNo;
        mDstNo = dstNo;

        mReferenceNo = sms.mReferenceNumber;
        mFirstSMS = firstSMS;
        mCreateTime = System.currentTimeMillis();
    }

// <editor-fold defaultstate="collapsed" desc="Old Decode">
//    public ConcatenatedSms(DeliverSM sms, VSMSSender sender) {
//        mSender = sender;
//        mListSMS = new Vector<SMSPart>();
//
//        byte data[] = sms.getOriginalMessage();
//        ByteArrayInputStream inp = new ByteArrayInputStream(data);
//
//        ProcessLog.Debug("Message Length: " + inp.available());
////        int headerLen = data[0];
////        int identifier = data[1];
//        int headerLen = inp.read(); // 0
//        int identifier = inp.read(); // 1
//        int partNo = 0;
//        mSrcPort = mDstPort = Integer.MAX_VALUE;
//
//        ProcessLog.Debug("NEW ConcatenatedSms: (total headerlen: " + headerLen + ") (identifier: " + identifier + ")");
//        if (identifier == 0x0000) {
//            // Concatenated Text SMS
//            int aHeaderLen = inp.read(); // len
//            ProcessLog.Debug("Header Len: " + aHeaderLen);
//
//            if (aHeaderLen == 3) {
//                // 8 bit refnumber
//                mReferenceNo = inp.read(); // 3
//                mTotalPart = inp.read();   // 4
//                partNo = inp.read();       // 5
//            } else {
//                // 16 bit ref
//                byte ref[] = new byte[2];
//                inp.read(ref, 0, 2);
//
////                String refNo = data.substring(3, 5);
//                mReferenceNo = Integer.parseInt(PublicLibs.byteArrToHexStr(ref), 16);
//                mTotalPart = inp.read(); //5
//                partNo = inp.read(); // 6
//            }
//
//            ProcessLog.Debug("Reference No: " + mReferenceNo);
//        } else {
//            PublicLibs.printBytes(data);
//            // 0x05, port number
//            int portLen = inp.read();
//            // skip read port
////            mDstPort = Integer.parseInt(data.substring(3, 3 + portLen/2), 16);
////            mSrcPort = Integer.parseInt(data.substring(3 + portLen/2, 3 + portLen), 16);
//            mDstPort = getNextInt(inp);
//            mSrcPort = getNextInt(inp);
//            // ----------
//            ProcessLog.Debug("Port: " + portLen + " " + mDstPort + " " + mSrcPort);
//            // skip end char
//            int aHeaderLen = inp.read(); //data.charAt(2 + portLen + 1); // len
//            ProcessLog.Debug("Header Len: " + aHeaderLen);
//
//            if (aHeaderLen == 3) {
//                // 8 bit refnumber
//                mReferenceNo = inp.read(); //data.charAt(3 + portLen + 1);
//                mTotalPart = inp.read(); //data.charAt(4 + portLen + 1);
//                partNo = inp.read(); // data.charAt(5 + portLen + 1);
//            } else {
//                // 16 bit ref
////                String ref = data.substring(3 + portLen + 1, 5 + portLen + 1);
//                mReferenceNo = getNextInt(inp);//Integer.parseInt(ref, 16);
//                mTotalPart = inp.read(); // data.charAt(5 + portLen + 1);
//                partNo = inp.read(); // data.charAt(6 + portLen + 1);
//            }
//
//            ProcessLog.Debug("Reference No: " + mReferenceNo);
//        }
//
//        ProcessLog.Debug("Total part: " + mTotalPart);
//        ProcessLog.Debug("Got partNo: " + partNo);
//        ProcessLog.Debug("Data Length: " + inp.available());
//
//        SMSPart aPart = new SMSPart();
//        aPart.mData = new byte[inp.available()];
////        aPart.mData = data.substring(headerLen + 1, data.length());
//        inp.read(aPart.mData, 0, inp.available());
//
//        aPart.mPart =  partNo;
//        ProcessLog.Debug("Data: '" + (new String(aPart.mData)) + "'");
//
//        mListSMS.add(aPart);
//
//        mReceivedPart = 1;
//
//        mSrcNo = sms.getSourceAddr().getAddress();
//        mDstNo = sms.getDestAddr().getAddress();
//    }
// </editor-fold>
    
    public boolean match(SMSPart sms, String srcNo) {
        if (!mSrcNo.equalsIgnoreCase(srcNo)) {
            SMSLog.Debug(srcNo + " (Source number not matched!)");
            return false;
        }

        return (sms.mReferenceNumber == mReferenceNo);
    }

    /**
     * 
     * @param sms
     * @return - true: SMS was process -> need to remove from list
     *         - false: no process
     */
    public boolean addSMS(SMSPart sms) {
        boolean wasAdd = false;
//            SMSPart aPart = new SMSPart();
//            ProcessLog.Debug("Data: '" + aPart.mData + "'");
//            ProcessLog.Debug("Data: '" + (new String(aPart.mData)) + "'");

        for (int i=0; i<mListSMS.size(); i++) {
            if (mListSMS.elementAt(i).mPart > sms.mPart) {
                mListSMS.add(i, sms);
                wasAdd = true;
                break;
            }
        }

        if (!wasAdd)
            mListSMS.add(sms);

        mReceivedPart++;
        if (mReceivedPart == sms.mTotalPart) {
            processSMS();
            return true;
        }

        return false;
    }
    
    protected void processSMS() {
//        ProcessLog.Debug("Got SMS: '" + sms.toString() + "'");
        SMSPart p = mListSMS.firstElement();
        if (p.mSrcPort == Integer.MAX_VALUE) {
            // text
//            StringBuilder sms = new StringBuilder();
//            for (int i=0; i<mListSMS.size(); i++) {
//                sms.append(new String(mListSMS.elementAt(i).mData));
//            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int i=0; i<mListSMS.size(); i++) {
                    out.write(mListSMS.elementAt(i).mData);
                }

                SMSSender sender = mSender.getSender();
                if (sender != null) {
                    mSender.onLongTextSMSArrive(mSrcNo, mDstNo, mFirstSMS, out.toByteArray());
                }
            } catch (Exception ex) {
                SMSLog.Error(ex);
            }

            
//            mSender.onTextSMSArrive(mSrcNo, mDstNo, sms.toString());
//            mSender.processTextSMS(mSrcNo, mDstNo, sms.toString());
        } else {
            // binary -> specific port
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int i=0; i<mListSMS.size(); i++) {
                    out.write(mListSMS.elementAt(i).mData);
                }

                SMSSender sender = mSender.getSender();
                if (sender != null) {
                    sender.processBinarySMS(mSrcNo, mDstNo, out.toByteArray(), p.mDstPort, p.mSrcPort);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    public boolean isTimeout() {
        long now = System.currentTimeMillis();
        return ((now - mCreateTime) > 3600000); // 1h
    }
    
    public String getSourceNumber() {
        return mSrcNo;
    }
}

