/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender;

import org.smpp.pdu.DeliverSM;

/**
 *
 * @author hoand
 */
public interface SMSListener {
    public void onTextSMSArrive(String src, String dst, DeliverSM sms);
    public void onLongTextSMSArrive(String src, String dst, DeliverSM firstSMS, byte[] data);
}
