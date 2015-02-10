package com.tellerulam.hue2mqtt;

import java.util.*;

import com.philips.lighting.hue.sdk.*;
import com.philips.lighting.model.*;
import com.sun.istack.internal.logging.*;

public class HueHandler implements PHSDKListener
{
	private static PHHueSDK phHueSDK;

	static void init()
	{
		phHueSDK=PHHueSDK.getInstance();
		phHueSDK.setAppName("hue2mqtt");
		phHueSDK.setDeviceName("hue2mqtt");
		phHueSDK.getNotificationManager().registerSDKListener(new HueHandler());
		String specifiedBridge=System.getProperty("hue2mqtt.hue.bridge");

		if(specifiedBridge==null)
		{
			PHBridgeSearchManager sm = (PHBridgeSearchManager)phHueSDK.getSDKService(PHHueSDK.SEARCH_BRIDGE);
		    sm.search(true, true);
		}
		else
		{
			PHAccessPoint pap=new PHAccessPoint();
			pap.setIpAddress(specifiedBridge);
			pap.setUsername("huetomqttuser");
			phHueSDK.connect(pap);
		}
	}

	@Override
	public void onAccessPointsFound(List<PHAccessPoint> bridges)
	{
		if(bridges.size()==0)
		{
			L.severe("No Hue bridge found");
			System.exit(1);
		}
		if(bridges.size()!=1)
		{
			for(PHAccessPoint pap:bridges)
				L.info("Found Hue bridge @ "+pap.getIpAddress());
			L.warning("Multiple bridges found. Will connect to the first bridge. This may not be what you want! Specify the bridge you want to connect to explicitely with hue.bridge=<name or ip>");
		}
		PHAccessPoint pap=bridges.get(0);
		pap.setUsername("huetomqttuser");
		L.info("Connecting to Hue bridge @ "+pap.getIpAddress());
		phHueSDK.connect(pap);
	}

	@Override
	public void onAuthenticationRequired(PHAccessPoint pap)
	{
		L.severe("IMPORTANT! AUTHENTICATION REQUIRED -- press the button on your Hue Bridge "+pap.getIpAddress()+" within 30s to authenticate hue2mqtt!");
		phHueSDK.startPushlinkAuthentication(pap);
	}

	@Override
	public void onBridgeConnected(PHBridge b)
	{
		L.info("Successfully connected to Hue bridge");
		phHueSDK.setSelectedBridge(b);
		phHueSDK.enableHeartbeat(b, PHHueSDK.HB_INTERVAL);
		MQTTHandler.setHueConnectionState(true);
		reportLights();
	}

	@Override
	public void onCacheUpdated(List<Integer> notification, PHBridge b)
	{
		L.info("Cache updated "+notification);
		if(notification.contains(PHMessageType.LIGHTS_CACHE_UPDATED))
			reportLights();
	}

	@Override
	public void onConnectionLost(PHAccessPoint pap)
	{
		L.warning("Connection to bridge "+pap.getIpAddress()+" lost");
		MQTTHandler.setHueConnectionState(false);
	}

	@Override
	public void onConnectionResumed(PHBridge b)
	{
		/* Appears to be called on every HB */
		/*L.warning("Connection to bridge resumed");*/
	}

	@Override
	public void onError(int e, String msg)
	{
		L.warning("Error connecting to bridge. Code "+e+": "+msg);
	}

	@Override
	public void onParsingErrors(List<PHHueParsingError> errors)
	{
		L.severe("Internal API error "+errors);
	}

	private void reportLights()
	{
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		for(PHLight l:cache.getAllLights())
		{
			PHLightState state=l.getLastKnownLightState();
			MQTTHandler.publish(
				l.getName(),
				true,
				"val", state.isOn().booleanValue() ? state.getBrightness() : Integer.valueOf(0),
				"hue_ct", state.getCt(),
				"hue_reachable", state.isReachable()
			);
		}
	}

	private static final Logger L=Logger.getLogger(HueHandler.class);
}
