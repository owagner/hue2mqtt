package com.tellerulam.hue2mqtt;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.Formatter;
import java.util.logging.*;

@SuppressWarnings("boxing")
public class SyslogHandler extends Handler
{
	static void readConfig() throws SecurityException, IOException
	{
		if(System.getProperty("java.util.logging.config.file")==null)
		{
			LogManager.getLogManager().readConfiguration(SyslogHandler.class.getResourceAsStream("/logging.properties"));
		}
	}

	private final DatagramSocket ds;
	private String hostname;
	private String pidSuffix;

	public SyslogHandler() throws SocketException, UnknownHostException
	{
		ds=new DatagramSocket();
		String syslogHost=LogManager.getLogManager().getProperty(getClass().getName()+".host");
		String syslogPort=LogManager.getLogManager().getProperty(getClass().getName()+".port");
		ds.connect(new InetSocketAddress(
			syslogHost!=null?syslogHost:"localhost",
			syslogPort!=null?Integer.parseInt(syslogPort):514
		));
		try
		{
			hostname = InetAddress.getLocalHost().getHostName();
			int domainIndex=hostname.indexOf('.');
			if(domainIndex>0)
				hostname=hostname.substring(0,domainIndex);
		}
		catch(UnknownHostException e)
		{
			hostname=InetAddress.getLocalHost().getHostAddress();
		}
		try
		{
			pidSuffix="["+new File("/proc/self").getCanonicalFile().getName()+"]";
		}
		catch(NumberFormatException | IOException e)
		{
			pidSuffix="";
		}
	}

	@Override
	public void close() throws SecurityException
	{
		if(ds!=null)
			ds.close();
	}

	@Override
	public void flush()
	{
		/* Nothing to do, we sent immediately */
	}

	private final Map<Level,Integer> levels=new HashMap<>();
	{
		levels.put(Level.SEVERE,3);
		levels.put(Level.WARNING,4);
		levels.put(Level.INFO,5);
		levels.put(Level.CONFIG,5);
		levels.put(Level.FINE,6);
		levels.put(Level.FINER,7);
		levels.put(Level.FINEST,7);
	}

	private void sendSyslogMessage(StringBuilder msg)
	{
		if(ds==null)
			return;

		byte msgData[]=msg.toString().getBytes(StandardCharsets.US_ASCII);
		try
		{
			ds.send(new DatagramPacket(msgData,msgData.length));
		}
		catch(IOException e)
		{
			/* Ignore */
		}
	}

	@Override
	public void publish(LogRecord r)
	{
		if(ds==null)
			return; // We are disabled

		int facility=23; // Local 23
		// Convert priority
		int pri=levels.get(r.getLevel()).intValue();

		Calendar cal=Calendar.getInstance();
		cal.setTimeInMillis(r.getMillis());

		StringBuilder m=new StringBuilder();
		m.append('<');
		m.append(facility*8+pri);
		m.append("> ");
		Formatter dateFormatter=new Formatter(m,Locale.US);
		dateFormatter.format("%1$tb %2$2d %1$TT",cal,cal.get(Calendar.DAY_OF_MONTH));
		dateFormatter.close();
		m.append(" ");
		m.append(hostname);
		m.append(" hue2mqtt");
		m.append(pidSuffix);
		m.append(": ");
		int prefixLength=m.length();
		for(char ch:r.getMessage().toCharArray())
		{
			if(ch=='\r')
				continue;
			if(ch=='\n')
			{
				sendSyslogMessage(m);
				m.setLength(prefixLength);
				continue;
			}
			if(m.length()==1020)
			{
				m.append("...");
			}
			if(m.length()==1023)
				continue;
			if(ch>=126)
				m.append('_');
			else
				m.append(ch);
		}
		if(m.length()!=prefixLength)
			sendSyslogMessage(m);
	}

}
