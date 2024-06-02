/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender.datatype;

import java.util.Enumeration;
import java.util.Vector;

/**
 *
 * @author hoand
 */
public class WaitingQueue {
    protected Vector<SMSPending> mQueue;
    private final Object mLock = new Object();

    public WaitingQueue() {
        mQueue = new Vector<SMSPending>();
    }

    public void addToQueue(SMSPending s) {
        synchronized (mLock) {
            mQueue.add(s);
        }
    }

//    public SMSPending getRequest() {
//        synchronized (mLock) {
//            if (mQueue.size() > 0) {
//                SMSPending t = mQueue.firstElement();
//                mQueue.removeElementAt(0);
//                return t;
//            }
//
//            return null;
//        }
//    }
    
    public Enumeration<SMSPending> getAllPending() {
        synchronized (mLock) {
            return mQueue.elements();
        }
    }

    public void remove(SMSPending sms) {
        synchronized (mLock) {
            mQueue.remove(sms);
        }
    }
    
    public int getSize() {
        synchronized (mLock) {
            return mQueue.size();
        }
    }
}
