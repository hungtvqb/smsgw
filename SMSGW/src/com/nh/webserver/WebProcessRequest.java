/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nh.webserver;

//import com.viettel.database.oracle.OracleConnection;
//import com.viettel.hlr.HLR;
//import com.viettel.hlr.HLR;
import com.nh.main.MyLog;
import com.nh.main.SMSGW;
import com.nh.mo.ShortCodePool;
import com.nh.mt.MTQueue;
import com.nh.mt.MTSMS;
import com.nh.mt.ProcessSendSMS;
import com.nh.mt.ShortCodeMatcher;
import com.nh.mt.VirtualShortCode;
import com.nh.ultil.CountryCode;
import com.wbxml.WbxmlLibs;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringEscapeUtils;
import org.smpp.util.CharsetDetector;
import org.w3c.dom.Document;
import sun.misc.BASE64Encoder;

/**
 *
 * @author hoand
 */
@SuppressWarnings("StaticNonFinalUsedInInitialization")
public class WebProcessRequest implements Runnable {

    public static final String PARAMS_SMS_TEXT = "TEXT";
    public static final String PARAMS_SMS_FLASH = "FLASH";
    public static final String PARAMS_SMS_BINARY = "BINARY";
    public static final String PARAMS_SMS_HEX_TEXT = "HEX_TEXT";
    public static final String PARAMS_SMS_HEX_FLASH = "HEX_FLASH";

    private static final String GET_STATISTICS = "/statistics";
    private static final String RELOAD_CFG = "/reloadconfig";
    private static final String GET_WS_WSDL = "/smsws?wsdl";

    private static final int RESULT_SMS_OK = 0;
    private static final int RESULT_SMS_FAILED = 1;
    private static final int RESULT_SMS_INVALID_PARAMS = 2;

    protected RequestQueue mQueue;
    protected boolean isRunning;
    protected int mId;
    protected long mRecievedRequestTime;
    private Socket mySocket;
    private String mPrefix;
    private MTQueue mSMSQueue;

