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

import com.hqme.cm.IContentObject;
import com.hqme.cm.Property;

/**
 * IVSD defines the HQME Virtual Storage Device (VSD) plugin interface.
 *
 * It abstracts object-data and meta-data storage and indexing facilities for a
 * variety of storage devices. Storage is hierarchical and easily mapped to 
 * different file systems..
 */
interface IVSD
{
	/** The storageId is a read-only data member and is the VSD identifier, assigned by VSD storage manager, 
	 *  platform unique and persistent once assigned.
	 * 
	 * @return The VSD storage ID
	 */
    int storageId();
    
	/** The name is a read only-data member and is the VSD name to which the VSD interface is associated.
	 * 
	 * @return The VSD name
	 */
    String name();
    
	/** The functionGroups is a read-only data member and is an array of longs where each value describes
	 *  a function group of the VSD to which the VSD Interface is associated. Properties and Commands may
	 *  be applied based on the function groups.
	 * 
	 * @return Array of function groups associated with the VSD.
	 */
    long[] functionGroups();
    
    /** The purpose of this method is to retrieve all objects matching the optional filter, substring of 
     *  the interested objects' name, if they are visible to the calling client's origin.
     *  This method returns a sequence of strings where each item in the sequence is a unique asset name.
     * 
     * @param filter The optional current "working" folder for the search. For example, "myFolder1/", 
     * 				 "myFolder1/mySubFolder1/" are valid filter, and NULL or empty string is treated as 
     *				 the VSD root "folder".
     * @return Array of unique asset name for all found objects.
     */
    String[] allObjects(String filter);
    
    /** Retrieve a ContentObject instance which provides additional information and access to the underlying object. 
     * 
     * @param name of the content object. It is the complete "path" including the object name to be accessed.
     * @return Reference to the ContentObject. Return NULL if not found.
     */
    IContentObject getObject(String name);
    
    /** Create a ContentObject object.  
     * 
     * @param name The name of the object to be created. The object logical hierarchy is indicated if a relative path included.
     *			   Valid name example: "myObject1", "myFolder1/myObject1.mp3". The old object with the same name 
     *             will be overwritten.
     * @return Reference to the ContentObject.
     */
    IContentObject createObject(String name);

    /** Remove a ContentObject object.
     * 
     * @param name The name of the object to be remove. The object logical hierarchy is indicated if a relative path included.
     *			   Valid name example: "myObject1", "myFolder1/myObject1.mp3".
     * @return Error code.
     */
    int removeObject(String name);

    /** Retrieve the current VSD property keys.
     *
     * @return Property keys.
     */
    String[] getPropertyKeys();
    
    /** Retrieve a specific property associated with a VSD, as visible and relevant to the calling origin.  
     *
     * @param key Property key name. The following VSD properties are defined for all VSDs:
     * 		"VS_FN_GROUPS", 
     * 		"VS_TOTAL_CAPACITY", 
     * 		"VS_AVAILABLE_CAPACITY", 
     * 		"VS_OBJECT_COUNT"
     * 
     * @return Property value. Return NULL if not found. If the return value is a binary chunk, it will be BASE64 encoded.
     */
    String getProperty(String key);
    
    /** Set a property key associated with a VSD
     * 
     * @param key 		Property key
     * @param value 	Property value. If the value is a binary chunk, it must be BASE64 encoded.
     * 
     * @return error code	The possible error codes are "STATUS_SUCCESS", "ERR_PERMISSION_DENIED", and "ERR_GENERAL".
     */
    int setProperty(String key, String value);
    
    /** Remove a specific property associated with a VSD.
     * 
     * @param key Property key name
     *
     * @return error code	The possible error codes are "STATUS_SUCCESS", "ERR_PERMISSION_DENIED",
     * 						"ERR_NOT_FOUND", and "ERR_GENERAL".
     */
    int removeProperty(String key);

    /** Issue a VSD a command. A VSD has a set of functional features which are utilized using
     *  the issueCommand and getCommandStatus methods. 
     * 
     * @param commandId Command id
     * @param arguments property array as arguments.
     * 
     * @return Error code.
     */
    int issueCommand(int commandId, in Property[] arguments);
    
    /** Retrieve the status for a previously submitted VSD command.
     * 
     * @param commandId Command id
     * @param results Property array as results.
     * 
     * @return Error code.
     */
    Property[] getCommandStatus(int commandId);
}
