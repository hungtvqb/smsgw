/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.main;

import com.smssender.TextSecurity;
import com.nh.ultil.CountryCode;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import com.nh.cdrdumper.CDRDumper;
import com.nh.database.OracleConnection;
import com.nh.mo.ProcessMO;
import com.nh.mt.ProcessMT;
import com.nh.mt.ShortCodeMatcher;
import org.apache.log4j.PropertyConfigurator;
import com.nh.webserver.UserPool;
import com.nh.webserver.WebServer;

/**
 *
 * @author thuzpt
 */
public class SMSGW {
    private static SMSGW mMe = null;
    public static String SMSGW_HOME = "";
    
    private int mNumberOfThread;
    private int mWSPort;
    private String mWSIp;
    private int mWSThread;
    private String mWSUser;
    private int mMOProcess;
    private String mtSoapUrl;
    private String mShortCodeTable, mVirtualShortCodeTable, mUserTable, mWebserviceTable, mMOListenerTable;
    private String mOracleConfig;
    private String mOracleUsername;
    private String mOraclePassword;
    private boolean mEnableLogs;
    private String mMOInsertSQL, mMTInsertSQL;
    private String mMOParams[], mMTParams[];
    private String mProxyStand;
    private boolean mFilterMobile;
    private String mUnSupportedContent;
    private String mMOCdrConfig, mMTCdrConfig;
    private CDRDumper mMOCdrDumper, mMTCdrDumper;
   
    private SMSGW() {

    }
    
    public void loadConfig() {
        SMSGW_HOME = "../etc/smsgw/";
        PropertyConfigurator.configure(SMSGW_HOME + "log4j.properties"); // log4j
        MyLog.Debug(showVersion());
        
        try {
            Properties prop = new Properties();
            FileInputStream gencfgFile = new FileInputStream(SMSGW_HOME + "smsgw.conf");
            prop.load(gencfgFile);
            
            CountryCode.config(prop);
            mNumberOfThread = Integer.parseInt(prop.getProperty("number_of_thread"));
            MyLog.Infor("Number of process Queue thread: " + mNumberOfThread);
            
            mWSIp = prop.getProperty("ws_serverip");
            MyLog.Infor("Webservice IP: " + mWSIp);
            
            mWSPort = Integer.parseInt(prop.getProperty("ws_port"));
            MyLog.Infor("Webservice Port: " + mWSPort);
            
            mtSoapUrl = null;
            String tmmtSoapMode = prop.getProperty("mt_soap_url");
            if (tmmtSoapMode != null) {
                mtSoapUrl = tmmtSoapMode;
            }
            
            MyLog.Infor("mt_soap_url: " + mtSoapUrl);

            mWSThread = Integer.parseInt(prop.getProperty("ws_thread"));
            MyLog.Infor("Webservice Thread: " + mWSThread);

            mWSUser = prop.getProperty("ws_user");
            if (mWSUser == null || mWSUser.isEmpty()) {
                mWSUser = SMSGW_HOME + "ws_user.conf";
            }
            MyLog.Infor("Webservice User: " + mWSUser);

            mMOProcess = Integer.parseInt(prop.getProperty("mo_process"));
            MyLog.Infor("Number of MO process: " + mMOProcess);

            mFilterMobile = Boolean.parseBoolean(prop.getProperty("mobile_only"));
            MyLog.Infor("Filter Mobile Only: " + mFilterMobile);
            
            mUnSupportedContent = prop.getProperty("unsupport_reply_content");
            MyLog.Infor("UnSupport Reply Content: " + mUnSupportedContent);
            
            mOracleConfig = TextSecurity.getInstance().Decrypt(prop.getProperty("db_config"));
            mOracleUsername = prop.getProperty("db_username");
            mOraclePassword = TextSecurity.getInstance().Decrypt(prop.getProperty("db_password"));
            MyLog.Infor("Oracle Configuration: " + mOracleConfig);
            
            mShortCodeTable = prop.getProperty("shortcode_table");
            mVirtualShortCodeTable = prop.getProperty("virtual_shortcode_table");
            mUserTable = prop.getProperty("user_table");
            mWebserviceTable = prop.getProperty("mo_ws_webservice");
            mMOListenerTable = prop.getProperty("mo_listener_table");
            mProxyStand = prop.getProperty("proxy_server").trim();
            if (mProxyStand != null) {
                mProxyStand = mProxyStand.trim();
            }
            MyLog.Infor("Proxy stand: " + mProxyStand);
            
            MyLog.Infor("Shortcode table: " + mShortCodeTable);
            MyLog.Infor("Virtual Shortcode table: " + mVirtualShortCodeTable);
            MyLog.Infor("User table: " + mUserTable);
            MyLog.Infor("Webservice table: " + mWebserviceTable);
            MyLog.Infor("Shortcode listener table: " + mMOListenerTable);
            
            mEnableLogs = Boolean.parseBoolean(prop.getProperty("enables_db_logs", "false"));
            MyLog.Infor("Enable Logs: " + mEnableLogs);
            if (mEnableLogs) {
                mMOInsertSQL = prop.getProperty("mo_insert_sql");
                mMOParams = PublicLibs.stringToArray(prop.getProperty("mo_params"), "\\|");
                
                mMTInsertSQL = prop.getProperty("mt_insert_sql");
                mMTParams = PublicLibs.stringToArray(prop.getProperty("mt_params"), "\\|");
                
                MyLog.Infor("MO Insert: " + mMOInsertSQL);
                MyLog.Infor("MO Params: " + mMOParams);
                MyLog.Infor("MT Insert: " + mMTInsertSQL);
                MyLog.Infor("MT Params: " + mMTParams);
            }
            // ------------------ CDR Dumpert -------------------
            mMOCdrConfig = prop.getProperty("mo_cdr_dumper");
            MyLog.Infor("MO CDR Dumpert configuration: " + mMOCdrConfig);
            mMTCdrConfig = prop.getProperty("mt_cdr_dumper");
            MyLog.Infor("MT CDR Dumpert configuration: " + mMTCdrConfig);
            
            MyLog.Infor("Load CONFIG Success!!");
        } catch (IOException e) {
            MyLog.Error("ERROR LOAD CONFIG FILE: " + e.getMessage());
            MyLog.Error(e);
            System.exit(1);
        } catch (NumberFormatException e) {
            MyLog.Error("ERROR LOAD CONFIG FILE: " + e.getMessage());
            MyLog.Error(e);
            System.exit(1);
        }
    }
    
