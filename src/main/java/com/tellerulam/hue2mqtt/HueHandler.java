package com.tellerulam.hue2mqtt;

import java.util.*;
import java.util.logging.Logger;

import com.philips.lighting.hue.listener.*;
import com.philips.lighting.hue.sdk.*;
import com.philips.lighting.model.*;

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
		Main.t.schedule(new TimerTask(){
			@Override
			public void run()
			{
				reportLights();
			}
		},2000);
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
		L.warning("Error in bridge connection. Code "+e+": "+msg);
	}

	@Override
	public void onParsingErrors(List<PHHueParsingError> errors)
	{
		L.severe("Internal API error "+errors);
	}

	static void reportLights()
	{
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		for(PHLight l:cache.getAllLights())
		{
			PHLightState state=l.getLastKnownLightState();
			MQTTHandler.publish(
				"lamp/"+l.getName(),
				true,
				"val", state.isOn().booleanValue() ? state.getBrightness() : Integer.valueOf(0),
				"hue_bri", state.getBrightness(),
				"hue_ct", state.getCt(),
				"hue_reachable", state.isReachable(),
				"hue_colormode", state.getColorMode(),
				"hue_effect", state.getEffectMode(),
				"hue_x", state.getX(),
				"hue_y", state.getY(),
				"hue_hue", state.getHue(),
				"hue_sat", state.getSaturation(),
				"hue_transitiontime", state.getTransitionTime()
			);
		}
	}

	public static PHLight findLightByName(String name)
	{
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		for(PHLight l:cache.getAllLights())
		{
			if(name.equals(l.getName()))
				return l;
		}
		L.warning("Unable to find light "+name);
		return null;
	}

	private static final Logger L=Logger.getLogger(HueHandler.class.getName());

	public static void updateLightState(final PHLight light,PHLightState ls)
	{
		phHueSDK.getSelectedBridge().updateLightState(light, ls,new PHLightListener() {

			@Override
			public void onSuccess()
			{
				L.info("Updating state ok for "+light);
			}

			@Override
			public void onStateUpdate(Map<String, String> p, List<PHHueError> err)
			{
				reportLights();
				L.info(p.toString());
				L.info(err.toString());
			}

			@Override
			public void onError(int rc, String msg)
			{
				L.info("Updating state FAILED for "+light+" RC "+rc+": "+msg);
			}

			@Override
			public void onSearchComplete()
			{
				/* Ignore */
			}

			@Override
			public void onReceivingLights(List<PHBridgeResource> arg0)
			{
				/* Ignore */
			}

			@Override
			public void onReceivingLightDetails(PHLight arg0)
			{
				/* Ignore */
			}
		});
	}

}
