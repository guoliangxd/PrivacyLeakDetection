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
 * FragmentsModel.java
 *
 * Created on 04 April 2005, 10:30
 */

package org.sandrop.webscarab.plugin.fragments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

import org.sandrop.webscarab.model.AbstractConversationModel;
import org.sandrop.webscarab.model.ConversationID;
import org.sandrop.webscarab.model.ConversationModel;
import org.sandrop.webscarab.model.FrameworkModel;
import org.sandrop.webscarab.model.HttpUrl;
import org.sandrop.webscarab.model.StoreException;
import org.sandrop.webscarab.plugin.AbstractPluginModel;
import org.sandrop.webscarab.util.Encoding;

import EDU.oswego.cs.dl.util.concurrent.Sync;

/**
 * 
 * @author rogan
 */
public class FragmentsModel extends AbstractPluginModel {
    
    private FragmentsStore _store = null;
    private FrameworkModel _model = null;
    private FragmentConversationModel _fcm = null;
    
    public static String KEY_SCRIPTS = "SCRIPTS";
    public static String KEY_COMMENTS = "COMMENTS";
    public static String KEY_FILEUPLOAD = "FILEUPLOAD";
    public static String KEY_FORMS = "FORMS";
    public static String KEY_HIDDENFIELD = "HIDDENFIELD";
    public static String KEY_DOMXSS = "DOMXSS";
    
    private Logger _logger = Logger.getLogger(getClass().getName());
    
    // sandrop
    // private EventListenerList _listenerList = new EventListenerList();
    
    /** Creates a new instance of FragmentsModel */
    public FragmentsModel(FrameworkModel model) {
        _model = model;
        _fcm = new FragmentConversationModel(model);
    }
    
    public void addFragment(HttpUrl url, ConversationID id, String type, String fragment) {
        try {
            _rwl.writeLock().acquire();
            String key = Encoding.hashMD5(fragment);
            int position = _store.putFragment(type, key, fragment);
            _rwl.readLock().acquire();
            _rwl.writeLock().release();
            _model.addConversationProperty(id, type, key);
            _model.addUrlProperty(url, type, key);
            fireFragmentAdded(url, id, type, key);
            if (position>-1)
                fireFragmentAdded(type, key, position);
            _rwl.readLock().release();
        } catch (InterruptedException ie) {
            _logger.severe("Interrupted! " + ie);
        }
        setModified(true);
    }
    
    public String[] getUrlFragmentKeys(HttpUrl url, String type) {
        if(KEY_COMMENTS.equals(type)|| 
                KEY_DOMXSS.equals(type)||
                KEY_FILEUPLOAD.equals(type)||
                KEY_FORMS.equals(type)||
                KEY_HIDDENFIELD.equals(type) ||
                KEY_SCRIPTS.equals(type))
        {
            return _model.getUrlProperties(url, type);
        } else {
            return new String[0];
        }
    }
    
    public String[] getConversationFragmentKeys(ConversationID id, String type) {
        if(KEY_COMMENTS.equals(type)|| 
                KEY_DOMXSS.equals(type)||
                KEY_FILEUPLOAD.equals(type)||
                KEY_FORMS.equals(type)||
                KEY_HIDDENFIELD.equals(type) ||
                KEY_SCRIPTS.equals(type))
        {
            return _model.getConversationProperties(id, type);
        } else {
            return new String[0];
        }
    }
    
    public int getFragmentTypeCount() {
        if (_store == null) return 0;
        return _store.getFragmentTypeCount();
    }
    
    public String getFragmentType(int index) {
        return _store.getFragmentType(index);
    }
    
    public int getFragmentCount(String type) {
        return _store.getFragmentCount(type);
    }
    
    public String getFragmentKeyAt(String type, int position) {
        return _store.getFragmentKeyAt(type, position);
    }
    
    public int indexOfFragment(String type, String key) {
        return _store.indexOfFragment(type, key);
    }
    
    public String getFragment(String key) {
        return _store.getFragment(key);
    }
    
    public void addModelListener(FragmentListener listener) {
        // sandrop 
        //_listenerList.add(FragmentListener.class, listener);
    }
    
    public void removeModelListener(FragmentListener listener) {
        // sandrop
        // _listenerList.remove(FragmentListener.class, listener);
    }
    
    public ConversationModel getConversationModel() {
        return _fcm;
    }
    
    public void setSelectedFragment(String type, String key) {
        try {
            _rwl.writeLock().acquire();
            _rwl.readLock().acquire();
            _rwl.writeLock().release();
            _rwl.readLock().release();
        } catch (InterruptedException ie) {
            _logger.severe("Interrupted! " + ie);
        }
        _fcm.setSelectedFragment(type, key);
    }
    
