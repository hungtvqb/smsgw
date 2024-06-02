/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mt;

import com.smssender.SMSListener;
import com.smssender.SMSSenderPool;
import com.wbxml.WbxmlLibs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.nh.main.MyLog;
import com.nh.main.PublicLibs;
import com.nh.main.SMSGW;
import com.nh.cdrdumper.CDR;
import com.nh.database.OracleConnection;
import com.nh.database.ShortCode;
import com.nh.mo.MOQueue;
import com.nh.mo.MOSMS;
import com.nh.mo.ShortCodePool;
import com.nh.mo.WSEndPoint;
import org.smpp.pdu.DeliverSM;
import org.smpp.util.CharsetDetector;

/**
 *
 * @author hoand
 */
public class ProcessSendSMS implements Runnable, SMSListener {
    private Map<String, SMSSenderPool> mMngSender;
//    private boolean mIsRunning;
    private long mLastReloadSMSC;

    private static ProcessSendSMS mMe = null;
    
    public static ProcessSendSMS getInstance() {
        if (mMe == null) {
            mMe = new ProcessSendSMS();
            mMe.start();
        }
        
        return mMe;
    }
    
    private ProcessSendSMS() {
        mMngSender = new ConcurrentHashMap<String, SMSSenderPool>();
//        mIsRunning = false;
//        mLastReloadSMSC = 0;
    }

    public void reloadSMSC() {
        if (System.currentTimeMillis() - mLastReloadSMSC < 60000) {
            return;
        }
        
        OracleConnection conn = OracleConnection.getInstance();
        if (conn.isConnected()) {
            String table = SMSGW.getInstance().getShortCodeTable();
            Map<String, ShortCode> list = conn.getAllShortCode(SMSGW.getInstance().getShortCodeTable());

            if (list == null || list.isEmpty()) {
                MyLog.Warning("ShortCode table " + table + " incorrect or empty.");
//                mLastReloadSMSC = System.currentTimeMillis();
                return;
            } 

            MyLog.Debug("There are " + list.size() + " Shortcode in (" + table + ")");
            for (Map.Entry<String, ShortCode> entry : list.entrySet()) {
                String id = entry.getKey();
                ShortCode sh = entry.getValue();
                
//                if (!mMngSender.containsKey(sh.getShortCode())) {
                if (!mMngSender.containsKey(id)) {
                    // add new
                    SMSSenderPool s = new SMSSenderPool(sh.getShortCode(), sh.getIP(), sh.getPort(), sh.getUserName(), sh.getPassword(), 
                                                        sh.getEnquireLinkTime(), sh.getLongSMSDelay(), sh.getNumberOfInterface(), 
                                                        sh.getLimitTPS(), sh.getConnectionPropeties());
                    mMngSender.put(id, s);
                    MyLog.Debug("Add ShortCode '" + s.getShortCode() + "'");
                    s.setListener(this);
                    s.start();
                } else {
                    MyLog.Debug("'" + sh.getShortCode() + "' is already loaded.");
                }
            }
            
            
            for (Map.Entry<String, SMSSenderPool> entry : mMngSender.entrySet()) {
                String k = entry.getKey();
                if (!list.containsKey(k)) {
                    // Was remove
                    SMSSenderPool r = mMngSender.remove(k);
                    r.stop();
                    MyLog.Debug("Stop process shortcode: '" + k + "'");
                }
            }
        } else {
            MyLog.Debug("DBThread is not CONNECTED to DB, skip load and wait 5s ...");
            try { Thread.sleep(5000); } catch (Exception ex) {}
        }
        mLastReloadSMSC = System.currentTimeMillis();
    }
    
    public void start() {
        reloadSMSC();
//        if (!mIsRunning) {
//            mIsRunning = true;
//            Thread t = new Thread(this);
//            t.start();
//        }
    }
    
    public void stop() {
        for (Map.Entry<String, SMSSenderPool> entry : mMngSender.entrySet()) {
            SMSSenderPool sMSSenderPool = entry.getValue();
            sMSSenderPool.stop();
            
        }
    }
    
    /**
     * Get SMS pool to send SMS
     * @param shortCode: ShortCode to use.
     * @return 
     */
    public SMSSenderPool getSMSPool(String shortCode) {
        List<SMSSenderPool> senderPools = new ArrayList<SMSSenderPool>();
        for (Map.Entry<String, SMSSenderPool> entry : mMngSender.entrySet()) {
            SMSSenderPool sMSSenderPool = entry.getValue();
            if (!sMSSenderPool.getShortCode().equals(shortCode)) {
                continue;
            }
            senderPools.add(sMSSenderPool);
        }
        
        if (senderPools.isEmpty()) {
            return null;
        }
        
        int length = senderPools.size();
        
        int idx = randInt(0, length - 1);
        if (idx >= length) {
            idx = 0;
        }
        
        return senderPools.get(idx);
    }
    
