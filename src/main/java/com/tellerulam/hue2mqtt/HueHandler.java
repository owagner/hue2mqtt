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

	private static HashMap<String, HashMap<String, Object>> valueCache = new HashMap<String, HashMap<String, Object>>();


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
			L.info("trying to connect with username huetomqttuser");
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
		L.info("Connecting to Hue bridge @ "+pap.getIpAddress() + " with username huetomqttuser");
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
		String lightName;
		PHBridgeResourcesCache cache=phHueSDK.getSelectedBridge().getResourceCache();
		for(PHLight l:cache.getAllLights())
		{
			lightName = l.getName();

			if (!valueCache.containsKey(lightName)) {
				L.info("creating new valueCache for " + lightName);
				valueCache.put(lightName, new HashMap<String, Object>());
			}

			PHLightState state=l.getLastKnownLightState();


			/*
			 * 	Publish distinct datapoints
			 */
			if (valueCache.get(lightName).get("on") ==  null || state.isOn() != valueCache.get(lightName).get("on")) {
				valueCache.get(lightName).put("on", state.isOn());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.isOn() != null) MQTTHandler.publishDistinct("lights/"+lightName, "on", state.isOn());
			}

			if (valueCache.get(lightName).get("bri") == null || !valueCache.get(lightName).get("bri").equals(state.getBrightness())) {
				valueCache.get(lightName).put("bri", state.getBrightness());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getBrightness() != null) MQTTHandler.publishDistinct("lights/"+lightName, "bri", state.getBrightness());
			}

			if (valueCache.get(lightName).get("hue") == null || !valueCache.get(lightName).get("hue").equals(state.getHue())) {
				valueCache.get(lightName).put("hue", state.getHue());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getHue() != null) MQTTHandler.publishDistinct("lights/"+lightName, "hue", state.getHue());
			}

			if (valueCache.get(lightName).get("sat") == null || !valueCache.get(lightName).get("sat").equals(state.getSaturation())) {
				valueCache.get(lightName).put("sat", state.getSaturation());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getSaturation() != null) MQTTHandler.publishDistinct("lights/"+lightName, "sat", state.getSaturation());
			}

			if (valueCache.get(lightName).get("ct") == null || !valueCache.get(lightName).get("ct").equals(state.getCt())) {
				valueCache.get(lightName).put("ct", state.getCt());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getCt() != null) MQTTHandler.publishDistinct("lights/"+lightName, "ct", state.getCt());
			}

			if (valueCache.get(lightName).get("transitiontime") == null || !valueCache.get(lightName).get("transitiontime").equals(state.getTransitionTime())) {
				valueCache.get(lightName).put("transitiontime", state.getTransitionTime());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getTransitionTime() != null) MQTTHandler.publishDistinct("lights/"+lightName, "transitiontime", state.getTransitionTime());
			}

			if (valueCache.get(lightName).get("alert") == null || !valueCache.get(lightName).get("alert").equals(state.getAlertMode())) {
				valueCache.get(lightName).put("alert", state.getAlertMode());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getAlertMode() != null) MQTTHandler.publishDistinct("lights/"+lightName, "alert", reworkName(state.getAlertMode()));
			}

			if (valueCache.get(lightName).get("effect") == null || !valueCache.get(lightName).get("effect").equals(state.getEffectMode())) {
				valueCache.get(lightName).put("effect", state.getEffectMode());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getEffectMode() != null) MQTTHandler.publishDistinct("lights/"+lightName, "effect", reworkName(state.getEffectMode()));
			}

			if (valueCache.get(lightName).get("colormode") == null || !valueCache.get(lightName).get("colormode").equals(reworkName(state.getColorMode()))) {
				valueCache.get(lightName).put("colormode", reworkName(state.getColorMode()));
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.getColorMode() != null) MQTTHandler.publishDistinct("lights/"+lightName, "colormode", reworkName(state.getColorMode()));
			}

			if (valueCache.get(lightName).get("reachable") == null || !valueCache.get(lightName).get("reachable").equals(state.isReachable())) {
				valueCache.get(lightName).put("reachable", state.isReachable());
				if (Boolean.getBoolean("hue2mqtt.mqtt.enableDistinctPublish") && state.isReachable() != null) MQTTHandler.publishDistinct("lights/"+lightName, "reachable", state.isReachable());
			}


			/*
			 * Generate and publish a JSON object of the combined state
			 */
			if (!Boolean.getBoolean("hue2mqtt.mqtt.disableCombinedPublish")) {
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
				json.add("reachable", state.isReachable());

				if(state.getX()!=null)
				{
					JsonArray xy=new JsonArray();
					xy.add(state.getX().floatValue());
					xy.add(state.getY().floatValue());
					json.add("xy",xy);
				}

				L.info("publishIfChanged " + lightName);
				MQTTHandler.publishIfChanged(
						"lights/"+lightName,
						true,
						"val", state.isOn().booleanValue() ? state.getBrightness() : Integer.valueOf(0),
						"hue_state", json
				);
			}
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

	@Override
	public void onBridgeConnected(PHBridge b, String name)
	{
		L.info("Successfully connected to Hue bridge as "+name);
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
}
