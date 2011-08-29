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

import com.hqme.cm.IQueueRequest;
import com.hqme.cm.Property;
import com.hqme.cm.QueueRequestState;
import com.hqme.cm.IRequestManagerCallback;

/**
 * IRequestManager defines the HQME request manager interface for HQME applications.
 */
interface IRequestManager
{
	/** Create a QueueRequest  
	 * 
	 * @return Reference to the QueueRequest Object.
	 *
	 * Note: [non-normative]
	 */
    IQueueRequest createQueueRequest();
   
	/** Create a QueueRequest  
	 * 
	 * @param queueRequestXml An XML-format string representing a QueueRequest object and its Properties 
	 *
	 * @return Reference to the QueueRequest Object.
	 *     
	 * Note: [non-normative]
	 */
	IQueueRequest createQueueRequestXml(in String queueRequestXml);
   
	/** Retrieve a count of queued QueueRequests.  
	 * 
	 * @return Number of queued requests in the queue.
	 */
    int requestCount();   
    
	/** Retrieve a count of queued QueueRequests.  
	 * 
	 * @param state Used as a filter.
	 * 
	 * @return Number of queued requests in the given state.
	 */
 	int requestCountState(int state);   
 	
	/** Retrieve an array of RequestIds associated with the currently queued QueueRequests
	 * 
	 * @return List of all request ids in the queue.
	 */
    long[] getRequestIds();    
    
	/** Retrieve an array of RequestIds associated with the currently queued QueueRequests
	 * 
	 * @param state Used as a filter.
	 * 
	 * @return List of request ids in the given state.
	 */
    long[] getRequestIdsState(int state);       

	/** Retrieve a QueueRequest object from the request queue
	 * 
	 * @param requestId Request ID of the requested QueueRequest.
	 * 
	 * @return QueueRequest object.
	 */
    IQueueRequest getRequest(long requestId);

	/** Submit a QueueRequest to the request queue.  
	 * 
	 * @param request QueueRequest to be submitted..
	 * 
	 * @return 1 on success. Negative error code on error.
	 */
    long submitRequest(in IQueueRequest request);
    
    // Operations on queued requests
    
	/** Cancel a previously submitted QueueRequest in the request queue
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * 
	 * @return 1 on success. Negative error code on error.
	 */
    int cancelRequest(long requestId);
    
	/** Suspend a previously submitted QueueRequest in the request queue
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * 
	 * @return 1 on success. Negative error code on error.
	 */
    int suspendRequest(long requestId);
    
	/** Resume a previously suspended QueueRequest in the request queue
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * 
	 * @return 1 on success. Negative error code on error.
	 */
    int resumeRequest(long requestId);

	/** Retrieve the process (as a percentage) for a QueueRequest in the request queue.
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * 
	 * @return Progress value on success. Negative error code on error.
	 */
    int getProgress(long requestId);
    
	/** Retrieve the state of a previously submitted QueueRequest.
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * 
	 * @return QueueRequestState on success. Null on erro.
	 */
    QueueRequestState getState(long requestId);
    
	/** Retrieve the priority of a previously submitted QueueRequest currently in the request queue. 
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * 
	 * @return Priority value on success [0-100]. Negative error code on error.
	 */
    int getPriority(long requestId);
    
	/** Set the priority of a previously submitted QueueRequest currently in the request queue. 
	 * 
	 * @param requestId Request ID of the QueueRequest.
	 * @param relativePriority New priority value. [0-100]
	 * 
	 * @return New priority value on success. Negative error code on error.
	 */
    int setPriority(long requestId, int relativePriority); 

    /** Register a client application callback function to get informed with RequestManager status changes.
     * 
     * @param cb the client application's callback function  
     * 
     * Note: not a P2200 API
     */
    void registerCallback(IRequestManagerCallback cb);

    /** Unregister a client application callback function to get informed with RequestManager status changes.
     * 
     * @param cb the client application's callback function  
     * 
     * Note: not a P2200 API
     */
    void unregisterCallback(IRequestManagerCallback cb);         
}
