/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.webserver;

import com.smssender.TextSecurity;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import com.nh.main.MyLog;
import com.nh.database.BlackListTableCache;

/**
 *
 * @author hoand
 */
public class MyUser {
    private static final String ALLOWED_ALL = "ALL";
    
    private String mUserName, mPassword;
    private HashSet<String> mAllowedShortCode;
    private HashSet<String> mAllowedAlias;
    private String mBlacklist;
    private boolean mAllowFlash;
    private boolean mRequiredAlias;

    public static MyUser createUser(Properties p) {
        MyUser result = null;
        try {
            String username = p.getProperty("username");
            String password = TextSecurity.getInstance().Decrypt(p.getProperty("password"));
            HashSet<String> allowedShortCode = null, allowedAlias = null;
            boolean allowedFlash = false;
            try { allowedFlash = Boolean.parseBoolean(p.getProperty("allow_flash_sms"));
            } catch (Exception ex) { }
            
            boolean requiredAlias = false;
            try { requiredAlias = Boolean.parseBoolean(p.getProperty("required_alias"));
            } catch (Exception ex) { }
            
            String tmp = p.getProperty("allowed_shortcode");
            if (tmp != null && !tmp.trim().isEmpty()) {
                tmp = tmp.trim();
                
                if (!tmp.equalsIgnoreCase(ALLOWED_ALL)) {
                    String arr[] = tmp.trim().split(",");
                    allowedShortCode = new HashSet<String>();
                    if (arr != null && arr.length > 0) {
                        for (int i=0; i<arr.length; i++) {
                            if (arr[i] != null && !arr[i].trim().isEmpty()) {
                                allowedShortCode.add(arr[i].trim().toUpperCase());
                            }
                        }
                    }
                }
            }
            
            tmp = p.getProperty("allowed_alias");
            if (tmp != null && !tmp.trim().isEmpty()) {
                tmp = tmp.trim();
                
                if (!tmp.equalsIgnoreCase(ALLOWED_ALL)) {
                    String arr[] = tmp.trim().split(",");
                    allowedAlias = new HashSet<String>();
                    if (arr != null && arr.length > 0) {
                        for (int i=0; i<arr.length; i++) {
                            if (arr[i] != null && !arr[i].trim().isEmpty()) {
                                allowedAlias.add(arr[i].trim().toUpperCase());
                            }
                        }
                    }
                }
            }
            
            result = new MyUser(username, password, allowedShortCode, allowedAlias, allowedFlash, requiredAlias, null);
        } catch (Exception ex) {
            MyLog.Error(ex);
        }
        return result;
    }
    
    private MyUser(String mUserName, String mPassword, HashSet<String> mAllowedShortCode, 
                   HashSet<String> mAllowedAlias, boolean mAllowFlash, boolean requireAlias, 
                   String blacklist) {
        this.mUserName = mUserName;
        this.mPassword = mPassword;
        this.mAllowedShortCode = mAllowedShortCode;
        this.mAllowedAlias = mAllowedAlias;
        this.mAllowFlash = mAllowFlash;
        mRequiredAlias = requireAlias;
        
        if (blacklist != null && !blacklist.isEmpty()) {
            mBlacklist = blacklist.toUpperCase().trim();
        }
    }
    
    private HashSet<String> parseHashSet(String input) {
        HashSet<String> result = null;
        
        if (input != null && !input.trim().isEmpty()) {
            String tmp = input.trim();
            if (!tmp.equalsIgnoreCase(ALLOWED_ALL)) {
                String arr[] = tmp.trim().split(",");
                result = new HashSet<String>();
                if (arr != null && arr.length > 0) {
                    for (int i=0; i<arr.length; i++) {
                        if (arr[i] != null && !arr[i].trim().isEmpty()) {
                            result.add(arr[i].trim().toUpperCase());
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    public MyUser(String userName, String password, String allowedShortCode, 
                  String allowedAlias, boolean allowedFlash, boolean requireAlias, String blacklist) {
        mUserName = userName;
        mPassword = password;
        mAllowedShortCode = parseHashSet(allowedShortCode);
        mAllowedAlias = parseHashSet(allowedAlias);
        mRequiredAlias = requireAlias;
        mAllowFlash = allowedFlash;
        if (blacklist != null && !blacklist.isEmpty()) {
            mBlacklist = blacklist.toUpperCase().trim();
        }
    }

    public boolean match(String user, String pass, String msisdn, String shortCode, String alias, boolean flashSMS) {
        if (!mPassword.equals(pass)) {
            MyLog.Debug("Password for user " + user + " is not matched");
            return false;
        }

        // Flash SMS
        if (flashSMS) {
            if (!mAllowFlash) {
                MyLog.Debug("User " + user + " is not allowed to send FLASH SMS");
                return false;
            }
        }
        
        // Alias
        if (mRequiredAlias) {
            if (alias != null && !alias.isEmpty()) {
                if (mAllowedAlias != null && !mAllowedAlias.isEmpty()) {
                    if (!mAllowedAlias.contains(alias.toUpperCase())) {
                        MyLog.Debug("User " + user + " is not allowed to user ALIAS '" + alias + "'");
                        return false;
                    }
                }
            }  else {
                MyLog.Debug("User " + user + " is not allowed to user ALIAS '" + alias + "'");
                return false;
            }
        }
       
        // ShortCode
        if (mAllowedShortCode != null && !mAllowedShortCode.isEmpty()) {
            if (!mAllowedShortCode.contains(shortCode.toUpperCase())) {
                MyLog.Debug("User " + user + " is not allowed to use SHORTCODE '" + shortCode + "'");
                return false;
            }
        }
        
        if (mBlacklist != null && !mBlacklist.isEmpty()) {
            HashSet<String> table = BlackListTableCache.getInstance().getTable(mBlacklist);
            if (table != null && table.contains(msisdn)) {
                MyLog.Debug("User " + user + " is not allowed to send SMS to " + msisdn + "'");
                return false;
            }
        }
        
        return true;
    }

    private String setToStr(HashSet<String> set) {
        if (set == null || set.isEmpty()) {
            return ALLOWED_ALL;
        }
        
        StringBuilder b = new StringBuilder();
        Iterator<String> iter = set.iterator();
        while (iter.hasNext()) {
            String tmp = iter.next();
            b.append(tmp).append(", ");
        }
        
        return b.toString();
    }
    
    @Override
    public String toString() {
        return "(User: " + mUserName + "; " + 
                "ShortCode: " + setToStr(mAllowedShortCode) + "; " +
                "Alias: " + setToStr(mAllowedAlias) + "; " +
                "Required Alias: " + mRequiredAlias + "; " +
                "FlashSMS: " + mAllowFlash + ")";
    }
}
