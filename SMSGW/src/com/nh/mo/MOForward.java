/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mo;

import java.util.HashSet;
import java.util.Iterator;
import com.nh.main.PublicLibs;

/**
 *
 * @author thuzpt
 */
public class MOForward {
    public static final String FORWARD_ALL = "ALL";
    private String mShortCode, mListenter;
    private HashSet<String> mForwardOnly, mForwardExcept;

    public MOForward(String mShortCode, String mListenter, String forwardOnly, String forwardExcept) {
        this.mShortCode = mShortCode;
        this.mListenter = mListenter;
        this.mForwardOnly = PublicLibs.stringToHashSet(forwardOnly, true);
        this.mForwardExcept = PublicLibs.stringToHashSet(forwardExcept, true);
    }

    public String getShortCode() {
        return mShortCode;
    }

    public String getListenter() {
        return mListenter;
    }

    public HashSet<String> getForwardOnly() {
        return mForwardOnly;
    }

    public HashSet<String> getForwardExcept() {
        return mForwardExcept;
    }

    /**
     * Check MO is allowed to forward or not
     * @param mo
     * @return 
     */
    public boolean matched(String mo) {
        boolean result = false;
        String upper = mo.toUpperCase();
        
        if (mForwardOnly != null && !mForwardOnly.isEmpty()) {
            Iterator<String> key = mForwardOnly.iterator();
            
            result = false;
            
            while (key.hasNext()) {
                String keyword = key.next();
                
                //specify case
                if (FORWARD_ALL.equals(keyword)) {
                    return true;
                }
                
                if (upper.equals(keyword) || upper.startsWith(keyword + " ") 
                        || upper.matches(keyword)) {
                    result = true;
                    break;
                }
            }
        } else {
            if (mForwardExcept != null && !mForwardExcept.isEmpty()) {
                Iterator<String> key = mForwardExcept.iterator();
                while (key.hasNext()) {
                    String keyword = key.next();
                    if (upper.equals(keyword) || upper.startsWith(keyword + " ")
                            || upper.matches(keyword)) {
                        result = false;
                        break;
                    }
                }
            }
        }
        
        return result;
    }
    
    @Override
    public String toString() {
        return "(ShortCode=" + mShortCode + 
               ", Listenter=" + mListenter + 
               ", mForwardOnly=" + mForwardOnly + 
               ", mForwardExcept=" + mForwardExcept + ")";
    }
}
