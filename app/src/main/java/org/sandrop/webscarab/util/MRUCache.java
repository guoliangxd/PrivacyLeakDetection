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

package org.sandrop.webscarab.util;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * maintains a Most-Recently-Used cache of its entries, up to the max size specified
 * @author knoppix
 */
public class MRUCache<K, V> extends LinkedHashMap<K, V> {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 147426251266610197L;
	private int _maxSize;
    
    /**
     * Creates a new instance of MRUCache, with an initial size of 16, and
     * a load factor of 0.75
     * @param maxSize the maximum size of the cache
     */
    public MRUCache(int maxSize) {
        this(16, maxSize, 0.75f);
    }
    
    /**
     * Creates a new instance of MRUCache, with a load factor of 0.75
     * @param initialCapacity the preferred initial capacity
     * @param maxSize the maximum size of the cache
     */
    public MRUCache(int initialCapacity, int maxSize) {
        this(initialCapacity, maxSize, 0.75f);
    }
    
    /**
     * Creates a new instance of MRUCache
     * @param loadFactor the preferred load factor
     * @param initialCapacity the preferred initial capacity
     * @param maxSize the maximum size of the cache
     */
    public MRUCache(int initialCapacity, int maxSize, float loadFactor) {
        super(initialCapacity, loadFactor, true);
        _maxSize = maxSize;
    }
    
    /**
     * called to determine whether the eldest entry should be removed. Eldest is defined
     * in terms of access order, so a more recently used entry will stay "young"
     * @param eldest the entry
     * @return true if this entry should be removed, false otherwise
     */    
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > _maxSize;
    }
    
}
