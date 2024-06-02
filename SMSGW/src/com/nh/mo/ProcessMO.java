/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.mo;

import com.wbxml.WbxmlLibs;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import com.nh.main.MyLog;
import org.apache.commons.lang3.StringEscapeUtils;
import org.smpp.util.CharsetDetector;
import org.w3c.dom.Document;

/**
 *
 * @author thuzpt
 */
public class ProcessMO implements Runnable {
    private boolean mIsRunning;
    private MOQueue mQueue;
    private long mLastAction;

    public ProcessMO() {
//        mMngSender = new Hashtable<String, SMSSenderPool>();
        mIsRunning = false;
        mQueue = MOQueue.getInstance();
    }

    public void start() {
        if (!mIsRunning) {
            mLastAction = System.currentTimeMillis();
            mIsRunning = true;
            Thread t = new Thread(this);
            t.start();
        }
    }
    
    public void stop() {
    }
    
     /**
      * Do send SMS to User
      */
    public void processSMS() {
        MOSMS sms = mQueue.getNextTask();
        if (sms != null) {
            MyLog.Debug("Got MO Task: " + sms +  " (" + mQueue.getSize() + " left)");
            sendMOToWS(sms);
            mLastAction = System.currentTimeMillis();
        } else {
            try { 
                Thread.sleep(100);
            } catch (Exception ex) {
                MyLog.Error(ex);
            }
        }
    }

    private void printLog() {
        if (System.currentTimeMillis() - mLastAction > 30000) { 
            // 30 sec
            MyLog.Debug("Queue is empty ...");
            mLastAction = System.currentTimeMillis();
        }
    }
    
    private String callWS(String wsURL, String inputXML, String soapAction) {
        String result = "";
        String responseString;
        
        MyLog.Debug("CallWS got XML Input: " + inputXML);
        try {
            URL url = new URL(wsURL);
            URLConnection connection = url.openConnection();
            HttpURLConnection httpConn = (HttpURLConnection)connection;
            httpConn.setReadTimeout(3000);
            httpConn.setConnectTimeout(3000);
            
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            bout.write(inputXML.getBytes());
            
            byte[] b = bout.toByteArray();
            // Set the appropriate HTTP parameters.
            httpConn.setRequestProperty("Content-Length", String.valueOf(b.length));
            httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            httpConn.setRequestProperty("SOAPAction", soapAction);
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            OutputStream out = httpConn.getOutputStream();
            //Write the content of the request to the outputstream of the HTTP Connection.
            out.write(b);
            //Ready with sending the request.
//            Thread.sleep(100);
            //Read the response.
            InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
            BufferedReader in = new BufferedReader(isr);

            //Write the SOAP message response to a String.
            while ((responseString = in.readLine()) != null) {
                result += responseString;
                MyLog.Debug("Result: " + result);
            }
            
            in.close();
            out.close();
        } catch (IOException ex) {
            MyLog.Error(ex);
            result = "";
        }
        
        MyLog.Debug("Send SMS: got Response: '" + result + "'");
        return result;
    }
       
    private String parseXML(MOSMS sms) {
        WSEndPoint endPoint = sms.getWSAddress();
        String result = endPoint.getRawXml();
        
        result = result.replace("#USERNAME", endPoint.getUsername());
        result = result.replace("#PASSWORD", endPoint.getPassword());
        result = result.replace("#MSISDN", sms.getSource());
        result = result.replace("#SHORT_CODE", sms.getDest());
        String content = sms.getContent();
        if (endPoint.isUseHexToSend()) {
            String charset = CharsetDetector.detectCharsetStr(content);
            if (charset.equalsIgnoreCase("UTF-16")) {
                try {
                    content = WbxmlLibs.byteArrToHexStr(content.getBytes("UTF-16"));
                } catch (Exception ex) {
                    content = WbxmlLibs.byteArrToHexStr(content.getBytes());
                }
            } else {
                content = WbxmlLibs.byteArrToHexStr(content.getBytes());
            }
        } else {
            content = StringEscapeUtils.escapeXml(content);
        }
        result = result.replace("#CONTENT", content);
        
        return result;
    }
    
    public void sendMOToWS(MOSMS sms) {
        MyLog.Debug("Gonna send MOSMS: " + sms);
        long start = System.currentTimeMillis();
        WSEndPoint endPoint = sms.getWSAddress();
        String wsURL = endPoint.getAddress();
        String soapAction = endPoint.getSoapAction();
        int result = -1;
//        
////        String newContent = sms.getContent().replace("\"", "&quot;");
//        String newContent = StringEscapeUtils.escapeXml(sms.getContent());
//        MyLog.Debug("New Content: " + newContent);
       
        String xmlInput = parseXML(sms);
//                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsd=\"http://mtws/xsd\">" +
//                "<soapenv:Header/>" + 
//                "<soapenv:Body>" + 
//                    "<xsd:moRequest xmlns=\"http://mtws/xsd\">" + 
//                        "<username>" + sms.getWSAddress().getUsername() + "</username>" + 
//                        "<password>" + sms.getWSAddress().getPassword() + "</password>" + 
//                        "<source>" + sms.getSource() + "</source>" + 
//                        "<dest>" + sms.getDest() + "</dest>" + 
//                        "<content>" + newContent + "</content>" + 
//                    "</xsd:moRequest>" + 
//                "</soapenv:Body>" + 
//                "</soapenv:Envelope>";
        try {
            String xmlOut = callWS(wsURL, xmlInput, soapAction);
            if("".equals(xmlOut)) {
                result = -1;
                MyLog.Debug("ERROR: Send MO SMS timeout ");
            } else {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                //parse using builder to get DOM representation of the XML file
                InputStream inp = new ByteArrayInputStream(xmlOut.getBytes());
                Document doc = db.parse(inp);

    //            int result = Integer.parseInt(doc.getElementsByTagName("return").item(0).getTextContent());
                result = Integer.parseInt(doc.getElementsByTagName(endPoint.getReturnTag()).item(0).getTextContent());
            }
        } catch (Exception e) {
            MyLog.Error(e);
        }
        
        MyLog.Debug("Send MO SMS '" + sms + "' -> " + result + 
                    " (" + (System.currentTimeMillis() - start) + " ms)");
        
    }
    
    @Override
    public void run() {
        while (mIsRunning) {
            try {
                processSMS();
                printLog();
                Thread.sleep(1);
            } catch (Exception ex) {
                MyLog.Error(ex);
            }
        }
    }
}
