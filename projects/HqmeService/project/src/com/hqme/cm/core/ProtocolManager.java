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

import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

/**
 * Protocol Manager handles registration and lifetime management of various
 * ProtocolHandlers.
 * 
 * Implementation Note: Currently Uri is a required field for selection of 
 * the ProtocolHandler and MIME type is not used.
 * 
 * Future implementations of the ProtocolManager will select a ProtocolHandler based on
 * both the MIME type and, if-defined, the Uri of the Request.                 
 */
public class ProtocolManager {

    private static ProtocolManager mSingleProtocolManager = null;
    private List<ProtocolPlugin> mPlugins = new LinkedList<ProtocolPlugin>();
    
    private ProtocolManager() {
        
    }
    
    /** Get Singleton instance of protocol manager.
     * 
     * @return ProtocolManager instance.
     */
    public static ProtocolManager getInstance() {
        if (mSingleProtocolManager == null)
        {
            mSingleProtocolManager = new ProtocolManager();
        
            // Register default plugins:
            mSingleProtocolManager.registerPlugin(new ProtocolPluginHttp());
        }
        
        return mSingleProtocolManager;
    }
    
    public boolean registerPlugin(ProtocolPlugin pm) {
        if (mPlugins.contains(pm))
            return false;
        
        return mPlugins.add(pm);
    }
    
    public boolean unregisterPlugin(ProtocolPlugin pm) {
        return mPlugins.remove(pm);
    }
    
    /**
     * Based on URI searches all registered ProtocolHandlers and returns the one
     * that can handle these type of URIs.
     * 
     * @param URI Source URI for the operation.
     * @return A ProtocolHandler instance that can handle this type of URIs.
     *         Instance is not initialized with the URI.
     * @throws ProtocolException Thrown if an unrecoverable error occurs.
     */
    public ProtocolHandler getProtocolHandler(String URI) throws ProtocolException {
        final String tag_LogLocal = "ProtocolManager_getProtocolHandler";
        String scheme = getUriScheme(URI);
        
        CmClientUtil.debugLog(getClass(), tag_LogLocal, "%s", "Scheme [ " + scheme +" ] for URI = [ " + URI + " ].");
        
        if (scheme == null)
            throw new ProtocolException(ProtocolException.ProtocolError.ERR_UNSUPPORTED_PROTOCOL, "Scheme is NULL.");
        
        for(ProtocolPlugin plugin: mPlugins) {
            if (plugin.canHandleProtocol(scheme))
                return plugin.getNewProtocolHandler();
        }
        
        throw new ProtocolException(ProtocolException.ProtocolError.ERR_UNSUPPORTED_PROTOCOL, "Not supported by registered plugins.");
    }

    /** Returns protocol scheme as a String.
     * 
     * @param URI   source URI
     * @return      lower case scheme String. null on error.
     */
    private String getUriScheme(String URI) {
        String scheme = null;
        try {
            java.net.URI uri = new java.net.URI(URI);
            scheme =  uri.getScheme();
        } catch (URISyntaxException fault) {
            // Ignore exception.
        }
        
        if (scheme != null)
            scheme = scheme.toLowerCase();
        
        return scheme;
    }
}
