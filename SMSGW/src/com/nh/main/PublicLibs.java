/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.main;

import com.nh.ultil.CountryCode;
import java.sql.Date;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 *
 * @author thuzpt
 */
public class PublicLibs {
    public static String nomalizeMSISDN(String msisdn) {
//        if (msisdn.startsWith("0")) {
//            return msisdn.substring(1);
//        }
//        if (msisdn.startsWith("84")) {
//            return msisdn.substring(2);
//        }
//        if (msisdn.startsWith("+84")) {
//            return msisdn.substring(3);
//        }
//
//        return msisdn;
        return CountryCode.formatMobile(msisdn);
    }

    public static String internationalMSISDN(String msisd) {
        if (msisd.startsWith("00")) {
            return msisd;
        }
//        return "84" + nomalizeMSISDN(msisd);
        return CountryCode.getCountryCode() + nomalizeMSISDN(msisd);
    }
    
    public static String getTAC(String imei) {
        if (imei != null && imei.length() > 8) {
            return imei.substring(0, 8);
        } else {
            return "";
        }
    }

    public static String removeSpace(String inp) {
        StringBuilder tmp = new StringBuilder(inp);
        int idx = tmp.indexOf(" ");
        while (idx >=0) {
            tmp.deleteCharAt(idx);
            idx = tmp.indexOf(" ");
        }

        return tmp.toString();
    }

    public static String convertUnicodeToASCII(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
//        return pattern.matcher(temp).replaceAll("").;
        return pattern.matcher(temp).replaceAll("").replaceAll("Đ", "D").replace("đ", "d");
    }
    
    public static String parseValue(String input, Properties replace) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String result = input;

        Enumeration<Object> key = replace.keys();
        while (key.hasMoreElements()) {
            String k = (String) key.nextElement();
            String val = replace.getProperty(k);
            result = result.replace(k, val);
        }
        return result;
    }
    
    public static String getCurrentDateTime() {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss");
        return f.format(new Date(System.currentTimeMillis()));
    }
    
    public static String formatDateTime(long time) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMddHHmmss");
        return f.format(new Date(time));
    }
    
//    public static String convertUnicodeToASCII(String s) {
//        String s2 = s;
//        try {
//            String s1 = Normalizer.normalize(s, Normalizer.Form.NFKD);
//            String regex = Pattern.quote("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");
//            s2 = new String(s1.replaceAll(regex, "").getBytes("ascii"), "ascii");
//
//    //        System.out.println(s2);
//    //        System.out.println(s.length() == s2.length());
////            return s2;
//        } catch (Exception ex) {
//            MyLog.Error(ex);
////            return s;
//        }
//        
//        //-----------------
////        StringBuilder sb = new StringBuilder();
////        
////        for (int i = 0; i < s2.length(); i++) {
////            char ch = s2.charAt(i);
////            if (ch <= 0xFF) {
////                sb.append(ch);
////            }
////        }
//        return s2.replace("?", "");
//        
////        Regex regex = new Regex(@"\p{IsCombiningDiacriticalMarks}+");
////        string strFormD = s.Normalize(System.Text.NormalizationForm.FormD);
////        return regex.Replace(strFormD, String.Empty).Replace('\u0111', 'd').Replace('\u0110', 'D');
//    }
    
    public static HashSet<String> stringToHashSet(String input, boolean convertToUpper) {
        HashSet<String> result = null;
        
        if (input != null && !input.trim().isEmpty()) {
            String arr[] = input.trim().split(",");
            result = new HashSet<String>();
            Collections.synchronizedSet(result);
            if (arr != null && arr.length > 0) {
                for (int i=0; i<arr.length; i++) {
                    if (arr[i] != null && !arr[i].trim().isEmpty()) {
                        if (convertToUpper) {
                            result.add(arr[i].trim().toUpperCase());
                        } else {
                            result.add(arr[i].trim());
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    public static String[] stringToArray(String input, String delim) {
        String result[] = input.split(delim);
        for (int i=0; i<result.length; i++) {
            if (result[i] != null) {
                result[i] = result[i].trim();
                MyLog.Debug(result[i]);
            }
        }

        return result;            
    }
}
