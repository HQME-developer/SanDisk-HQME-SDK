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

package com.hqme.cm.util;

import android.net.Uri;

public class CmUri {
    // ==================================================================================================================================
    public static final CmUri EMPTY = new CmUri(Uri.EMPTY);

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static final CmUri create(Uri uri) {
        return new CmUri(uri);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static final CmUri create(String uri) {
        return new CmUri(uri);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static final Uri toUri(CmUri uri) {
        return uri == null ? Uri.EMPTY : uri.mUri;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static final String toString(CmUri uri) {
        return uri == null ? "" : uri.toString();
    }

    // ==================================================================================================================================
    private Uri mUri = null;

    private CmUri(Uri uri) {
        this.mUri = uri;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    private CmUri(String uri) {
        try {
            this.mUri = Uri.parse(uri);
        } catch (Throwable fault) {
            this.mUri = Uri.EMPTY;
        }
    }

    // ==================================================================================================================================
    public final Uri toUri() {
        return this.mUri;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    // this implementation works around a formatting bug in Uri's toString()
    // method
    // which causes a null char to be inserted at index 0 in the resulting
    // serialized Uri string (weird...)
    // the "corrupted" string is cleaned by trim()
    //
    public final String toString() {
        return this.mUri == null ? "" : this.mUri.toString().trim();
    }
    // ==================================================================================================================================
}
