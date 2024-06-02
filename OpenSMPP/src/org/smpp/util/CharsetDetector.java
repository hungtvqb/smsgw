/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.smpp.util;

/**
 *
 * @author hoand
 */
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 *
 * @author Georgios Migdos <cyberpython@gmail.com>
 */
public class CharsetDetector {
//    public static final String [] CHARSETS = new String[] {"US-ASCII", "ISO-8859-1", "UTF-8", "UTF-16BE", "UTF-16LE", "UTF-16"};
    public static final String [] CHARSETS = new String[] {"ASCII", "UTF-16", "UTF-8"};
    
    public static Charset detectCharset(String str) {
        Charset charset = null;

        for (String charsetName : CHARSETS) {
            charset = detectCharset(str.getBytes(), Charset.forName(charsetName));
            if (charset != null) {
                break;
            }
        }

        return charset;
    }
    
    public static String detectCharsetStr(String str) {
        String result = null;

        for (String charsetName : CHARSETS) {
            Charset charset = detectCharset(str.getBytes(), Charset.forName(charsetName));
            if (charset != null) {
                result = charsetName;
                break;
            }
        }

        return result;
    }
    
    public static String detectCharsetStr(byte[] b) {
        String result = null;

        for (String charsetName : CHARSETS) {
            Charset charset = detectCharset(b, Charset.forName(charsetName));
            if (charset != null) {
                result = charsetName;
                break;
            }
        }

        return result;
    }

    private static Charset detectCharset(byte[] b, Charset charset) {
        try {
            BufferedInputStream input = new BufferedInputStream(new ByteArrayInputStream(b));

            CharsetDecoder decoder = charset.newDecoder();
            decoder.reset();

            byte[] buffer = new byte[512];
            boolean identified = false;
            while ((input.read(buffer) != -1) && (!identified)) {
                identified = identify(buffer, decoder);
            }

            input.close();

            if (identified) {
                return charset;
            } else {
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    private static boolean identify(byte[] bytes, CharsetDecoder decoder) {
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return false;
        }
        return true;
    }

//    public static void main(String[] args) {
//        File f = new File("example.txt");
//
//        String[] charsetsToBeTested = {"UTF-8", "windows-1253", "ISO-8859-7"};
//
//        CharsetDetector cd = new CharsetDetector();
//        Charset charset = cd.detectCharset(f, charsetsToBeTested);
//
//        if (charset != null) {
//            try {
//                InputStreamReader reader = new InputStreamReader(new FileInputStream(f), charset);
//                int c = 0;
//                while ((c = reader.read()) != -1) {
//                    System.out.print((char)c);
//                }
//                reader.close();
//            } catch (FileNotFoundException fnfe) {
//                fnfe.printStackTrace();
//            }catch(IOException ioe){
//                ioe.printStackTrace();
//            }
//
//        }else{
//            System.out.println("Unrecognized charset.");
//        }
//    }
    public static boolean isUFT16(byte[] b) {
        return (b[0] == (byte)0 || b[0] == (byte)1);
    }
}