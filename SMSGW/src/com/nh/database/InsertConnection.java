/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.database;

import java.sql.*;
import com.nh.main.MyLog;

/**
 *
 * @author thuzpt
 */
public class InsertConnection implements Runnable {
    public static final int CONNECTION_TIMEOUT = 10000; //ms
    protected static int CHECK_TIME = 60 * 1000; //ms
    protected boolean isRunning;
    protected Connection mConnection;
    protected boolean mIsConnected;
    private long mLastConnect;
    private String mConnectionStr;
    
    public InsertConnection(String conn) {
        isRunning = false;
        mIsConnected = false;
        mConnection = null;
        mLastConnect = 0;
        mConnectionStr = conn;

        try {
            DriverManager.registerDriver(new oracle.jdbc.OracleDriver());
        }
        catch (Exception e) {
            MyLog.Error(getThreadName() + "ERROR " + e.getMessage());
            System.exit(0);
        }
    }

    // <editor-fold desc="Init & connect Oracle" defaultstate="collapsed">
    protected void init() {
        if (System.currentTimeMillis() - mLastConnect > CONNECTION_TIMEOUT) {
            mLastConnect = System.currentTimeMillis();

            if (mConnectionStr != null) {
                MyLog.Infor(getThreadName() + "Connecting to '" + mConnectionStr + "'");
                mConnection = connectToOraServer(mConnectionStr);

                if (mConnection != null) {
                    mIsConnected = true;
                    MyLog.Infor(getThreadName() + "CONNECTED."); // to '" + conn + "'");
                } else {
                    MyLog.Error(getThreadName() + "CAN NOT Connect to DB"); //'" + conn + "'");
                }
            } else {
                MyLog.Infor(getThreadName() + "get Connection String Failed");
            }
        } else {
            MyLog.Error("Wait for timeout .... ");
        }
    }

    public boolean isConnected() {
        return mIsConnected;
    }
    
    private void reconnect() {
        mIsConnected = false;
        try {
            MyLog.Debug(getThreadName() + "Wait 5 secs before RECONNECT to DB ....");
            Thread.sleep(5000); // wait 5s before reconnect
            MyLog.Debug(getThreadName() + "Reconnecting ...");
            mConnection.close();
        } catch(Exception e) {}
        finally {
            mConnection = null;
            init();
        }
    }
    
    private String getThreadName() {
        return "DBInsert: ";
    }
    
    protected Connection connectToOraServer(String conn) {
        try {
            Connection co = OracleConnection.getInstance().getConnection();
            MyLog.Infor(getThreadName() + "CONNECTED.");
            return co;
        }
        catch (Exception e) {
            MyLog.Error(getThreadName() + "Error connect to Oracle DB server" + e.getMessage());
            return null;
        }
    }
    
    public void startConnect() {
        init();

        isRunning = true;
        Thread t = new Thread(this);
        t.start();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                Statement s = null;
                try {
                    String strSQL = "select 1 from dual";
                    s = mConnection.createStatement();
                    java.sql.ResultSet r = s.executeQuery(strSQL);
                    r.next();
                    s.close();
                } catch (Exception ex) {
                    MyLog.Error(getThreadName() + "Check connection ERROR" + ex.getMessage() + ", RECONNECTING .... ");
                    reconnect();
                } finally {
                    if (s != null) {
                        s.close();
                    }
                }

                Thread.sleep(CHECK_TIME);

            } catch (Exception e) {
                MyLog.Error(getThreadName() + "ERROR" + e.getMessage());
                MyLog.Error(e);
            }
        }
    }

    public CallableStatement getStatement(String strSql) {
        try {
            return mConnection.prepareCall(strSql);
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
            reconnect();
            
            return null;
        }
    }
    
    public Connection getDatabaseConnection() {
        return mConnection;
    }
    
    public boolean excuteUpdate(String strSQL) {
        MyLog.Debug(getThreadName() + "start execute'" + strSQL + "'");
        boolean result = false;
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        int count = 0;
        try {
            s = mConnection.prepareCall(strSQL);
            count = s.executeUpdate();
            result = true;
            s.close();
        } catch (SQLException e) {
            MyLog.Error(getThreadName() + "ERROR" + e.getMessage());
            MyLog.Error(e);
            result = false;
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
            reconnect();
            result = false;
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish execute '" + strSQL + "'" + result + " (" 
                        + (System.currentTimeMillis() - start) + " ms) (Count" + count + ")");

        return result;
    }
    // </editor-fold>
}
