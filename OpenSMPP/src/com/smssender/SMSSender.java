/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

/*
 * Created on Feb 26, 2010
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */

import org.smpp.*;
import org.smpp.pdu.*;
import org.smpp.util.ByteBuffer;
import org.smpp.util.CharsetDetector;
import com.smssender.datatype.BinarySMS;
import com.smssender.datatype.SMSPending;
import com.wbxml.Wbxml10;
import com.wbxml.Wbxml13;
import com.wbxml.WbxmlLibs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.Random;
import java.util.ResourceBundle;

/**
 * @author Avelino Benavides
 *
 * Proof of concept for sending SMS Messages and OTA Bookmarks
 * @see main
 */
public class SMSSender implements ServerPDUEventListener, Runnable {

    public static final byte SMS_NO_UDH = (byte) 0x00;
    public static final byte SMS_UDH = (byte) 0x40;

    public static final byte ENCODE_TEXT = (byte) 0x00;
    public static final byte ENCODE_DEFAULT = (byte) 0xF1;
    public static final byte ENCODE_TEXT_DEFAULT = (byte) 0x00;
    public static final byte ENCODE_TEXT_UNICODE = (byte) 0x08;
    public static final byte ENCODE_TEXT_FLASH_SMS = (byte) 0x10;
    public static final byte ENCODE_BIN = (byte) 0xF5;

    private int mMaxTPS;
//    private long mLastReceiveEnquire;
//    private long mLastReceiveEnquireResp;
//    private long mLastSendEnquire;

    private long mLastCommunicate;
    private long mLastEnquire;
    
//    public static long SMSC_TIME_OUT = 30000; // (ms) ~ 30 sec
    public static long MY_ENQUIRE_TIME_OUT = 180000; // (ms) ~ 180 sec
    public static long ENQUIRE_SEQUENCE = 5000; // (ms) ~ 5 sec

    private boolean mIsRunning;
    private DecodeUDHSms mDecodeSms = null;
    protected SMSListener mListener;
    protected SMSConfirmQueue mConfirm;
    private int mId;

    private long mLastCheckStatus;
    private long mLastResetTPS;
    private int mTPS;
    
    private ProcessSendLongSMS[] mLongSMSProcess; // Send long sms
    private int mSepradeBetweenLongSMS = 2000;
    
// <editor-fold defaultstate="collapsed" desc="Logica SMS interface">
    public static final String TRANSMITER = "t";
    public static final String RECEIVER = "r";
    public static final String TRANSCEIVER = "tr";
    
    private static final int UNCONNECTED = 0;
    private static final int CONNECTED = 1;
    private static final int DISCONNECTED = 2;
    private static final int SUCCESS = 3;
    private static final int ERROR = 4;
    private static final int CONNECTING = 5;

    private boolean asynchronous = false;
    private ServerPDUEventListener pduListener;
    private Session logicaAPIsession;
//    private String smsDestination;
    private String smsCAddress;
    private int smsCPort;
    private int status;
    private String mShortCode;
//	private String message;
//    private byte[] message;
    private String smsUser;
    private String smsPassword;

    protected String smsParServiceType = "0";
    protected int smsParSourceTON = 0;
    protected int smsParSourceNPI = 0;
    protected int smsParDestTON = 0;
    protected int smsParDestNPI = 0;
    protected int smsParReplaceFlag = 0;
    protected String smsParSchedDelivery = null;
    protected String smsParValidity = null; //=050101010101100R
    /**
     * Over The Air
     */
    protected String smsParSystemType = "OTA";
    /**
     * Message Transaction mode
     * ESM
     * Store and Forward - Asynchronous delivery with receipt = SMSC_Delivery_Receipt = 0 and 3
     * Datagram - Asynchronous delivery without receipt = DATAGRAM = 1
     * Transaction Mode - Syncronous delivery with receipt = FORWARD = 2
     * Binary - = 64
     */
//    protected int smsParESMClassText = 0;
    protected int smsParESMClassBinary = 64;
    protected int smsParProtocol = 0;
    protected int smsParPriorityFlag = 0;
    /**
     * Refer to the SMPP Spec 3.4
     */
    protected byte smsParRegisteredDelivery = (byte) 0x10; //16
    /**
     * Refer to the SMPP Spec 3.4
//	 */
    // HoaND: Remove global variale
//    protected byte smsParDataEncoding = (byte) 0xF1;
    /**
     * Refer to the SMPP Spec 3.4
     */
    protected byte smsParBinaryEnconding = (byte)0xF5;
    //-15(0x10) Document says 0xF5 (-11) for bin and 0 for text
    protected int smsParMsgId = 0;

    protected String connectionType = TRANSCEIVER;
    protected boolean connectionMode = asynchronous;
    protected TCPIPConnection logicaAPIconnection;
// </editor-fold>
    /**
     * Initilizates this object with the values and initialization file
     *  and tries to bind to the SMSC server, otherwise throws a RuntimeException
     * @param id
     * @param address IP
     * @param port 1-65535
     * @param user String
     * @param password String
     * @param shortCode
     * @param confFile filespec
     * @see readParams
     * @throws IOException If no connection to the server is possible
     * @throws RuntimeException If the server refuses the connection
     */
    public SMSSender(int id,
                     String address, int port,
                     String user, String password, String shortCode, 
                     String confFile) throws IOException {

        mId = id;
        smsCAddress = address;
        smsCPort = port;
        smsUser = user;
        smsPassword = password;
        status = UNCONNECTED;
        readParameters(confFile);
        mShortCode = shortCode;
        printParameters();
        mListener = null;
        mIsRunning = false;
        mLastCommunicate = mLastEnquire = System.currentTimeMillis();
//        smsParDataEncoding = ENCODE_TEXT_DEFAULT;
//        smsParESMClassText = SMS_NO_UDH;
    }
    
    public SMSSender(int id,
                     String address, int port,
                     String user, String password, String shortCode, 
                     Properties connectionPropeties) throws IOException {

        mId = id;
        smsCAddress = address;
        smsCPort = port;
        smsUser = user;
        smsPassword = password;
        status = UNCONNECTED;
        readParameters(connectionPropeties);
        mShortCode = shortCode;
        printParameters();
        mListener = null;
        mIsRunning = false;
        mLastCommunicate = mLastEnquire = System.currentTimeMillis();
//        smsParDataEncoding = ENCODE_TEXT_DEFAULT;
//        smsParESMClassText = SMS_NO_UDH;
    }

    public void setDecodeSMS(DecodeUDHSms d) {
        mDecodeSms = d;
    }