     /**
     * @return x, with min <= x <= max
     */
    public int randInt(int min, int max) {
        Random rand = new Random();
        int randomNum = rand.nextInt((max - min) + 1) + min;
        return randomNum;
    }
    
    /**
     * Check ShortCode available
     * @param shortCode
     * @return 
     */
    public boolean isValidShortCode(String shortCode) {
        for (Map.Entry<String, SMSSenderPool> entry : mMngSender.entrySet()) {
            SMSSenderPool sMSSenderPool = entry.getValue();
            if (sMSSenderPool.getShortCode().equals(shortCode)) {
                return true;
            }
        }
        return false;
    }
//     /**
//      * Do send SMS to User
//      */
//    public void processSMS() {
//        MySMS sms = mQueue.getNextTask();
//        
//        if (sms != null) {
//            SMSSenderPool pool = mMngSender.get(sms.getSourceAddress());
//                
//            if (pool != null) {
//                SMSSender s = pool.getSender();
//                if (s != null) {
//                    String destAddr = NGPPublicLibs.internationalMSISDN(sms.getDestAddress());
//                    String text = sms.getContent();
//                    String charSet = CharsetDetector.detectCharsetStr(text);
//
//                    if (charSet != null && charSet.equals("UTF-16")) {
//                        try { s.sendTextSMS(destAddr, text.getBytes(charSet), sms.getAlias()); } 
//                        catch (Exception ee) { MyLog.Error(ee); }
//                    } else {
//                        s.sendTextSMS(destAddr, text.getBytes(), sms.getAlias());
//                    }
//                } else {
//                    MyLog.Debug("ERROR: get Sender return null");
//                }
//            } else {
//                MyLog.Error("Sender for '" + sms.getSourceAddress() + "' not found.");
//            }
//        } else {
//            try { 
//                MyLog.Infor("SMS Queue is empty, wait 1s ...");
//                Thread.sleep(1000);
//            } catch (Exception ex) {
//                MyLog.Error(ex);
//            }
//        }
//    }

    @Override
    public void run() {
//        while (mIsRunning) {
//            try {
////                processSMS();
//                reloadSMSC();
//                Thread.sleep(60000);
//            } catch (Exception ex) {
//                MyLog.Error(ex);
//            }
//        }
    }

    private void addCDR(String src, String dst, String content) {
        // Add CDR
        if (SMSGW.getInstance().getMOCdrDumper() != null) {
            CDR cdr = new CDR();
            String msisdn = PublicLibs.nomalizeMSISDN(src);
            cdr.addField(PublicLibs.nomalizeMSISDN(src)); // MSISDN              1
            cdr.addField(""); // Ma thue bao tren BCCS                           2
            cdr.addField(""); // Loai thue bao Tra truoc/tra sau             3   
            cdr.addField(""); // Ma dich vu VAS MaHeThong_MaDichVu -> Chua ro 4
            cdr.addField(""); // Ma Tac dong -> chua ro 5
            cdr.addField("1"); // Ma hinh thuc tac dong 6
                              // 1: SMS, 2: WEB, 3: USSD, 4: WAP, 
                              // 5: IVR, 6: He thong xu ly, 7: Websevices, 
                              // 8: Nguoi quan tri khai thác, 9: Các kenh tac dong ben ngoai
            cdr.addField(PublicLibs.getCurrentDateTime()); // 7 Thoi gian tac dong
            cdr.addField(msisdn); // 8 So dien thoai tac dong
            cdr.addField(PublicLibs.getCurrentDateTime()); // 9 Thoi gian bat dau hieu luc
            cdr.addField(""); // 10 Thoi gian ket thuc hieu luc
            cdr.addField("0"); // Cuoc
            cdr.addField("0"); // Trang thai tac dong
            cdr.addField("0"); // Ma loi giao dich
            cdr.addField(""); // Nguyen nhan loi
            cdr.addField(content.replace("\n", "")); // Noi dung tin nhan
            cdr.addField(dst); // Dau so
            cdr.addField(""); // Tin nhan tra ve
            cdr.addField(""); // Thoi gian tra ve
            cdr.addField(""); // Party code
            cdr.addField(""); // Thong tin du tru 1
            cdr.addField(""); // Thong tin du tru 2
            cdr.addField(""); // Thong tin du tru 3
            cdr.addField(""); // Thong tin du tru 4
            cdr.addField(""); // Thong tin du tru 5
            cdr.addField(""); // Thong tin du tru 6
            cdr.addField(""); // Thong tin du tru 7
            cdr.addField(""); // Thong tin du tru 8
            cdr.addField(""); // Thong tin du tru 9
            cdr.addField(""); // Thong tin du tru 10
            
            SMSGW.getInstance().getMOCdrDumper().addCDR(cdr);
        }
    }
    
