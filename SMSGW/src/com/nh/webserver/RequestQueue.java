/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.webserver;

import java.net.Socket;
import java.util.Vector;

/**
 *
 * @author hoand
 */
public class RequestQueue {
//    protected static int NUMBER_OF_THREAD = 5;
    
    protected Vector<Socket> mQueue;
    private final Object mLock = new Object();

    public RequestQueue() {
        mQueue = new Vector<Socket>();
//        for (int i=0; i<GlobalVariables.NUMBER_OF_THREAD; i++) {
//            ProcessRequest p = new ProcessRequest(i, this);
//            p.start();
//        }
    }

    public void addToQueue(Socket s) {
        synchronized (mLock) {
            mQueue.add(s);
        }
    }

    public Socket getRequest() {
        synchronized (mLock) {
            if (mQueue.size() > 0) {
                Socket t = mQueue.firstElement();
                mQueue.removeElementAt(0);
                return t;
            }

            return null;
        }
    }
    
    public int getSize() {
        synchronized (mLock) {
            return mQueue.size();
        }
    }
}