    public void setConfirmQueue(SMSConfirmQueue q) {
        mConfirm = q;
    }
    /**
     * Connects to the SMSC Server as the configured connectionMode and connectionType.
     *
     * @throws RuntimeException
     */
    public synchronized void init() {
        SMSLog.Debug(getLogName() + ": start INIT (Current Status: " + getStatusStr() +")");
        if (status == CONNECTED || status == CONNECTING) {
            return;
        }
        
        SMSLog.Infor(getLogName() + ": Try to bind to SMSC ....");
        
        BindResponse response;
        try {
            response = bind(connectionMode, connectionType);
        } catch (Exception ex) {
            SMSLog.Error(ex);
            SMSLog.Error(getLogName() + ": Exception while trying to bind (" + ex.getMessage() + ")");
            response = null;
            status = ERROR;
        }
        
        if (status != CONNECTED) {
            SMSLog.Error(getLogName() + ": CONNECT TO SMSC FAILED !!!");
            if (response != null) {
                SMSLog.Error(getLogName() + ": Error while attempting connection - code " +
                                "(" + response.getCommandStatus() + ")");
            }
            
            try { Thread.sleep(30000); } catch (Exception ex) {};
//           SMSLog.Error(response.getCommandStatus());
//           throw new RuntimeException("Not Connected");
        }
        else {
            mLastCommunicate = System.currentTimeMillis();
            SMSLog.Infor(getLogName() + ": CONNECT TO SMSC OK!!!");
            startSMSListener();
        }

        if (!mIsRunning) {
            // For sending long SMS
            mLongSMSProcess = new ProcessSendLongSMS[3];
            for (int i = 0; i< mLongSMSProcess.length; i++) {
                mLongSMSProcess[i] = new ProcessSendLongSMS(i, this);
                mLongSMSProcess[i].start();
            }
            
            mIsRunning = true;
            Thread t = new Thread(this);
            t.start();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Read Connection parameters">
    /**
     * Print the setup parameters of a LogicaSmscApiAccess object
     * @param test
     * @param stream
     */
    private void printParameters() {
            SMSLog.Infor("/******************SMSC Parameters******************/");
            SMSLog.Infor("connectionType     " + connectionType);
            SMSLog.Infor("asynchronous       " + connectionMode            );
            SMSLog.Infor("ServiceType        " + smsParServiceType       );
            SMSLog.Infor("SourceTON          " + smsParSourceTON         );
            SMSLog.Infor("SourceNPI          " + smsParSourceNPI         );
            SMSLog.Infor("DestTON            " + smsParDestTON           );
            SMSLog.Infor("DestNPI            " + smsParDestNPI           );
            SMSLog.Infor("ReplaceFlag        " + smsParReplaceFlag       );
            SMSLog.Infor("SchedDelivery      " + smsParSchedDelivery     );
            SMSLog.Infor("Validity           " + smsParValidity          );
            SMSLog.Infor("SystemType         " + smsParSystemType        );
//            SMSLog.Infor("ESMClassText       " + smsParESMClassText      );
            SMSLog.Infor("ESMClassBinary     " + smsParESMClassBinary    );
            SMSLog.Infor("Protocol           " + smsParProtocol          );
            SMSLog.Infor("PriorityFlag       " + smsParPriorityFlag      );
            SMSLog.Infor("RegisteredDelivery " + smsParRegisteredDelivery);
//            SMSLog.Infor("DataEncoding       " + smsParDataEncoding      );
            SMSLog.Infor("MsgId              " + smsParMsgId             );
            SMSLog.Infor("BinaryEnconding    " + smsParBinaryEnconding);
            SMSLog.Infor("/***********************Parameters******************/");
    }

    /**
     * Reads the parameters from a configuration file formated as a java resource bundle.
     * The parameters to read, default value and expected type are:
     * <pre>
     * PARAM-NAME           DEFAULT-VALUE		TYPE
     * asynchronous			false				boolean (true or false)
     * connection-type		t					one of tr,r or t
     * data-enconding		-15					signed byte (-127 to 127)
     * binary-enconding     -11					signed byte (-127 to 127)
     * destination-npi      9					integer
     * destination-ton      1					integer
     * esm-class-text		0					integer
     * esm-class-bin		64					integer
     * default-msg-id		0					integer
     * priority-flag		0					integer
     * protocol-id			0					integer
     * registered-delivery	0					integer
     * replace-if-present	0					integer
     * schedule-delivery	null				SMPP date
     * service-type			WAP					String
     * system-type			OTA					String
     * source-npi			9					Integer
     * source-ton			1					Integer
     * validity-period		null				SMPP date
     * </pre>
     * If the parameter reads to the word "null", a null reference will be used.
     * Don't do that with booleans, bytes and integers.
     */
    private void readParameters(String confFile) throws IOException {
            FileInputStream fis = new FileInputStream(confFile);
            PropertyResourceBundle prb = new PropertyResourceBundle(fis);

            connectionMode =
                    readParameter(prb, "asynchronous", "false").equals("true")
                            ? true
                            : false;
            /*
             * connectionType cant be null or other than t, r, or tr
             */
            connectionType = readParameter(prb, "connection-type", TRANSMITER);
            if (connectionType == null || !(connectionType.equals("t") ||
                connectionType.equals("r") || connectionType.equals("tr"))) {
                    connectionType = TRANSMITER;
            }
            byte  smsParDataEncoding = (byte) Integer.parseInt(readParameter(prb, "data-enconding", 0xF1 + ""));
            smsParBinaryEnconding = (byte) Integer.parseInt(readParameter(prb, "binary-enconding", 0xF5 + ""));
            smsParDestNPI = Integer.parseInt(readParameter(prb, "destination-npi", 1 + ""));
            smsParDestTON = Integer.parseInt(readParameter(prb, "destination-ton", 1 + ""));
            int smsParESMClassText = Integer.parseInt(readParameter(prb, "esm-class-text", 0 + ""));
            smsParESMClassBinary = Integer.parseInt(readParameter(prb, "esm-class-bin", 64 + ""));
            smsParMsgId = Integer.parseInt(readParameter(prb, "default-msg-id", 0 + ""));
            smsParPriorityFlag = Integer.parseInt(readParameter(prb, "priority-flag", 0 + ""));
            smsParProtocol = Integer.parseInt(readParameter(prb, "protocol-id", 0 + ""));
            smsParRegisteredDelivery = (byte) Integer.parseInt(readParameter(prb, "registered-delivery", 0 + ""));
            smsParReplaceFlag = Integer.parseInt(readParameter(prb, "replace-if-present", 0 + ""));
            smsParSchedDelivery = readParameter(prb, "schedule-delivery", null);
            smsParServiceType = readParameter(prb, "service-type", null);
            smsParSystemType = readParameter(prb, "system-type", null);
            smsParSourceNPI = Integer.parseInt(readParameter(prb, "source-npi", 9 + ""));
            smsParSourceTON = Integer.parseInt(readParameter(prb, "source-ton", 1 + ""));
            smsParValidity = readParameter(prb, "validity-period", null);
    }
    
    private void readParameters(Properties connectionPropeties) {
            connectionMode = Boolean.parseBoolean(connectionPropeties.getProperty("asynchronous", "false"));
            /*
             * connectionType cant be null or other than t, r, or tr
             */
            connectionType = connectionPropeties.getProperty("connection-type", TRANSMITER);
            if (connectionType == null || !(connectionType.equals("t") ||
                connectionType.equals("r") || connectionType.equals("tr"))) {
                    connectionType = TRANSMITER;
            }
            byte  smsParDataEncoding = (byte) Integer.parseInt(connectionPropeties.getProperty("data-enconding", 0xF1 + ""));
            smsParBinaryEnconding = (byte) Integer.parseInt(connectionPropeties.getProperty("binary-enconding", 0xF5 + ""));
            smsParDestNPI = Integer.parseInt(connectionPropeties.getProperty("destination-npi", 1 + ""));
            smsParDestTON = Integer.parseInt(connectionPropeties.getProperty("destination-ton", 1 + ""));
            int smsParESMClassText = Integer.parseInt(connectionPropeties.getProperty("esm-class-text", 0 + ""));
            smsParESMClassBinary = Integer.parseInt(connectionPropeties.getProperty("esm-class-bin", 64 + ""));
            smsParMsgId = Integer.parseInt(connectionPropeties.getProperty("default-msg-id", 0 + ""));
            smsParPriorityFlag = Integer.parseInt(connectionPropeties.getProperty("priority-flag", 0 + ""));
            smsParProtocol = Integer.parseInt(connectionPropeties.getProperty("protocol-id", 0 + ""));
            smsParRegisteredDelivery = (byte) Integer.parseInt(connectionPropeties.getProperty("registered-delivery", 0 + ""));
            smsParReplaceFlag = Integer.parseInt(connectionPropeties.getProperty("replace-if-present", 0 + ""));
            smsParSchedDelivery = connectionPropeties.getProperty("schedule-delivery", null);
            smsParServiceType = connectionPropeties.getProperty("service-type", null);
            smsParSystemType = connectionPropeties.getProperty("system-type", null);
            smsParSourceNPI = Integer.parseInt(connectionPropeties.getProperty("source-npi", 9 + ""));
            smsParSourceTON = Integer.parseInt(connectionPropeties.getProperty("source-ton", 1 + ""));
            smsParValidity = connectionPropeties.getProperty("validity-period", null);
    }

    /**
     * Reads the paramater from the ResourceBundle.
     * If it is null, use the default.
     * If its equal to the word "null" return null instead.
     * Watch for null pointers with non object stuff.
     * @param prb
     * @param string
     * @param string2
     */
    private String readParameter(ResourceBundle prb, String key, String deflt) {
            String tmp = prb.getString(key);
            return ((tmp != null)? (tmp.trim().equals("null") ? null : tmp.trim()) : deflt);
    }

    //</editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Bind & Unbind message">
    /**
     * Connect to the constructed SMSC server in the mode and connection type associated.
     * @param asynchronous Connect to the SMSC in synchronous or asynchronous way, true = asynchronous
     * @param bindOption, one of "r", "t" or "tr". Receiver, Transmiter or Transceiver, respectively
     */
    protected BindResponse bind(boolean asynchronous, String bindOption) throws IOException {
        status = CONNECTING;
        BindRequest logicaAPIbindReq = null;
        BindResponse logicaAPIbindResp = null;
        if (bindOption.compareToIgnoreCase(TRANSMITER) == 0) {
                logicaAPIbindReq = new BindTransmitter();
        } else if (bindOption.compareToIgnoreCase(RECEIVER) == 0) {
                logicaAPIbindReq = new BindReceiver();
        } else if (bindOption.compareToIgnoreCase(TRANSCEIVER) == 0) {
                logicaAPIbindReq = new BindTransciever();
        } else {
                throw new IllegalArgumentException(
                        "Invalid bind mode, expected t, r or tr, got "
                                + bindOption
                                + ". Operation canceled.");
        }
        logicaAPIconnection = new TCPIPConnection(smsCAddress, smsCPort);
        logicaAPIconnection.setReceiveTimeout(20 * 1000);
        logicaAPIsession = new Session(logicaAPIconnection);

        SMSLog.Debug(getLogName() + ": Bind Message: " + logicaAPIbindReq.debugString());

        try {
                logicaAPIbindReq.setSystemId(smsUser);
                logicaAPIbindReq.setPassword(smsPassword);
//			logicaAPIbindReq.setSystemType(smsParSystemType);
                logicaAPIbindReq.setInterfaceVersion((byte) 0x34);
                AddressRange logicaAPIrange = new AddressRange();
                logicaAPIrange.setTon((byte) 1); //From logica test settings
                logicaAPIrange.setNpi((byte) 1); //from logica test settings
//			logicaAPIrange.setAddressRange("11*"); //from logica test settings
//                    logicaAPIbindReq.setAddressRange("189");
                if (asynchronous) {
                        pduListener = new LogicaApiPduEventListener(logicaAPIsession);
                        logicaAPIbindResp = logicaAPIsession.bind(logicaAPIbindReq, pduListener);
                } else {
                        logicaAPIbindResp = logicaAPIsession.bind(logicaAPIbindReq);
                }
        } catch (WrongLengthOfStringException e) {
                e.printStackTrace(System.err);
                throw new IllegalArgumentException(e.getMessage());
        } catch (ValueNotSetException e) {
                SMSLog.Error(e);
                throw new IllegalArgumentException(e.getMessage());
        } catch (TimeoutException e) {
                SMSLog.Error(e);
                throw new RuntimeException(e);
        } catch (PDUException e) {
                SMSLog.Error(e);
                throw new RuntimeException(e);
        } catch (WrongSessionStateException e) {
                SMSLog.Error(e);
                throw new RuntimeException(e);
        }

        if (logicaAPIbindResp == null) {
            SMSLog.Error(getLogName() + ": logicaAPIbindResp is NULL !!!!!!!!!");
            status = ERROR;
        }
        else {
            if (logicaAPIbindResp.debugString() == null) {
                SMSLog.Error(getLogName() + ": logicaAPIbindResp.debugString() is NULL !!!!!!!!!");
            } else {
                SMSLog.Infor(getLogName() + ": Bind response " + logicaAPIbindResp.debugString());
            }

            if (logicaAPIbindResp.getCommandStatus() == Data.ESME_ROK) {
                    status = CONNECTED;
            } else {
                    status = ERROR;
            }
        }

        return logicaAPIbindResp;
    }

    /**
     * Unbinds a previously bound connection to the SMSC server
     */
    public UnbindResp unbind() {
        SMSLog.Debug(getLogName() + ": Going to unbind. (Current Status: " + getStatusStr() + ")");
        if (getStatus() == CONNECTING) {
            return null;
        }

        if (getStatus() != CONNECTED) {
            status = DISCONNECTED;
            return null;
        }

        UnbindResp logicaAPIresponse = null;
        try {
            SMSLog.Infor(getLogName() + ": It can take a while to stop the receiver.");
            logicaAPIresponse = logicaAPIsession.unbind();
//            System.out.println("Unbind response " + logicaAPIresponse.debugString());
            status = DISCONNECTED;
        } catch (Exception e) {
            SMSLog.Error(e);
            SMSLog.Error(getLogName() + ": Unbind operation failed. " + e);
        }

        // Sleep and wait in 5s
        SMSLog.Debug("Unbind to SMSC, wait 5 second before continue ...");
        try { Thread.sleep(5000); } catch (Exception ex) {}
        
        status = DISCONNECTED;
        return logicaAPIresponse;
    }

    // </editor-fold>
    /**
     * Sends and SMS text message to the gateway
     */
    synchronized public SubmitSMResp sendSMS(String dest, byte[] msg, String alias, 
                                             byte dataEncoding, byte parESMClass) throws IOException {
//        if (getStatus() != CONNECTED) { init(); }
        waitForConnecting();
        waitForTPS();
        mTPS++;

        if (getStatus() == CONNECTED) {
            SubmitSM logicaAPIsubmitSMreq = new SubmitSM();
            SubmitSMResp logicaAPIsubmitSMResp = null;

            try {
                logicaAPIsubmitSMreq.setServiceType(smsParServiceType);
                
                if (alias != null && alias.length() > 0) {
                    if (checkAliasHasCharacter(alias)) {
                        logicaAPIsubmitSMreq.setSourceAddr((byte)5, (byte)0, alias);
                    } else {
                        logicaAPIsubmitSMreq.setSourceAddr((byte)smsParSourceTON, (byte)smsParSourceNPI, alias);
                    }
                } else {
                    logicaAPIsubmitSMreq.setSourceAddr((byte)smsParSourceTON, (byte)smsParSourceNPI, getShortCode());
                }
                
                logicaAPIsubmitSMreq.setDestAddr(dest);
                logicaAPIsubmitSMreq.setReplaceIfPresentFlag((byte) smsParReplaceFlag);
                logicaAPIsubmitSMreq.setShortMessage(msg);
                logicaAPIsubmitSMreq.setScheduleDeliveryTime(smsParSchedDelivery);
                logicaAPIsubmitSMreq.setValidityPeriod(smsParValidity);
//                logicaAPIsubmitSMreq.setEsmClass((byte) smsParESMClassText);
                logicaAPIsubmitSMreq.setEsmClass((byte) parESMClass);
                logicaAPIsubmitSMreq.setProtocolId((byte) smsParProtocol);
                logicaAPIsubmitSMreq.setPriorityFlag((byte) smsParPriorityFlag);
                logicaAPIsubmitSMreq.setRegisteredDelivery(smsParRegisteredDelivery);
//                logicaAPIsubmitSMreq.setDataCoding(smsParDataEncoding);
                logicaAPIsubmitSMreq.setDataCoding(dataEncoding);
                logicaAPIsubmitSMreq.setSmDefaultMsgId((byte) smsParMsgId);

                logicaAPIsubmitSMreq.assignSequenceNumber(true);
                SMSLog.Debug(getLogName() + ": SEND SMS: " + logicaAPIsubmitSMreq.debugString());
                // add to confirm list
                if (asynchronous) {
                    logicaAPIsession.submit(logicaAPIsubmitSMreq);
                } else {
    //                System.out.println("Call SYN");
                    logicaAPIsubmitSMResp = logicaAPIsession.submit(logicaAPIsubmitSMreq);
    //                if (logicaAPIsubmitSMResp != null)
    //                System.out.println("Submit response " + logicaAPIsubmitSMResp.debugString());
    //				messageId = logicaAPIsubmitSMResp.getMessageId();
                }

//                mConfirm.addWaitConfirm(logicaAPIsubmitSMreq);

            } catch (WrongLengthOfStringException e) {
                    SMSLog.Error(e);
                    throw new RuntimeException(e);
            } catch (WrongDateFormatException e) {
                    SMSLog.Error(e);
                    throw new RuntimeException(e);
            } catch (ValueNotSetException e) {
                    SMSLog.Error(e);
                    throw new IllegalArgumentException(e.getMessage());
            } catch (TimeoutException e) {
                    SMSLog.Error(e);
                    throw new RuntimeException(e);
            } catch (PDUException e) {
                    SMSLog.Error(e);
                    throw new RuntimeException(e);
            } catch (WrongSessionStateException e) {
                    SMSLog.Error(e);
                    throw new RuntimeException(e);
            }
            return logicaAPIsubmitSMResp;
        } else {
            SMSLog.Debug(getLogName() + ": sendSMS: DISCONNECTED, Wait 5s");
            try { Thread.sleep(5000); } catch (Exception ex) {}
            return null;
        }
    }

    private boolean checkAliasHasCharacter(String alias) {
        byte tmp[] = alias.getBytes();
        for (int i=0; i<tmp.length; i++) {
            if (tmp[i] < '0' || tmp[i] > '9') {
                return true;
            }
        }
        
        return false;
    }
    
    private String getStatusStr() {
        switch (getStatus()) {
            case CONNECTED: return "CONNECTED";
            case DISCONNECTED: return "DISCONNECTED";
            case CONNECTING: return "CONNECTING";
            case ERROR: return "ERROR";
        }

        return "UNKNOW";
    }
    
    private void waitForConnecting() {
        while (getStatus() != CONNECTED) {
            try {
                SMSLog.Debug(getLogName() + " Status is " + getStatusStr() + ", wait 5s");
                Thread.sleep(5000);
                init();
            } catch (Exception ex) { }
        }
    }


    public void sendSubmitSM(PDU p) {
        try {
            if (getStatus() != CONNECTED) {
                init();
            }

            if (getStatus() == CONNECTED) {
                SMSLog.Debug(getLogName() + ": sendSubmitSM: " + p.debugString());
                if (asynchronous) {
                    logicaAPIsession.submit((SubmitSM)p);
                } else {
    //                System.out.println("Call SYN");
                    SubmitSMResp logicaAPIsubmitSMResp = logicaAPIsession.submit((SubmitSM)p);
                    if (logicaAPIsubmitSMResp != null) {
                        SMSLog.Infor(getLogName() + ": Submit response " + logicaAPIsubmitSMResp.debugString());
                    }
                }
            } else {
                SMSLog.Error(getLogName() + ": sendSubmitSM: DISCONNECTED, wait 5s");
                try { Thread.sleep(5000); } catch (Exception ex) {}
            }
        } catch (Exception e) {
            SMSLog.Error(getLogName() + ": ERROR sendSubmitSM PDU: " + p.debugString());
            SMSLog.Error(e);
        } 
    }

    // <editor-fold defaultstate="collapsed" desc="Currently Not use">
    // Currently not use, so comment out
//    public DataSMResp sendSMSData(String dest, byte[] packet) throws IOException {
//        if (getStatus() != CONNECTED) {
//                init();
//        }
//        DataSM logicaAPIdataSMreq = new DataSM();
//        DataSMResp  logicaAPIdataSMResp = null;
//
//        try {
//            logicaAPIdataSMreq.setServiceType(smsParServiceType);
//            logicaAPIdataSMreq.setSourceAddr(getSmsSender());
//            logicaAPIdataSMreq.setDestAddr(dest);
//            logicaAPIdataSMreq.setEsmClass((byte) smsParESMClassBinary);
//            logicaAPIdataSMreq.setRegisteredDelivery(smsParRegisteredDelivery);
//            logicaAPIdataSMreq.setDataCoding(smsParBinaryEnconding);
//            //request.assignSequenceNumber(true);
//            //Logica API data method only accepts ByteBuffers
//            ByteBuffer logicaAPIbuffer = new ByteBuffer(packet);
//            logicaAPIdataSMreq.setPayloadType((byte)0);//WDP
//            logicaAPIdataSMreq.setMessagePayload(logicaAPIbuffer);
//            if (asynchronous) {
//                logicaAPIsession.data(logicaAPIdataSMreq);
//            } else {
//                logicaAPIdataSMResp = logicaAPIsession.data(logicaAPIdataSMreq);
////                        if (logicaAPIdataSMResp != null)
////                            System.out.println("Submit response " + logicaAPIdataSMResp.debugString());
////					messageId = logicaAPIdataSMResp.getMessageId();
//            }
//        } catch (WrongLengthOfStringException e) {
//            SMSLog.Error(e);
//            throw new RuntimeException(e);
//        } catch (WrongDateFormatException e) {
//            SMSLog.Error(e);
//            throw new RuntimeException(e);
//        } catch (ValueNotSetException e) {
//            SMSLog.Error(e);
//            throw new IllegalArgumentException(e.getMessage());
//        } catch (TimeoutException e) {
//            SMSLog.Error(e);
//            throw new RuntimeException(e);
//        } catch (PDUException e) {
//            SMSLog.Error(e);
//            throw new RuntimeException(e);
//        } catch (WrongSessionStateException e) {
//            SMSLog.Error(e);
//            throw new RuntimeException(e);
//        }
//        return logicaAPIdataSMResp;
//    }
    //</editor-fold>
    /**
     * @return
     */
    public String getShortCode() {
        return mShortCode;
    }

    /**
     * @return
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get message header for long SMS message
     * @param ref: Reference number of SMS
     * @param totalSMS: Total SMS for message
     * @param msgPart: Which part
     * @return 
     */
    private ByteArrayOutputStream getMessageHeader(int ref, int totalSMS, int msgPart) {
        /** UDH Infor for concatenated SMS case 1
        * Field 1 (1 octet): Length of User Data Header, in this case 05.
        * Field 2 (1 octet): Information Element Identifier, equal to 00 (Concatenated short messages, 8-bit reference number)
        * Field 3 (1 octet): Length of the header, excluding the first two fields; equal to 03
        * Field 4 (1 octet): 00-FF, CSMS reference number, must be same for all the SMS parts in the CSMS
        * Field 5 (1 octet): 00-FF, total number of parts. The value shall remain constant for every short message 
        *                    which makes up the concatenated short message. If the value is zero then the receiving entity
        *                    shall ignore the whole information element
        * Field 6 (1 octet): 00-FF, this part's number in the sequence. The value shall start at 1 and increment for 
        *                    every short message which makes up the concatenated short message.
        *                    If the value is zero or greater than the value in Field 5 then the receiving entity
        *                    shall ignore the whole information element.
        *                    [ETSI Specification: GSM 03.40 Version 5.3.0: July 1996]
        **/
        ByteArrayOutputStream msgout = new ByteArrayOutputStream();
        msgout.write(0x05); // UDH Length
        msgout.write(0x00); // 8 bit reference
        msgout.write(0x03); // Header length
        msgout.write(ref); // Reference number
        msgout.write(totalSMS); // Total SMS
        msgout.write(msgPart); // SMS part

       /** UDH Infor for concatenated SMS case 2
        * Field 1 (1 octet): Length of User Data Header (UDL), in this case 6.
        * Field 2 (1 octet): Information Element Identifier, equal to 08 (Concatenated short messages, 16-bit reference number)
        * Field 3 (1 octet): Length of the header, excluding the first two fields; equal to 04
        * Field 4 (2 octets): 0000-FFFF, CSMS reference number, must be same for all the SMS parts in the CSMS
        * Field 5 (1 octet): 00-FF, total number of parts. The value shall remain constant for every 
        *                    short message which makes up the concatenated short message.
        *                    If the value is zero then the receiving entity shall ignore the whole information element
        * Field 6 (1 octet): 00-FF, this part's number in the sequence. The value shall start at 1 and increment 
        *                    for every short message which makes up the concatenated short message.
        *                    If the value is zero or greater than the value in Field 5
        *                    then the receiving entity shall ignore the whole information element.
        *                    [ETSI Specification: GSM 03.40 Version 5.3.0: July 1996]
        **/
//                        ByteArrayOutputStream msgout = new ByteArrayOutputStream();
//                        msgout.write(0x06); // UDH Length
//                        msgout.write(0x08); // 16 bit reference
//                        msgout.write(0x04); // Header length
//                        msgout.write(ref); // Reference number
//                        msgout.write(count); // Total SMS
//                        msgout.write(ii); // SMS part
        
        return msgout;
    }
    /**
     * HoaND add EASY public INTERFACE
     */
    private boolean isTanza = true; //fix code cho Tanza
    
    synchronized public void sendTextSMS(String dest, byte[] message, String alias) {
        try {
            byte smsParDataEncoding = ENCODE_TEXT_DEFAULT;
            byte smsParESMClassText = SMS_NO_UDH;
            
            String charSet = CharsetDetector.detectCharsetStr(message);
            // Default 7 bit
            int smsLength = 160; // 7 bit, 140 for 8bit, back up
            if (isTanza) {
                 smsLength = 134; 
            }
            // Header = 8x6 = 48 Bit -> Lenght=7 in 7 bit encoded 
            int headerLen = 7; // Byte
                
            if (charSet.equals("UTF-16")) {
                smsParDataEncoding = ENCODE_TEXT_UNICODE;
                smsLength = 140;
                headerLen = 6; // 6 byte
                if (isTanza) {
                  smsLength = 134; 
                }
            }
            
            SMSLog.Debug(getLogName() + ": Byte to Send: " + message.length);
            if (message.length <= smsLength) {
                SMSLog.Debug(getLogName() + ": NUMBER OF SMS: " + 1);
//                smsParESMClassText = SMS_NO_UDH;
                sendSMS(dest, message, alias, smsParDataEncoding, smsParESMClassText);
            }
            else {
                smsParESMClassText = SMS_UDH;
                // Total message is 140 bytes, header use 6 bytes so message data has only 134 byte
                // smsLength = 140; // Must use 8 bit for header
                int partLen = smsLength - headerLen; 

                int count = message.length / partLen;
                count += ((message.length % partLen) > 0 ? 1 : 0);
                SMSLog.Debug(getLogName() + ": NUMBER OF SMS: " + count);

                int ii = 0;
                int li = 0;

                java.util.Random r = new Random(System.currentTimeMillis());
                int ref = r.nextInt(0xFF); // case 1
//                byte[] ref = PublicLibs.convertToBytes(r.nextInt(0xFFFF)); // for case 2: 16 Bits header

                for (int i=1; i<=message.length; i++) {
                    if (i % partLen == 0 || i == message.length) {
                        ii ++;
//                        int msglen = headerLen + // UDH header
//                                     ((i % partLen == 0) ? partLen : (i % partLen));
                        // Get message header
                        ByteArrayOutputStream msgout = getMessageHeader(ref, count, ii);
                        // Write message Content
                        for (int j = li; j<i; j++) {
                            msgout.write(message[j]);
                        }
                        // Send SMS
//                        sendSMS(dest, msgout.toByteArray(), alias);
                        SMSLog.Debug(getLogName() + " Add SMS with (Ref: " + ref + "; Total: " + count + ": Part: " + ii + ") " +
                                     "To " + dest);
                        // Add Queue to send
                        if (ii - 1 < mLongSMSProcess.length) {
                            mLongSMSProcess[ii - 1].addToQueue(new SMSPending(System.currentTimeMillis() + (ii - 1) * mSepradeBetweenLongSMS, 
                                                            dest, msgout, alias, smsParDataEncoding, smsParESMClassText));
                        } else {
                            mLongSMSProcess[0].addToQueue(new SMSPending(System.currentTimeMillis() + (ii - 1) * mSepradeBetweenLongSMS, 
                                                                        dest, msgout, alias, smsParDataEncoding, smsParESMClassText));
                        }
                        // ----------------
                        li = i;
                        // Sleep Here to slow down
//                        try { Thread.sleep(10); } catch (Exception ex) {};
                    }
                }
            }
        } catch (Exception ex) {
            SMSLog.Error(getLogName() + ": Error while sending SMS (" + ex.getMessage() + ")");
            SMSLog.Error(ex);
            unbind();
        }
    }
    
    synchronized public void sendFlashTextSMS(String dest, byte[] message, String alias) {
        try {
            byte smsParDataEncoding = ENCODE_TEXT_FLASH_SMS;
            byte smsParESMClassText = SMS_NO_UDH;
            String charSet = CharsetDetector.detectCharsetStr(message);
            int smsLength = 160;
            // Header = 8x6 = 48 Bit -> Lenght=7 in 7 bit encoded 
            int headerLen = 7; // Byte
            
            if (charSet.equals("UTF-16")) {
                smsParDataEncoding |= ENCODE_TEXT_UNICODE;
                smsLength = 140;
                headerLen = 6;
            }
            SMSLog.Debug(getLogName() + ": Byte to Send: " + message.length);

            if (message.length <= smsLength) {
                SMSLog.Debug(getLogName() + ": NUMBER OF SMS: " + 1);
//                smsParESMClassText = SMS_NO_UDH;
                sendSMS(dest, message, alias, smsParDataEncoding, smsParESMClassText);
            }
            else {
                smsParESMClassText = SMS_UDH;
                // Header = 8x6 = 48 Bit -> Lenght=7 in 7 bit encoded 
                //smsLength = 140;
                int partLen = smsLength - headerLen;

                int count = message.length / partLen;
                count += ((message.length % partLen) > 0 ? 1 : 0);
                SMSLog.Debug(getLogName() + ": NUMBER OF SMS: " + count);

                int ii = 0;
                int li = 0;

                java.util.Random r = new Random(System.currentTimeMillis());
                int ref = r.nextInt(0xFF); // case 1
//                byte[] ref = PublicLibs.convertToBytes(r.nextInt(0xFFFF)); // for case 2

                for (int i=1; i<=message.length; i++) {
                    if (i % partLen == 0 || i == message.length) {
                        ii ++;
                        int msglen = headerLen + // UDH header
                                     ((i % partLen == 0) ? partLen : (i % partLen));

                       // Get message header
                        ByteArrayOutputStream msgout = getMessageHeader(ref, count, ii);
                        // Write message Content
                        for (int j = li; j<i; j++) {
                            msgout.write(message[j]);
                        }
                        // Send SMS
                        SMSLog.Debug(getLogName() + " Add SMS with (Ref: " + ref + "; Total: " + count + ": Part: " + ii + ") " +
                                     "To " + dest);
                        // Add Queue to send
                        if (ii - 1 < mLongSMSProcess.length) {
                            mLongSMSProcess[ii - 1].addToQueue(new SMSPending(System.currentTimeMillis() + (ii - 1) * mSepradeBetweenLongSMS, 
                                                            dest, msgout, alias, smsParDataEncoding, smsParESMClassText));
                        } else {
                            mLongSMSProcess[0].addToQueue(new SMSPending(System.currentTimeMillis() + (ii - 1) * mSepradeBetweenLongSMS, 
                                                                        dest, msgout, alias, smsParDataEncoding, smsParESMClassText));
                        }
                        
                        li = i;
                    }
                }
            }
        } catch (Exception ex) {
            SMSLog.Error(getLogName() + ": Error while sending SMS (" + ex.getMessage() + ")");
            SMSLog.Error(ex);
            unbind();
        }
    }

    synchronized public void sendBinarySMS(String dest, BinarySMS msg, String alias) {
        try {
            byte smsParDataEncoding = ENCODE_BIN;
            byte smsParESMClassText = SMS_UDH;

            int smallestHeader = 8;
            ByteArrayOutputStream message = msg.getData();
            SMSLog.Debug(getLogName() + ": Byte to Send: " + message.size());

            if (message.size() <= 140 - smallestHeader) {
                SMSLog.Debug(getLogName() + ": NUMBER OF SMS: " + 1);
//                smsParESMClassText = SMS_NO_UDH;
                ByteArrayOutputStream sendData = new ByteArrayOutputStream();
                sendData.write(WbxmlLibs.hexToByte("08")); // header length
                sendData.write(WbxmlLibs.hexToByte("05")); // UDH IE identifier: port number
                sendData.write(WbxmlLibs.hexToByte("04")); // UDH Port number lenght
                sendData.write(WbxmlLibs.convertToBytes(msg.getDestPort()));
                sendData.write(WbxmlLibs.convertToBytes(msg.getSrcPort()));
                sendData.write(WbxmlLibs.hexToByte("00")); // End

                sendData.write(message.toByteArray());
                sendSMS(dest, message.toByteArray(), alias, smsParDataEncoding, smsParESMClassText);
            }
            else {
                int partLen = 128;
                int headerLen = 12; // include 0B

                byte[] data = message.toByteArray();
                int count = data.length / partLen;
                count += ((data.length % partLen) > 0 ? 1 : 0);
                SMSLog.Debug(getLogName() + ": NUMBER OF SMS: " + count);

                int ii = 0;
                int li = 0;

                java.util.Random r = new Random(System.currentTimeMillis());
                int ref = r.nextInt(0xFF); // case 1
//                byte[] ref = PublicLibs.convertToBytes(r.nextInt(0xFFFF)); // for case 2

                for (int i=1; i<= data.length; i++) {
                    if (i % partLen == 0 || i == data.length) {
                        ii ++;
                        int msglen = headerLen + // UDH header
                             ((i % partLen == 0) ? partLen : (i % partLen));

                        ByteArrayOutputStream sendData = new ByteArrayOutputStream();
                        sendData.write(WbxmlLibs.hexToByte("0B")); // header length (11)
                        sendData.write(WbxmlLibs.hexToByte("05")); // UDH IE identifier: port number
                        sendData.write(WbxmlLibs.hexToByte("04")); // UDH Port number lenght
                        sendData.write(WbxmlLibs.convertToBytes(msg.getDestPort())); // 2 byte
                        sendData.write(WbxmlLibs.convertToBytes(msg.getSrcPort()));  // 2 byte
                        sendData.write(WbxmlLibs.hexToByte("00"));  // end

//                        System.out.println("MSG LEN: " + msglen + " " + i);

                       /** UDH Infor for concatenated SMS case 1
                        * Field 1 (1 octet): Length of User Data Header, in this case 05.
                        * Field 2 (1 octet): Information Element Identifier, equal to 00 (Concatenated short messages, 8-bit reference number)
                        * Field 3 (1 octet): Length of the header, excluding the first two fields; equal to 03
                        * Field 4 (1 octet): 00-FF, CSMS reference number, must be same for all the SMS parts in the CSMS
                        * Field 5 (1 octet): 00-FF, total number of parts. The value shall remain constant for every short message 
                        *                    which makes up the concatenated short message. If the value is zero then the receiving
                        *                    entity shall ignore the whole information element
                        * Field 6 (1 octet): 00-FF, this part's number in the sequence. The value shall start at 1 and increment 
                        *                    for every short message which makes up the concatenated short message.
                        *                    If the value is zero or greater than the value in Field 5 then the receiving entity
                        *                    shall ignore the whole information element.
                        *                    [ETSI Specification: GSM 03.40 Version 5.3.0: July 1996]
                        **/
//                        msgout.write(0x05); // UDH Length
//                        sendData.write(0x00); // 8 bit reference
                        sendData.write(0x03); // Header length
                        sendData.write(ref); // Reference number
                        sendData.write(count); // Total SMS
                        sendData.write(ii); // SMS part

                       /** UDH Infor for concatenated SMS case 2
                        * Field 1 (1 octet): Length of User Data Header (UDL), in this case 6.
                        * Field 2 (1 octet): Information Element Identifier, equal to 08 (Concatenated short messages, 16-bit reference number)
                        * Field 3 (1 octet): Length of the header, excluding the first two fields; equal to 04
                        * Field 4 (2 octets): 0000-FFFF, CSMS reference number, must be same for all the SMS parts in the CSMS
                        * Field 5 (1 octet): 00-FF, total number of parts. The value shall remain constant for every short message 
                        *                    which makes up the concatenated short message. If the value is zero then the receiving entity
                        *                    shall ignore the whole information element
                        * Field 6 (1 octet): 00-FF, this part's number in the sequence. The value shall start at 1 and increment 
                        *                    for every short message which makes up the concatenated short message.
                        *                    If the value is zero or greater than the value in Field 5 then the receiving entity shall 
                        *                    ignore the whole information element.
                        *                    [ETSI Specification: GSM 03.40 Version 5.3.0: July 1996]
                        **/
//                        ByteArrayOutputStream msgout = new ByteArrayOutputStream();
//                        msgout.write(0x06); // UDH Length
//                        msgout.write(0x08); // 16 bit reference
//                        msgout.write(0x04); // Header length
//                        msgout.write(ref); // Reference number
//                        msgout.write(count); // Total SMS
//                        msgout.write(ii); // SMS part

                        for (int j = li; j<i; j++) {
                            sendData.write(data[j]);
                        }

//                        sendSMS(dest, sendData.toByteArray(), alias);
                        // Add Queue to send
                        if (ii - 1 < mLongSMSProcess.length) {
                            mLongSMSProcess[ii - 1].addToQueue(new SMSPending(System.currentTimeMillis() + (ii - 1) * mSepradeBetweenLongSMS, 
                                                            dest, sendData, alias, smsParDataEncoding, smsParESMClassText));
                        } else {
                            mLongSMSProcess[0].addToQueue(new SMSPending(System.currentTimeMillis() + (ii - 1) * mSepradeBetweenLongSMS, 
                                                                        dest, sendData, alias, smsParDataEncoding, smsParESMClassText));
                        }

                        li = i;
                    }
                }
            }
        } catch (Exception ex) {
            SMSLog.Error(getLogName() + ": Error while sending SMS (" + ex.getMessage() + ")");
//            ex.printStackTrace();
            SMSLog.Error(ex);
            unbind();
        }
    }

    public void startSMSListener() {
        try {
            if (getStatus() != CONNECTED) {
                init();
            }

            logicaAPIsession.getReceiver().setServerPDUEventListener(this);
            logicaAPIsession.getReceiver().start();
//            Receiver rc = new Receiver(logicaAPIconnection);
//            rc.setServerPDUEventListener(this);
//            rc.start();
        } catch (Exception ex) {
            SMSLog.Error(ex);
        }
    }

    public void stop() {
        mIsRunning = false;
        unbind();
    }
    
    /**
     * Handle message send from SMSC
     * @param event : Message from SMSC
     */
    
    @Override
    public void handleEvent(ServerPDUEvent event) {
        try {
            PDU pp = event.getPDU();
            ByteBuffer data = pp.getData();
//            PublicLibs.printBytes(pp.getData().getBuffer());

            int len = data.removeInt();
            int msgCommand = data.removeInt();
            int cmdStatus = data.removeInt(); // not use
            int sequence = data.removeInt();

//            System.out.println("Sequence: " + sequence);
            switch (msgCommand) {
                case Data.SUBMIT_SM_RESP :
                {
                    SubmitSMResp resp = new SubmitSMResp();
                    resp.setBody(data);
//                    SMSLog.Infor(resp.debugString());
                    SMSLog.Debug(getLogName() + ": GOT SUBMIT_SM_RESP !!! (SEQ: " + sequence + ") " + resp.debugString());
//                    mConfirm.addSubmitSMResp(sequence, Long.parseLong(resp.getMessageId(), 16));

                    mLastCommunicate = System.currentTimeMillis();
                    break;
                }

                case Data.DELIVER_SM :
                    SMSLog.Debug(getLogName() + ": GOT DELIVER_SM !!! (SEQ: " + sequence + ")");
                    mLastCommunicate = System.currentTimeMillis();

                    try {
                        DeliverSM pkg = new DeliverSM();
                        pkg.setBody(data);
                        SMSLog.Infor(getLogName() + ": " + pkg.debugString());
                        SMSLog.Debug(getLogName() + ": " + new String(pkg.getOriginalMessage(), "UTF-16"));

                        DeliverSMResp resp = (DeliverSMResp) ((DeliverSM) pp).getResponse();
                        SMSLog.Debug("SMSSender Thread " + mId + ": Send DELIVER_SM_RESP: " + resp.debugString());
                        logicaAPIsession.respond(resp);
                        
                        if ((pkg.getEsmClass() & 0x0004) != 0) {
                            SMSLog.Infor(getLogName() + ": Got DELIVERY REPORT !!!") ;
//                            mConfirm.addDeliverReport(pkg);
                            
                        } else {
                            // check for UHD
                            if ((pkg.getEsmClass() & 0x0040) != 0) {
                                // decode header
                                SMSLog.Debug(getLogName() + ": Got SMS with UDH, put to decode class");

                                if (mDecodeSms != null) {
                                    mDecodeSms.addSms(pkg);
                                } else {
                                    SMSLog.Warning(getLogName() + ": No Decode SMS was set");
                                }

                            } else {
                                processTextSMS(pkg.getSourceAddr().getAddress(),
                                               pkg.getDestAddr().getAddress(),
                                               pkg);
//                                               pkg.getShortMessage());
                            }
                        }
                        
                    } catch (Exception ex ) {
                        SMSLog.Error(ex);
                    }
                    break;

                case Data.ENQUIRE_LINK :  
                    SMSLog.Debug(getLogName() + ": GOT ENQUIRE_LINK !!!" + sequence);
//                    mLastReceiveEnquire = System.currentTimeMillis();
                    mLastCommunicate = System.currentTimeMillis();
                    // From SMSC, so send RESP
                    try {
                        // Send response
                        EnquireLinkResp resp = new EnquireLinkResp();
                        ByteBuffer tmp = new ByteBuffer();
                        tmp.appendCString("" + sequence);
                        resp.setBody(tmp);
                        SMSLog.Debug(getLogName() + ": Send ENQUIRED_LINK_RESP: " + resp.debugString());
                        logicaAPIsession.respond(resp);

                    } catch (Exception ex ) {
                        SMSLog.Error(ex);
                    }
                    break;

                case Data.ENQUIRE_LINK_RESP :  
                    SMSLog.Debug(getLogName() + ": GOT ENQUIRE_LINK_RESP !!!" + sequence);
//                    mLastReceiveEnquireResp = System.currentTimeMillis();
                    mLastCommunicate = System.currentTimeMillis();
                    break;
// <editor-fold defaultstate="collapsed" desc="Unused message">
                case Data.ALERT_NOTIFICATION :  
                    SMSLog.Debug(getLogName() + ": GOT ALERT_NOTIFICATION !!!" + sequence);
                    break;

                case Data.GENERIC_NACK :  
                    SMSLog.Debug(getLogName() + ": GOT GENERIC_NACK !!!" + sequence);
                    break;

                case Data.BIND_RECEIVER_RESP :
                    SMSLog.Debug(getLogName() + ": GOT BIND_RECEIVER_RESP !!!" + sequence);
                    break;

                case Data.BIND_TRANSCEIVER_RESP :
                    SMSLog.Debug(getLogName() + ": GOT BIND_TRANSCEIVER_RESP !!!" + sequence);
                    break;

                case Data.BIND_TRANSMITTER_RESP :
                    SMSLog.Infor(getLogName() + ": GOT BIND_TRANSMITTER_RESP !!!" + sequence);
                    break;

                case Data.OUTBIND :
                    SMSLog.Infor(getLogName() + ": GOT OUTBIND !!!" + sequence);
                    break;

                case Data.UNBIND :
                    SMSLog.Infor(getLogName() + ": GOT UNBIND !!!" + sequence);
                    break;

                case Data.UNBIND_RESP :
                    SMSLog.Infor(getLogName() + ": GOT UNBIND_RESP !!!" + sequence);
                    break;

                case Data.QUERY_SM_RESP :
                    SMSLog.Infor(getLogName() + ": GOT QUERY_SM_RESP !!!" + sequence);
                    break;

                case Data.CANCEL_SM :
                    SMSLog.Infor(getLogName() + ": GOT CANCEL_SM !!!" + sequence);
                    break;

                case Data.REPLACE_SM_RESP :
                    SMSLog.Infor(getLogName() + ": GOT REPLACE_SM_RESP !!!" + sequence);
                    break;

                case Data.SUBMIT_MULTI_RESP :
                    SMSLog.Infor(getLogName() + ": GOT SUBMIT_MULTI_RESP !!!" + sequence);
                    break;

                case Data.DATA_SM :
                    SMSLog.Infor(getLogName() + ": GOT DATA_SM !!!" + sequence);
                    break;

                case Data.DATA_SM_RESP :
                    SMSLog.Infor(getLogName() + ": GOT DATA_SM_RESP !!!" + sequence);
                    break;
// </editor-fold>
            }
        } catch (Exception ex) {
            SMSLog.Error(ex);
        }
    }

    public void processTextSMS(String src, String dst, DeliverSM msg) {
        if (mListener != null) {
            mListener.onTextSMSArrive(src, dst, msg);
        }

    }

    public void processBinarySMS(String src, String dst, byte msg[], int dstPort, int srcPort) {
        SMSLog.Infor(getLogName() + ": Got Setting Message: from " + src + " to " + dst + " SrcPort: " + srcPort + " DstPort " + dstPort);
//        byte data[] = msg.getBytes();
//        PublicLibs.printBytes(data);
//
//        if (data[0] == Wbxml13.VER_03) {
//            SMSLog.Infor("Got version 1.3");
//            Wbxml13 convert = new Wbxml13();
//            convert.convertToXml(data);
//        } else {
//            SMSLog.Infor("Got version 1.0");
//            Wbxml10 convert = new Wbxml10();
//            convert.convertToXml(data);
//        }

        if (dstPort == 2948) {
            // OMA message
            SMSLog.Debug(getLogName() + ": Got OMA message!");
            try {
                ByteArrayInputStream inp = new ByteArrayInputStream(msg);
                SMSLog.Debug(getLogName() + ": Message ID: " + Integer.toHexString(inp.read()));
                SMSLog.Debug(getLogName() + ": PDU Type: " + Integer.toHexString(inp.read()));
                int headerLength = inp.read();
                SMSLog.Debug(getLogName() + ": Header length: " + Integer.toHexString(headerLength));
                int contentType = WbxmlLibs.getNextInt(inp);
                SMSLog.Debug(getLogName() + ": Header Format: " + Integer.toHexString(contentType) + " (" + contentType + ")");
                SMSLog.Debug(getLogName() + ": Content Type: " + Integer.toHexString(inp.read()));
                int secType = WbxmlLibs.getNextInt(inp);
                SMSLog.Debug(getLogName() + ": Security Type: " + Integer.toHexString(secType));
                SMSLog.Debug(getLogName() + ": Before HMAC: " + Integer.toHexString(inp.read()));
                byte hMac[] = WbxmlLibs.getNextBytes(inp, 40); // 40 byte fix for HMAC

                SMSLog.Debug(getLogName() + ": HMAC: " + WbxmlLibs.printBytes(hMac));
                SMSLog.Debug(getLogName() + ":     : " + (new String(hMac)));
                SMSLog.Debug(getLogName() + ": next HMAC: " + Integer.toHexString(inp.read()));

                // Data
                byte data[] = WbxmlLibs.getNextBytes(inp, inp.available());
                Wbxml13 convert = Wbxml13.getInstance();
                convert.convertToXml(data);

            } catch (Exception ex) {
                SMSLog.Error(ex);
            }

        } else
        if (dstPort == 49999) {
            // OTA Message
            SMSLog.Debug(getLogName() + ": Got OTA message!");
            try {
                ByteArrayInputStream inp = new ByteArrayInputStream(msg);
                SMSLog.Debug(getLogName() + ": Message ID: " + Integer.toHexString(inp.read()));
                SMSLog.Debug(getLogName() + ": PDU Type: " + Integer.toHexString(inp.read()));
                int headerLength = inp.read();
                SMSLog.Debug(getLogName() + ": Header length: " + Integer.toHexString(headerLength));
                SMSLog.Debug(getLogName() + ": Format (Unknow): " + Integer.toHexString(inp.read()));
                SMSLog.Debug(getLogName() + ": Format (Unknow): " + Integer.toHexString(inp.read()));
                byte hSetting[] = WbxmlLibs.getNextBytes(inp, headerLength - 5); // 40 byte fix for HMAC
                SMSLog.Debug(getLogName() + ": Setting App: " + WbxmlLibs.printBytes(msg));
                SMSLog.Debug(getLogName() + ":            : " + (new String(hSetting)));
                SMSLog.Debug(getLogName() + ": next SETTING: " + Integer.toHexString(inp.read()));
                SMSLog.Debug(getLogName() + ": Charset: " + Integer.toHexString(inp.read()));
                SMSLog.Debug(getLogName() + ": End WSP Header(UTF-8): " + Integer.toHexString(inp.read()));

                // Data
                byte data[] = WbxmlLibs.getNextBytes(inp, inp.available());
                Wbxml10 convert = Wbxml10.getInstance();
                convert.convertToXml(data);

            } catch (Exception ex) {
                SMSLog.Error(ex);
            }

        } else {
            SMSLog.Error(getLogName() + ": Destination Port not recognize.");
        }
    }

    private void resetTPS() {
        long now = System.currentTimeMillis();
        //Reset SMS TPS
        if (now - mLastResetTPS >= 1000) {
            if (mTPS > 0) {
                SMSLog.Debug(getLogName() + ": Reset SMS TPS: " + mTPS);
            }
            mTPS = 0;
            mLastResetTPS = System.currentTimeMillis();
        }
    }
    
    @SuppressWarnings("empty-statement")
    private void waitForTPS() {
        long lastPrint = 0;
        while (mTPS >= mMaxTPS) {
            if (System.currentTimeMillis() - lastPrint> 100) {
                SMSLog.Debug("Wait for TPS Reset: " + mTPS);
                lastPrint = System.currentTimeMillis();
            }
            try { Thread.sleep(10); } catch (Exception ex) {};
            resetTPS(); // add here to make sure it does not hang
        }
    }
    
    private void checkStatus() {
        SMSLog.Debug(getLogName() + ": Check STATUS: " + getStatusStr());
        mLastCheckStatus = System.currentTimeMillis();
    }
    
    public void run() {
        // For sending ENQIRE_LINK to SMSC
        while (mIsRunning) {
            try {
                long now = System.currentTimeMillis();
                //Reset SMS TPS
                resetTPS();
                
                if (now - mLastCheckStatus > 1000) {
                    checkStatus();
                }
                
                if (getStatus() != CONNECTED) {
                    // Disconnnect so try to connect
                    init();
                }

                // SEND Enquired link
                if (getStatus() == CONNECTED) {
                    if ((now - mLastCommunicate > ENQUIRE_SEQUENCE) && (now - mLastEnquire > ENQUIRE_SEQUENCE)) {
                        EnquireLink pkg = new EnquireLink();
                        pkg.assignSequenceNumber();
                        SMSLog.Debug(getLogName() + ": SEND ENQUIRED_LINK: " + pkg.debugString());
                        try {
                            logicaAPIsession.enquireLink(pkg);
                            mLastEnquire = now;

                        } catch (Exception ex) {
                            SMSLog.Error(ex);
                            unbind();
                            init();
                            mLastCommunicate = mLastEnquire = now;
                            continue;
                        }
                    }
                }

                // check ENQUIRE RESP
                if (now - mLastCommunicate > MY_ENQUIRE_TIME_OUT) {
                    SMSLog.Error(getLogName() + ": Does not receive ENQUIRE_LINK_RESP for too long, reset connection");
                    unbind();
                    init();
                    mLastCommunicate = now;
                    continue;
                }

                Thread.sleep(10);
            } catch (Exception ex) {
                SMSLog.Error(ex);
            }
        }
    }

    /**
     * Get Current status
     * @return 
     */
    public boolean isConnected() {
        return (status == CONNECTED);
    }
    
    /**
     * Get unique log name to write log
     * @return 
     */
    private String getLogName() {
        return "SMSSender (" + mShortCode + ") Thread " + mId;
    }

    /**
     * Set limited TPS for this interface
     * @param mMaxTPS 
     */
    public void setMaxTPS(int mMaxTPS) {
        this.mMaxTPS = mMaxTPS;
    }
    
    /**
     * Set listener for Incoming SMS (MO)
     * @param l 
     */
    public void setTextListener(SMSListener l) {
        mListener = l;
    }
    
    /**
     * Set delay time for sending long SMS
     * @param millis 
     */
    public void setLongSMSSepradeTime(int millis) {
        mSepradeBetweenLongSMS = millis;
    }
}

