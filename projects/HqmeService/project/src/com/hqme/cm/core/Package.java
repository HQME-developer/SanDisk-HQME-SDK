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

import com.hqme.cm.util.CmDate;
import com.hqme.cm.util.CmNumber;
import com.hqme.cm.util.CmProperties;
import com.hqme.cm.util.CmUri;

/**
 * 
 * The Package class is used for internal representation of the item downloaded by QueueRequest.
 * 
 * A WorkOrder consists of a list of Properties, and may have a Policy limiting conditions when
 * the download may take place. The Package is the item which the WorkOrder downloads.
 * 
 */
public class Package {
    // ==================================================================================================================================
    public static final String TAG_PACKAGE_MIME_TYPE = QueueRequestProperties.RequiredProperties.REQPROP_TYPE.name();

    public static final String TAG_SOURCE_URI = QueueRequestProperties.RequiredProperties.REQPROP_SOURCE_URI.name();

    public static final String TAG_SOURCE_LOCAL_PATH = QueueRequestProperties.RequiredProperties.REQPROP_STORE_NAME.name();

    public static final String TAG_CONTENT_SIZE = QueueRequestProperties.RequiredProperties.REQPROP_TOTAL_LENGTH.name();

    public static final String TAG_PROGRESS_BYTES = QueueRequestProperties.TransientProperties.REQPROP_CURRENT_BYTES_TRANSFERRED.name();

    public static final String TAG_TRANSFER_BYTES_MOBILE = QueueRequestProperties.TransientProperties.REQPROP_TRANSFER_BYTES_MOBILE.name();

    public static final String TAG_MODIFIED = QueueRequestProperties.TransientProperties.REQPROP_LAST_MODIFICATION_DATE.name();

    public final CmProperties properties = new CmProperties(); 
    
    private long    dbIndex = 0;
    
    public long getDbIndex() {
        return dbIndex;
    }

    public void setDbIndex(long dbIndex) {
        this.dbIndex = dbIndex;
    }

    // constructor for conversion from general property hashmap
    public Package(CmProperties props) {
        properties.putAll(props);
    }
    
    public Package() {
        // TODO Auto-generated constructor stub
    }
    // ----------------------------------------------------------------------------------------------------------------------------------
    public String getPackageMimeType() {
        return this.properties.get(TAG_PACKAGE_MIME_TYPE);
    }

    public String setPackageMimeType(String newPackageMimeType) {
        return properties.set(TAG_PACKAGE_MIME_TYPE, newPackageMimeType);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public String getSourceLocalPath() {
        return this.properties.get(TAG_SOURCE_LOCAL_PATH);
    }

    public String setSourceLocalPath(String newSourceLocalPath) {
        return properties.set(TAG_SOURCE_LOCAL_PATH, newSourceLocalPath);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public CmUri getSourceUri() {
        return CmUri.create(this.properties.get(TAG_SOURCE_URI, CmUri.EMPTY));
    }

    public CmUri setSourceUri(CmUri newSourceUri) {
        setSourceUri(newSourceUri == null ? (newSourceUri = CmUri.EMPTY).toString() : newSourceUri
                .toString());
        return newSourceUri;
    }

    public String setSourceUri(String newSourceUri) {
        return properties.set(TAG_SOURCE_URI, newSourceUri);
    }
   

    // ----------------------------------------------------------------------------------------------------------------------------------
    public CmDate getModified() {
        return CmDate.valueOf(this.properties.get(TAG_MODIFIED, CmDate.EPOCH));
    }

    public CmDate setModified(CmDate newDate) {
        setModified(newDate == null ? (newDate = CmDate.EPOCH).toString() : newDate.toString());
        return newDate;
    }

    public String setModified(String newDate) {
        return properties.set(TAG_MODIFIED, newDate);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public Long getProgressBytes() {
        return CmNumber.parseLong(this.properties.get(TAG_PROGRESS_BYTES, 0L), 0L);
    }

    public Long setProgressBytes(Long newProgressBytes) {
        setProgressBytes(newProgressBytes == null ? (newProgressBytes = 0L).toString()
                : newProgressBytes.toString());
        return newProgressBytes;
    }

    public String setProgressBytes(String newProgressBytes) {
        return properties.set(TAG_PROGRESS_BYTES, newProgressBytes);
    }

 // ----------------------------------------------------------------------------------------------------------------------------------
    public Long getMobileDownloadBytes() {
        return CmNumber.parseLong(this.properties.get(TAG_TRANSFER_BYTES_MOBILE, 0L), 0L);
    }

    public Long setMobileDownloadBytes(Long newProgressBytes) {
        setMobileDownloadBytes(newProgressBytes == null ? (newProgressBytes = 0L).toString()
                : newProgressBytes.toString());
        return newProgressBytes;
    }

    public String setMobileDownloadBytes(String newProgressBytes) {
        return properties.set(TAG_TRANSFER_BYTES_MOBILE, newProgressBytes);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public Long getContentSize() {
        return CmNumber.parseLong(this.properties.get(TAG_CONTENT_SIZE, 0L), 0L);
    }

    public Long setContentSize(Long newContentSize) {
        setContentSize(newContentSize == null ? (newContentSize = 0L).toString() : newContentSize
                .toString());
        return newContentSize;
    }

    public String setContentSize(String newContentSize) {
        return properties.set(TAG_CONTENT_SIZE, newContentSize);
    }
    // ----------------------------------------------------------------------------------------------------------------------------------
}
