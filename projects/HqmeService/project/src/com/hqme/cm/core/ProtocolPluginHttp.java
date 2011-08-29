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

import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmDate;
import com.hqme.cm.util.CmUri;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Protocol Handler for HTTP and HTTPS schemes.
 *
 */
public class ProtocolPluginHttp implements ProtocolPlugin {

    public boolean canHandleProtocol(String scheme) {
        if (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
            return true;
        
        return false;
    }

    public ProtocolHandler getNewProtocolHandler() {
        return new ProtocolHandlerHttp();
    }
    
    public class ProtocolHandlerHttp implements ProtocolHandler {
        
        // ==================================================================================================================================
        // true for mirrored remote files (to ensure file has not changed since last
        // downloaded)
        // false is slightly more dangerous (requires a file accuracy verification
        // method, for example, MD5)
        private boolean mIsRequiredHeader_IfUnmodifiedSince = false;
        
        private HttpGet mRequest = null;
        private InputStream mResponseStream = null;
        
        private CmUri mURI = null;
        private CmDate mLastModified = null;
        private Long mContentLength = null;
        
        public Long getContentLength() throws ProtocolException {
            return mContentLength;
        }

        public Date getLastModified() throws ProtocolException{
            return mLastModified;
        }

        public int finalizeRequest() {
            final String tag_LogLocal = "ProtocolHandlerHttp_finalizeRequest";
            if (mRequest != null)
                try {
                    mRequest.abort();
                } catch (Exception fault) {
                    CmClientUtil
                            .debugLog(getClass(), tag_LogLocal + " @ mRequest.abort", fault);
                } finally {
                    mRequest = null;
                }

            if (mResponseStream != null)
                try {
                    mResponseStream.close();
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ responseStream.close",
                            fault);
                } finally {
                    mResponseStream = null;
                }
            return 0;
        }
    
        public int intitializeRequest(String URI) {
            this.mURI = CmUri.create(URI);
            return 0;
        }
    
        public int pauseTransfer() throws ProtocolException{
            throw new ProtocolException(ProtocolException.ProtocolError.ERR_UNSUPPORTED_OPERATION);
        }
    
        public int readData(byte[] buffer, int bufSize, int offset) throws ProtocolException {
            try {
                return mResponseStream.read(buffer, offset, bufSize);
            } catch (IOException e) {
                throw new ProtocolException(e);
            }
        }
    
        public int resumeTransfer() throws ProtocolException{
            throw new ProtocolException(ProtocolException.ProtocolError.ERR_UNSUPPORTED_OPERATION);
        }
    
        public int startTransfer() throws ProtocolException {
            return startTransfer(0);
        }
    
        public int startTransfer(int offset) throws ProtocolException {
            return startTransfer(offset, CmDate.EPOCH);
        }

        public int startTransfer(int offset, Date unmodifiedSince) throws ProtocolException {
            final String tag_LogLocal = "ProtocolHandlerHttp_startTransfer";
            
            Long progressBytes = (long)offset;
            CmDate modifiedDate = new CmDate(unmodifiedSince);
            
            mRequest = new HttpGet(mURI.toString());
            
            if (progressBytes > 0) {
                if (mIsRequiredHeader_IfUnmodifiedSince)
                    if (!CmDate.EPOCH.equals(modifiedDate))
                        mRequest.addHeader("If-Unmodified-Since", CmDate.HTTP_FORMATTER
                                .format(modifiedDate));

                mRequest.addHeader("Range", "bytes=" + progressBytes + "-");
            }
            CmClientUtil.debugLog(getClass(), tag_LogLocal, "Downloading %s", mRequest
                    .getURI().toString());
            
            HttpResponse response;
            try {
                response = new DefaultHttpClient().execute(mRequest);
            } catch (Throwable fault) {
                throw new ProtocolException(fault);
            }
            // this
            // action
            // may
            // take
            // a
            // very
            // long
            // time,
            // especially
            // when
            // the
            // target
            // doesn't
            // exist
            
            int statusCode = response.getStatusLine().getStatusCode();
            String statusMessage = response.getStatusLine().toString();
            CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ " + statusMessage,
                    "Status = %d (0x%08x)", statusCode, statusCode);

            switch (statusCode) {
                case HttpStatus.SC_PARTIAL_CONTENT:
                case HttpStatus.SC_OK:
                    Header lastModifiedHeader = response.getFirstHeader("Last-Modified");
                    // TODO: This code will fail if HTTP server does not send last-modified header.
                    if (lastModifiedHeader == null) {
                        CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ " + statusMessage,
                                "Required header \"Last-Modified\" is missing.");
                        throw new ProtocolException(ProtocolException.ProtocolError.ERR_HTTP_REQUIRED_HEADER_MISSING);
                    }
                    mLastModified = CmDate.valueOf(lastModifiedHeader.getValue());
                    
                    Header contentLengthHeader = response.getFirstHeader("Content-Length");
                    if (contentLengthHeader != null)
                        mContentLength = Long.valueOf(contentLengthHeader.getValue());

                    try {
                        mResponseStream = response.getEntity().getContent();
                    } catch (Throwable fault) {
                        throw new ProtocolException(fault);
                    }
                    return 0; // SUCCESS

                case HttpStatus.SC_PRECONDITION_FAILED:
                    throw new ProtocolException(ProtocolException.ProtocolError.ERR_HTTP_PRECONDITION_FAILED);
                case HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE:
                    throw new ProtocolException(ProtocolException.ProtocolError.ERR_HTTP_RANGE_NOT_SATISFIABLE);
                default:
                    throw new ProtocolException(ProtocolException.ProtocolError.ERR_HTTP_GENERIC);
            }
        }
        
        public int stopTransfer() {
            final String tag_LogLocal = "ProtocolHandlerHttp_stopTransfer";
            if (mRequest != null)
                try {
                    mRequest.abort();
                } catch (Exception fault) {
                    CmClientUtil
                            .debugLog(getClass(), tag_LogLocal + " @ mRequest.abort", fault);
                } finally {
                    mRequest = null;
                }

            if (mResponseStream != null)
                try {
                    mResponseStream.close();
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ responseStream.close",
                            fault);
                } finally {
                    mResponseStream = null;
                }
            return 0;
        }
    
        public int writeData(byte[] buffer, int bufSize, int offset) throws ProtocolException {
            throw new ProtocolException(ProtocolException.ProtocolError.ERR_UNSUPPORTED_OPERATION);
        }
    }
}
