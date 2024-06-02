/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mt;

import java.util.HashMap;
import java.util.Map;
import com.nh.main.MyLog;
import com.nh.main.SMSGW;
import com.nh.database.OracleConnection;

/**
 *
 * @author HoaND6
 * @created a long time
 * 
 * @author haind25
 * @since 
 * su dung Map thay Hashtable
 */
public class ShortCodeMatcher {
    private static ShortCodeMatcher mMe = null;
    private Map<String, VirtualShortCode> mMatcher;
    
    private ShortCodeMatcher() {
        mMatcher = new HashMap<String, VirtualShortCode>();
    }
    
    public static ShortCodeMatcher getInstance() {
        if (mMe == null) {
            mMe = new ShortCodeMatcher();
        }
        return mMe;
    }
    
    public void loadConfig() {
        OracleConnection conn = OracleConnection.getInstance();
        if (conn != null && conn.isConnected()) {
            mMatcher = conn.getAllVirtualShortCode(SMSGW.getInstance().getVirtualShortCodeTable());
        } else {
            MyLog.Debug("DBThread is not CONNECTED to DB, skip load");
        }
    }
    
    public VirtualShortCode getMatched(String shortCode) {
        if (mMatcher.containsKey(shortCode)) {
            VirtualShortCode result = mMatcher.get(shortCode);
            MyLog.Debug("Shortcode " + shortCode + " match with " + result);
            return result;
        } else {
            MyLog.Debug("Shortcode " + shortCode + " does not match with any VirtualShortCode");
            return null;
        }
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Virtual ShortCode: \n");
        
        for (Map.Entry<String, VirtualShortCode> entry : mMatcher.entrySet()) {
            VirtualShortCode v = entry.getValue();
            result.append(v).append("\n");
        }
        
        return result.toString();
    }
}
