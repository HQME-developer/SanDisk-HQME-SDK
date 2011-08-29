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

/**
 * VSDFunctionGroups defines the HQME Extensible Function Groups for 
 * the optional defined features which may be supported by an VSD. 
*/
public class VSDFunctionGroups {
    public static enum VSDFunctionGroup {
        VSD_FUNCTION_GROUP_AC (0x10000001, "Access Control"),
        VSD_FUNCTION_GROUP_CM (0x10000002, "Capacity Management"),
        VSD_FUNCTION_GROUP_EX (0x10000003, "Expiration"),
        VSD_FUNCTION_GROUP_TR (0x10000004, "Transformative Read and Write");
    
        private final int id;
        private final String description; 
     
        private VSDFunctionGroup(int id, String description) { 
            this.id = id; 
            this.description = description; 
        } 
     
        public String getDescription() { 
            return description; 
        } 
     
        public int getFunctionGroupId() { 
            return id; 
        } 
     
        @Override 
        public String toString() { 
            return id + ": " + description; 
        }
    }
    
    // ===========================================================================================
    // The following properties are defined for the Access Control VSDs.
    public static enum ACVSProperty {
        ACVS_HWASSIST_SUPPORTED,    // Boolean, read-only 
        ACVS_CERTIFICATE_SUPPORTED, // Boolean, read-only
        ACVS_HWACL,                 // Boolean, read-only
        ACVS_ACCOUNTS,              // Sequence<Long>, read-only
        ACVS_CURRENT_ACCOUNT,       // Account, read-only
        ACVS_PASSWORD,              // String, write-only
        ACVS_CERTIFICATE,           // Certificate, write-only
    };
    
    // The following properties are defined for assets in the Access Control VSDs.
    public static enum ACSProperty {
        ACS_ACL,                    // OriginACL, read-only
    };
    
    // The following commands are defined for the Access Control Function Group.
    public static enum ACCommand {
        AC_CREATE_ACCOUNT,          // Param: account
        AC_DELETE_ACCOUNT,          // Param: origin
        AC_DELEGATE,                // Param: origin, newAcl
        AC_ADMIN_DELEGATE,          // Param: from_origin, to_origin, acl
        AC_SET_ACCOUNT_PROPERTIES,  // Param: account
    };
    
    // ===========================================================================================
    // No VSD properties are defined for the Capacity Management Function Group.    
    
    // The following properties are defined for assets in the Capacity Management Function Group.
    public static enum CMSProperty {
        CMS_PRIORITY,               // Short, read & write
        S_EXPIRATION,               // Long, read & write
        CMS_DISCARDED,              // Boolean, read
    };
    
    // The following VSD commands are defined for the Capacity Management Function Group.
    public static enum CMCommand {
        CM_CONSOLIDATE,             // Param: maxPriorityLevel, minSpaceRequired, age
        CM_GET_EFFECTIVE_CAPACITY,  // Param: priority
        CM_GET_FREE_SPACE,          // Param: priority
    };
    
    // ===========================================================================================
    // No VSD properties are defined for the Expiration Function Group.    
    
    // The following properties are defined for assets in the Expiration Function Group.
    public static enum EXSProperty {
        EXS_EMBARGO,                // Short, read & write
        S_EXPIRATION,               // Long, read & write
    };

    // ===========================================================================================
    // The following properties are defined for the Transformative Read and Write Function Group.
    public static enum TRVSProperty {
        TR_TRANSFORMATION,          // Sequence<String>, read-only 
    };
    
    // The following properties are defined for assets in the Transformative Read and Write Function Group.
    public static enum TRSProperty {
        TRS_TRANSFORMATION,         // String, read & write
        TRS_COOKED,                 // Boolean, read & write
    };
}
