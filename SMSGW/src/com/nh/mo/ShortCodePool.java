/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nh.mo;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import com.nh.main.MyLog;
import com.nh.main.SMSGW;
import com.nh.database.OracleConnection;

/**
 *
 * @author thuzpt
 */
public class ShortCodePool {
    private static ShortCodePool mMe;
    private Map<String, WSEndPoint> mEndPointPool;
    private Map<String, HashSet<MOForward> > mPool;

    private ShortCodePool() {
    }

    public static ShortCodePool getInstance() {
        if (mMe == null) {
            mMe = new ShortCodePool();
            mMe.loadConfig();
        }

        return mMe;
    }

    public void loadConfig() {
        OracleConnection conn = OracleConnection.getInstance();
        if (conn != null && conn.isConnected()) {
            mEndPointPool = conn.getAllWebServices(SMSGW.getInstance().getWebserviceTable());
            mPool = conn.getAllMOListener(SMSGW.getInstance().getMOListenerTable());
        } else {
            MyLog.Debug("DBThread is not CONNECTED to DB, skip load");
        }
    }
    
//    public String loadConfig() {
//        StringBuilder result = new StringBuilder();
//        try {
//            FileInputStream fis = new FileInputStream(mConfFile);
//            BufferedReader s = new BufferedReader(new InputStreamReader(fis));
//            String line = s.readLine();
//            int count = 1;
//
//            while (line != null) {
//                if (line != null) {
//                    line = line.trim();
//                    if (line.length() > 0 && line.charAt(0) != '#') {
//                        StringTokenizer st = new StringTokenizer(line, " ");
//                        if (st.countTokens() >= 4) {
//                            String shortCode = st.nextToken();
//                            boolean convertToUnicode = true;
//                            try { convertToUnicode = Boolean.parseBoolean(st.nextToken()); }
//                            catch (Exception ex) {}
//                            int total = st.countTokens() / 4;
//                            WSEndPoint address[] = new WSEndPoint[total];
//                            
//                            TextSecurity sec = TextSecurity.getInstance();
//                            for (int i=0; i<total; i++) {
//                                String user = st.nextToken();
//                                String pass = sec.Decrypt(st.nextToken());
//                                String add = st.nextToken();
//                                String soapAction = st.nextToken();
//                                
//                                address[i] = new WSEndPoint(user, pass, add, soapAction);
//                            }
//                            
//                            ShortCodeListener u = new ShortCodeListener(shortCode, convertToUnicode, address);
//                            mPool.put(shortCode, u);
//                            
//                            result.append("Put: ").append(u.toString()).append("\n");
//                            MyLog.Infor("Put: " + u.toString());
//                        }
//                        else {
//                            if (st.countTokens() != 0) {// empty line
//                                String ll = "Error on line " + count + "( " + mConfFile + "): " + line;
//                                MyLog.Error(ll);
//                                result.append(ll).append("\n");
//                            }
//                        }
//                    }
//                }
//                line = s.readLine();
//                count ++;
//            }
//
//            s.close();
//        } catch (Exception ex) {
//            
//        }
//
//        return result.toString();
//    }

    public HashSet<WSEndPoint> match(String shortCode, String mo) {
        if (mPool != null && !mPool.isEmpty()) {
            HashSet<WSEndPoint> result = new HashSet<WSEndPoint>();
            HashSet<MOForward> wsName = mPool.get(shortCode);
            
            if (wsName != null && !wsName.isEmpty()) {
                Iterator<MOForward> e = wsName.iterator();
                while (e.hasNext()) {
                    MOForward ws = e.next();
                    if (ws.matched(mo)) {
                        WSEndPoint point = mEndPointPool.get(ws.getListenter());
                        if (point != null) {
                            result.add(point);
                        } 
                    }
                }
            }
            
            if (result == null || result.isEmpty()) {
                MyLog.Debug(shortCode + " doesn't matched with any shortcode, try to match as long code.");
                result = matchLongCode(shortCode, mo);
            }
            
            MyLog.Debug("Match ShortCode for (" + shortCode + "; " + mo + ") -> " + result);
            return result;
        } else {
            return null;
        }
    }
    
    /**
     * @author haind25
     * @since 28/10/2015
     * thay Hashtable bang Map doi tuong mPool
     */
    public HashSet<WSEndPoint> matchLongCode(String shortCode, String mo) {
        HashSet<WSEndPoint> result = new HashSet<WSEndPoint>();
        
        if (mPool != null && !mPool.isEmpty()) {
            for (Map.Entry<String, HashSet<MOForward>> entry : mPool.entrySet()) {
            String code = entry.getKey();
                if (shortCode.startsWith(code)) {
                     HashSet<MOForward> wsName = entry.getValue();
            
                    if (wsName != null && !wsName.isEmpty()) {
                        Iterator<MOForward> ite = wsName.iterator();
                        while (ite.hasNext()) {
                            MOForward ws = ite.next();
                            
                            if (ws.matched(mo)) {
                                WSEndPoint point = mEndPointPool.get(ws.getListenter());
                                if (point != null) {
                                    result.add(point);
                                } 
                            }
                        }
                    }
                }
            }
        } 
        
        MyLog.Debug("Match long code for (" + shortCode + "; " + mo + ") -> " + result);
        return result;
    }
    
    /**
     * 
     * @author haind25
     * @since 28/10/2015
     * thay Hashtable bang Map doi tuong mPool
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Short Code Listener: \n");
        for (Map.Entry<String, HashSet<MOForward> > entry : mPool.entrySet()) {
            String shortCode = entry.getKey();
            HashSet<MOForward> ws = entry.getValue();
            result.append(shortCode).append(" - ").append(ws).append("\n");
        }
        

        result.append("Webservice Definition: \n\n");
        
        for (Map.Entry<String, WSEndPoint> entry : mEndPointPool.entrySet()) {
            String key = entry.getKey();
            WSEndPoint endpoint = entry.getValue();
            result.append(key).append(" - ").append(endpoint).append("\n\n");
        }

        return result.toString();
    }
}
