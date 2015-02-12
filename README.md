# hue2mqtt
Gateway between a Philips Hue bridge and MQTT

Topic structure
===============
hue2mqtt follows the mqtt-smarthome topic structure with a top-level prefix and a function like
_status_ and _set_. Lamp, group and scene names are read from the Hue bridge.

Status reports are sent to the topic

    hue/status/lamp/<lampname>
    
The payload is a JSON encoded object with the following fields:

* val - either 0 if the lamp is off, or the current brightness
* hue_reachable - boolean, whether the lamp is currently reachable
* hue_brightness - integer, distinct brightness value
* hue_ct ..
* ...

Setting state is possible in one of three ways:    

Method 1: Publishing a simple integer value to
    
    hue/set/lamp/<lampname>
    
will for value=0 turn off the lamp and for values > 0 turn the lamp on and set the
brightness to the given value.

Method 2: Publishing a JSON encoded object to

    hue/set/lamp/<lampname>

will set multiple parameters of the given lamp. The field names are the same as
the ones used in status reports.

Method 3: Publishing a simple value to

	hue/set/lamp/<lampname>/<datapoint>
	
will distinctly set a single datapoint (equal to the field names in the composite
JSON objects) to the simple value.


Dependencies
------------
* Java 1.7 SE Runtime Environment: https://www.java.com/
* Eclipse Paho: https://www.eclipse.org/paho/clients/java/ (used for MQTT communication)
* Minimal-JSON: https://github.com/ralfstx/minimal-json (used for JSON creation and parsing)
* Philips HUE Java API Library: https://github.com/PhilipsHue/PhilipsHueSDK-Java-MultiPlatform-Android

[![Build Status](https://travis-ci.org/owagner/hue2mqtt.svg)](https://travis-ci.org/owagner/hue2mqtt) Automatically built jars can be downloaded from the release page on GitHub at https://github.com/owagner/hue2mqtt/releases

     
