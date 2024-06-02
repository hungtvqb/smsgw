  /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mt;

import org.smpp.util.CharsetDetector;
import com.smssender.SMSSender;
import com.smssender.SMSSenderPool;
import com.wbxml.WbxmlLibs;
import com.nh.main.MyLog;
import com.nh.main.PublicLibs;
import com.nh.main.SMSGW;
import com.nh.cdrdumper.CDR;

/**
 *
 * @author hoand
 */
public class ProcessMT implements Runnable {
    private boolean mIsRunning;
    private MTQueue mQueue;
    private ProcessSendSMS mSMS;
    private long mLastAction;

    public ProcessMT() {
//        mMngSender = new Hashtable<String, SMSSenderPool>();
        mIsRunning = false;
        mQueue = MTQueue.getInstance();
        mSMS = ProcessSendSMS.getInstance();
    }

    public void start() {
        if (!mIsRunning) {
            mLastAction = System.currentTimeMillis();
            mIsRunning = true;
            Thread t = new Thread(this);
            t.start();
        }
    }
    
    public void stop() {
    }
    
    private void addCDR(MTSMS sms, int numberOfMT) {
        // Add CDR
        if (numberOfMT < 0) {
            return;
        }
        
        if (SMSGW.getInstance().getMTCdrDumper() != null) {
            CDR cdr = new CDR();
            String msisdn = PublicLibs.nomalizeMSISDN(sms.getDestAddress());
            cdr.addField(PublicLibs.nomalizeMSISDN(sms.getDestAddress())); // MSISDN
            cdr.addField(""); // Ma thue bao tren BCCS
            cdr.addField(""); // Loai thue bao Tra truoc/tra sau
            cdr.addField(""); // Ma dich vu VAS MaHeThong_MaDichVu -> Chua ro
            cdr.addField(""); // Ma Tac dong -> chua ro
            cdr.addField("1"); // Ma hinh thuc tac dong 
                              // 1: SMS, 2: WEB, 3: USSD, 4: WAP, 
                              // 5: IVR, 6: He thong xu ly, 7: Websevices, 
                              // 8: Nguoi quan tri khai thác, 9: Các kenh tac dong ben ngoai
            cdr.addField(PublicLibs.formatDateTime(sms.getCreatedTime())); // Thoi gian tac dong
            cdr.addField(msisdn); // 8 So dien thoai tac dong
            cdr.addField(PublicLibs.getCurrentDateTime()); // Thoi gian bat dau hieu luc
            cdr.addField(""); // Thoi gian ket thuc hieu luc
            cdr.addField("0"); // Cuoc
            cdr.addField("0"); // Trang thai tac dong
            cdr.addField("0"); // Ma loi giao dich
            cdr.addField(""); // Nguyen nhan loi
            cdr.addField(""); // Noi dung tin nhan
            cdr.addField((sms.getAlias() != null && !sms.getAlias().isEmpty()) ? sms.getAlias() : sms.getSourceAddress() ); // Dau so
            String content = sms.getContent();
            if (sms.getSMSType() == MTSMS.SMS_TYPE_HEX_FLASH || sms.getSMSType() == MTSMS.SMS_TYPE_HEX_TEXT) {
                content = new String(WbxmlLibs.toByteArr(content));
            }    
            
            cdr.addField(content.replace("\n", "")); // Tin nhan tra ve
            cdr.addField(""); // Thoi gian tra ve
            cdr.addField(""); // Party code
            cdr.addField("" + numberOfMT); // Thong tin du tru 1 -> Luu so luong MT cua tin nhan
            cdr.addField(sms.getUsername()); // Thong tin du tru 2 -> Luu Username da ban tin
            cdr.addField(""); // Thong tin du tru 3
            cdr.addField(""); // Thong tin du tru 4
            cdr.addField(""); // Thong tin du tru 5
            cdr.addField(""); // Thong tin du tru 6
            cdr.addField(""); // Thong tin du tru 7
            cdr.addField(""); // Thong tin du tru 8
            cdr.addField(""); // Thong tin du tru 9
            cdr.addField(""); // Thong tin du tru 10
            
            SMSGW.getInstance().getMTCdrDumper().addCDR(cdr);
        }
    }
     /**
      * Do send SMS to User
      */
    public void processSMS() {
        MTSMS sms = mQueue.getNextTask();
        if (sms != null) {
            MyLog.Debug("Got SMS task: " + sms + " (" + mQueue.getSize() + " left)");
            
            mLastAction = System.currentTimeMillis();
            SMSSenderPool pool = mSMS.getSMSPool(sms.getSourceAddress());
            int numberOfMT = 1;
            
            if (pool != null) {
                SMSSender s = pool.getSender();
                if (s != null) {
                    String destAddr = PublicLibs.internationalMSISDN(sms.getDestAddress());
                    String text = sms.getContent();
//                    String charSet = CharsetDetector.detectCharsetStr(text);
                   
                    switch (sms.getSMSType()) {
                        case MTSMS.SMS_TYPE_TEXT: {
                            String charSet = CharsetDetector.detectCharsetStr(text);
                            if (charSet != null && charSet.equals("UTF-16")) {
                                try { 
                                    s.sendTextSMS(destAddr, text.getBytes(charSet), sms.getAlias()); } 
                                catch (Exception ee) { MyLog.Error(ee); }
                            } else {
                                s.sendTextSMS(destAddr, text.getBytes(), sms.getAlias());
                            }
                            break;
                        }
                        
                        case MTSMS.SMS_TYPE_FLASH: {
                            s.sendFlashTextSMS(destAddr, text.getBytes(), sms.getAlias());
                            break;
                        }
                    
                        case MTSMS.SMS_TYPE_HEX_FLASH: {
                            s.sendFlashTextSMS(destAddr, WbxmlLibs.toByteArr(sms.getContent()), sms.getAlias());
                            break;
                        }
                        case MTSMS.SMS_TYPE_HEX_TEXT: {
                            s.sendTextSMS(destAddr, WbxmlLibs.toByteArr(sms.getContent()), sms.getAlias());
                            break;
                        }
                        
                        case MTSMS.SMS_TYPE_BINARY: {
//                        try {
//                            //s.sendBinarySMS(text, null, text);
//                            s.sendSMS(destAddr, WbxmlLibs.toByteArr(sms.getContent()), sms.getAlias(), SMSSender.ENCODE_BIN);
//                        } catch (IOException ex) {
//                            MyLog.Error(ex);
//                        }
                            break;
                        }
                        default: {
                            // Unidentify
                            String charSet = CharsetDetector.detectCharsetStr(text);
                            MyLog.Warning("Unknow MT SMS type detected: " + sms.getSMSType() + ", send as text SMS");
                            if (charSet != null && charSet.equals("UTF-16")) {
                                try { 
                                    s.sendTextSMS(destAddr, text.getBytes(charSet), sms.getAlias()); } 
                                catch (Exception ee) { MyLog.Error(ee); }
                            } else {
                                s.sendTextSMS(destAddr, text.getBytes(), sms.getAlias());
                            }
                            break;
                        }
                    }
                    // Add CDR Dumper
                    addCDR(sms, numberOfMT);
                    
                } else {
                    MyLog.Debug("ERROR: get Sender return null");
                }
            } else {
                MyLog.Error("Sender for '" + sms.getSourceAddress() + "' not found.");
            }
        } else {
            try { 
//                MyLog.Infor("SMS Queue is empty, wait 5s ...");
                Thread.sleep(100);
            } catch (Exception ex) {
                MyLog.Error(ex);
            }
        }
    }

    private void printLog() {
        if (System.currentTimeMillis() - mLastAction > 30000) { 
            // 30 sec
            MyLog.Debug("Queue is empty ...");
            mLastAction = System.currentTimeMillis();
        }
    }
    
    @Override
    public void run() {
        while (mIsRunning) {
            try {
                processSMS();
                printLog();
                Thread.sleep(1);
            } catch (Exception ex) {
                MyLog.Error(ex);
            }
        }
    }
}
