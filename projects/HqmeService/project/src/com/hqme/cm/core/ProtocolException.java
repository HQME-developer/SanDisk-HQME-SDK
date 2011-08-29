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

/**
 * Protocol exception raised if an unrecoverable occurs within a Protocol Handler operation.
 *
 */
public class ProtocolException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -7909641711696400843L;

    
    private String mMessage = null;
    private ProtocolError mErr = ProtocolError.ERR_UNKNOWN;
    
    /**
     * Enumeration for protocol error codes.
     *
     */
    public enum ProtocolError {
        ERR_UNKNOWN(-1),
        ERR_UNSUPPORTED_PROTOCOL(-2),
        ERR_UNSUPPORTED_OPERATION(-3),
        ERR_HTTP_GENERIC(-10),
        ERR_HTTP_REQUIRED_HEADER_MISSING(-11),
        ERR_HTTP_PRECONDITION_FAILED(-12),
        ERR_HTTP_RANGE_NOT_SATISFIABLE(-13);
    
        private final int mErrCode;
        ProtocolError(int error) {
            mErrCode = error;
        }
        
        public int getErrorCode(){
            return mErrCode;
        }
    }
    
    /** Constructor for custom detailed error messages.
     * @param detailMessage Detailed error message.
     */
    public ProtocolException(String detailMessage) {
        super(detailMessage);
        mMessage = detailMessage;
        mErr = ProtocolError.ERR_UNKNOWN;
    }


    /** Constructor for wrapping other faults.
     * @param throwable Other fault.
     */
    public ProtocolException(Throwable throwable) {
        this(throwable.toString());
    }

    /** Constructor for predefined errors.
     * @param error Predefined error code.
     */
    public ProtocolException(ProtocolError error) {
        mErr = error;
        mMessage = error.toString();
    }
    
    /** Constructor for predefined errors with detail message.
     * @param error Predefined error code.
     * @param detailMessage Detailed error message. Will be appended to default message
     */
    public ProtocolException(ProtocolError error, String detailMessage) {
        mErr = error;
        mMessage = error.toString() + " : \"" + detailMessage + "\"";
    }

    /** Default constructor for unknown error messages.
     * 
     */
    public ProtocolException() {
        this(ProtocolError.ERR_UNKNOWN);
    }
    
    @Override
    public String toString() {
        return "ProtocolException ( " + mErr.getErrorCode() + " ) : " + mMessage;
    }
}
