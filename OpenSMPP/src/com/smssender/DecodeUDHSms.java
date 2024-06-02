/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

import com.smssender.datatype.SMSPart;
import com.smssender.datatype.ConcatenatedSms;
import java.util.Enumeration;
import java.util.Vector;
import org.smpp.pdu.DeliverSM;

/**
 *
 * @author Hoa
 */
public class DecodeUDHSms implements Runnable {
    private final Object mLock = new Object();
    
    private SMSSenderPool mSender;
    private Vector<ConcatenatedSms> mList;
    private Vector<DeliverSM> mListPart;

    private boolean mIsRunning;

    public DecodeUDHSms(SMSSenderPool sender) {
        mSender = sender;
        mList = new Vector<ConcatenatedSms>();
        mListPart = new Vector<DeliverSM>();
        
        mIsRunning = false;
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
    
    public void addSms(DeliverSM pkg) {
        synchronized (mLock) {
            mListPart.add(pkg);
        }
    }

    private DeliverSM getNextDeliverSM() {
        synchronized (mLock) {
            if (mListPart.isEmpty()) 
                return null;
            
            DeliverSM pkg = mListPart.firstElement();
            mListPart.removeElementAt(0);
            
            return pkg;
        }
    }
    public void process() {
        DeliverSM pkg = getNextDeliverSM();

        if (pkg != null) {
            Enumeration e = mList.elements();
            SMSPart p = SMSPart.getSMSPart(pkg);

            if (p == null) {
                SMSLog.Error("Convert null !!!!");
                SMSLog.Error(pkg.debugString());
                return;
            }
            
            while (e.hasMoreElements()) {
                ConcatenatedSms cc = (ConcatenatedSms) e.nextElement();
                
                if (cc.isTimeout()) {
                    mList.remove(cc);
                    SMSLog.Debug("Remove ConcatenatedSms from " + cc.getSourceNumber() + " (Timed Out)");
                    continue;
                }
                
                if (cc.match(p, pkg.getSourceAddr().getAddress())) {
                    SMSLog.Debug("Packge " + pkg.debugString() + " matched");
                    if (cc.addSMS(p)) {
                        // process so remove
                        mList.remove(cc);
                        SMSLog.Debug("Remove ConcatenatedSms from list.");
                    }
                    return;
                }
            }

            mList.add(new ConcatenatedSms(pkg, p, mSender, pkg.getSourceAddr().getAddress(), pkg.getDestAddr().getAddress()));
            SMSLog.Debug("Packge " + pkg.debugString() + " added");
        }
    }
    
    public void run() {
        while (mIsRunning) {
            try {
                process();
                Thread.sleep(100);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

}
