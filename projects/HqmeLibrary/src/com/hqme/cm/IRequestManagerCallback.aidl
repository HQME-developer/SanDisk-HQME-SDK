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

import com.hqme.cm.ReqEvents;

/**
 * IRequestManagerCallback defines the HQME request manager callback interface for HQME applications.
 */
interface IRequestManagerCallback
{
     /** Invoked when a QueueRequest's status changes.
     * 
     * @param reqEvent Refer to ReqEvents.java for event definitions.
     * 
     */     
     oneway void handleEvent(in ReqEvents reqEvent);
	
}
