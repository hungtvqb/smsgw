/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.database;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import com.nh.main.MyLog;

/**
 *
 * @author thuzpt
 */
public class BlackListTableCache {
    private static BlackListTableCache mMe;
    private Hashtable<String, HashSet<String> > mPool;

    private BlackListTableCache() {
        mPool = new Hashtable<String, HashSet<String> >();
    }

    public static BlackListTableCache getInstance() {
        if (mMe == null) {
            mMe = new BlackListTableCache();
        }

        return mMe;
    }

    public void addTable(String table) {
        if (table == null) {
            return;
        }
        
        String tbl = table.toUpperCase().trim();
        if (mPool.contains(tbl)) {
            MyLog.Debug("Table " + table + " already in cache");
            return;
        } 
        // Load Here
        OracleConnection conn = OracleConnection.getInstance();
        HashSet<String> cache = conn.getBlackListTable(tbl);
        MyLog.Debug("Add Blacklist '" + tbl + "' to cache");
        mPool.put(tbl, cache);
    }

    public void clear() {
        mPool.clear();
    }
    
    public HashSet<String> getTable(String table) {
        return mPool.get(table);
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Blacklist Table: \n");
        Enumeration<String> e = mPool.keys();
        
        while (e.hasMoreElements()) {
            String table = e.nextElement();
            HashSet<String> cache = mPool.get(table);
            result.append(table).append("; Size: ").append(cache.size()).append("\n");
        }
        
        return result.toString();
    }
}
