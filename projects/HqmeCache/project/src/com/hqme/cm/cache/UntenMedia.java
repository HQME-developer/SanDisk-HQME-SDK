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

package com.hqme.cm.cache;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

public class UntenMedia extends BroadcastReceiver {
    private static String sTag = "UntenMedia";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_EJECT.equals(intent.getAction())) {
            try {
                UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_EJECT): myPid = " + Process.myPid()); 
                
                UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_EJECT): unloading contents...");
                UntenCacheService.doUnload();
                
                if (UntenCacheService.sPluginManagerProxy != null && UntenCacheService.sUntenCache != null) {
                    UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_EJECT): unregistering plugin...");
                    UntenCacheService.sPluginManagerProxy.unregisterPlugin(UntenCacheService.sUntenCache);
                } else {
                    Log.w(sTag, "onReceive(ACTION_MEDIA_EJECT): sPluginManagerProxy = " + UntenCacheService.sPluginManagerProxy
                            + ", sUntenCache = " + UntenCacheService.sUntenCache);
                    Process.killProcess(Process.myPid()); 
                }
            } catch (RemoteException fault) {
                fault.printStackTrace();
            } catch (Exception fault) {
                fault.printStackTrace();
            } finally {
                Message.obtain(UntenMedia.eventHandler, Intent.ACTION_MEDIA_EJECT.hashCode()).sendToTarget();
            }
        } else if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) {
            try {
                UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_MOUNTED): myPid = " + Process.myPid());

                UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_MOUNTED): loading contents...");
                UntenCacheService.doLoad();
                
                if (UntenCacheService.sPluginManagerProxy != null && UntenCacheService.sUntenCache != null) {
                    UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_MOUNTED): registering plugin...");
                    UntenCacheService.sPluginManagerProxy.registerPlugin(UntenCacheService.sUntenCache);
                } else {
                    Log.w(sTag, "onReceive(ACTION_MEDIA_MOUNTED): sPluginManagerProxy = " + UntenCacheService.sPluginManagerProxy
                            + ", sUntenCache = " + UntenCacheService.sUntenCache);
                    Intent i = new Intent();
                    i.setClassName("com.hqme.cm.cache", UntenCacheService.class.getName());
                    context.startService(i);
                }
            } catch (RemoteException fault) {
                fault.printStackTrace();
            } finally {
                Message.obtain(UntenMedia.eventHandler, Intent.ACTION_MEDIA_MOUNTED.hashCode()).sendToTarget();
            }
        } else if (Intent.ACTION_MEDIA_UNMOUNTABLE.equals(intent.getAction())) {
            UntenCacheService.debugLog(sTag, "onReceive(ACTION_MEDIA_UNMOUNTABLE): sPluginManagerProxy = " + UntenCacheService.sPluginManagerProxy
                    + ", sUntenCache = " + UntenCacheService.sUntenCache);
        
            Message.obtain(UntenMedia.eventHandler, Intent.ACTION_MEDIA_UNMOUNTABLE.hashCode())
                    .sendToTarget();
        }
    }
    
    // Add code here to handle media events with UI
    // note that registration/unregistration of the adaptor is handled within
    // this broadcast receiver's onReceive()
    public final static Handler eventHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == Intent.ACTION_MEDIA_MOUNTED.hashCode())
                ;
            else if (msg.what == Intent.ACTION_MEDIA_UNMOUNTABLE.hashCode())
                ;
            else if (msg.what == Intent.ACTION_MEDIA_EJECT.hashCode())
                ;
        }
    };
}
