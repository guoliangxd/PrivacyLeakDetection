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

/*
 * Calculates the Levenshtein distance between two byte arrays
 * This is great for showing which responses are similar or different
 * to others. However, it is VERY slow, O(n*m), which bogs down really
 * quickly if we start looking at sequences of a few thousand bytes :-(
 * 
 * We optimize by tokenising the input into words, and comparing those
 * 
 * An alternative might be the XDelta algorithm, see e.g. 
 * http://sourceforge.net/projects/javaxdelta/&e=10313
 * 
 * Also see a paper "A Linear Time, Constant Space Differencing Algorithm" by Burns and Long
 */

import java.util.List;
import java.util.Iterator;

public class LevenshteinDistance<T> {
    
    private List<T> _baseline;
    private int[] _current, _previous;
    
    public LevenshteinDistance(List<T> baseline) {
        _baseline = baseline;
        _current = new int[_baseline.size()+1];
        _previous = new int[_baseline.size()+1];
    }
    
    public synchronized int getDistance(List<T> target) {
        if (_baseline.size() == 0)
            return target.size();
        if (target.size() == 0)
            return _baseline.size();
        
        for (int i = 0; i < _current.length; i++) {
            _current[i] = i;
        }
        
        Iterator<T> targIt = target.iterator();
        int j=0;
        while(targIt.hasNext()) {
            T targObj = targIt.next();
            j++;
            
            int[] t = _previous;
            _previous = _current;
            _current = t;
            
            _current[0] = _previous[0]+1;
            
            Iterator<T> baseIt = _baseline.iterator();
            int i=0;
            while(baseIt.hasNext()) {
                T baseObj = baseIt.next();
                i++;
                
                int cost;
                if (baseObj.equals(targObj)) {
                  cost = 0;
                } else {
                  cost = 1;
                }
                _current[i] = Math.min(Math.min(_previous[i]+1, _current[i-1]+1), _previous[i-1] + cost);
            }
        }
        return _current[_baseline.size()];
    }

    public static void main(String[] args) {
        List<Character> baseline = new java.util.ArrayList<Character>();
        baseline.add(new Character('l'));
        baseline.add(new Character('e'));
        baseline.add(new Character('v'));
        baseline.add(new Character('e'));
        baseline.add(new Character('n'));
        baseline.add(new Character('s'));
        baseline.add(new Character('h'));
        baseline.add(new Character('t'));
        baseline.add(new Character('e'));
        LevenshteinDistance<Character> ld = new LevenshteinDistance<Character>(baseline);
        List<Character> target = new java.util.ArrayList<Character>();
        target.add(new Character('m'));
        target.add(new Character('e'));
        target.add(new Character('i'));
        target.add(new Character('l'));
        target.add(new Character('e'));
        target.add(new Character('n'));
        target.add(new Character('s'));
        target.add(new Character('t'));
        target.add(new Character('e'));
        int distance = ld.getDistance(target);
        System.out.println("Distance between \"meilenstein\" and \"levenshtein\": " + distance);
    }
}
