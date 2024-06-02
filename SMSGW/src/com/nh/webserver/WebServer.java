package com.nh.webserver;

//import com.viettel.utils.configs.LoadConfigs;
import java.net.ServerSocket;
import java.io.IOException;
import com.nh.main.MyLog;

/**
 * This class accepts client connection on given port. When the connection
 * is accepted, the listener creates an instance of <code>ServerSession</code>,
 * generates new <code>PDUProcessor</code> using object derived from
 * <code>PDUProcessorFactory</code>, passes the processor to the smsc session
 * and starts the session as a standalone thread.
 */
public class WebServer implements Runnable {

    public static int RUNNING = 1;
    public static int FINISH = 0;

    private ServerSocket mServerSockets;
    private long acceptTimeout = 10;
    private final Object lock;
    private int status;
    private int mPort;
    private boolean isReceiving;
    private boolean isFirst;

    private static RequestQueue mQueue = null;
    private final String ip;
    /**
     * Constructor with control if the listener starts as a separate thread.
     * If <code>asynchronous</code> is true, then the listener is started
     * as a separate thread, i.e. the creating thread can continue after
     * calling of method <code>enable</code>. If it's false, then the
     * caller blocks while the listener does it's work, i.e. listening.
     *
     * @param port   list of listener port
     * @param logMdl index of log module
     * @param authen the authenticator object
     * @see #start()
     */
    public WebServer(String ip, int port, int thread) {
        this.lock = new Object();
        mPort = port;
        this.ip = ip;
        this.isFirst = true;
        if (mQueue == null) {
            mQueue = new RequestQueue();
        }
        
        for (int i=0; i<thread; i++) {
            WebProcessRequest p = new WebProcessRequest(i, mQueue);
            p.setHost(ip);
            p.start();
        }
    }

    /**
     * Starts the listening. If the listener is asynchronous (reccomended),
     * then new thread is created which listens on the port and the
     * <code>enable</code> method returns to the caller. Otherwise
     * the caller is blocked in the enable method.
     *
     * @see #start()
     */
    public void start() {
        if (isFirst) {
            init(mPort);
        }
        if (mServerSockets == null) {
            MyLog.Infor("none listen port declare, server is now " + " return initialize");
            return;
        }
        String log = "soap server listen on port: ";
        //noinspection SynchronizeOnNonFinalField
        synchronized (lock) {
                if (mServerSockets == null) {
                    MyLog.Infor("Listener on " + mPort + " is null, it may" +
                                               " be used in other program");
                }
                if (mServerSockets.isClosed()) {
                    int p = mServerSockets.getLocalPort();
                    try {
                        MyLog.Infor("port " + p + " is closed, reopen again ...");
                        mServerSockets = createSocket(p);
                    } catch (IOException e) {
                        //e.printStackTrace(getPrintStream());
                        MyLog.Infor("create Socket on Port:" + p + "  error");
                    }
                }
                log += mServerSockets.getLocalPort() + "  ";
        }

        MyLog.Infor(log);
        status = RUNNING;
        isReceiving = true;
        Thread t = new Thread(this);
        t.start();
    }

    /**
     * Signals the listener that it should disable listening and wait
     * until the listener stops. Note that based on the timeout settings
     * it can take some time befor this method is finished -- the listener
     * can be blocked on i/o operation and only after exiting i/o
     * it can detect that it should disable.
     *
     * @see #start()
     */
    public void stop() {
        try {
            //noinspection SynchronizeOnNonFinalField
            synchronized (lock) {
                isReceiving = false;
                while (status == RUNNING) {
                    Thread.yield();
                }

                if (mServerSockets != null) {
                    mServerSockets.close();
                }
            }
        } catch (IOException e) {
            // e.printStackTrace(getPrintStream());
            MyLog.Infor("stop soap server error");
        }
    }

