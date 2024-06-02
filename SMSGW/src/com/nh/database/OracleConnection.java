/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.database;

import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import com.nh.main.MyLog;
import com.nh.main.PublicLibs;
import com.nh.mo.MOForward;
import com.nh.mo.WSEndPoint;
import com.nh.mt.VirtualShortCode;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManager;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSourceImpl;
import com.nh.webserver.MyUser;

/**
 *
 * @author thuzpt
 */
public class OracleConnection implements Runnable {
    public static final int CONNECTION_TIMEOUT = 10000; //ms
    private static long GET_CONNECTION_TIME_WAITING = 1500l; //wait 1.5s
    public static int ORA_CONNECTION_TIMEOUT = 30000;
    protected static int CHECK_TIME = 10 * 1000; //ms
    protected boolean isRunning;
    protected Connection mConnection;
    protected boolean mIsConnected;
    private long mLastConnect;
    private String mConnectionStr;
    private String username;
    private String password;
    private static OracleConnection mMe = null;
     private static PoolDataSourceImpl ds = null;
     private static UniversalConnectionPoolManager mgr = null;
    
    public static OracleConnection getInstance() {
        if (mMe == null) {
            mMe = new OracleConnection();
        }
        
        return mMe;
    }

    public void setConnectionString(String conn) {
        mConnectionStr = conn;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
    private OracleConnection() {
        isRunning = false;
        mIsConnected = false;
        mConnection = null;
        mLastConnect = 0;

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
                initConnectionPool();
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
        return "DBConfig: ";
    }
    
    protected Connection connectToOraServer(String conn) {
        try {
//            Connection co = DriverManager.getConnection(conn);
            Connection co = getConnection();
            MyLog.Infor(getThreadName() + "CONNECTED.");
            return co;
        }
        catch (Exception e) {
            MyLog.Error(getThreadName() + "Error connect to Oracle DB server" + e.getMessage());
            return null;
        }
    }
    
    private void initConnectionPool() {   
        try {
           
            //-------------------
            ds = new PoolDataSourceImpl();
            ds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            ds.setURL(mConnectionStr);//add new            
            ds.setUser(username);//add new
            ds.setPassword(password);
            ds.setConnectionPoolName("SMSGW_Pooling");
            ds.setInitialPoolSize(2);
            ds.setMaxPoolSize(5);
            // wait to get connection
            ds.setValidateConnectionOnBorrow(true);
            ds.setSQLForValidateConnection("select 1 from dual");
            // create new connection pool manager and start it
            mgr = UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
            mgr.createConnectionPool(ds);
            mgr.startConnectionPool(ds.getConnectionPoolName());
        } catch (SQLException ex) {  
            MyLog.Error(ex);
        } catch (UniversalConnectionPoolException ex) {
            MyLog.Error(ex);
        }
    }
    
    public synchronized Connection getConnection() {
        Connection con = null;
        long startTime = System.currentTimeMillis();
        try {
            con = getConnectionFromDatasource();
            while (con == null) {
                try {
                    wait(GET_CONNECTION_TIME_WAITING);
                } catch (InterruptedException ie) {
                    throw ie;
                }
                if ((System.currentTimeMillis() - startTime) > ORA_CONNECTION_TIMEOUT) {
                    // Timeout has expired
                    throw new SQLException("Qua time ma khong lay duoc connection....");
                } else {
                    con = getConnectionFromDatasource();
                }
            }
        } catch (SQLException ex) {
            MyLog.Error(ex);
        } catch (InterruptedException ex) {
            MyLog.Error(ex);
        }
        return con;
    }

    /**
     * get available connectin from pool
     *
     * @return
     */
    private synchronized Connection getConnectionFromDatasource() throws SQLException {
        Connection con;
        try {
            con = ds.getConnection();
            if (null != con && con.isClosed()) {
                con = null;
            }
        } catch (SQLException ex) {
            con = null;
            return con;
        }
        return con;
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
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_INACTIVE = 0;
    
    public Map<String, ShortCode> getAllShortCode(String table) {
        Map<String, ShortCode> result = new HashMap<String, ShortCode>();
        MyLog.Debug(getThreadName() + "start getAllShortCode '" + table + "'");
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        try {
            String strSQL = "SELECT * FROM " + table + " WHERE status=" + STATUS_ACTIVE;
            s = mConnection.prepareCall(strSQL);
            ResultSet r = s.executeQuery();
            
            while (r.next()) {
                String shortCode = r.getString("SHORT_CODE");
                String ip = r.getString("IP");
                int port = r.getInt("PORT");
                String username = r.getString("USERNAME");
                String password = r.getString("PASSWORD");
                int enquireLinkTime = r.getInt("ENQIRE_LINK_TIME");
                int numberOfInterface = r.getInt("NUMBER_OF_INTERFACE");
                int longSMSDelay = r.getInt("LONG_SMS_DELAY");
                int limitTPS = r.getInt("LIMIT_TPS");
                String connectionProperties = r.getString("CONNECTION_PROPERTIES");
                int status = r.getInt("STATUS");
                String id = r.getString("ID");
                
                ShortCode sh = new ShortCode(shortCode, ip, username, password, connectionProperties, 
                                             port, enquireLinkTime, numberOfInterface, longSMSDelay, limitTPS, status, id);
                MyLog.Debug(getThreadName() + "add " + sh);
                result.put(id, sh);
            }
        } catch (SQLException e) {
            MyLog.Error(getThreadName() + "ERROR" + e.getMessage());
            MyLog.Error(e);
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
            reconnect();
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish getAllShortCode '" + table + "' -> " + result.size() + " record (" 
                        + (System.currentTimeMillis() - start) + " ms)");

        return result;
        
    }
    
    public Map<String, VirtualShortCode> getAllVirtualShortCode(String table) {
        Map<String, VirtualShortCode> result = new HashMap<String, VirtualShortCode>();
        MyLog.Debug(getThreadName() + "start getAllVirtualShortCode '" + table + "'");
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        try {
            String strSQL = "SELECT * FROM " + table + " WHERE status=" + STATUS_ACTIVE;
            s = mConnection.prepareCall(strSQL);
            ResultSet r = s.executeQuery();
            
            while (r.next()) {
                String virtualShortCode = r.getString("VIRTUAL_SHORTCODE");
                String shortCode = r.getString("REAL_SHORTCODE");
                String Alias = r.getString("ALIAS");
                int status = r.getInt("STATUS");
                
                VirtualShortCode sh = new VirtualShortCode(shortCode, Alias, virtualShortCode);
                MyLog.Debug(getThreadName() + "add " + sh);
                result.put(virtualShortCode, sh);
            }
        } catch (SQLException e) {
            MyLog.Error(getThreadName() + "ERROR" + e.getMessage());
            MyLog.Error(e);
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
            reconnect();
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish getAllVirtualShortCode '" + table + "' -> " + result.size() + " record (" 
                        + (System.currentTimeMillis() - start) + " ms)");

        return result;
    }
    
    private static final int NUMBER_TRUE = 1;
    private static final int NUMBER_FALSE = 0;
    
    public Map<String, MyUser> getAllUser(String table) {
        Map<String, MyUser> result = new HashMap<String, MyUser>();
        MyLog.Debug(getThreadName() + "start getAllUser '" + table + "'");
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        try {
            String strSQL = "SELECT * FROM " + table + " WHERE status=" + STATUS_ACTIVE;
            s = mConnection.prepareCall(strSQL);
            ResultSet r = s.executeQuery();
            
            while (r.next()) {
                String username = r.getString("USERNAME");
                String password = r.getString("PASSWORD");
                String allowedShortCode = r.getString("ALLOWED_SHORTCODE");
                String allowedAlias = r.getString("ALLOWED_ALIAS");
                int requiredAlias = r.getInt("REQUIRED_ALIAS");
                int allowFlashSMS = r.getInt("ALLOW_FLASH_SMS");
                int status = r.getInt("STATUS");
                String blackListTable = r.getString("BLACK_LIST");
                // Add to cache
                BlackListTableCache cache = BlackListTableCache.getInstance();
                cache.addTable(blackListTable);
                
                MyUser user = new MyUser(username, password, allowedShortCode, allowedAlias, 
                                        allowFlashSMS == NUMBER_TRUE, requiredAlias == NUMBER_TRUE, blackListTable);
                MyLog.Debug(getThreadName() + "add " + user);
                result.put(username, user);
            }
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR " + ex.getMessage());
            MyLog.Error(ex);
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish getAllUser '" + table + "' -> " + result.size() + " record (" 
                        + (System.currentTimeMillis() - start) + " ms)");

        return result;
    }
    
    public HashSet<String> getBlackListTable(String table) {
        HashSet<String> result = new HashSet<String>();
        MyLog.Debug(getThreadName() + "start getBlackListTable '" + table + "'");
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        try {
            String strSQL = "SELECT * FROM " + table + " WHERE status=" + STATUS_ACTIVE;
            s = mConnection.prepareCall(strSQL);
            ResultSet r = s.executeQuery();
            
            while (r.next()) {
                String msisdn = r.getString("MSISDN");
                int status = r.getInt("STATUS");
                
                if (msisdn != null) {
                    //MyLog.Debug(getThreadName() + "add " + msisdn);
                    result.add(PublicLibs.nomalizeMSISDN(msisdn));
                }
            }
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish getBlackListTable '" + table + "' -> " + result.size() + " record (" 
                        + (System.currentTimeMillis() - start) + " ms)");

        return result;
    }
    
    public Map<String, WSEndPoint> getAllWebServices(String table) {
        Map<String, WSEndPoint> result = new HashMap<String, WSEndPoint>();
        MyLog.Debug(getThreadName() + "start getAllWebServices '" + table + "'");
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        try {
            String strSQL = "SELECT * FROM " + table + " WHERE status=" + STATUS_ACTIVE;
            s = mConnection.prepareCall(strSQL);
            ResultSet r = s.executeQuery();
            
            while (r.next()) {
                String wsName = r.getString("WS_NAME");
                String wsURL = r.getString("URL");
                String soapAction = r.getString("SOAP_ACTION");
                String username = r.getString("USERNAME");
                String password = r.getString("PASSWORD");
                String rawXML = r.getString("RAW_XML");
                String returnTag = r.getString("RETURN_TAG");
                int convertToASCII = r.getInt("CONVERT_TO_ASCII");
                int useHex = r.getInt("USE_HEX");
                int status = r.getInt("STATUS");
                
                WSEndPoint endPoint = new WSEndPoint(username, password, wsURL, soapAction, rawXML, returnTag, 
                                                     convertToASCII == NUMBER_TRUE, useHex == NUMBER_TRUE);
                MyLog.Debug(getThreadName() + "add " + endPoint);
                result.put(wsName, endPoint);
            }
        } catch (SQLException e) {
            MyLog.Error(getThreadName() + "ERROR" + e.getMessage());
            MyLog.Error(e);
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
            reconnect();
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish getAllWebServices '" + table + "' -> " + result.size() + " record (" 
                        + (System.currentTimeMillis() - start) + " ms)");

        return result;
    }
    
    public Map<String, HashSet<MOForward> > getAllMOListener(String table) {
        Map<String, HashSet<MOForward> > result = new HashMap<String, HashSet<MOForward>>();
        MyLog.Debug(getThreadName() + "start getAllMOListener '" + table + "'");
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        
        try {
            String strSQL = "SELECT * FROM " + table + " WHERE status=" + STATUS_ACTIVE + " ORDER BY shortcode";
            s = mConnection.prepareCall(strSQL);
            ResultSet r = s.executeQuery();

            String currentShortCode = null;
            HashSet<MOForward> forward = null;
        
            while (r.next()) {
                String shortCode = r.getString("SHORTCODE");
                String listener = r.getString("LISTENER");
                int status = r.getInt("STATUS");
                String forwardOnly = r.getString("FORWARD_ONLY");
                String forwardExcept = r.getString("FORWARD_EXCEPT");
                
                if (currentShortCode == null || !currentShortCode.equals(shortCode)) {
                    if (forward != null && !forward.isEmpty()) {
                        result.put(currentShortCode, forward);
                    }
                     
                    currentShortCode = shortCode;
                    forward = new HashSet<MOForward>();
                } 
                
                MOForward f = new MOForward(shortCode, listener, forwardOnly, forwardExcept);
                MyLog.Debug(getThreadName() + "add " + f);
                forward.add(f);
            }
            
            if (currentShortCode != null && forward != null && !forward.isEmpty()) {
                result.put(currentShortCode, forward);
            }
        } catch (SQLException e) {
            MyLog.Error(getThreadName() + "ERROR" + e.getMessage());
            MyLog.Error(e);
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR" + ex.getMessage());
            MyLog.Error(ex);
            reconnect();
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        MyLog.Infor(getThreadName() + " Finish getAllMOListener '" + table + "' -> " + result.size() + " record (" 
                        + (System.currentTimeMillis() - start) + " ms)");

        return result;
    }
    
    public static final int TYPE_DCOM = 2;
    public static final int TYPE_MOBILE = 1;
    public static final int TYPE_ERROR = 3;
    
    public boolean isDCOM(String msisdn) {
        MyLog.Debug(getThreadName() + "Start check DCOM for " + msisdn);
        CallableStatement s = null;
        long start = System.currentTimeMillis();
        int result = 0;
        
        try {
            String strSQL = "begin pr_dcom_blacklist_c(?, ?); end;";
            s = mConnection.prepareCall(strSQL);
            // Set timeout
            s.setQueryTimeout(30); // sec
            // -----------------
            s.setString(1, PublicLibs.nomalizeMSISDN(msisdn));
            s.registerOutParameter(2, java.sql.Types.INTEGER);
            s.execute();
            
            result = s.getInt(2);
        } catch (Exception ex) {
            MyLog.Error(getThreadName() + "ERROR " + ex.getMessage());
            MyLog.Error(ex);
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ee) {}
            }
        }
        
        MyLog.Infor(getThreadName() + "Finish check DCOM for " + msisdn + " -> (" + result + ") (" 
                                        + (System.currentTimeMillis() - start) + " ms)");
        return result == TYPE_DCOM;
    }

}
