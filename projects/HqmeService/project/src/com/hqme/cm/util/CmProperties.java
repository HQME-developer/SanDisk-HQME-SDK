/** 
* This reference code is an implementation of the IEEE P2200 standard.  It is not
* a contribution to the IEEE P2200 standard.
* 
* Copyright (c) 2011 SanDisk Corporation.  All rights reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use
* this file except in compliance with the License.  You may obtain a copy of the
* License at
* 
*        http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software distributed
* under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
* CONDITIONS OF ANY KIND, either express or implied.
* 
* See the License for the specific language governing permissions and limitations
* under the License.
*/

package com.hqme.cm.util;

import android.os.Bundle;

import java.util.HashMap;

public class CmProperties extends HashMap<String, String> {
    // ==================================================================================================================================
    // the purpose of this class is to create a <key, value> hash map of strings
    // with default value substitution and guaranteed non-null
    // get and set operations, is thread-safe, and which cleans the value
    // strings on set operations by trimming whitespace from the head
    // and tail of the string
    // ==================================================================================================================================
    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_VALUE = "";

    public CmProperties(CmProperties from)
    {
        super(from);
    }
    
    public CmProperties()
    {
        super();
    }
    
    // ----------------------------------------------------------------------------------------------------------------------------------
    // get the specified key; return DEFAULT_VALUE if not found
    //
    @Override
    public synchronized String get(Object key) {
        return key == null ? DEFAULT_VALUE : this.get(key.toString(), DEFAULT_VALUE);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    // get the specified key from super; set and return a "cleaned"
    // defaultValueIfNotFound (never null) if the entry is not found
    //
    public synchronized String get(String key, Object defaultValueIfNotFound) {
        String object = super.get(key);
        return object == null || DEFAULT_VALUE.equals(object) ? this.set(key,
                defaultValueIfNotFound) : object;
    }
    
  
    // ----------------------------------------------------------------------------------------------------------------------------------
    // set the value of the specified key; return the "cleaned" newValue (never
    // null) inserted into the super's hash map
    //
    public synchronized String set(String key, Object newValue) {
        String value = newValue == null ? DEFAULT_VALUE : newValue.toString().trim();
        super.put(key, value);
        return value;
    }
    // ==================================================================================================================================
    public synchronized Bundle toBundle() {
        Bundle b = new Bundle();        
        for (String key : keySet()) {
            b.putString(key, get(key));
        }        
        return b;
    }
}
