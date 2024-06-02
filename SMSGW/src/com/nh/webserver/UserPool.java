/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.webserver;

import java.util.HashMap;
import java.util.Map;
import com.nh.main.MyLog;
import com.nh.main.SMSGW;
import com.nh.database.OracleConnection;

/**
 *
 * @author hoand
 */
public class UserPool {
    private static UserPool mMe;
    private Map<String, MyUser> mPool;

    private UserPool() {
        mPool = new HashMap<String, MyUser>();
    }

    public static UserPool getInstance() {
        if (mMe == null) {
            mMe = new UserPool();
            mMe.loadConfig();
        }

        return mMe;
    }

    public void loadConfig() {
        OracleConnection conn = OracleConnection.getInstance();
        if (conn != null && conn.isConnected()) {
            mPool = conn.getAllUser(SMSGW.getInstance().getUserTable());
        } else {
            MyLog.Debug("DBThread is not CONNECTED to DB, skip load");
        }
    }

    public boolean match(String user, String pass, String msisdn, String shortCode, String alias, boolean flashSMS) {
        if (mPool != null && !mPool.isEmpty()) {
            MyUser u = mPool.get(user);
            if (u != null) {
                return u.match(user, pass, msisdn, shortCode, alias, flashSMS);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("User Pool: \n");
        for (Map.Entry<String, MyUser> entry : mPool.entrySet()) {
            MyUser u = entry.getValue();
            result.append(u).append("\n");
        }
        
        return result.toString();
    }
}
