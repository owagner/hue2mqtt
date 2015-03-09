hue2mqtt
========

  Written and (C) 2015 Oliver Wagner <owagner@tellerulam.com> 
  
  Provided under the terms of the MIT license.


Overview
--------
Gateway between a Philips Hue bridge and MQTT

It is intended as a building block in heterogenous smart home environments where 
an MQTT message broker is used as the centralized message bus.
See https://github.com/mqtt-smarthome for a rationale and architectural overview.

hm2mqtt communicates with Homematic using the documented XML-RPC API
and thus requires either an CCU1/CCU2 or a Homematic configuration adapter with
the XML-RPC service running on a host (currently Windows-only). It is _not_
able to talk directly to Homematic devices using 3rd party hardware like a CUL.



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

The same is possible with groups:

	hue/set/groups/<groupname>

The special group name 0 is also recognized and refers to the default group which contains
all lights connected to a bridge.


Dependencies
------------
* Java 1.7 SE Runtime Environment: https://www.java.com/
* Eclipse Paho: https://www.eclipse.org/paho/clients/java/ (used for MQTT communication)
* Minimal-JSON: https://github.com/ralfstx/minimal-json (used for JSON creation and parsing)
* Philips HUE Java API Library: https://github.com/PhilipsHue/PhilipsHueSDK-Java-MultiPlatform-Android

[![Build Status](https://travis-ci.org/owagner/hue2mqtt.svg)](https://travis-ci.org/owagner/hue2mqtt) Automatically built jars can be downloaded from the release page on GitHub at https://github.com/owagner/hue2mqtt/releases


History
-------
* 0.4 - 2015/03/09 - owagner
  - better output of available groups and scenes after initial connect
  
     
