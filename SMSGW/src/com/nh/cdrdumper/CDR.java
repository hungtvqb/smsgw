/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.cdrdumper;

import java.util.Enumeration;
import java.util.Vector;

/**
 *
 * @author thuzpt
 */
public class CDR {
    private Vector<String> mField;
    private String mSpread;

    public CDR() {
        mField = new Vector<String>();
        mSpread = "|";
    }

    public String getSpread() {
        return mSpread;
    }

    public void setSpread(String mSpread) {
        this.mSpread = mSpread;
    }
    
    public void addField(String f) {
        mField.add(f);
    }
    
    @Override
    public String toString() {
        Enumeration<String> e = mField.elements();
        StringBuilder str = new StringBuilder();
        while (e.hasMoreElements()) {
            String f = e.nextElement();
            str.append(f).append(getSpread());
        }
        
        str.delete(str.length() - getSpread().length(), str.length());
        return str.toString();
    }
}
