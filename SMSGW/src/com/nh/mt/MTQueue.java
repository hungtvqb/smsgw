/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mt;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Vector;
import com.nh.main.MyLog;
import com.nh.main.PublicLibs;
import com.nh.main.SMSGW;
import com.nh.database.InsertConnection;

/**
 *
 * @author thuzpt
 */
public class MTQueue implements Runnable {
    private static final String PARAMS_MSISDN = "#MSISDN"; 
    private static final String PARAMS_SHORTCODE = "#SHORT_CODE";
    private static final String PARAMS_ALIAS = "#ALIAS";
    private static final String PARAMS_CONTENT = "#CONTENT";
    private static final String PARAMS_USERNAME = "#USERNAME";
    private static final String PARAMS_CREATED_TIME = "#CREATED_TIME";
    private static final int BATCH_SIZE = 1000;
    private static final int INSERT_SEQUENCE = 10 * 1000;
    
    private static MTQueue mMe = null;
    private static final Object mLock = new Object(), mLogLock = new Object();
    
    private Vector<MTSMS> mQueue;
    private Vector<MTSMS> mLogQueue;
    private long mLastInsert;
    private boolean mIsRunning;
    private InsertConnection mConn;
    
    private MTQueue() {
        mQueue = new Vector<MTSMS>();
        mLogQueue = new Vector<MTSMS>();
        mIsRunning = false;
        mConn = new InsertConnection(SMSGW.getInstance().getOracleConfig());
    }

    public static MTQueue getInstance() {
        if (mMe == null) {
            mMe = new MTQueue();
            mMe.start();
        }
        
        return mMe;
    }
    
    public void add(MTSMS value) {
        synchronized (mLock) {
            mQueue.add(value);
        }
        addLog(value);
    }
    
    public MTSMS getNextTask() {
        synchronized (mLock) {
            if (mQueue.size() > 0) {
                MTSMS m = mQueue.firstElement();
                mQueue.removeElementAt(0);
                return m;
            } else {
                return null;
            }
        } 
    }
    
    public void addLog(MTSMS value) {
        if (SMSGW.getInstance().isEnableLogs()) {
            synchronized (mLogLock) {
                mLogQueue.add(value);
            }
        } 
    }
    
    public int getSize() {
        synchronized (mLock) {
            return mQueue.size();
        }
    }
    
    private MTSMS getNextLog() {
        synchronized (mLogLock) {
            if (mLogQueue.size() > 0) {
                MTSMS m = mLogQueue.firstElement();
                mLogQueue.removeElementAt(0);
                return m;
            } else {
                return null;
            }
        } 
    }
    
    private int getLogSize() {
        synchronized (mLock) {
            return mLogQueue.size();
        }
    }
    
    public void start() {
        if (!mIsRunning) {
            mIsRunning = true;
            mLastInsert = System.currentTimeMillis();
            mConn.startConnect();
            
            Thread t = new Thread(this);
            t.start();
        }
    }
    
    public void stop() {
        mIsRunning = false;
    }
    
    private void insertBatchToDB() {
        long start = System.currentTimeMillis();
        SMSGW gw = SMSGW.getInstance();
        String f[] = gw.getMTParams();
        Connection conn = null;
        CallableStatement cs = null;
        conn = mConn.getDatabaseConnection();
        int count = 0;
        
        if (conn != null) {
            try {
                conn.setAutoCommit(false);
                cs = conn.prepareCall(gw.getMTInsertSQL());
                cs.setQueryTimeout(300); // s
                
                MTSMS task = getNextLog();
                while (task != null) {
                    count++;
                    for (int i=0; i<f.length; i++) {
                        if (f[i].equalsIgnoreCase(PARAMS_MSISDN)) {
                            cs.setString(i + 1, PublicLibs.nomalizeMSISDN(task.getDestAddress()));
                        } else if (f[i].equalsIgnoreCase(PARAMS_SHORTCODE)) {
                            cs.setString(i + 1, task.getSourceAddress());
                        } else if (f[i].equalsIgnoreCase(PARAMS_CONTENT)) {
                            cs.setString(i + 1, task.getOriginalContent());
                        } else if (f[i].equalsIgnoreCase(PARAMS_USERNAME)) {
                            cs.setString(i + 1, task.getUsername());
                        } else if (f[i].equalsIgnoreCase(PARAMS_CREATED_TIME)) {
                            cs.setTimestamp(i + 1, new Timestamp(task.getCreatedTime()));
                        } else if (f[i].equalsIgnoreCase(PARAMS_ALIAS)) {
                            cs.setString(i + 1, task.getAlias());
                        } else {
                            MyLog.Warning("Invalid MT Var: '" + f[i] + "'");
                        } 
                    }
                    cs.addBatch();
                    if (count > BATCH_SIZE) {
                        break;
                    }
                    
                    task = getNextLog();
                }
                
                int result[] = cs.executeBatch();
                conn.commit();
                MyLog.Debug("MT Insert RESULT count = " + result.length);
            } catch (Exception ex) {
                MyLog.Error(ex);
            } finally {
                try { cs.close(); } catch (Exception ee) {}
                try { conn.commit(); } catch (Exception ee) {}
            }
        }

        MyLog.Debug("Finish insert batch (" + count + " records) ("+ (System.currentTimeMillis() - start) + " ms)");
    }
    
    private void process() {
        if (getLogSize() > BATCH_SIZE || System.currentTimeMillis() - mLastInsert > INSERT_SEQUENCE) {
            if (getLogSize() > 0) {
                insertBatchToDB();
            }
            mLastInsert = System.currentTimeMillis();
        }
    }
    
    @Override
    public void run() {
        while (mIsRunning) {
            try {
                process();
                Thread.sleep(1000);
            } catch (Exception ex) {
                MyLog.Error(ex);
            }
        }
    }
}
