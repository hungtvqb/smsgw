/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mt;

/**
 *
 * @author HoaND6
 */
public class VirtualShortCode {
    private String mRealShortCode, mAlias, mVirtualShortCode;

    public VirtualShortCode(String mRealShortCode, String mAlias, String virtualShortCode) {
        this.mRealShortCode = mRealShortCode;
        this.mAlias = mAlias;
        mVirtualShortCode = virtualShortCode;
    }

    public String getRealShortCode() {
        return mRealShortCode;
    }

    public String getAlias() {
        return mAlias;
    }

    public String getVirtualShortCode() {
        return mVirtualShortCode;
    }

    
    @Override
    public String toString() {
        return "(Virtual: " + getVirtualShortCode() + "; Real ShortCode: " + mRealShortCode + "; Alias: " + mAlias + ")";
    }
}
