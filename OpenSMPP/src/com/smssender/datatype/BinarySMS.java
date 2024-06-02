/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smssender.datatype;

import java.io.ByteArrayOutputStream;

/**
 *
 * @author hoand
 */
public interface BinarySMS {
    public static final int SMS_OTA_MESSAGE = 0;
    public static final int SMS_OMA_MESSAGE = 1;

    /**
     * Get Binary data of SMS
     * @return Binary data.
     */
    public ByteArrayOutputStream getData();

    /**
     * Get Binary data of SMS
     * @return Type of SMS.
     */
    public int getSMSType();

   /**
    * get Source Port when send to Handset
    */
    public int getSrcPort();

   /**
    * get Dest Port when send to Handset
    */
    public int getDestPort();

    public void setSrcPort(int val);

    public void setDstPort(int val);

    /**
     *
     * @return Header to set before send to Handset
     */

    public ByteArrayOutputStream getWSPHeader();
}
