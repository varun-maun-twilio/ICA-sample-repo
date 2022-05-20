package com.twilio.examples.wav;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@WebSocket
public class WebSocketHandler {
    final static Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private AudioFormat format = new AudioFormat(
            AudioFormat.Encoding.ULAW,
            8000,
            8,
            1,
            160,
            50,
            true
    );
    // This assumes a new Handler for each connection
    private File ulawFile;
    private FileOutputStream uLawFOS;
    private String callSid;
    private boolean hasSeenMedia = false;
    private ArrayList<JSONObject>  messages =new ArrayList<JSONObject>();
    private int repeatCount = 0;
private Session session;
    
    

    @OnWebSocketConnect
    public void connected(Session session) {
        logger.info("Media WS: Connection Accepted");
        this.session = session;
       
    }

    @OnWebSocketMessage
    public void message(Session session, String message) {
    	
        try {
            JSONObject jo = new JSONObject(message);
            String event = jo.getString("event");

            if (event.equals("connected")) {
                logger.info("Media WS: Connected message received: {}", message);
            }
            else if (event.equals("start")) {
                logger.info("Media WS: Start message received: {}", message);
            
                if (ulawFile == null) {
                    callSid = jo.getJSONObject("start").getString("callSid");
                    ulawFile = new File(callSid + ".ulaw");
                    ulawFile.createNewFile();
                    uLawFOS = new FileOutputStream(ulawFile);
                }
            }
            else if (event.equals("mark")) {
                logger.info("Media WS: mark message received: {}", message);
            }
            else if (event.equals("media")) {
                String payload = jo.getJSONObject("media").getString("payload");
                byte[] decodedBytes = Base64.getDecoder().decode(payload);
                uLawFOS.write(decodedBytes);
                
                if (!this.hasSeenMedia) {
                   
                    this.hasSeenMedia = true;
                  }
                
                this.messages.add(jo);
                if (this.messages.size() >= 50) {
                  
                  this.repeat();
                }

            }
        } catch (JSONException e) {
            logger.error("Unrecognized JSON: {}", e);
        } catch (IOException ioe) {
            logger.error("IOException: {}", ioe);
        }
    }
    
    
    byte[] toPrimitives(Byte[] oBytes)
    {

        byte[] bytes = new byte[oBytes.length];
        for(int i = 0; i < oBytes.length; i++){
            bytes[i] = oBytes[i];
        }
        return bytes;

    }
    
  //byte[] to Byte[]
    Byte[] toObjects(byte[] bytesPrim) {

        Byte[] bytes = new Byte[bytesPrim.length];
        int i = 0;
        for (byte b : bytesPrim) bytes[i++] = b; //Autoboxing
        return bytes;

    }
    
    
    private void repeat() {
    	
    	try {
    	
    	ArrayList<JSONObject>  newMessages = new  ArrayList<JSONObject>();
    	newMessages.addAll(messages);
    	
    	this.messages = new ArrayList<JSONObject>();
    	
    	
    	String streamSid = newMessages.get(0).getString("streamSid");
    	
    	ArrayList<Byte> decodedBytes = new ArrayList<Byte>();
    	for(int iter=0;iter< newMessages.size();iter++) {
    		 byte[] decodedMessageBytes = Base64.getDecoder().decode(newMessages.get(iter).getJSONObject("media").getString("payload"));
    		 decodedBytes.addAll(Arrays.asList(toObjects(decodedMessageBytes)));
    	}
    	
    	
    	String datapayload = Base64.getEncoder().encodeToString(toPrimitives(decodedBytes.toArray(new Byte[decodedBytes.size()])));
    	
    	{
    	JSONObject markO = new JSONObject();
    	markO.put("event", "media");
    	markO.put("streamSid", streamSid);    	
    	JSONObject markMediaO = new JSONObject();
    	markMediaO.put("payload", datapayload);
    	markO.put("media", markMediaO);
    	
    	
    	String messageJSON = markO.toString();
    	this.session.getRemote().sendString(messageJSON);
    	}
    	
    	
    	
    	{
    	JSONObject markO = new JSONObject();
    	markO.put("event", "mark");
    	markO.put("streamSid", streamSid);    	
    	JSONObject markMediaO = new JSONObject();
    	markMediaO.put("name", "Repeat Message "+this.repeatCount);
    	markO.put("mark", markMediaO);
    	
    	
    	String messageJSON = markO.toString();
    	this.session.getRemote().sendString(messageJSON);
    	
    	this.repeatCount = this.repeatCount+1;
    	
    	
    	}
    	
    	
    	}catch(Exception ex) {
    		ex.printStackTrace();
    	}
    
    }


    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        try {
            if (uLawFOS != null)
                uLawFOS.close();
            File wavFile = new File(callSid + ".wav");
            FileOutputStream wavFOS = new FileOutputStream(wavFile);
            if (!wavFile.exists()) {
                wavFile.createNewFile();
            }
            AudioInputStream ais = new AudioInputStream(
                    new FileInputStream(ulawFile),
                    format,
                    ulawFile.length() / 160
            );
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFOS);
            wavFOS.close();

            
            wavFOS = null;
            wavFile = null;
            ulawFile = null;
            uLawFOS = null;
        } catch (Exception ex) {
            logger.error("Exception in closing: {}", ex);
        }
    }
}
