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

import com.hqme.cm.IVSD;
import com.hqme.cm.IStorageManagerCallback;

/**
 * IStorageManager defines the HQME Virtual Storage Device(VSD) plugin manager interfaces.
 *
 * IStorageManager provides an abstract interface for an application 
 * to query and determine the number/attributes of VSDs that are available 
 * for the client.
 */
interface IStorageManager
{
    /** Interface to let a VSD plugin register itself with IStorageManager (aka the plugin manager). 
     *  
     * @param pluginProxy the instance of the VSD plugin.  
     * 
     * @return VSD storage id. It is assigned by the content storage manager and shall be 
     * persistent and stored in VSD. 
     *
     * Note: not a P2200 API
     */
    int registerPlugin(IVSD pluginProxy);

    /** Interface to let a VSD plugin unregister itself with IStorageManager (aka the plugin manager). 
     * 
     * @param pluginProxy the instance of the VSD plugin.  
     *
     * Note: not a P2200 API
     */
    void unregisterPlugin(IVSD pluginProxy);

    /** Interface to let a VSD plugin inform IStorageManager its status' change. 
     *  This is not a HQME App's interface.
     * 
     * @param eventCode enumeration of the event code.  
     * @param storageId indicates the storage the event sent from.  
     *
     * Note: not a P2200 API
     */
    oneway void updateStatus(int eventCode, int storageID);
    
    /** Register a client application callback function to get informed with VSDs' status changes.
     * 
     * @param cb the client application's callback function  
     * 
     * Note: not a P2200 API
     */
    void registerCallback(IStorageManagerCallback cb);   

    /** Unregister a client application callback function.
     * 
     * @param cb the client application's callback function  
     * 
     * Note: not a P2200 API
     */
    void unregisterCallback(IStorageManagerCallback cb);         
    
    /** Provides a count of the number of ready and available VSDs on the client.
     * 
     * @return Count of available VSDs. Negative if error. Possible error codes are 
     * "ERR_NOT_FOUND", "ERR_GENERAL".
     */
    int VSDCount();
    
    /** Provide a list of VSD storageIds that are ready and available on the client.
     * 
     * @param functiongroups Filter for desired VSDs. If not null, this optional argument is 
     *  an array of long values identifying one or more capabilities supported by the VSD.  
     * 
     * @return List of storage ids. Return null if no VSD available.
     */
    int[] getStorageIds(in long[] functiongroups);
    
    /** Retrieve an instance of a client's VSD with the matching storageId
     * 
     * @param storageId
     * 
     * @return Reference to actual VSD. If the storage ID value is "zero", the default VSD 
     *  will be returned to the caller.
     */
    IVSD getStorage(int storageId);
    
    /** Retrieve function groups for a registered VSD
     * 
     * @param storageId
     * 
     * @return Array of function groups for the given VSD storage. If the storage ID value 
     *  is "zero", the first VSD will be returned to the caller.
     *  If NULL is returned, the storageId may be invalid, or the VSD may have been removed 
     *  from the time the storageIds were retrieved to the time this method was invoked. 
     *  NULL may be returned if the VSD does not support any additional function groups.
     */
    long[] getFunctionGroups(int storageId);
}