    /**
     * The actual listening code which is run either from the thread
     * (for async listener) or called from <code>enable</code> method
     * (for sync listener). The method can be exited by calling of method
     * <code>disable</code>.
     *
     * @see #start()
     * @see #stop()
     */
    public void run() {
        if (status != RUNNING) {
            return;
        }
        try {
            while (isReceiving) {
                listen();
                Thread.yield();
            }
        } finally {
            status = FINISH;
        }
    }

    /**
     * The "one" listen attempt called from <code>run</code> method.
     * The listening is atomicised to allow contoled stopping of the listening.
     * The length of the single listen attempt
     * is defined by <code>acceptTimeout</code>.
     * If a connection is accepted, then new session is created on this
     * connection, new PDU processor is generated using PDU processor factory
     * and the new session is started in separate thread.
     *
     * @see #run()
     */
    private void listen() {
        //noinspection SynchronizeOnNonFinalField
        synchronized (lock) {
            try {
                if (mServerSockets == null || mServerSockets.isClosed()) {
                    // Reinit port
                    mServerSockets = createSocket(mPort);
                }
                mQueue.addToQueue(mServerSockets.accept());
            } catch (Exception e) {
//                MyLog.Error("While accept connection from client: " + e.getMessage());
//                e.printStackTrace();
            }
        }
    }

    /**
     * Sets new timeout for accepting new connection.
     * The listening blocks the for maximum this time, then it
     * exits regardless the connection was acctepted or not.
     *
     * @param value the new value for accept timeout
     */
    public void setAcceptTimeout(int value) {
        acceptTimeout = value;
    }

    /**
     * Returns the current setting of accept timeout.
     *
     * @return the current accept timeout
     * @see #setAcceptTimeout(int)
     */
    public long getAcceptTimeout() {
        return acceptTimeout;
    }

    private ServerSocket createSocket(int port) throws IOException {
        MyLog.Infor("Binding to Port " + port);
        ServerSocket socket = new ServerSocket(port);
        socket.setSoTimeout((int) getAcceptTimeout());
        return socket;
    }

    protected void init(int port) {
        MyLog.Infor("Create instance soap listener on " + port);
        try {
            mServerSockets = createSocket(port);
        } catch (IOException e) {
            MyLog.Error("Binding to Port " + port + " error, port has already used");
            System.exit(1);
        }
    }

    /**
     * do reload listen port
     *
     * @param port the list of listener port
     */
    public void reloadConfig(int port) {
        //noinspection SynchronizeOnNonFinalField
//        synchronized (lock) {
//            int movSocket[] = new int[mServerSockets.length];
//            for (int i = 0; i < movSocket.length; i++) {
//                movSocket[i] = -1;
//            }
//            for (int i = 0; i < port.length; i++) {
//                int indexOfPort = findServerSocket(port[i]);
//                if (indexOfPort == -1) {
//                    try {
//                        newSocket[i] = createSocket(port[i]);
//                    } catch (IOException e) {
//                        //e.printStackTrace(getPrintStream());
//                        MyLog.Infor("add new  Port " + port[i] + " error, port has already used");
//                    }
//                } else {
//                    newSocket[i] = mServerSockets[indexOfPort];
//                    movSocket[indexOfPort] = 1;
//                }
//            }
//            //close unused socket
//            for (int i = 0; i < movSocket.length; i++) {
//                if (movSocket[i] == 1) {
//                    continue;
//                }
//                try {
//                    int _port = mServerSockets[i].getLocalPort();
//                    MyLog.Infor("Close unused port  " + _port);
//                    mServerSockets[i].close();
//                } catch (IOException e) {
//                    MyLog.Infor(e.getMessage());
//                }
//            }
//            mServerSockets = newSocket;
//        }
//        String log = "VASPServer listen on Port: ";
//        for (int i = 0; i < mServerSockets.length; i++) {
//            if (mServerSockets[i] != null) {
//                log += mServerSockets[i].getLocalPort();
//            }
//            if (i != mServerSockets.length - 1) {
//                log += ":";
//            }
//        }
//        MyLog.Infor(log);
    }
}

