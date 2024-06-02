/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.cdrdumper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;
import com.nh.main.MyLog;
import com.nh.main.PublicLibs;

/**
 *
 * @author thuzpt
 */
public class CDRDumper implements Runnable {
    public static final String REPLACE_DUMP_NAME = "#DUMP_NAME";
    public static final String REPLACE_DATETIME = "#DATETIME";
    public static final String REPLACE_SEQUENCE = "#SEQUENCE";
    
    public static final String DATE_FORMAT = "yyyyMMddHHmmss";
    public static final int MAX_FILE_NAME_SEQUENCE = 100000; // 100K
    public static final int TWO_HOUR = 7200 * 1000;
    
    private String mCDRLocation;
    private String mFileName; 
    private int mCDRSeq;
    private int mDumpSequence;
    private int mMaxRecord;
    private boolean mSpreadByDate;
    private String mCDRSpread;
    
    private Vector<CDR> mCDRQueue;
    private long mLastDump;
    private final Object mLock = new Object();
    private boolean mIsRunning;
    private String mDumpName;
    private long mLastResetFileSequence;
//    private String mConfig;
    
    public CDRDumper() {
        mIsRunning = false;
//        mConfig = config;
        mCDRSeq = 0;
//        mLastResetFileSequence = System.currentTimeMillis();
        mLastResetFileSequence = 0;
    }
    
    private String getThreadName() {
        return "CDRDumper (" + mDumpName + ") ";
    }
    
    public void loadConfig(String config) {
        try {
            Properties prop = new Properties();
            FileInputStream gencfgFile = new FileInputStream(config);
            prop.load(gencfgFile);
            // Connect to radius server
            mDumpName = prop.getProperty("dumper_name");
            MyLog.Infor("CDR Dumper Name: " + mDumpName);
            
            mCDRLocation = prop.getProperty("cdr_location");
            if (mCDRLocation == null || mCDRLocation.isEmpty())
                mCDRLocation = "cdr";

            MyLog.Infor(getThreadName() + "CDR Location: " + mCDRLocation);
            
            File f = new File(mCDRLocation);
            if (!f.exists() || !f.isDirectory()) {
                if (f.mkdir()) {
                    MyLog.Debug(getThreadName() + "Create dir: " + mCDRLocation);
                } else {
                    MyLog.Error(getThreadName() + "Cannot create dir: " + mCDRLocation);
                }
            }
            
            mCDRSpread = prop.getProperty("cdr_spread");
            MyLog.Infor("CDR Spread: " + mCDRSpread);
            
            mFileName = prop.getProperty("cdr_file_name");
            MyLog.Infor(getThreadName() + "CDR File Name: " + mFileName);
            
            try {
                String tt = prop.getProperty("dump_sequence");
                mDumpSequence = Integer.parseInt(tt.trim()) * 1000;
            } catch (Exception e) {
                mDumpSequence = 60000;
            }
            MyLog.Infor(getThreadName() + "Dump Sequence: " + mDumpSequence);
            
            try {
                String tt = prop.getProperty("max_record");
                mMaxRecord = Integer.parseInt(tt.trim());
            } catch (Exception e) {
                mMaxRecord = 1000;
            }
            MyLog.Infor(getThreadName() + "Max Record per file: " + mMaxRecord);
            
            try {
                String tt = prop.getProperty("spread_date");
                mSpreadByDate = Boolean.parseBoolean(tt);
            } catch (Exception e) {
                mSpreadByDate = true;
            }
            MyLog.Infor(getThreadName() + "Spread by date: " + mSpreadByDate);
            
            gencfgFile.close();
        } catch (Exception ex) {
            MyLog.Error(ex);
        }
    }
    
    public void start() {
        if (!mIsRunning) {
            mLastDump = System.currentTimeMillis();
            mIsRunning = true;
            
            mCDRQueue = new Vector<CDR>();
            Thread t = new Thread(this);
            t.start();
        }
    }
    
    public void stop() {
        mIsRunning = false;
    }

