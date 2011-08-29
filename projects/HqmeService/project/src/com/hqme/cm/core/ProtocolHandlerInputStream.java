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

import java.io.IOException;
import java.io.InputStream;

public class ProtocolHandlerInputStream extends InputStream {

    private ProtocolHandler mHandler;
        
    public ProtocolHandlerInputStream(ProtocolHandler handler) {
        super();
        mHandler = handler;
    }

    @Override
    public void close() throws IOException {
        super.close();
        mHandler.stopTransfer();
    }

    @Override
    public int read(byte[] b, int offset, int length) throws IOException {
        try {
            return mHandler.readData(b, length, offset);
        } catch (ProtocolException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        read(b, 0, 1);
        return (int)(b[0]);
    }

}
