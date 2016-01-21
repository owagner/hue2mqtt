package com.tellerulam.hue2mqtt;

import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.*;

import com.eclipsesource.json.*;
import com.eclipsesource.json.JsonObject.Member;
import com.philips.lighting.model.*;
import com.philips.lighting.model.PHLight.PHLightAlertMode;
import com.philips.lighting.model.PHLight.PHLightColorMode;
import com.philips.lighting.model.PHLight.PHLightEffectMode;

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

	private final Pattern topicPattern=Pattern.compile("([^/]+/[^/]+)(?:/((?:on|bri|hue|sat|ct|alert|effect|colormode|reachable|x|y|transitiontime)(?:_inc)?))?");

	private final Map<String,Integer> transitionTimeCache=new HashMap<>();

	void processSet(String topic,MqttMessage msg)
	{
		String payload=new String(msg.getPayload());
		/*
		 * Possible formats:
		 *
		 * object/name <simple value>
		 * object/name <json>
		 * object/name/<datapoint> <simple value>
		 */
		Matcher m=topicPattern.matcher(topic);
		if(!m.matches())
		{
			L.warning("Received set to unparsable topic "+topic);
			return;
		}
		if(m.group(2)!=null)
		{
			// Third format
			if("transitiontime".equals(m.group(2)))
			{
				// We only cache that, for future reference
				transitionTimeCache.put(m.group(1),Integer.valueOf(payload));
				return;
			}
			if(msg.isRetained())
			{
				L.fine("Ignoring retained set message "+msg+" to "+topic);
				return;
			}
			processSetDatapoint(m.group(1),m.group(2),payload);
		}
		else
		{
			if(msg.isRetained())
			{
				L.fine("Ignoring retained set message "+msg+" to "+topic);
				return;
			}
			processSetComposite(topic,payload);
		}
	}

	@SuppressWarnings("boxing")
	private void processSetComposite(String resource, String payload)
	{
		PHLightState ls=new PHLightState();

		// Attempt to decode payload as a JSON object
		if(payload.trim().startsWith("{"))
		{
			JsonObject jso=(JsonObject)Json.parse(payload);
			for(Iterator<Member> mit=jso.iterator();mit.hasNext();)
			{
				Member m=mit.next();
				JsonValue val=m.getValue();
				addDatapointToLightState(ls, m.getName(), val.isString()?val.asString():val.toString());
			}
		}
		else
		{
			double level=Double.parseDouble(payload);
			if(level<1)
			{
				ls.setOn(false);
			}
			else
			{
				if(level>254)
					level=254;
				ls.setOn(true);
				ls.setBrightness((int)level);
			}
			// May be null
			ls.setTransitionTime(transitionTimeCache.get(resource));
		}
		HueHandler.updateLightState(resource,ls);
	}

	/*
	 * Parse a number and truncate to integer
	 */
	private int parseNumber(String number)
	{
		return (int)Double.parseDouble(number);
	}

	private void addDatapointToLightState(PHLightState ls,String datapoint,String value)
	{
		switch(datapoint)
		{
			case "on":
				if("1".equals(value)||"on".equals(value)||"true".equals(value))
					ls.setOn(Boolean.TRUE);
				else
					ls.setOn(Boolean.FALSE);
				break;
			case "bri":
				ls.setBrightness(parseNumber(value));
				break;
			case "bri_inc":
				ls.setIncrementBri(parseNumber(value));
				break;
			case "hue":
				ls.setHue(parseNumber(value));
				break;
			case "hue_inc":
				ls.setIncrementHue(parseNumber(value));
				break;
			case "sat":
				ls.setSaturation(parseNumber(value));
				break;
			case "sat_inc":
				ls.setIncrementSat(parseNumber(value));
				break;
			case "x":
				ls.setX(Float.valueOf(value));
				break;
			case "x_inc":
				ls.setIncrementX(Float.valueOf(value));
				break;
			case "y":
				ls.setY(Float.valueOf(value));
				break;
			case "y_inc":
				ls.setIncrementY(Float.valueOf(value));
				break;
			case "ct":
				ls.setCt(parseNumber(value));
				break;
			case "ct_inc":
				ls.setIncrementCt(parseNumber(value));
				break;
			case "transitiontime":
				ls.setTransitionTime(parseNumber(value));
				break;
			case "colormode":
				ls.setColorMode(parseColorMode(value));
				break;
			case "alert":
				ls.setAlertMode(parseAlertMode(value));
				break;
			case "effect":
				ls.setEffectMode(parseEffectMode(value));
				break;
			default:
				throw new IllegalArgumentException("Attempting to set unknown datapoint "+datapoint+" to value "+value);
		}
	}

	private PHLightColorMode parseColorMode(String value)
	{
		switch(value)
		{
			case "ct":
				return PHLightColorMode.COLORMODE_CT;
			case "xy":
				return PHLightColorMode.COLORMODE_XY;
			case "hs":
				return PHLightColorMode.COLORMODE_HUE_SATURATION;
			default:
				throw new IllegalArgumentException("Unknown color mode "+value);
		}
	}

	private PHLightAlertMode parseAlertMode(String value)
	{
		switch(value)
		{
			case "lselect":
				return PHLightAlertMode.ALERT_LSELECT;
			case "select":
				return PHLightAlertMode.ALERT_SELECT;
			case "none":
				return PHLightAlertMode.ALERT_NONE;
			default:
				throw new IllegalArgumentException("Unknown alert mode "+value);
		}
	}

	private PHLightEffectMode parseEffectMode(String value)
	{
		switch(value)
		{
			case "colorloop":
				return PHLightEffectMode.EFFECT_COLORLOOP;
			case "none":
				return PHLightEffectMode.EFFECT_NONE;
			default:
				throw new IllegalArgumentException("Unknown effect mode "+value);
		}
	}

	private void processSetDatapoint(String resource, String datapoint, String payload)
	{
		PHLightState ls=new PHLightState();
		addDatapointToLightState(ls,datapoint,payload);
		// May be null
		ls.setTransitionTime(transitionTimeCache.get(resource));
		HueHandler.updateLightState(resource,ls);
	}

	void processMessage(String topic,MqttMessage msg)
	{
		try
		{
			topic=topic.substring(topicPrefix.length(),topic.length());
			if(topic.startsWith("set/"))
				processSet(topic.substring(4),msg);
		}
		catch(Exception e)
		{
			L.log(Level.WARNING, "Exception when processing published message to "+topic+": "+msg,e);
		}
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
			L.info("Successfully connected to broker, subscribing to "+topicPrefix+"set/#");
			try
			{
				mqttc.subscribe(topicPrefix+"set/#",1);
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

	static private Map<String,String> previouslyPublishedValues=new HashMap<>();

	static void publishDistinct(String name, String key, Object val)
	{
		Boolean retain = true;

		WrappedJsonObject jso=new WrappedJsonObject();

		jso.add("val", val);
		String txtmsg=jso.toString();

		MqttMessage msg=new MqttMessage(txtmsg.getBytes(StandardCharsets.UTF_8));
		msg.setQos(0);
		msg.setRetained(retain);
		try
		{
			String fullTopic=instance.topicPrefix+"status/"+name+"/"+key;
			instance.mqttc.publish(fullTopic, msg);
			instance.L.info("Published "+txtmsg+" to "+fullTopic+(retain?" (R)":""));
		}
		catch(MqttException e)
		{
			instance.L.log(Level.WARNING,"Error when publishing message "+val,e);
		}
	}

	static void publishIfChanged(String name, boolean retain, Object... vals)
	{
		WrappedJsonObject jso=new WrappedJsonObject();
		for (int pix = 0; pix < vals.length; pix += 2) {
			String vname=vals[pix].toString();
			Object val=vals[pix+1];
			jso.add(vname,val);

		}
		String txtmsg=jso.toString();
		if(txtmsg.equals(previouslyPublishedValues.put(name,txtmsg)))
			return;
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