    private String host; //dia chi server

//    private OracleConnection mDBConn;
    public WebProcessRequest(int id, RequestQueue q) {
        mQueue = q;
        isRunning = true;
        mId = id;

        mRecievedRequestTime = System.currentTimeMillis();
        mSMSQueue = MTQueue.getInstance();

//        mDBConn = new OracleConnection(10 + mId, WSSMSGW.getInstance().getOracleConf());
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void start() {
        Thread t = new Thread(this);
        t.start();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                mySocket = mQueue.getRequest();
                if (mySocket != null) {
                    MyLog.Debug(getThreadName() + "Got connection from "
                            + mySocket.getInetAddress().getHostAddress() + " (" + mQueue.getSize() + " left)");
                    process();
                }

                Thread.sleep(1);
            } catch (Exception e) {
                MyLog.Error(getThreadName() + "Error: " + e.getMessage());
            }
        }
    }

    private String getThreadName() {
        return "THREAD " + mId + ": ";
    }
    /**
     * GMT date formatter
     */
    private static java.text.SimpleDateFormat gmtFrmt;

    static {
        gmtFrmt = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public void process() {
        String src = mySocket.getInetAddress().getHostAddress();
        try {
            mRecievedRequestTime = System.currentTimeMillis();

            mySocket.setSoTimeout(5000);
            InputStream is = mySocket.getInputStream();
            if (is == null) {
                return;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            String s = in.readLine();
            if (s == null) {
                MyLog.Infor(getThreadName() + "Send BAD REQUEST (Syntax Error) response to client (" + (System.currentTimeMillis() - mRecievedRequestTime) + " ms)");
                sendError(HttpResponse.HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
                return;
            }
            // Read the request line
            StringTokenizer st = new StringTokenizer(s);
            if (!st.hasMoreTokens()) {
                MyLog.Infor(getThreadName() + ": Send BAD REQUEST (Syntax Error) response to client (" + (System.currentTimeMillis() - mRecievedRequestTime) + " ms)");
                sendError(HttpResponse.HTTP_BADREQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
            }

            String method = st.nextToken();
            if (!st.hasMoreTokens()) {
                MyLog.Infor(getThreadName() + ": Send BAD REQUEST (URI) response to client (" + (System.currentTimeMillis() - mRecievedRequestTime) + " ms)");
                sendError(HttpResponse.HTTP_BADREQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
            }
            String uri = decodePercent(st.nextToken());
            String rawUrl = uri;
            // Decode parameters from the URI
            Properties parms = new Properties();

            /**
             * ********************************** PROCESS TO CLEINT *****************************
             */
            // If there's another token, it's protocol version,
            // followed by HTTP headers. Ignore version but parse headers.
            // NOTE: this now forces header names uppercase since they are
            // case insensitive and vary by client.
            Properties header = new Properties();
            if (st.hasMoreTokens()) {
                String line = in.readLine();
                while (line.trim().length() > 0) {
                    int p = line.indexOf(':');
                    header.put(line.substring(0, p).trim().toLowerCase(), line.substring(p + 1).trim());
                    line = in.readLine();
//                    System.out.println(line + "\n");
                }
            }

            // If the method is POST, there may be parameters
            // in data section, too, read it:
            String postLine = "";
            if (method.equalsIgnoreCase("POST")) {
                long tmp = System.currentTimeMillis();
                MyLog.Debug(getThreadName() + ": read time " + (tmp - mRecievedRequestTime) + " (ms)");

                long size = 0x7FFFFFFFFFFFFFFFl;
                String contentLength = header.getProperty("content-length");
                if (contentLength != null) {
                    try {
                        size = Integer.parseInt(contentLength);
                    } catch (NumberFormatException ex) {
                        //ignore this exception
                        MyLog.Error(ex);
                    }
                }
                char buf[] = new char[512];
                int read = in.read(buf);
                while (read >= 0 && size > 0 && !postLine.endsWith("\r\n")) {
                    size -= read;
                    postLine += String.valueOf(buf, 0, read);
                    if (postLine.trim().endsWith("Envelope")) {
                        break;
                    }
                    if (size > 0) {
                        read = in.read(buf);
                    }
                }
                postLine = postLine.trim();
//                printWrite(postLine, LOG_DEBUG, logName);
                decodeParms(postLine, parms);
//                    MyLog.Debug(getThreadName() + ": decode time " + (System.currentTimeMillis() - tmp) + " (ms)");
//                    // send response
//                    sendWSResponse(mMSISDN, mDevName, mConfigType);
//                }
            }

            if (method.equals("GET")) {
                if (rawUrl.toLowerCase().indexOf(GET_STATISTICS.toLowerCase()) >= 0) {
                    sendStatistics();
                } else if (rawUrl.toLowerCase().indexOf(RELOAD_CFG.toLowerCase()) >= 0) {
                    reloadConfig();
                } else if (rawUrl.toLowerCase().indexOf(GET_WS_WSDL.toLowerCase()) >= 0) {
                    sendWSDLFile();
                } else {
                    sendResponse(HttpResponse.HTTP_NOTFOUND, HttpResponse.MIME_HTML, null, new ByteArrayInputStream("NOT FOUND".getBytes()));
                }
            }
            in.close();
        } catch (Exception ioe) {
            MyLog.Error(getThreadName() + "Error (From " + src + "): " + ioe.getMessage());
            MyLog.Error(ioe);
            try {
                sendError(HttpResponse.HTTP_INTERNALERROR, "SERVER INTERNAL LOG_ERROR, logName: IOException: " + ioe.getMessage());
            } catch (Throwable t) { /*ignore this exception */

            }
        }
    }

    protected void sendRequestResultToClient(String result) {
        try {
            MyLog.Debug(getThreadName() + ": Send RESULT to client " + result + " (" + (System.currentTimeMillis() - mRecievedRequestTime) + " ms)");

            String resp;
            resp = "<?xml version=\"1.0\"?>"
                    + "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\" >"
                    + "<S:Body>"
                    + "<" + mPrefix + "smsRequestResponse xmlns=\"http://smsws/xsd\">"
                    + "<return>"
                    + result
                    + "</return>"
                    + "</" + mPrefix + "smsRequestResponse>"
                    + "</S:Body>"
                    + "</S:Envelope>";

//            }
            InputStream inp = new ByteArrayInputStream(resp.getBytes());
            HttpResponse response = new HttpResponse(HttpResponse.HTTP_OK, HttpResponse.MIME_XML, inp);
            response.addHeader("content-length", "" + inp.available());
            sendResponse(HttpResponse.HTTP_OK, response.mimeType, response.header, response.data);
        } catch (Exception e) {
            MyLog.Error(getThreadName() + ": ERROR while sending result to client: " + e.getMessage());
            MyLog.Error(e);
        }
    }

    protected void sendStatistics() {
        try {
//            String resp = mStatis.getStatistic();
            Date d = new Date(System.currentTimeMillis());
            String resp = d.toString() + "\n"
                    + SMSGW.getInstance() + "\n"
                    + ProcessSendSMS.getInstance().getStatistics() + "\n"
                    + ShortCodePool.getInstance() + "\n"
                    + UserPool.getInstance() + "\n"
                    + ShortCodeMatcher.getInstance();

            InputStream inp = new ByteArrayInputStream(resp.getBytes());
            HttpResponse response = new HttpResponse(HttpResponse.HTTP_OK, HttpResponse.MIME_PLAINTEXT, inp);
            response.addHeader("content-length", "" + inp.available());
            sendResponse(HttpResponse.HTTP_OK, response.mimeType, response.header, response.data);

            MyLog.Infor(getThreadName() + ": Send STATISTICS to client.");
        } catch (Exception e) {
            MyLog.Error(e);
        }
    }

    protected void reloadConfig() {
        MyLog.Infor(getThreadName() + "Reload configuration called");

        SMSGW.getInstance().loadConfig();
        ProcessSendSMS.getInstance().reloadSMSC();
        ShortCodePool.getInstance().loadConfig();
        UserPool.getInstance().loadConfig();
        ShortCodeMatcher.getInstance().loadConfig();
//        HLR.getInstance().reloadConfig();
        sendStatistics();
    }

    /**
     * Decodes the percent encoding scheme. <br/>
     * For example: "an+example%20string" -> "an example string"
     *
     * @param str <code>String</code> value will be decoded
     * @return value after decode
     * @throws InterruptedException when processing in String value, may be out
     * of index bound,...
     */
    private String decodePercent(
            String str) throws InterruptedException {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i
                    < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '+':
                        sb.append(' ');
                        break;

                    case '%':
                        sb.append((char) Integer.parseInt(str.substring(i + 1, i + 3), 16));
                        i += 2;
                        break;

                    default:

                        sb.append(c);
                        break;

                }
            }
            return new String(sb.toString().getBytes());
        } catch (Exception e) {
            sendError(HttpResponse.HTTP_BADREQUEST, "BAD REQUEST: Bad percent-encoding.");
            return null;
        }

    }

    private boolean checkUserPass(String username, String password, String msisdn, String shortCode, String alias, boolean isFlash) {
        UserPool pool = UserPool.getInstance();
        return pool.match(username, password, msisdn, shortCode, alias, isFlash);
    }

    private String PREFIX_REMOVE[] = new String[]{"xsd:", "xsi:", "ns1:", "ns0:"};

    private String replaceXML(String input) {
        mPrefix = "";
        String result = input;
        for (int i = 0; i < PREFIX_REMOVE.length; i++) {
            if (input.contains(PREFIX_REMOVE[i])) {
                result = input.replace(PREFIX_REMOVE[i], "");
                mPrefix = PREFIX_REMOVE[i];
                break;
            }
        }

        return result;
    }

    /**
     * Decodes parameters in percent-encoded URI-format ( e.g.
     * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
     * Properties.
     *
     * @param parms param string will be parse
     * @param p stored properties to store value
     * @throws InterruptedException when processing in String value, may be out
     * of index bound,...
     */
    private void decodeParms(String parms, Properties p)
            throws InterruptedException {
        if (parms == null) {
            return;
        }

//        MyLog.Debug(getThreadName() + "got: " + parms);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        String newParam = replaceXML(parms);

        MyLog.Debug(getThreadName() + "got: " + parms);
        if (mPrefix != null && !mPrefix.isEmpty()) {
            MyLog.Debug("Remove Prefix from XML: '" + mPrefix + "'");
        }

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            InputStream inp = new ByteArrayInputStream(newParam.getBytes());
            Document doc = db.parse(inp);
            doc.getDocumentElement().normalize();

            if (doc.getElementsByTagName("smsRequest").getLength() > 0) {
                // Call confidevice tag
                try {
                    String usernameTag = "username";
                    String passwordTag = "password";
                    String msisdnTag = "msisdn";
                    String contentTag = "content";
                    String shortCodeTag = "shortcode";
                    String aliasTag = "alias";
                    String paramsTag = "params";

                    String username = doc.getElementsByTagName(usernameTag).item(0).getFirstChild().getNodeValue();
                    String password = doc.getElementsByTagName(passwordTag).item(0).getFirstChild().getNodeValue();
                    String msisdn = doc.getElementsByTagName(msisdnTag).item(0).getFirstChild().getNodeValue();
                    String content = doc.getElementsByTagName(contentTag).item(0).getFirstChild().getNodeValue();
                    String shortCode = doc.getElementsByTagName(shortCodeTag).item(0).getFirstChild().getNodeValue();
                    String alias;
                    try {
                        alias = doc.getElementsByTagName(aliasTag).item(0).getFirstChild().getNodeValue();
                    } catch (Exception ex) {
                        alias = "";
                    }

//                    // Check virual shortcode for Invalid ShortCode
                    if ("".equals(alias) && shortCode != null && ProcessSendSMS.getInstance().isValidShortCode(shortCode)) {
                        ShortCodeMatcher matcher = ShortCodeMatcher.getInstance();
                        VirtualShortCode v = matcher.getMatched(shortCode);
                        if (v != null) {
                            MyLog.Debug("Short code " + shortCode + " matched & replaced by " + v);
                            shortCode = v.getRealShortCode();
                            alias = v.getAlias();
                        }
                    }

                    String params;
                    try {
                        params = doc.getElementsByTagName(paramsTag).item(0).getFirstChild().getNodeValue();
                    } catch (Exception ex) {
                        params = "";
                    }

                    int smsType = MTSMS.SMS_TYPE_TEXT;
                    if (params != null) {
                        if (params.trim().equalsIgnoreCase(PARAMS_SMS_FLASH)) {
                            smsType = MTSMS.SMS_TYPE_FLASH;
                        } else if (params.trim().equalsIgnoreCase(PARAMS_SMS_BINARY)) {
                            smsType = MTSMS.SMS_TYPE_BINARY;
                        } else if (params.trim().equalsIgnoreCase(PARAMS_SMS_HEX_TEXT)) {
                            smsType = MTSMS.SMS_TYPE_HEX_TEXT;
                        } else if (params.trim().equalsIgnoreCase(PARAMS_SMS_HEX_FLASH)) {
                            smsType = MTSMS.SMS_TYPE_HEX_FLASH;
                        } else {
                            smsType = MTSMS.SMS_TYPE_TEXT;
                        }
                    }

                    boolean textSMS = (smsType == MTSMS.SMS_TYPE_TEXT || smsType == MTSMS.SMS_TYPE_HEX_TEXT);
//                    int hlr = HLR.getInstance().getHLRNo(msisdn);
//                    if (hlr > 0 && content != null && !content.isEmpty() && 
//                            ProcessSendSMS.getInstance().isValidShortCode(shortCode) &&
//                                checkUserPass(username, password, msisdn, shortCode, alias, !textSMS )) {
                    if (content != null && !content.isEmpty()
                            && ProcessSendSMS.getInstance().isValidShortCode(shortCode)
                            && checkUserPass(username, password, msisdn, shortCode, alias, !textSMS)) {
//                        if (smsType == MTSMS.SMS_TYPE_TEXT) {
//                            content = convertContent(content, true);
//                            smsType = MTSMS.SMS_TYPE_HEX_TEXT;
//                        }

                        MTSMS mtsms = new MTSMS(shortCode, msisdn, content, alias, smsType, username);
                        
                        if (SMSGW.getInstance().getMtSoapUrl() != null 
                                && !SMSGW.getInstance().getMtSoapUrl().isEmpty()) {
                            sendMt(mtsms, SMSGW.getInstance().getMtSoapUrl());
                        } else {
                            mSMSQueue.add(mtsms);
                        }

                        sendRequestResultToClient(RESULT_SMS_OK + "");
                    } else {
                        sendRequestResultToClient(RESULT_SMS_INVALID_PARAMS + "");
                    }
                } catch (Exception ex) {
                    MyLog.Error(ex);
                    sendRequestResultToClient(RESULT_SMS_INVALID_PARAMS + "");
                }
            } else {
//                MyLog.Infor(getThreadName() + "Unknown Webservice");
                sendRequestResultToClient(RESULT_SMS_INVALID_PARAMS + "");
            }
        } catch (Exception ex) {
            MyLog.Error(ex);
            MyLog.Error(getThreadName() + ": Error decode params. " + ex.getMessage());
            sendRequestResultToClient(RESULT_SMS_INVALID_PARAMS + "|" + ex.getMessage());
        }
    }

    private String sendMt(MTSMS mtsms, String url) {
//        String url = "http://10.228.33.40:6635/vasp/Service.asmx";
        long start = System.currentTimeMillis();
        BASE64Encoder encoder = new BASE64Encoder();
        String orginContent = mtsms.getOriginalContent();
         String msisdn = CountryCode.getCountryCode() + 
                CountryCode.formatMobile(mtsms.getDestAddress());
         
        MyLog.Infor("send mt: " + msisdn + ", content" + orginContent );
        String content = encoder.encode(orginContent.getBytes());
        try {
            content = URLEncoder.encode(content, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
//        
//        String content = StringEscapeUtils.escapeXml(mtsms.getOriginalContent());
        
        String reqContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                " <soap:Header><AuthHeader xmlns=\"http://tempuri.org/\">" +
                "<Username>gwhaiti</Username>" +
                "<Password>gwhaiti</Password>" +
                "</AuthHeader>" +
                "</soap:Header>" +
                "<soap:Body>" +
                "<sendMT xmlns=\"http://tempuri.org/\">" +
                "<SessionId>0</SessionId>" +
                "<ServiceId>musicbg</ServiceId>" +
                "<Sender>#SHORTCODE</Sender>" +
                "<Receiver>#MSISDN</Receiver>" +
                "<ContentType>0</ContentType>" +
                "<Content>#CONTENT</Content>" +
                "<Status>1</Status>" +
                "</sendMT>" +
                "</soap:Body></soap:Envelope>";
       
        reqContent = reqContent.replace("#SHORTCODE", mtsms.getSourceAddress());
        reqContent = reqContent.replace("#MSISDN", msisdn);
        reqContent = reqContent.replace("#CONTENT", content);
        
        String ok = callHttpWS(url, reqContent, "", 1, 30000);
        MyLog.Infor("Try " + msisdn + " Call WS got Response: " 
                + ok + ", duration (ms):" + (System.currentTimeMillis() -start));
        return ok;
    }
    
    private  String callHttpWS(String wsURL, String inputXML, String soapAction, int retry, int timeout) {
        String result = "";
        String responseString;
        
        int i=0;
        for (i = 0; i<retry; i++) {
            MyLog.Infor("Try " + i + " Call WS (" + wsURL + ") got XML input: " + inputXML);
            
            try {
                URL url = new URL(wsURL);
                URLConnection connection = url.openConnection();
                HttpURLConnection httpConn = (HttpURLConnection)connection;
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                bout.write(inputXML.getBytes());

                byte[] b = bout.toByteArray();
                // Set the appropriate HTTP parameters.
                httpConn.setReadTimeout(timeout); // ms
                httpConn.setConnectTimeout(timeout); // ms
                httpConn.setRequestProperty("Content-Length", String.valueOf(b.length));
                httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                httpConn.setRequestProperty("SOAPAction", soapAction);

                httpConn.setRequestMethod("POST");
                httpConn.setDoOutput(true);
                httpConn.setDoInput(true);
                OutputStream out = httpConn.getOutputStream();
                //Write the content of the request to the outputstream of the HTTP Connection.
                out.write(b);
                out.close();
                //Ready with sending the request.
                //Read the response.
                InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
                BufferedReader in = new BufferedReader(isr);

                //Write the SOAP message response to a String.
                while ((responseString = in.readLine()) != null) {
                    result += responseString;
                }
            } catch (Exception ex) {
                MyLog.Error(ex);
                result = "";
            }
            
            if (result != null && !result.isEmpty()) {
                break;
            }
        }
        MyLog.Debug("Try " + i + " Call WS got Response: '" + result + "'");
        
        return result;
    }

    private String convertContent(String content, boolean useHex) {
        if (useHex) {
            String charSet = CharsetDetector.detectCharsetStr(content);
            if (charSet != null && !charSet.equals("ASCII")) {
                try {
                    return WbxmlLibs.byteArrToHexStr(content.getBytes(charSet));
                } catch (UnsupportedEncodingException ex) {
                    return WbxmlLibs.byteArrToHexStr(content.getBytes());
                }
            } else {
                return WbxmlLibs.byteArrToHexStr(content.getBytes());
            }
        } else {
            return content;
        }
    }

    /**
     * Returns an error message as a HTTP response and throws
     * InterruptedException to stop furhter request processing.
     *
     * @param status status of http processing
     * @param msg http message content
     * @throws InterruptedException exception will be throwed after sending
     */
    private void sendError(String status, String msg) throws InterruptedException {
        sendResponse(status, HttpResponse.MIME_XML, null, new ByteArrayInputStream(msg.getBytes()));
    }

    /**
     * Sends given response to the socket.
     *
     * @param status status of http processing
     * @param mime content type of response
     * @param header header values
     * @param data <code>InputStream</code> stored content of response
     */
    public void sendResponse(String status, String mime, Properties header, InputStream data) {
        try {
            if (status == null) {
                throw new Error("sendResponse(): Status can't be null.");
            }
            OutputStream out = mySocket.getOutputStream();
            PrintWriter pw = new PrintWriter(out);
            pw.print("HTTP/1.1 " + status + " \r\n");
            if (mime != null) {
                pw.print("Content-Type: " + mime + "\r\n");
            }

            if (header == null || header.getProperty("Date") == null) {
                pw.print("Date: " + gmtFrmt.format(new Date()) + "\r\n");
            }

            if (header != null) {
                Enumeration e = header.keys();
                while (e.hasMoreElements()) {
                    String key = (String) e.nextElement();
                    String value = header.getProperty(key);
                    pw.print(key + ": " + value + "\r\n");
                }

            }
            pw.print("\r\n");
            pw.flush();

            if (data != null) {
                byte[] buff = new byte[2048];
                StringBuilder sbuf = new StringBuilder();
                while (true) {
                    int read = data.read(buff, 0, 2048);
                    if (read <= 0) {
                        break;
                    }

                    int i = 0;
                    for (i = 0; i < buff.length; i++) {
                        if (buff[i] == 0) {
                            break;
                        }
                    }
                    if (i > 0) {
                        sbuf.append(new String(buff, 0, i));
                    }

                    out.write(buff, 0, read);
                }
            }

            out.flush();
//            Thread.sleep(100);
            out.close();
            if (data != null) {
                data.close();
            }

//            Thread.sleep(100);
            mySocket.close();
        } catch (Exception ioe) {
//             Couldn't write? No can do.
//            ioe.printStackTrace();
            try {
                mySocket.close();
            } catch (Exception ex) {
                MyLog.Error(getThreadName() + ": Error while close socket " + ex.getMessage());
            }
            MyLog.Error(getThreadName() + ": Error resposed to client: " + ioe.getMessage());
        }
    }

    protected void sendWSDLFile() {
        String address = null;
        if (getHost() != null) {
            address = getHost() + ":" + mySocket.getLocalPort();
        } else {
            address = mySocket.getLocalAddress().getHostAddress() + ":" + mySocket.getLocalPort();
            String proxy = SMSGW.getInstance().getProxyStand();
            if (proxy != null && !proxy.isEmpty()) {
                address = proxy;
            }
        }

        String text = getWSDL(address);

        HttpResponse response = new HttpResponse(HttpResponse.HTTP_OK, HttpResponse.MIME_XML, new ByteArrayInputStream(text.getBytes()));
        response.addHeader("content-length", "" + text.getBytes().length);
        sendResponse(HttpResponse.HTTP_OK, response.mimeType, response.header, response.data);

        MyLog.Debug(getThreadName() + ": Send WSDL to client. (" + (System.currentTimeMillis() - mRecievedRequestTime) + " ms)");
    }

    private String getWSDL(String serverAddr) {
        String text
                = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" "
                + "xmlns:axis2=\"http://smsws/\" "
                + "xmlns:ns1=\"http://org.apache.axis2/xsd\" "
                + "xmlns:wsaw=\"http://www.w3.org/2006/05/addressing/wsdl\" "
                + "xmlns:http=\"http://schemas.xmlsoap.org/wsdl/http/\" "
                + "xmlns:ns0=\"http://smsws/xsd\" "
                + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" "
                + "xmlns:mime=\"http://schemas.xmlsoap.org/wsdl/mime/\" "
                + "xmlns:soap12=\"http://schemas.xmlsoap.org/wsdl/soap12/\" "
                + "targetNamespace=\"http://smsws/\">"
                + "<wsdl:documentation>smsws</wsdl:documentation>"
                + "<wsdl:types>"
                + "<xs:schema xmlns:ns=\"http://smsws/xsd\" attributeFormDefault=\"qualified\" elementFormDefault=\"qualified\" targetNamespace=\"http://smsws/xsd\">"
                + //            "<xs:element name=\"getIMSI\">" +
                //                "<xs:complexType>" +
                //                    "<xs:sequence>" +
                //                        "<xs:element minOccurs=\"0\" name=\"username\" nillable=\"true\" type=\"xs:string\"/>" +
                //                        "<xs:element minOccurs=\"0\" name=\"password\" nillable=\"true\" type=\"xs:string\"/>" +
                //                        "<xs:element minOccurs=\"0\" name=\"msisdn\" nillable=\"true\" type=\"xs:string\"/>" +
                //                    "</xs:sequence>" +
                //                "</xs:complexType>" +
                //            "</xs:element>" +
                //            "<xs:element name=\"getIMSIResponse\">" +
                //                "<xs:complexType>" +
                //                    "<xs:sequence>" +
                //                        "<xs:element minOccurs=\"0\" name=\"return\" nillable=\"true\" type=\"ns0:ResultResponse\"/>" +
                //                    "</xs:sequence>" +
                //                "</xs:complexType>" +
                //            "</xs:element>" +
                //            "<xs:complexType name=\"ResultResponse\">" +
                //                "<xs:sequence>" +
                //                    "<xs:element minOccurs=\"0\" name=\"code\" type=\"xs:int\"/>" +
                //                    "<xs:element minOccurs=\"0\" name=\"message\" nillable=\"true\" type=\"xs:string\"/>" +
                //                "</xs:sequence>" +
                //            "</xs:complexType>" +
                "<xs:element name=\"smsRequest\">"
                + "<xs:complexType>"
                + "<xs:sequence>"
                + "<xs:element minOccurs=\"0\" name=\"username\" nillable=\"true\" type=\"xs:string\"/>"
                + "<xs:element minOccurs=\"0\" name=\"password\" nillable=\"true\" type=\"xs:string\"/>"
                + "<xs:element minOccurs=\"0\" name=\"msisdn\" nillable=\"true\" type=\"xs:string\"/>"
                + "<xs:element minOccurs=\"0\" name=\"content\" nillable=\"true\" type=\"xs:string\"/>"
                + "<xs:element minOccurs=\"0\" name=\"shortcode\" nillable=\"true\" type=\"xs:string\"/>"
                + "<xs:element minOccurs=\"0\" name=\"alias\" nillable=\"true\" type=\"xs:string\"/>"
                + "<xs:element minOccurs=\"0\" name=\"params\" nillable=\"true\" type=\"xs:string\"/>"
                + "</xs:sequence>"
                + "</xs:complexType>"
                + "</xs:element>"
                + "<xs:element name=\"smsRequestResponse\">"
                + "<xs:complexType>"
                + "<xs:sequence>"
                + "<xs:element minOccurs=\"0\" name=\"return\" type=\"xs:string\"/>"
                + "</xs:sequence>"
                + "</xs:complexType>"
                + "</xs:element>"
                + "</xs:schema>"
                + "</wsdl:types>"
                + //    "<wsdl:message name=\"getIMSIRequest\">" +
                //        "<wsdl:part name=\"parameters\" element=\"ns0:getIMSI\"/>" +
                //    "</wsdl:message>" +
                //    "<wsdl:message name=\"getIMSIResponse\">" +
                //        "<wsdl:part name=\"parameters\" element=\"ns0:getIMSIResponse\"/>" +
                //    "</wsdl:message>" +
                "<wsdl:message name=\"smsRequestRequest\">"
                + "<wsdl:part name=\"parameters\" element=\"ns0:smsRequest\"/>"
                + "</wsdl:message>"
                + "<wsdl:message name=\"smsRequestResponse\">"
                + "<wsdl:part name=\"parameters\" element=\"ns0:smsRequestResponse\"/>"
                + "</wsdl:message>"
                + "<wsdl:portType name=\"smswsPortType\">"
                + //        "<wsdl:operation name=\"getIMSI\">" +
                //            "<wsdl:input message=\"axis2:getIMSIRequest\" wsaw:Action=\"urn:getIMSI\"/>" +
                //            "<wsdl:output message=\"axis2:getIMSIResponse\" wsaw:Action=\"urn:getIMSIResponse\"/>" +
                //        "</wsdl:operation>" +
                "<wsdl:operation name=\"smsRequest\">"
                + "<wsdl:input message=\"axis2:smsRequestRequest\" wsaw:Action=\"urn:smsRequest\"/>"
                + "<wsdl:output message=\"axis2:smsRequestResponse\" wsaw:Action=\"urn:smsRequestResponse\"/>"
                + "</wsdl:operation>"
                + "</wsdl:portType>"
                + "<wsdl:binding name=\"smswsSOAP11Binding\" type=\"axis2:smswsPortType\">"
                + "<soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\" style=\"document\"/>"
                + //        "<wsdl:operation name=\"getIMSI\">" +
                //            "<soap:operation soapAction=\"urn:getIMSI\" style=\"document\"/>" +
                //            "<wsdl:input>" +
                //                "<soap:body use=\"literal\"/>" +
                //            "</wsdl:input>" +
                //            "<wsdl:output>" +
                //                "<soap:body use=\"literal\"/>" +
                //            "</wsdl:output>" +
                //        "</wsdl:operation>" +
                "<wsdl:operation name=\"smsRequest\">"
                + "<soap:operation soapAction=\"urn:smsRequest\" style=\"document\"/>"
                + "<wsdl:input>"
                + "<soap:body use=\"literal\"/>"
                + "</wsdl:input>"
                + "<wsdl:output>"
                + "<soap:body use=\"literal\"/>"
                + "</wsdl:output>"
                + "</wsdl:operation>"
                + "</wsdl:binding>"
                + "<wsdl:service name=\"smsws\">"
                + "<wsdl:port name=\"smswsSOAP11port_http\" binding=\"axis2:smswsSOAP11Binding\">"
                + "<soap:address location=\"http://" + serverAddr + "/smsws\"/>"
                + "</wsdl:port>"
                + "</wsdl:service>"
                + "</wsdl:definitions>";
        return text;
    }
}
