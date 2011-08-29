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

package com.hqme.cm;

public class VSDProperties {
    // The following properties are defined for all VSDs.
    public static enum VSProperty {
        VS_FN_GROUPS,               // Sequence<Long>, read-only 
        VS_TOTAL_CAPACITY,          // Long, read-only
        VS_AVAILABLE_CAPACITY,      // Long, read-only
        VS_OBJECT_COUNT,            // Long, read-only
    };

    // The following properties are valid for all assets. 
    // If the Access Control function group is enabled, read and write of some 
    // of these properties may be restricted to specific accounts.
    public static enum SProperty {
        S_STORE_NAME,               // String 
        S_STORE_SIZE,               // Long
        S_SOURCEURI,                // String
        S_ORIGIN,                   // String
        S_LOCKED,                   // Boolean
        S_TYPE,                     // String
        S_REDOWNLOAD_URI,           // String
    };
    
    // The following properties are optional for all assets. 
    // If the Access Control function group is enabled, read and write of some 
    // of these properties may be restricted to specific accounts.
    public static enum OptionalProperty {
        S_POLICY,               	// String 
        S_METADATA,               	// String   
        S_CONTENTPROFILE,			// String
        S_VALIDITYCHECK,            // Boolean
        S_RIGHTSCHECK,				// Boolean
    };

    public static enum SEEK_ORIGIN {
        SEEK_SET,                   // Beginning of object 
        SEEK_CUR,                   // Current object position
        SEEK_END                    // End of object
    };
}
