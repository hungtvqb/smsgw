/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

import org.smpp.pdu.DeliverSM;
import org.smpp.pdu.PDU;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

/**
 *
 * @author Ebaymark
 */
public class SMSConfirmQueue implements Runnable {
    public static String STR_DELIVRD = "DELIVRD";
    public static String STR_EXPIRED = "EXPIRED";
    public static String STR_DELETED = "DELETED";
    public static String STR_UNDELIV = "UNDELIV";
    public static String STR_UNKNOWN = "UNKNOWN";
    public static String STR_REJECTD = "REJECTD";

    private static final long RESEND_TIME = 3600000; // 1h
//    private long mLastResend;
    private boolean mIsRunning;
    private SMSSenderPool mSender;
    private Hashtable<Integer, WaitConfirmElement> mQueue;
    private Hashtable<Long, WaitConfirmElement> mDeliverQueue;
    
//    private Vector<Integer> mConfirmQueue;
//    private Vector<Long> mConfirmDeliverQueue;
    private long mLastCleanWaitList;

    public SMSConfirmQueue(SMSSenderPool sender) {
        mSender = sender;
    }

    public void addSubmitSMResp(int pduID, long newID) {
        SMSLog.Debug("ADD SUBMIT_SM_RESP " + pduID + " " + newID);

//        mConfirmQueue.add(pduID);
        //online checking
        if (mQueue.containsKey(pduID)) {
            SMSLog.Infor("Move SEQ " + pduID + " to WAIT DEILVER LIST");
//            mQueue.remove(seq);
            WaitConfirmElement e = mQueue.get(pduID);
            mDeliverQueue.put(newID, e);

            mQueue.remove(pduID);
        } else {
            SMSLog.Warning("SEQ " + pduID + " not found on waitlist");
        }
    }
    
    public void addDeliverReport(DeliverSM report) {
//        mConfirmQueue.add(pduID);
        String msg = report.getShortMessage().toUpperCase();
        String id = report.getShortMessage().substring(3, 13);
        SMSLog.Debug("GOT ID: " + id);
        long confirmID = 0;

        try {
            confirmID = Long.parseLong(id);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        if (mDeliverQueue.containsKey(confirmID)) {
            SMSLog.Debug("POST: " + msg.indexOf(STR_DELIVRD));
            if (msg.indexOf(STR_DELIVRD) > 0) {
                SMSLog.Infor("Remove ID " + confirmID + " (DELIVRD)");

                mDeliverQueue.remove(confirmID);
            } else 
            if (msg.indexOf(STR_EXPIRED) > 0 ||
                msg.indexOf(STR_DELETED) > 0 ||
//                msg.indexOf(STR_UNDELIV) > 0 ||
                msg.indexOf(STR_REJECTD) > 0)
            {
                WaitConfirmElement ele = mDeliverQueue.get(confirmID);
                mDeliverQueue.remove(confirmID);
                
                SMSLog.Infor("RESEND: " + ele.mpdu.debugString());

                SMSSender s = mSender.getSender();
                if (s != null) {
                    s.sendSubmitSM(ele.mpdu);
                }
            } else
            if (msg.indexOf(STR_UNKNOWN) > 0) {
                // Need to check SMSC CODE
                SMSLog.Infor("Remove ID " + confirmID + " (UNKNOWN)");
                mDeliverQueue.remove(confirmID);
            }
            else {
                // Other
                SMSLog.Infor("Remove ID " + confirmID + " (OTHERS)");
                mDeliverQueue.remove(confirmID);
            }
            
        } else {
            SMSLog.Warning("ID " + id + " Does not Exist");
        }
    }

    public void start() {
        if (!mIsRunning) {
            SMSLog.Infor("START SMSConfirmQueue !!");

            mIsRunning = true;
            mQueue = new Hashtable<Integer, WaitConfirmElement>();
            mDeliverQueue = new Hashtable<Long, WaitConfirmElement>();
//            mLastResend = System.currentTimeMillis();
            mLastCleanWaitList = System.currentTimeMillis();

            Thread t = new Thread (this);
            t.start();
        }
    }

    public void stop() {
        mIsRunning = false;
    }
    
    synchronized public void addWaitConfirm(PDU p) {
        SMSLog.Infor("Add " + p.debugString() + " to WaitList");
        mQueue.put(p.getSequenceNumber(), new WaitConfirmElement(p));
    }

//    private void process() {
//        while (mConfirmQueue.size() > 0) {
//            int seq = mConfirmQueue.firstElement();
//            mConfirmQueue.removeElementAt(0);
//
//            if (mQueue.containsKey(seq)) {
//                SMSLog.Warning("Remove SEQ " + seq + " from WAITLIST");
//                mQueue.remove(seq);
//            } else {
//                SMSLog.Warning("SEQ " + seq + " not found on waitlist");
//            }
//        }

//        // check queue & resend sms
//        long now = System.currentTimeMillis();
//        for (WaitConfirmElement ele : mQueue.values()) {
//            if (now - ele.mAddTime > RESEND_TIME) {
//
//                SMSLog.Infor("RESEND: " + ele.mpdu.debugString());
//                mSender.sendSubmitSM(ele.mpdu);
//                ele.mAddTime = now;
//            }
//        }
//    }

    private void doCleanWaitList() {
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(now));

        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour == 3 && (now - mLastCleanWaitList > 3600000)) { // > 1h
            // clear at 03:00
//            mWaitConfirm.clear();
            SMSLog.Infor("CLEANED SMS ConfirmList ! " + "CONFIRM Q: " + mQueue.size() + " DELIVER Q: " + mDeliverQueue.size());
            mQueue.clear();
            mDeliverQueue.clear();
//            mConfirmDeliverQueue.clear();
//            mConfirmQueue.clear();
            mLastCleanWaitList = now;
        }
    }
    
    public void run() {
        while (mIsRunning) {
            try {
//                process();
                doCleanWaitList();
                Thread.sleep(1000); // 1s
            } catch (Exception ex) {
                SMSLog.Error("SMSConfirm Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}

class WaitConfirmElement {
    public PDU mpdu;
    public long mAddTime;
//    public String mMSISDN;

    public WaitConfirmElement(PDU p) {
        mpdu = p;
        mAddTime = System.currentTimeMillis();
//        mMSISDN = msisdn;
    }
}


