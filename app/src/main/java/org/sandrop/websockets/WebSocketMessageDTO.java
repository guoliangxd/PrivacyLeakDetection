/***********************************************************************
 *
 * This file is part of SandroProxy, 
 * For details, please see http://code.google.com/p/sandrop/
 *
 * Copyright (c) 2012 supp.sandrob@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Getting Source
 * ==============
 *
 * Source for this application is maintained at
 * http://code.google.com/p/sandrop/
 *
 * Software is build from sources of WebScarab project
 * For details, please see http://www.sourceforge.net/projects/owasp
 *
 */
/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.sandrop.websockets;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.sandrop.websockets.utility.InvalidUtf8Exception;
import org.sandrop.websockets.utility.Utf8Util;


/**
 * Data Transfer Object used for displaying WebSockets communication. Intended to
 * decouple user interface representation from version specific
 * {@link WebSocketMessage}.
 */
public class WebSocketMessageDTO {
	
	/**
	 * Each message is sent on a specific connection. Ensure that it is not
	 * <code>null</code>!
	 */
	public WebSocketChannelDTO channel;

	/**
	 * Consecutive number for each message unique for one given channel.
	 */
	public Integer id;

	/**
	 * Used for sorting, containing time of arrival in milliseconds.
	 */
	public Long timestamp;

	/**
	 * When the message was finished (it might consist of several frames).
	 */
	public String dateTime;

	/**
	 * You can retrieve a textual version of this opcode via:
	 * {@link WebSocketMessage#opcode2string(int)}.
	 */
	public Integer opcode;

	/**
	 * Textual representation of {@link WebSocketMessageDTO#opcode}.
	 */
	public String readableOpcode;

	/**
	 * Might be either a string (readable representation for TEXT frames) OR
	 * byte[].
	 * <p>
	 * If it is a byte[] then it might be readable though.
	 * </p>
	 */
	public Object payload;

	/**
	 * For close messages, there is always a reason.
	 */
	public Integer closeCode;

	/**
	 * Outgoing or incoming message.
	 */
	public Boolean isOutgoing;

	/**
	 * Number of the payload bytes.
	 */
	public Integer payloadLength;

	/**
	 * Temporary object holding arbitrary values.
	 */
	public volatile Object tempUserObj;
	
	/**
	 * Used to format {@link WebSocketMessage#timestamp} in user's locale.
	 */
	protected static final DateFormat dateFormatter;
	
	/**
	 * Use the static initializer for setting up one date formatter for all
	 * instances.
	 */
	static {
		// milliseconds are added later (via usage java.sql.Timestamp.getNanos())
		dateFormatter = DateFormat.getDateTimeInstance(
				SimpleDateFormat.SHORT, SimpleDateFormat.MEDIUM);
	}
	
	/**
	 * 
	 * @param channel
	 */
	public WebSocketMessageDTO(WebSocketChannelDTO channel) {
		this.channel = channel; 
	}

	
	public WebSocketMessageDTO() {
		this(new WebSocketChannelDTO());
	}

	/**
	 * Helper to set {@link WebSocketMessageDTO#timestamp} and
	 * {@link WebSocketMessageDTO#dateTime}.
	 * 
	 * @param ts
	 */
	public void setTime(Timestamp ts) {
		timestamp = ts.getTime() + (ts.getNanos() / 1000000000);
		
		synchronized (dateFormatter) {
			dateTime = dateFormatter.format(ts);
		}
		
		String nanos = Integer.toString(ts.getNanos()).replaceAll("0+$", "");
		if (nanos.length() == 0) {
			nanos = "0";
		}
		
		dateTime = dateTime.replaceFirst("([0-9]+:[0-9]+:[0-9]+)", "$1." + nanos);
	}

	@Override
	public String toString() {
		if (channel.id != null && id != null) {
			return "#" + channel.id + "." + id;
		}
		return "";
	}

	/**
	 * Assigns all values to given object.
	 * 
	 * @param other
	 */
	public void copyInto(WebSocketMessageDTO other) {
		other.channel = this.channel;
		other.closeCode = this.closeCode;
		other.dateTime = this.dateTime;
		other.isOutgoing = this.isOutgoing;
		other.id = this.id;
		other.opcode = this.opcode;
		other.payload = this.payload;
		other.payloadLength = this.payloadLength;
		other.readableOpcode = this.readableOpcode;
		other.tempUserObj = this.tempUserObj;
		other.timestamp = this.timestamp;
	}
	
//	@Override
//	public boolean isInScope() {
//		if (channel == null) {
//			return false;
//		}
//		return channel.isInScope();
//	}

	/**
	 * Returns content of {@link WebSocketMessageDTO#payload} directly if it is
	 * of type {@link String}. Otherwise it tries to convert it.
	 * @return readable representation of payload
	 * @throws InvalidUtf8Exception 
	 */
	public String getReadablePayload() throws InvalidUtf8Exception {
		if (payload instanceof String) {
			return (String) payload;
		} else if (payload instanceof byte[]){
			return Utf8Util.encodePayloadToUtf8((byte[]) payload);
		} else {
			return "";
		}
	}
}