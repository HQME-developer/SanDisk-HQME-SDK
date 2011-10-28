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

package com.hqme.cm.core;

import java.util.EnumSet;
import java.util.HashSet;


public class QueueRequestProperties {
    public static enum RequiredProperties {
        REQPROP_SOURCE_URI, REQPROP_STORE_NAME, REQPROP_TYPE, REQPROP_TOTAL_LENGTH
    };

    public static enum OptionalProperties {
       REQPROP_POLICY, REQPROP_IMMEDIATE, REQPROP_PERMISSIONS_USER, REQPROP_PERMISSIONS_GROUP, REQPROP_PERMISSIONS_WORLD, REQPROP_GROUP, REQPROP_BROADCAST_INTENT
    };

    public static enum TransientProperties {
        REQPROP_REQUEST_ID, REQPROP_REQUEST_STATE, REQPROP_CURRENT_BYTES_TRANSFERRED, REQPROP_LAST_MODIFICATION_DATE, REQPROP_CALLING_UID, REQPROP_TRANSFER_BYTES_MOBILE, REQPROP_DOWNLOAD_RATE, REQPROP_MANDATORY, REQPROP_PRIORITY
    };

    private static final HashSet<String> requiredPropertiesSet = new HashSet<String>();
    static {
        for (RequiredProperties s : EnumSet.allOf(RequiredProperties.class))
            requiredPropertiesSet.add(s.name());
    }

    private static final HashSet<String> optionalPropertiesSet = new HashSet<String>();
    static {
        for (RequiredProperties s : EnumSet.allOf(RequiredProperties.class))
            optionalPropertiesSet.add(s.name());
    }

    private static final HashSet<String> transientPropertiesSet = new HashSet<String>();
    static {
        for (RequiredProperties s : EnumSet.allOf(RequiredProperties.class))
            transientPropertiesSet.add(s.name());
    }

    public static boolean isRequired(String property) {
        return requiredPropertiesSet.contains(property);
    }

    public static boolean isOptional(String property) {
        return requiredPropertiesSet.contains(property);
    }

    public static boolean isTransient(String property) {
        return requiredPropertiesSet.contains(property);
    }

}
