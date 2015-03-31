/*
 * Adds a type-guessing add() method
 */

package com.tellerulam.hue2mqtt;

import com.eclipsesource.json.*;

public class WrappedJsonObject extends JsonObject
{
	private static final long serialVersionUID = 1L;

	public void add(String name,Object val)
	{
		if(val==null)
			return;
		if(val instanceof Boolean)
			add(name,((Boolean)val).booleanValue());
		else if(val instanceof Integer)
			add(name,((Integer)val).intValue());
		else if(val instanceof Double)
			add(name,((Double)val).doubleValue());
		else if(val instanceof Float)
			add(name,((Float)val).floatValue());
		else if(val instanceof JsonValue)
			add(name,(JsonValue)val);
		else
			add(name,val.toString());
	}
}
