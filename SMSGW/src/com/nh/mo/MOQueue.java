/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mo;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Vector;
import com.nh.main.MyLog;
import com.nh.main.PublicLibs;
import com.nh.main.SMSGW;
import com.nh.database.InsertConnection;
import com.nh.database.OracleConnection;
import com.nh.mt.MTQueue;
import com.nh.mt.MTSMS;
import com.nh.mt.ProcessSendSMS;
import com.nh.mt.ShortCodeMatcher;
import com.nh.mt.VirtualShortCode;

/**
 *
 * @author thuzpt
 */
public class MOQueue implements Runnable {

    private static final String PARAMS_MSISDN = "#MSISDN";
    private static final String PARAMS_SHORTCODE = "#SHORT_CODE";
    private static final String PARAMS_CONTENT = "#CONTENT";
    private static final String PARAMS_FORWARD = "#FORWARD";
    private static final String PARAMS_CREATED_TIME = "#CREATED_TIME";
    private static MOQueue mMe = null;
    private static final Object mLock = new Object(), mLogLock = new Object();
    private static final int BATCH_SIZE = 1000;
    private static final int INSERT_SEQUENCE = 10 * 1000;
    private Vector<MOSMS> mQueue;
    private Vector<MOSMS> mLogQueue;
    private long mLastInsert;
    private boolean mIsRunning;
    private InsertConnection mConn;

    private MOQueue() {
        mQueue = new Vector<MOSMS>();
        mLogQueue = new Vector<MOSMS>();
        mIsRunning = false;
        mConn = new InsertConnection(SMSGW.getInstance().getOracleConfig());
    }

    public static MOQueue getInstance() {
        if (mMe == null) {
            mMe = new MOQueue();
            mMe.start();
        }

        return mMe;
    }

    public void add(MOSMS value) {
        // add log
        addLog(value);
        synchronized (mLock) {
            if (SMSGW.getInstance().isFilterMobile()) {
                // Check DCOM
                OracleConnection conn = OracleConnection.getInstance();
                if (conn.isDCOM(value.getSource())) {
                    MyLog.Debug("Service not support for DCOM " + value);
                    // Send Reply
                    String content = SMSGW.getInstance().getUnSupportedContent();
                    if (content != null && !content.isEmpty()) {
                        String shortCode = value.getDest();
                        String alias = "";
                        if (!ProcessSendSMS.getInstance().isValidShortCode(shortCode)) {
                            ShortCodeMatcher matcher = ShortCodeMatcher.getInstance();
                            VirtualShortCode v = matcher.getMatched(shortCode);
                            if (v != null) {
                                MyLog.Debug("Short code " + shortCode + " matched & replaced by " + v);
                                shortCode = v.getRealShortCode();
                                alias = v.getAlias();
                            }
                        }

                        MTSMS reply = new MTSMS(shortCode, value.getSource(), content, alias, MTSMS.SMS_TYPE_TEXT, "MPS Auto Reply");
                        MTQueue.getInstance().add(reply);
                    }
                    return;
                }
            }
            mQueue.add(value);
        }
    }

    public void addLog(MOSMS value) {
        if (SMSGW.getInstance().isEnableLogs()) {
            synchronized (mLogLock) {
                mLogQueue.add(value);
                 MyLog.Infor("receive MO_LOG: " + value);
                 MyLog.Infor("MO_LOG queue size: " + mLogQueue.size());
            }
        }
    }

    public MOSMS getNextTask() {
        synchronized (mLock) {
            if (mQueue.size() > 0) {
                MOSMS m = mQueue.firstElement();
                mQueue.removeElementAt(0);
                return m;
            } else {
                return null;
            }
        }
    }

    public int getSize() {
        synchronized (mLock) {
            return mQueue.size();
        }
    }

    private MOSMS getNextLog() {
        synchronized (mLogLock) {
            if (mLogQueue.size() > 0) {
                MOSMS m = mLogQueue.firstElement();
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
            t.setName("MO queue");
            t.start();
        }
    }

    public void stop() {
        mIsRunning = false;
    }

    private void insertBatchToDB() {
        long start = System.currentTimeMillis();
        SMSGW gw = SMSGW.getInstance();
        String f[] = gw.getMOParams();
        Connection conn = null;
        CallableStatement cs = null;
        conn = mConn.getDatabaseConnection();
        int count = 0;

        if (conn != null) {
            try {
                conn.setAutoCommit(false);
                cs = conn.prepareCall(gw.getMOInsertSQL());
                cs.setQueryTimeout(300); // s

                MOSMS task = getNextLog();
                while (task != null) {
                    count++;
                    for (int i = 0; i < f.length; i++) {
                        if (f[i].equalsIgnoreCase(PARAMS_MSISDN)) {
                            cs.setString(i + 1, PublicLibs.nomalizeMSISDN(task.getSource()));
                        } else if (f[i].equalsIgnoreCase(PARAMS_SHORTCODE)) {
                            cs.setString(i + 1, task.getDest());
                        } else if (f[i].equalsIgnoreCase(PARAMS_CONTENT)) {
                            cs.setString(i + 1, task.getContent());
                        } else if (f[i].equalsIgnoreCase(PARAMS_FORWARD)) {
                            if (task.getWSAddress() != null) {
                                cs.setString(i + 1, task.getWSAddress().getAddress());
                            } else {
                                cs.setString(i + 1, "");
                            }
                        } else if (f[i].equalsIgnoreCase(PARAMS_CREATED_TIME)) {
                            cs.setTimestamp(i + 1, new Timestamp(task.getCreatedTime()));
                        } else {
                            MyLog.Warning("Invalid MO Var: '" + f[i] + "'");
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
                MyLog.Debug("MO Insert RESULT count = " + result.length);
            } catch (SQLException ex) {
                MyLog.Error(ex);
            } finally {
                try {
                    if (cs != null) {
                        cs.close();
                    }
                } catch (SQLException ee) {
                }
                try {
                    conn.commit();
                } catch (SQLException ee) {
                }
            }
        }

        MyLog.Debug("Finish insert batch (" + count + " records) (" + (System.currentTimeMillis() - start) + " ms)");
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
