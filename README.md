hue2mqtt
========

  Written and (C) 2015-16 Oliver Wagner <owagner@tellerulam.com> 
  
  Provided under the terms of the MIT license.


Overview
--------
Gateway between a Philips Hue bridge and MQTT, using the official Philips
Java API library.

It is intended as a building block in heterogenous smart home environments where 
an MQTT message broker is used as the centralized message bus.
See https://github.com/mqtt-smarthome for a rationale and architectural overview.


Topic structure
===============
hue2mqtt follows the mqtt-smarthome topic structure with a top-level prefix and a function like
_status_ and _set_. Lamp, group and scene names are read from the Hue bridge.

Status reports are sent to the topic

    hue/status/lights/<lampname>
    
The payload is a JSON encoded object with the following fields:

* val - either 0 if the lamp is off, or the current brightness (1..254)
* hue_state - A JSON object which has the complete lamp state as returned from the Hue API:
   * on: boolean, whether the lamp is on
   * bri: current brightness 1..254
   * hue: hue from 0..65535
   * sat: saturation from 0..254
   * xy: an array of floats containing the coordinates (0..1) in CIE colorspace
   * ct: Mired color temperature (153..500)
   * alert: alert effect, textual
   * effect: color effect, textual
   * colormode: current color mode, textual (ct, hs, or xy)
   * reachable: boolean, whether the light is reachable

Setting state is possible in one of three ways:    

Method 1: Publishing a simple integer value to
    
    hue/set/lights/<lampname>
    
will for value=0 turn off the lamp and for values > 0 turn the lamp on and set the
brightness to the given value.

Method 2: Publishing a JSON encoded object to

    hue/set/lights/<lampname>

will set multiple parameters of the given lamp. The field names are the same as
the ones used in the hue_state state object. Additionally, a field
"transitiontime" can be specified which defines the transitiontime to the new
state in multiple of 100ms.

Method 3: Publishing a simple value to

	hue/set/lights/<lampname>/<datapoint>
	
will distinctly set a single datapoint (equal to the field names in the composite
JSON state object) to the simple value.

The fields "bri", "hue", "sat", "x", "y" and "ct" have variants with a "_inc" suffix
which accept a relative value. For example, setting "bri_inc" to "5" will increase
the brightness by 5, setting "bri_inc" to "-5" will decrease the brightness by 5.
The values will clip properly within their allowed range.

The same is possible with groups:

	hue/set/groups/<groupname>

The special group name 0 is also recognized and refers to the default group which contains
all lights connected to a bridge.


Authentication
--------------
Like all applications connecting to a Hue bridge, hue2mqtt needs to be authenticated using push link
at least once. The bridge will then assign a whitelist username (in fact a token) which is automatically
used on subsequent connections. The token is stored using Java Preferences. 

When authentication is required, a one-shot not retained message is published to topic

	hue/status/authrequired
	

### Available options:    

- bridge.id

  ID of the Hue bridge to connect to. Required if there is more than one bridge on the network.
  
- bridge.ip

  Like ID, but using the IP address. Not recommended.

- mqtt.server

  ServerURI of the MQTT broker to connect to. Defaults to "tcp://localhost:1883".
  
- mqtt.clientid

  ClientID to use in the MQTT connection. Defaults to "hue".
  
- mqtt.topic

  The topic prefix used for publishing and subscribing. Defaults to "knx/".


Dependencies
------------
* Java 1.7 SE Runtime Environment: https://www.java.com/
* Eclipse Paho: https://www.eclipse.org/paho/clients/java/ (used for MQTT communication)
* Minimal-JSON: https://github.com/ralfstx/minimal-json (used for JSON creation and parsing)
* Philips HUE Java API Library: https://github.com/PhilipsHue/PhilipsHueSDK-Java-MultiPlatform-Android

[![Build Status](https://travis-ci.org/owagner/hue2mqtt.svg)](https://travis-ci.org/owagner/hue2mqtt) Automatically built jars can be downloaded from the release page on GitHub at https://github.com/owagner/hue2mqtt/releases


History
-------
* 0.12 - 2016/06/11 - owagner
  - will now always go through bridge discovery. Bridges can be specified using either their ID (preferred) or IP.
    Username is stored per ID.
  - will now publish a non-retained message to topic/status/authrequired if authentication is required.

* 0.11 - 2016/05/28 - owagner/hobbyquaker
  - adapted to new 1.8.1+ API scheme of whitelist usernames being assigned by the bridge.
    The assigned username is stored using Java Preferences. The scheme should be compatible
    with already authorized hue2mqtt instances, which used a hardcoded username of "hue2mqttuser".
  - Hue API libraries updated to 0.11.2.

* 0.10 - 2016/02/23 - owagner
  - attempt to reconnect every 10s if bridge connection errors out

* 0.9 - 2015/10/01 - owagner
  - truncate float numbers to integer when setting integer-only datapoints. Fixes #6
  - update Philip Hue lib to 1.8.3

* 0.8 - 2015/09/28 - hobbyquaker
  - fixed set alert
  - fixed set effect

* 0.7 - 2015/07/25 - owagner
  - fixed direct datapoint set variant of _inc fields
  - updated eclipse-paho to 1.0.2 and minimal-json to 0.9.4
  
* 0.6 - 2015/07/15 - owagner
  - updated SDK libs to 1.8
  - added support for the "_inc" variants of fields. Implements #3

* 0.5 - 2015/06/25 - owagner
  - fix syslog logging to ignore intermediate IO errors
  - minimal-json updated to 0.9.2

* 0.4 - 2015/03/09 - owagner
  - better output of available groups and scenes after initial connect
  
     