    @Override
    public void onTextSMSArrive(String src, String dest, DeliverSM sms) {
        String dst = dest;
        if (dst.startsWith("+")) { 
            // remove '+' sign
            dst = dest.substring(1);
        }
        MyLog.Debug("User '" + src + "' sent '" + sms.getShortMessage() + "' to '" + dst + "'");
        boolean isUTF16 = CharsetDetector.isUFT16(sms.getOriginalMessage());
        String text = sms.getShortMessage();

        MyLog.Debug(WbxmlLibs.printBytes(sms.getOriginalMessage()));
        if (isUTF16) {
            try {
                text = new String(sms.getOriginalMessage(), "UTF-16");
                MyLog.Debug("Convert to (UTF-16): '" + text + "'");
                MyLog.Debug("Convert to (ASCII): '" + PublicLibs.convertUnicodeToASCII(text) + "'");// Hex: '" + WbxmlLibs.printBytes(NGPPublicLibs.convertUnicodeToASCII(text).getBytes()));
            } catch (Exception ee) {
                MyLog.Error(ee);
            }
        }
        
        // Add CDR Dumper
        addCDR(src, dst, text);
        
        HashSet<WSEndPoint> listener = ShortCodePool.getInstance().match(dst, text);
        if (listener != null && !listener.isEmpty()) {
            Iterator<WSEndPoint> ite = listener.iterator();
            while (ite.hasNext()) {
                WSEndPoint endPoint = ite.next();
                if (endPoint != null) {
                    String newText = text;
                    if (isUTF16 && endPoint.isConvertToACSII()) {
                        newText = PublicLibs.convertUnicodeToASCII(text);
                    }
                    MOQueue.getInstance().add(new MOSMS(src, dst, newText, endPoint)); // add to Queue
                }
            }
        } else {
            MyLog.Debug("Listener for shortcode " + dst + " not found");
            MOQueue.getInstance().addLog(new MOSMS(src, dst, text, null)); // add to Log Queue
        }
    }
    
    @Override
    public void onLongTextSMSArrive(String src, String dest, DeliverSM firstSMS, byte[] data) {
        String dst = dest;
        if (dst.startsWith("+")) { 
            // remove '+' sign
            dst = dest.substring(1);
        }
        
        String text = (new String(data));
        MyLog.Debug("User '" + src + "' sent '" + text + "' to '" + dst + "'");

        boolean isUTF16 = CharsetDetector.isUFT16(data);
        MyLog.Debug(WbxmlLibs.printBytes(data));
        if (isUTF16) {
            try {
                text = new String(data, "UTF-16");
                MyLog.Debug("Convert to (UTF-16): '" + text + "'");
                MyLog.Debug("Convert to (ASCII): '" + PublicLibs.convertUnicodeToASCII(text) + "' Hex: '" + WbxmlLibs.printBytes(PublicLibs.convertUnicodeToASCII(text).getBytes()));
            } catch (Exception ee) {
                MyLog.Error(ee);
            }
        }
        
        // Add CDR Dumper
        addCDR(src, dst, text);
        
        HashSet<WSEndPoint> listener = ShortCodePool.getInstance().match(dst, text);
        if (listener != null && !listener.isEmpty()) {
            Iterator<WSEndPoint> ite = listener.iterator();
            while (ite.hasNext()) {
                WSEndPoint endPoint = ite.next();
                if (endPoint != null) {
                    String newText = text;
                    if (isUTF16 && endPoint.isConvertToACSII()) {
                        newText = PublicLibs.convertUnicodeToASCII(text);
                    }
                    MOQueue.getInstance().add(new MOSMS(src, dst, newText, endPoint)); // add to Queue
                }
            }
        } else {
            MyLog.Debug("Listener for shortcode " + dst + " not found");
            MOQueue.getInstance().addLog(new MOSMS(src, dst, text, null)); // add to Log Queue
        }
    }
    
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, SMSSenderPool> entry : mMngSender.entrySet()) {
            SMSSenderPool sMSSenderPool = entry.getValue();
            sb.append(sMSSenderPool.getShortCode()).append(",");
        }
        Set<String> key = mMngSender.keySet();
        return "Current Shortcode on system: " + key.toString();
    }
}
