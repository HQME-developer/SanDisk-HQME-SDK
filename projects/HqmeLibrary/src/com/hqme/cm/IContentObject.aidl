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

import com.hqme.cm.Property;

/**
 * IContentObject defines the HQME VSD plugin's content object interface.
 * IContentObject provides an abstract interface for an application 
 * to query and access ContentObject within VSD(Virtual Storage Device).
 */
interface IContentObject
{  
    /** Retrieve all properties associated with the content object.
     * 
     * @return All properties which are not write only.
     */
	Property[] properties();
	
    /** Retrieve the current content object property keys.
     * 
     * @return Property keys.
	 *
     * Note: [non-normative] Helper method for enumerating the current keys.
     */
	String[] getPropertyKeys();
	
    /** Retrieve specific metadata information, key/value pair, associated with a content object.
     *
     * @param key Property key.
     *
     * <pre>
     *     The mandatory properties must be supported by all VSDs: 
     *          "S_STORE_NAME"    Describes the name used to store the cached object on the 
     *                            physical media.
     *          "S_STORE_SIZE"    Describes the size of the cached object in bytes.
     *          "S_SOURCEURI"     Describes the source Universal Resource Identifier (URI) 
     *                            where this content was obtained from.
     *          "S_ORIGIN"        The origin (server or app) that the stream originates from.
     *          "S_LOCKED"        Identified if the stream or content is locked. A locked  
     *                            stream may only be read or written to by its calling origin, 
     *                            regardless of delegated permissions.
     *          "S_TYPE"          Describes the MIME type of the cached object.
     *          "S_REDOWNLOAD_URI"     Describes the MIME type of the cached object.
     *
     *     ContentObjects also support the following optional properties :
     *   		"S_POLICY"               	 
     *    		"S_METADATA"               	   
     *			"S_CONTENTPROFILE"			
     *			"S_VALIDITYCHECK"           
     *   		"S_RIGHTSCHECK"				
     * </pre>
     *   
     * @return Property value. Return NULL if not found.
     */
    String getProperty(String key);
    
    /** Set a metadata key associated with a Content Object
     *
     * @param key Property key.
     * @param value Property value.
     *
     * @return Error code. Possible error codes are "STATUS_SUCCESS", "ERR_PERMISSION_DENIED",
     * "ERR_NOT_FOUND", "ERR_GENERAL".
     */
    int setProperty(String key, String value);
    
    /** Remove a metadata key associated with a Content Object
     *
     * @param key Property key.
     *
     * @return Error code. Possible error codes are "STATUS_SUCCESS", "ERR_PERMISSION_DENIED",
     * "ERR_NOT_FOUND", "ERR_GENERAL".
     */
    int removeProperty(String key);

    /** Retrieve the size of the ContentObject in bytes
     *
     * @return Size. Negative on error. Possible error codes are "ERR_PERMISSION_DENIED",
     * "ERR_NOT_FOUND", "ERR_GENERAL".
     */
    long size();
    
    /** Retrieve the current offset used for reads and writes, related to the beginning of the ContentObject.
     *
     * @return The current offset. Negative on error. Possible error codes are "ERR_PERMISSION_DENIED",
     * "ERR_NOT_FOUND".
     */
    long tell();

    /** Set the current position for the ContentObject IO operation
     *
     * @param offset The offset to seek to.
     * @param origin Can be one of these values: 
     *		SEEK_SET(0): Beginning of object
     *		SEEK_CUR(1): Current position of object
     *		SEEK_END(2): End of object
     *
     * @return The new position relative to the beginning of the ContentObject. Negative on error. 
     * Possible error codes are "ERR_PERMISSION_DENIED", "ERR_NOT_FOUND", "ERR_UNSUPPORTED_METHOD", 
     * "ERR_IO".
     */
    long seek(long offset, int seekFrom);

    /** Opens the ContentObject for further IO operations.
     *
     * @param mode Indicates the desired I/O access operation:
     *                 "r"  - read only
     *				   "rw" - read and write
     * The behavior is undefined for invalid operation flags.
     * @param lock If the lock argument is set to true, then another application is prohibited 
     * from opening the stream associated with a ContentObject. The lock is removed when the 
     * ContentObject is closed.  
     *
     * @return Error code. Possible error codes are "STATUS_SUCCESS", "ERR_PERMISSION_DENIED",
     * "ERR_NOT_FOUND", "ERR_GENERAL".
     */
    int open(String mode, boolean lock);
    
    /** Close the ContentObject for further reading or writing.
     *
     * @return Error code. Negative on error. Possible error codes are "ERR_PERMISSION_DENIED",
     * "ERR_NOT_FOUND", "ERR_GENERAL".
     */
    int close();
    
    /** Retrieve data from the ContentObject. This method uses the current offset and advances 
     *  the offset at the completion of the read. 
     *
     * @param buf 		Read buffer
     * @param count		Number of bytes to read.
     * 
     * @return Number of bytes read. Negative on error. The possible errors are 
     * "ERR_PERMISSION_DENIED", "ERR_NOT_FOUND", "ERR_UNSUPPORTED_METHOD", "ERR_IO".
     */
    int read(out byte[] buf, int count);
    
    /** Update the ContentObject data. This method uses the current offset and advances 
     *  the offset at the completion of the write.
     *
     * @param buf 		Write buffer
     * @param count		Number of bytes to write.
     * 
     * @return Number of bytes written. Negative on error. The possible errors are 
     * "ERR_PERMISSION_DENIED", "ERR_NOT_FOUND", "ERR_UNSUPPORTED_METHOD", "ERR_IO".
     */
    int write(in byte[] buf, int count);

    /** Remove the ContentObject and associated metadata/data.
     *
     * @return True if content deleted. False on error.
     */
    int remove();

    /** Retrieve the ContentObject streaming URI by the Streaming Server
     *
     * @return local host URI for streaming playback. If an error occurred, NULL will be returned.
     */
    String getStreamingUri();
}

