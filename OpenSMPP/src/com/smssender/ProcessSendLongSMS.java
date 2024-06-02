/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

import com.smssender.datatype.WaitingQueue;
import com.smssender.datatype.SMSPending;
import java.util.Enumeration;

/**
 *
 * @author Hoa
 */
public class ProcessSendLongSMS implements Runnable {
    private SMSSender mSender;
    private boolean mIsRunning;
    private WaitingQueue mQueue;
    private int mId;

    public ProcessSendLongSMS(int id, SMSSender sender) {
        mId = id;
        mSender = sender;

        mIsRunning = false;
        mQueue = new WaitingQueue();
    }

    public void start() {
        if (!mIsRunning) {
            mIsRunning = true;
            Thread t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        mIsRunning = false;
    }
    
    public void run() {
        while (mIsRunning) {
            try {
                process();
                Thread.sleep(10);
            } catch (Exception ex) {
                SMSLog.Error(ex);
            }
        }
    }

    private void process() throws Exception {
        if (mQueue.getSize() > 0) {
            Enumeration<SMSPending> e = mQueue.getAllPending();
            while (e.hasMoreElements()) {
                SMSPending req = e.nextElement();
                if (System.currentTimeMillis() >= req.getSendTime()) {
                    mSender.sendSMS(req.getDest(), req.getSMSData().toByteArray(), req.getAlias(), 
                                    req.getDataEnconding(), req.getParESMClass());
                    SMSLog.Debug(getThreadName() + " remove " + req.getDebugStr() + " (" +  mQueue.getSize() + " left)");
                    mQueue.remove(req);
                    
                    // slow down
                    Thread.sleep(20);
                }
            }
        } else {
//            MDMLog.Infor("Thread " + mId + ": No Request in Queue.");
        }
    }
    
    private String getThreadName() {
        return "LongSMS " + mId + "(" + mSender.getShortCode() + ":";
    }
    
    public void addToQueue(SMSPending s) {
        mQueue.addToQueue(s);
    }
}