    public int getWSPort() {
        return mWSPort;
    }

    public String getWSUser() {
        return mWSUser;
    }
    
    public int getNumberOfThread() {
        return mNumberOfThread;
    }

    public int getMOProcess() {
        return mMOProcess;
    }

    public String getShortCodeTable() {
        return mShortCodeTable;
    }

    public String getVirtualShortCodeTable() {
        return mVirtualShortCodeTable;
    }

    public String getUserTable() {
        return mUserTable;
    }

    public String getWebserviceTable() {
        return mWebserviceTable;
    }
    
    public String getMtSoapUrl() {
        return mtSoapUrl;
    }

    public String getMOListenerTable() {
        return mMOListenerTable;
    }

    public String getOracleConfig() {
        return mOracleConfig;
    }

    public boolean isEnableLogs() {
        return mEnableLogs;
    }

    public String getMOInsertSQL() {
        return mMOInsertSQL;
    }

    public String getMTInsertSQL() {
        return mMTInsertSQL;
    }

    public String[] getMOParams() {
        return mMOParams;
    }

    public String[] getMTParams() {
        return mMTParams;
    }

    public String getProxyStand() {
        return mProxyStand;
    }

    public boolean isFilterMobile() {
        return mFilterMobile;
    }

    public String getUnSupportedContent() {
        return mUnSupportedContent;
    }

    public static SMSGW getInstance() {
        if (mMe == null) {
            mMe = new SMSGW();
        }
        return mMe;
    }
    
    public void start() {
//        HLR h = HLR.getInstance();
//        h.setConfigFile("config/hlr/hlr.conf");
        // Oracle
        OracleConnection conn = OracleConnection.getInstance();
        conn.setConnectionString(mOracleConfig);
        conn.setUsername(mOracleUsername);
        conn.setPassword(mOraclePassword);
        conn.startConnect();

        if (mMOCdrConfig != null && !mMOCdrConfig.isEmpty()) {
            mMOCdrDumper = new CDRDumper();
            mMOCdrDumper.loadConfig(mMOCdrConfig);
            mMOCdrDumper.start();
        } else {
            mMOCdrDumper = null;
        }
        
        if (mMTCdrConfig != null && !mMTCdrConfig.isEmpty()) {
            mMTCdrDumper = new CDRDumper();
            mMTCdrDumper.loadConfig(mMTCdrConfig);
            mMTCdrDumper.start();
        } else {
            mMTCdrDumper = null;
        }
        
        for (int i = 0; i < mNumberOfThread; i++) {
            ProcessMT pp = new ProcessMT();
            pp.start();
        }
        
        for (int i = 0; i < mMOProcess; i++) {
            ProcessMO pp = new ProcessMO();
            pp.start();
        }
        
        UserPool.getInstance().loadConfig();
        ShortCodeMatcher.getInstance().loadConfig();
        
        WebServer w = new WebServer(mWSIp, mWSPort, mWSThread);
        w.start();
    }

    public CDRDumper getMOCdrDumper() {
        return mMOCdrDumper;
    }

    public CDRDumper getMTCdrDumper() {
        return mMTCdrDumper;
    }
    
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Number Of MO Thread: ").append(getNumberOfThread()).append("\n");
        result.append("WS Port: ").append(getWSPort()).append("\n");
        result.append("WS Thread: ").append(mWSThread).append("\n");
        result.append("WS User: ").append(getWSUser()).append("\n");
        result.append("MO Thread: ").append(mMOProcess).append("\n");
        result.append("Proxy Stand: ").append(mProxyStand).append("\n");
        result.append("Shortcode table: ").append(mShortCodeTable);
        result.append("Virtual Shortcode table: ").append(mVirtualShortCodeTable);
        result.append("User table: ").append(mUserTable);
        result.append("Webservice table: ").append(mWebserviceTable);
        result.append("Shortcode listener table: ").append(mMOListenerTable);
        result.append("MO CDR Dumper: ").append(mMOCdrConfig);
        result.append("MT CDR Dumper: ").append(mMTCdrConfig);
        
        return result.toString();
    }
    
    public String showVersion() {
        StringBuilder result = new StringBuilder();
        
        try {
            InputStream is = getClass().getResourceAsStream("version.properties");
            Properties p = new Properties();
            p.load(is);
            
            Enumeration<Object> key = p.keys();
            result.append("SMSGW Build Information: \n");
            while (key.hasMoreElements()) {
                String k = (String) key.nextElement();
                result.append(k).append(": ").append(p.getProperty(k)).append("\n");
            }
            
            is.close();
        } catch (Exception ex) {
            
        }
        
        return result.toString();
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        SMSGW gw = SMSGW.getInstance();
        if (args.length > 0 && args[0].equalsIgnoreCase("-version")) {
            System.out.println(gw.showVersion());
            return;
        }
        
        gw.loadConfig();
        gw.start();
    }
}
