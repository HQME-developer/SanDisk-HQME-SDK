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

import java.util.HashMap;
import java.util.Map;

public enum HqmeError
{	
    // Error codes
    STATUS_SUCCESS					(1,	 "Succcess"),
    ERR_INVALID_ARGUMENT    		(-1, "One or more arguments passed as parameters of method were invalid.  As an example, null is passed where an object is expected, or a value passed as a parameter exceeds the expected range of values."),
    ERR_NOT_FOUND          			(-2, "An object, object within a database, or other construct which is to be operated on by the invoked method could not be found."),
    ERR_TIMEOUT             		(-3, "The expected duration of an invoked method has been exceeded."),
    ERR_PENDING_OPERATION   		(-4, "The invoked method could not be executed due to a previously pending operation.  This may occur when a shared resource requires access which is taken by another pending operation."),
    ERR_IO                  		(-5, "A method which depends on an Input/Ouput device has encountered an error.  As an example, a hardware failure would result in this error being returned."),
    ERR_NOT_SUPPORTED       		(-6, "The method, or operation, feature, or function is not supported by this implementation."),
    ERR_PERMISSION_DENIED   		(-7, "The caller which invoked the method does not have the appropriate permissions to execute the method."),
    ERR_VSD_UNAVAILABLE         	(-8, "The invoked method could not access the VSD required to complete the method successfully."),

    ERR_INVALID_REQUEST_ID   		(-9, "The requestId passed as an argument could not be found in the request queue."),
    ERR_CANCEL_FAILED        		(-10, "The QueueRequest could not be canceled.  As an example, the application may try to cancel a request that completes before the cancellation is executed."),
    ERR_SUSPEND_FAILED          	(-11, "The QueueRequest could not be suspended.  As an example, the application may try to suspend a request that completes before the cancellation is executed."),
    ERR_RESUME_FAILED        		(-12, "The QueueRequest could not be resumed.  As an example, the application may try to resume a request that has an erroneous property or other setting which prohibits the request from resuming."),

    ERR_INVALID_POLICY       		(-13, "This error is encountered when one or more rules comprising the Policy was not recognized or a property value could not be parsed properly."),
    ERR_INVALID_PROPERTY     		(-14, "This error is encountered when a property key is not recognized or property value could not be parsed properly."),
    ERR_GENERAL             		(-15, "An unidentified error occurred when attempting to execute the method.  This could be a database error, out of memory condition, or other general system failure."),
    
    ERR_UNKNOWN                     (-1234567890, "This error code is undefined.");

    private static final Map<Integer, HqmeError> codeMap = new HashMap<Integer, HqmeError>();
    static {
        for (HqmeError type : HqmeError.values()) {
            codeMap.put(type.code, type);
        }
    }

    public static HqmeError fromInt(int i) {
        HqmeError type = codeMap.get(Integer.valueOf(i));
        if (type == null)
            return HqmeError.ERR_UNKNOWN;
        return type;
    }

    private final int code; 
    private final String description; 

    private HqmeError(int code, String description) { 
        this.code = code; 
        this.description = description; 
    } 
 
    public String getDescription() { 
        return description; 
    } 
 
    public int getCode() { 
        return code; 
    } 
}