    public String getCDRSpread() {
        return mCDRSpread;
    }

    
    private String getFileName() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        Date now = new Date(System.currentTimeMillis());
        Properties p = new Properties();
        mCDRSeq ++;
        if (mCDRSeq > MAX_FILE_NAME_SEQUENCE) {
            mCDRSeq = 1;
        }
        
        p.put(REPLACE_DUMP_NAME, mDumpName);
        p.put(REPLACE_DATETIME, format.format(now));
        p.put(REPLACE_SEQUENCE, "" + String.format("%06d", mCDRSeq));
        
        return PublicLibs.parseValue(mFileName, p);
    }
    
    private String getExportFolder() {
        if (mSpreadByDate) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            Date now = new Date(System.currentTimeMillis());
            
            File f = new File(mCDRLocation + "/" + format.format(now));
            if (!f.exists() || !f.isDirectory()) {
                if (f.mkdir()) {
                    MyLog.Debug(getThreadName() + "Created dir: " + f.getAbsolutePath());
                    return f.getAbsolutePath();
                } else {
                    MyLog.Debug(getThreadName() + "Can NOT Created dir: " + f.getAbsolutePath());
                    return mCDRLocation;
                }
            } else {
                return f.getAbsolutePath();
            }
        } else {
            return mCDRLocation;
        }
    }
    
    public void addCDR(CDR cdr) {
        synchronized (mLock) {
            mCDRQueue.add(cdr);
            MyLog.Debug(getThreadName() + "CDR DUMPER: Add '" + cdr + "'");
        }
    }
    
    public CDR getCDR() {
        synchronized (mLock) {
            if (!mCDRQueue.isEmpty()) {
                CDR cdr = mCDRQueue.firstElement();
                mCDRQueue.remove(0);
                
                return cdr;
            } else {
                return null;
            }
        }
    }
    
    private void dump() {
        if (mCDRQueue.size() > 0) {
            FileOutputStream out = null;
            BufferedWriter buff = null;
            try {
                String fname = getExportFolder() + "/" + getFileName();
                out = new FileOutputStream(fname);
                buff = new BufferedWriter(new OutputStreamWriter(out));
                
                int count = 0;
                CDR cdr = getCDR();
                
                while (count < mMaxRecord && cdr != null) {
                    cdr.setSpread(getCDRSpread());
                    buff.write(cdr + "\n");
                    count ++;
                    cdr = getCDR();
                }
                
                MyLog.Infor(getThreadName() + "Dumped CDR to: '" + fname + "'");
            } catch (Exception ex) {
                MyLog.Error(ex);
            } finally {
                try {
                    buff.close();
                    out.close();
                } catch (Exception ee) {}
            }
        }
    }
    
    private void process() {
        long now = System.currentTimeMillis();
        if (mCDRQueue.size() > mMaxRecord || (now - mLastDump) > mDumpSequence) {
            dump();
            mLastDump = System.currentTimeMillis();
        }
    }

    private void resetFileSequence() {
        long now = System.currentTimeMillis();
        Date d = new Date(now);
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour == 0 && System.currentTimeMillis() - mLastResetFileSequence > TWO_HOUR) {
            MyLog.Infor(getThreadName() + "Reset FileName sequence at: " + mCDRSeq);
            mCDRSeq = 0;
            mLastResetFileSequence = System.currentTimeMillis();
        }
    }
    
    @Override
    public String toString() {
        return "(" + 
               "Dump Name: " + mDumpName + 
               "; Location: " + mCDRLocation + 
               "; File Name: " + mFileName + 
               "; Dump SEQ: " + mDumpSequence + 
               "; Max Record: " + mMaxRecord + 
               "; Spread By Date: " + mSpreadByDate + 
               "; CDRSpread: " + mCDRSpread + 
               ")";
    }
    
    @Override
    public void run() {
        while (mIsRunning) {
            try {
                resetFileSequence();
                process();
                Thread.sleep(1000);
            } catch (Exception ex) {
                MyLog.Error(ex);
            }
        }
    }
}
