package com.tellerulam.hue2mqtt;

import java.math.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.*;

import com.eclipsesource.json.*;
import com.philips.lighting.model.*;

public class MQTTHandler
{
	private final Logger L=Logger.getLogger(getClass().getName());

	public static void init() throws MqttException
	{
		instance=new MQTTHandler();
		instance.doInit();
	}

	private static MQTTHandler instance;

	private final String topicPrefix;
	private MQTTHandler()
	{
		String tp=System.getProperty("hue2mqtt.mqtt.topic","hue");
		if(!tp.endsWith("/"))
			tp+="/";
		topicPrefix=tp;
	}

	private MqttClient mqttc;

	private void queueConnect()
	{
		shouldBeConnected=false;
		Main.t.schedule(new TimerTask(){
			@Override
			public void run()
			{
				doConnect();
			}
		},10*1000);
	}

	private class StateChecker extends TimerTask
	{
		@Override
		public void run()
		{
			if(!mqttc.isConnected() && shouldBeConnected)
			{
				L.warning("Should be connected but aren't, reconnecting");
				queueConnect();
			}
		}
	}

	private boolean shouldBeConnected;

	void processSet(String topic,MqttMessage msg)
	{
		if(msg.isRetained())
		{
			L.fine("Ignoring retained set message "+msg+" to "+topic);
			return;
		}

		String payload=new String(msg.getPayload());

		/*
		 * Possible formats:
		 *
		 * lightname <simple value>
		 * lightname <json>
		 * lightname/<datapoint> <simple value>
		 */

		int slashIx=topic.lastIndexOf('/');
		if(slashIx>=0)
		{
			// Third format
			processSetDatapoint(topic.substring(0,slashIx),topic.substring(slashIx+1),payload);
		}
		else
		{
			// One of the first two foramts
			processSetComposite(topic,payload);
		}
	}

	@SuppressWarnings("boxing")
	private void processSetComposite(String lamp, String payload)
	{
		PHLightState ls=new PHLightState();
		PHLight l=HueHandler.findLightByName(lamp);
		if(l==null)
			return;

		// Attempt to decode payload as a JSON object
		if(payload.trim().startsWith("{"))
		{
			JsonObject jso=JsonObject.readFrom(payload);
			/* TODO */
		}
		else
		{
			double level=Double.parseDouble(payload);
			if(level==0)
			{
				ls.setOn(false);
			}
			else
			{
				ls.setOn(true);
				ls.setBrightness((int)level);

			}
		}
		HueHandler.updateLightState(l,ls);
	}

	private void processSetDatapoint(String lamp, String datapoint, String payload)
	{
		/* TODO */
	}

	void processMessage(String topic,MqttMessage msg)
	{
		topic=topic.substring(topicPrefix.length(),topic.length());
		if(topic.startsWith("set/"))
			processSet(topic.substring(4),msg);
	}

	private void doConnect()
	{
		L.info("Connecting to MQTT broker "+mqttc.getServerURI()+" with CLIENTID="+mqttc.getClientId()+" and TOPIC PREFIX="+topicPrefix);

		MqttConnectOptions copts=new MqttConnectOptions();
		copts.setWill(topicPrefix+"connected", "0".getBytes(), 2, true);
		copts.setCleanSession(true);
		try
		{
			mqttc.connect(copts);
			setHueConnectionState(false);
			L.info("Successfully connected to broker, subscribing to "+topicPrefix+"(set|get)/#");
			try
			{
				mqttc.subscribe(topicPrefix+"set/#",1);
				mqttc.subscribe(topicPrefix+"get/#",1);
				shouldBeConnected=true;
			}
			catch(MqttException mqe)
			{
				L.log(Level.WARNING,"Error subscribing to topic hierarchy, check your configuration",mqe);
				throw mqe;
			}
		}
		catch(MqttException mqe)
		{
			L.log(Level.WARNING,"Error while connecting to MQTT broker, will retry: "+mqe.getMessage(),mqe);
			queueConnect(); // Attempt reconnect
		}
	}

	private void doInit() throws MqttException
	{
		String server=System.getProperty("hue2mqtt.mqtt.server","tcp://localhost:1883");
		String clientID=System.getProperty("hue2mqtt.mqtt.clientid","hue2mqtt");
		mqttc=new MqttClient(server,clientID,new MemoryPersistence());
		mqttc.setCallback(new MqttCallback() {
			@Override
			public void messageArrived(String topic, MqttMessage msg) throws Exception
			{
				try
				{
					processMessage(topic,msg);
				}
				catch(Exception e)
				{
					L.log(Level.WARNING,"Error when processing message "+msg+" for "+topic,e);
				}
			}
			@Override
			public void deliveryComplete(IMqttDeliveryToken token)
			{
				/* Intentionally ignored */
			}
			@Override
			public void connectionLost(Throwable t)
			{
				L.log(Level.WARNING,"Connection to MQTT broker lost",t);
				queueConnect();
			}
		});
		doConnect();
		Main.t.schedule(new StateChecker(),30*1000,30*1000);
	}

	static void publish(String name, boolean retain, Object... vals)
	{
		JsonObject jso=new JsonObject();
		for(int pix=0;pix<vals.length;pix+=2)
		{
			String vname=vals[pix].toString();
			Object val=vals[pix+1];
			if(val==null)
				continue;
			if(val instanceof BigDecimal)
				jso.add(vname,((BigDecimal)val).doubleValue());
			else if(val instanceof Integer)
				jso.add(vname,((Integer)val).intValue());
			else if(val instanceof Boolean)
				jso.add(vname,((Boolean)val).booleanValue()?1:0);
			else
				jso.add(vname,val.toString());
		}
		String txtmsg=jso.toString();
		MqttMessage msg=new MqttMessage(txtmsg.getBytes(StandardCharsets.UTF_8));
		msg.setQos(0);
		msg.setRetained(retain);
		try
		{
			String fullTopic=instance.topicPrefix+"status/"+name;
			instance.mqttc.publish(fullTopic, msg);
			instance.L.info("Published "+txtmsg+" to "+fullTopic+(retain?" (R)":""));
		}
		catch(MqttException e)
		{
			instance.L.log(Level.WARNING,"Error when publishing message "+txtmsg,e);
		}
	}

	public static void setHueConnectionState(boolean connected)
	{
		try
		{
			instance.mqttc.publish(instance.topicPrefix+"connected",(connected?"2":"1").getBytes(),1,true);
		}
		catch(MqttException e)
		{
			/* Ignore */
		}
	}

}