    public void setStore(FragmentsStore store) {
        _store = store;
        fireFragmentsChanged();
        setModified(false);
    }
    
    public void flush() throws StoreException {
        if (_store != null && isModified()) {
            _store.flush();
            setModified(false);
        }
    }
    
    private void fireFragmentsChanged() {
        // Guaranteed to return a non-null array
        // sandrop
        //Object[] listeners = _listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        // sandrop
        /*
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==FragmentListener.class) {
                try {
                    ((FragmentListener)listeners[i+1]).fragmentsChanged();
                } catch (Exception e) {
                    _logger.severe("Unhandled exception: " + e);
                }
            }
        }
        */
    }
    
    private void fireFragmentAdded(String type, String key, int position) {
        // Guaranteed to return a non-null array
        // sandop
        //Object[] listeners = _listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        // sandrop
        /*
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==FragmentListener.class) {
                try {
                    ((FragmentListener)listeners[i+1]).fragmentAdded(type, key, position);
                } catch (Exception e) {
                    _logger.severe("Unhandled exception: " + e);
                }
            }
        }
        */
    }
    
    private void fireFragmentAdded(HttpUrl url, ConversationID id, String type, String key) {
        // Guaranteed to return a non-null array
        // sandrop
        //Object[] listeners = _listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        // sandrop
        /*
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==FragmentListener.class) {
                try {
                    ((FragmentListener)listeners[i+1]).fragmentAdded(url, id, type, key);
                } catch (Exception e) {
                    _logger.severe("Unhandled : " + e);
                }
            }
        }
        */
    }
    
    private class FragmentConversationModel extends AbstractConversationModel implements FragmentListener {
        
        private String _type = null;
        private String _key = null;
        private ArrayList<ConversationID> _conversationList = new ArrayList<ConversationID>();
        
        public FragmentConversationModel(FrameworkModel model) {
            super(model);
        }
        
        public void setSelectedFragment(String type, String key) {
            try {
                _rwl.writeLock().acquire();
                _type = type;
                _key = key;
                _conversationList.clear();
                if (_type != null && _key != null) {
                    ConversationModel cmodel = _model.getConversationModel();
                    int count = cmodel.getConversationCount();
                    for (int i=0; i<count; i++) {
                        ConversationID id = cmodel.getConversationAt(i);
                        String[] fragments = getConversationFragmentKeys(id,  _type);
                        if (fragments != null) {
                            for (int j=0; j<fragments.length; j++) {
                                if (fragments[j].equals(_key)) {
                                    _conversationList.add(id);
                                    break;
                                }
                            }
                        }
                    }
                }
                _rwl.readLock().acquire();
                _rwl.writeLock().release();
                fireConversationsChanged();
                _rwl.readLock().release();
            } catch (InterruptedException ie) {
                _logger.severe("Interrupted! " + ie);
            }
        }
        
        public ConversationID getConversationAt(int index) {
            return _conversationList.get(index);
        }
        
        public int getConversationCount() {
            return _conversationList.size();
        }
        
        public int getIndexOfConversation(ConversationID id) {
            // sandrop 
            //return Collections.binarySearch(_conversationList, id);
            return 0;
        }
        
        public Sync readLock() {
            return _rwl.readLock();
        }
        
        public void fragmentAdded(String type, String key, int position) {}
        
        public void fragmentAdded(HttpUrl url, ConversationID id, String type, String key) {
            if (_type != null && _key != null) {
                if (_type.equals(type) && _key.equals(key)) {
                    int index = getIndexOfConversation(id);
                    if (index < 0) {
                        try {
                            _rwl.writeLock().acquire();
                            _conversationList.add(-index-1, id);
                            _rwl.readLock().acquire();
                            _rwl.writeLock().release();
                            fireConversationAdded(id,  -index-1);
                            _rwl.readLock().release();
                        } catch (InterruptedException ie) {
                            _logger.severe("Interrupted! " + ie);
                        }
                    }
                }
            }
        }
        
        public void fragmentsChanged() {
            try {
                _rwl.writeLock().acquire();
                _key = null;
                _type = null;
                _conversationList.clear();
                _rwl.readLock().acquire();
                _rwl.writeLock().release();
                fireConversationsChanged();
                _rwl.readLock().release();
            } catch (InterruptedException ie) {
                _logger.severe("Interrupted! " + ie);
            }
        }
        
    }
}
