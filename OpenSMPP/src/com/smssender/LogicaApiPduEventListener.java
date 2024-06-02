/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

import org.smpp.ServerPDUEvent;
import org.smpp.ServerPDUEventListener;
import org.smpp.Session;
import org.smpp.SmppObject;
import org.smpp.pdu.PDU;
import org.smpp.util.Queue;

/**
 *
 * @author hoand
 */
public class LogicaApiPduEventListener extends SmppObject implements ServerPDUEventListener {
	protected Session logicaAPIsession;
	protected Queue requestEvents = new Queue();

	public LogicaApiPduEventListener(Session session) {
		this.logicaAPIsession = session;
	}

	/* (non-Javadoc)
	 * @see com.logica.smpp.ServerPDUEventListener#handleEvent(com.logica.smpp.ServerPDUEvent)
	 */
	public void handleEvent(ServerPDUEvent event) {
		PDU logicaAPIpdu = event.getPDU();
		if (logicaAPIpdu.isRequest()) {
			SMSLog.Infor(
				"async request received, enqueuing " + logicaAPIpdu.debugString());
			synchronized (requestEvents) {
				requestEvents.enqueue(event);
				requestEvents.notify();
			}
		} else if (logicaAPIpdu.isResponse()) {
			SMSLog.Infor("async response received " + logicaAPIpdu.debugString());
		} else {
			SMSLog.Infor(
				"pdu of unknown class (not request nor "
					+ "response) received, discarding "
					+ logicaAPIpdu.debugString());
		}
	}

	/**
	 * Returns received pdu from the queue. If the queue is empty,
	 * the method blocks for the specified timeout.
	 */
	public ServerPDUEvent getRequestEvent(long timeout) {
		ServerPDUEvent pduEvent = null;
		synchronized (requestEvents) {
			if (requestEvents.isEmpty()) {
				try {
					requestEvents.wait(timeout);
				} catch (InterruptedException e) {
					// ignoring, actually this is what we're waiting for
				}
			}
			if (!requestEvents.isEmpty()) {
				pduEvent = (ServerPDUEvent) requestEvents.dequeue();
			}
		}
		return pduEvent;
	}

}
