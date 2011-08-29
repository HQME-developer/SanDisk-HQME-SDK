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

import java.util.Date;

/**
 * ProtocolHandler is the common interface for handling different protocols. 
 * Third party applications and core can provide implementations for different protocols. 
 *
 */
public interface ProtocolHandler {
    
    /**
     * Initialize protocol with given URI for further operations.
     * 
     * @param URI   Source address
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException Thrown if an unrecoverable error occurs.
     */
    public int intitializeRequest(String URI) throws ProtocolException;
    
    
    /** Starts transfer form offset 0.
     * @return
     * @throws ProtocolException Thrown if an unrecoverable error occurs.
     */
    public int startTransfer() throws ProtocolException ;
    
    /** Starts transfer from offset.
     * @param offset
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException Thrown if an unrecoverable error occurs.
     */
    public int startTransfer(int offset) throws ProtocolException ;
    
    /** Starts transfer from offset only if the remote content is not modified since the given date.
     * 
     * @param offset
     * @param unmodifiedSince
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException  Thrown if an unrecoverable error occurs.
     */
    public int startTransfer(int offset, Date unmodifiedSince) throws ProtocolException;
    
    /** Pauses transfer
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException  Thrown if an unrecoverable error occurs.
     */
    public int pauseTransfer() throws ProtocolException;
    
    /** Resumes transfer
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException  Thrown if an unrecoverable error occurs.
     */
    public int resumeTransfer() throws ProtocolException;
    
    /** Stops transfer.
     * @return 0 on success. Error code otherwise.
     */
    public int stopTransfer();
    
    /** Finalizes request for given URI.
     * 
     * @return 0 on success. Error code otherwise.
     */
    public int finalizeRequest();
    
    /** Reads data from source to the buffer.
     * @param buffer
     * @param bufSize
     * @param offset
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException
     */
    public int readData(byte[] buffer, int bufSize, int offset) throws ProtocolException;
    
    /** Writes data from buffer to the source.
     * @param buffer
     * @param bufSize
     * @param offset
     * @return 0 on success. Error code otherwise.
     * @throws ProtocolException
     */
    public int writeData(byte[] buffer, int bufSize, int offset) throws ProtocolException;
    
    /** Returns last-modified date of the content.
     * 
     * @return Last modified date, null if not available.
     * @throws ProtocolException thrown if method not supported by this handler.
     */
    public Date getLastModified() throws ProtocolException;
    
    /** Returns content length as reported by remote party.
     * 
     * @return Content length, null if not available.
     * @throws ProtocolException thrown if method not supported by this handler.
     */
    public Long getContentLength() throws ProtocolException;
}
