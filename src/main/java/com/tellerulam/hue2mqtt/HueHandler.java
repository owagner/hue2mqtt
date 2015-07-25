package com.tellerulam.hue2mqtt;

import java.util.*;
import java.util.logging.Logger;

import com.eclipsesource.json.*;
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

	private void reportGroups(PHBridgeResourcesCache cache)
	{
		StringBuilder r=new StringBuilder("Available groups:");
		for(Map.Entry<String,PHGroup> me:cache.getGroups().entrySet())
		{
			r.append(' ');
			r.append(me.getKey());
			r.append('/');
			r.append(me.getValue().getName());
		}
		L.info(r.toString());
	}
	private void reportScenes(PHBridgeResourcesCache cache)
	{
		StringBuilder r=new StringBuilder("Available scenes:");
		for(Map.Entry<String,PHScene> me:cache.getScenes().entrySet())
		{
			r.append(' ');
			r.append(me.getKey());
			r.append('/');
			r.append(me.getValue().getName());
		}
		L.info(r.toString());
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
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		reportGroups(cache);
		reportScenes(cache);
	}

	@Override
	public void onCacheUpdated(List<Integer> notification, PHBridge b)
	{
		L.fine("Cache updated "+notification);
		if(notification.contains(PHMessageType.LIGHTS_CACHE_UPDATED))
			reportLights();
		if(notification.contains(PHMessageType.GROUPS_CACHE_UPDATED))
		{
			PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
			reportGroups(cache);
		}
		if(notification.contains(PHMessageType.SCENE_CACHE_UPDATED))
		{
			PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
			reportScenes(cache);
		}
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

	private static String reworkName(Object enumValue)
	{
		String name=enumValue.toString();
		int usc=name.indexOf('_');
		if(usc>=0)
			name=name.substring(usc+1);
		// Use the value of the raw API
		if("HUE_SATURATION".equals(name))
			return "hs";
		// Occasionally we receive status updates with the modes set to UNKNOWN
		// To avoid bogus publishes, we change that to "none"
		if("UNKNOWN".equals(name))
			return "none";
		return name.toLowerCase();
	}

	static void reportLights()
	{
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		for(PHLight l:cache.getAllLights())
		{
			PHLightState state=l.getLastKnownLightState();
			/*
			 * Generate a JSON object with the state
			 */
			WrappedJsonObject json=new WrappedJsonObject();
			json.add("on",state.isOn());
			json.add("bri",state.getBrightness());
			json.add("hue",state.getHue());
			json.add("sat",state.getSaturation());
			json.add("ct",state.getCt());
			json.add("transitiontime",state.getTransitionTime());
			json.add("alert",reworkName(state.getAlertMode()));
			json.add("effect",reworkName(state.getEffectMode()));
			json.add("colormode",reworkName(state.getColorMode()));
			json.add("reachable",state.isReachable());
			if(state.getX()!=null)
			{
				JsonArray xy=new JsonArray();
				xy.add(state.getX().floatValue());
				xy.add(state.getY().floatValue());
				json.add("xy",xy);
			}
			MQTTHandler.publishIfChanged(
				"lights/"+l.getName(),
				true,
				"val", state.isOn().booleanValue() ? state.getBrightness() : Integer.valueOf(0),
				"hue_state", json
			);
		}
	}

	private static final Logger L=Logger.getLogger(HueHandler.class.getName());

	private static final PHBridgeResource DEFAULT_GROUP_RESOURCE=new PHBridgeResource(null, null);

	public static PHBridgeResource findResourceByName(String name)
	{
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		if(name.startsWith("lights/"))
		{
			name=name.substring(7);
			for(PHLight l:cache.getAllLights())
			{
				if(name.equals(l.getName()))
					return l;
			}
			return cache.getLights().get(name);
		}
		if(name.startsWith("groups/"))
		{
			name=name.substring(7);
			if("0".equals(name))
				return DEFAULT_GROUP_RESOURCE;
			for(PHGroup g:cache.getAllGroups())
			{
				if(name.equals(g.getName()))
					return g;
			}
			return cache.getGroups().get(name);
		}
		return null;
	}

	public static void updateLightState(String name,PHLightState ls)
	{
		final PHBridgeResource res=findResourceByName(name);
		if(res==null)
		{
			L.info("Unable to find resource by name "+name);
			return;
		}

		if(res instanceof PHLight)
		{
			phHueSDK.getSelectedBridge().updateLightState((PHLight)res, ls,new PHLightListener() {

				@Override
				public void onSuccess()
				{
					L.fine("Updating state ok for "+res);
				}

				@Override
				public void onStateUpdate(Map<String, String> p, List<PHHueError> err)
				{
					// Done by cache_updated notification
					//reportLights();
				}

				@Override
				public void onError(int rc, String msg)
				{
					L.info("Updating state FAILED for "+res+" RC "+rc+": "+msg);
				}

				@Override
				public void onSearchComplete()
				{
					/* Ignore */
				}

				@Deprecated
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
		else if(res==DEFAULT_GROUP_RESOURCE)
		{
			phHueSDK.getSelectedBridge().setLightStateForDefaultGroup(ls);
		}
		else if(res instanceof PHGroup)
		{
			phHueSDK.getSelectedBridge().setLightStateForGroup(res.getIdentifier(),ls,new PHGroupListener(){

				@Override
				public void onError(int rc, String msg)
				{
					L.info("Updating state FAILED for "+res+" RC "+rc+": "+msg);
				}

				@Override
				public void onStateUpdate(Map<String, String> p, List<PHHueError> err)
				{
					// Done by cache_updated notification
					//reportLights();
				}

				@Override
				public void onSuccess()
				{
					L.fine("Updating state ok for "+res);
				}

				@Override
				public void onCreated(PHGroup arg0)
				{
					// Ignore
				}

				@Override
				public void onReceivingAllGroups(List<PHBridgeResource> arg0)
				{
					// Ignore
				}

				@Override
				public void onReceivingGroupDetails(PHGroup arg0)
				{
					// Ignore
				}

			});
		}
	}
}
