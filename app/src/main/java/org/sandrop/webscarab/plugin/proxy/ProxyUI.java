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


package org.sandrop.webscarab.plugin.proxy;

import java.io.IOException;


import org.sandrop.webscarab.model.ConversationID;
import org.sandrop.webscarab.model.HttpUrl;
import org.sandrop.webscarab.plugin.PluginUI;

/**
 *
 * @author  knoppix
 */
public interface ProxyUI extends PluginUI {
    
    void proxyAdded(ListenerSpec spec);
    
    void proxyStarted(ListenerSpec spec);
    
    void proxyStartError(ListenerSpec spec, IOException ioe);
    
    void proxyStopped(ListenerSpec spec);
    
    void proxyRemoved(ListenerSpec spec);
    
    void requested(ConversationID id, String method, HttpUrl url);
    
    void received(ConversationID id, String status);
    
    void aborted(ConversationID id, String reason);
    
}
