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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.Process;
import android.util.Log;

import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmNumber;
import com.hqme.cm.VSDEvent;
import com.hqme.cm.IVSD;
import com.hqme.cm.IStorageManager;
import com.hqme.cm.IStorageManagerCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class StorageManager extends Service {
    private static final String sTag = "StorageManager";
    private static final long[] EMPTY_LONGS = new long[0];
    
    private static RemoteCallbackList<IVSD> sCachePlugins = new RemoteCallbackList<IVSD>();
    private static RemoteCallbackList<IStorageManagerCallback> sAppCallbacks = new RemoteCallbackList<IStorageManagerCallback>(); 
    private static Random sRandomGenerator = new Random(new Date().getTime());
    private static VSDPluginManager sVsdPluginManager = null;

    /**
    * Class for clients to access.  Because we know this service does not always
    * runs in the same process as its clients, we do need to deal with IPC.
    */
    protected static final IStorageManager.Stub sCacheManager = new IStorageManager.Stub() {
        private final HashSet<Integer> ids = new HashSet<Integer>();

        public int registerPlugin(IVSD pluginProxy) throws RemoteException {
            synchronized (sCachePlugins) {
                int storageId = -12345;
                if (pluginProxy == null) {
                    Log.w(sTag, "registerPlugin: invalid plugin proxy ignored");
                } else if (!sCachePlugins.register(pluginProxy)) {
                    Log.w(sTag, "registerPlugin: register failed");
                } else {
                    storageId = pluginProxy.storageId();
                    if (storageId < 0) {
                        CmClientUtil.debugLog(sTag, "registerPlugin: new VSD plugin registered");
                        do {
                            storageId = sRandomGenerator.nextInt(0x7fffffff) + 1;
                        } while (ids.contains(storageId));
                    }
                    CmClientUtil.debugLog(sTag, "registerPlugin: storageId = " + storageId + ", storageName = " + pluginProxy.name() + ", myPid = " + Process.myPid());
                    ids.add(storageId);
                    
                    updateStatus(VSDEvent.VSD_ADDED.getCode(), storageId);
                }
                return storageId;
            }
        }

        public void unregisterPlugin(IVSD pluginProxy) throws RemoteException {
            synchronized (sCachePlugins) {
                int storageId = pluginProxy.storageId();
                updateStatus(VSDEvent.VSD_REMOVED.getCode(), storageId);

                CmClientUtil.debugLog(sTag, "unregisterPlugin: storageId = " + storageId + ", storageName = " + pluginProxy.name());
                
                sCachePlugins.unregister(pluginProxy);
            }
        }

        public void updateStatus(int eventCode, int storageId) throws RemoteException {
            try {
                final int cbCount = sAppCallbacks.beginBroadcast();
                for (int cbIndex = 0; cbIndex < cbCount; cbIndex++) {
                    try {
                        sAppCallbacks.getBroadcastItem(cbIndex).handleEvent(eventCode, storageId);
                    } catch (RemoteException fault) {
                        Log.e(sTag, "updateStatus: client's updateStatus failed: " + fault);
                    }
                }
                sAppCallbacks.finishBroadcast();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public int VSDCount() throws RemoteException {
            int count = sCachePlugins.beginBroadcast();
            sCachePlugins.finishBroadcast();
            return count;
        }

        public int[] getStorageIds(long[] functionGroups) throws RemoteException {
            synchronized (sCachePlugins) {
                ArrayList<Integer> storageIds = new ArrayList<Integer>();
                final int pluginCount = sCachePlugins.beginBroadcast();
                if (pluginCount > 0) {
                    for (int pluginIndex = 0; pluginIndex < pluginCount; pluginIndex++) {
                        try {
                            IVSD plugin = sCachePlugins.getBroadcastItem(pluginIndex);
                            if (functionGroups != null) {
                                long[] funcGroups = plugin.functionGroups();
                                if (funcGroups == null) {
                                    continue;
                                }
                                
                                boolean met = true;
                                for (long funcWanted : functionGroups) {
                                    if (Arrays.binarySearch(funcGroups, funcWanted) < 0) {
                                        met = false;
                                        break;
                                    }
                                }
                                
                                if (!met) {
                                    continue;
                                }
                            }
                            storageIds.add(plugin.storageId());
                        } catch (RemoteException fault) {
                        }
                    }
                }
                sCachePlugins.finishBroadcast();
                return storageIds.size() == 0 ? null : CmNumber.convertIntegers(storageIds);
            }
        }

        public long[] getFunctionGroups(int storageId) throws RemoteException {
            synchronized (sCachePlugins) {
                IVSD plugin = getStorage(storageId);
                if (plugin != null) {
                    return plugin.functionGroups();
                } else {
                    return EMPTY_LONGS;
                }
            }
        }

        public IVSD getStorage(int storageId) throws RemoteException {
            synchronized (sCachePlugins) {
                if (storageId == 0) {
                    return getDefaultStore();
                }
                
                IVSD plugin = null;
                final int pluginCount = sCachePlugins.beginBroadcast();
                for (int pluginIndex = 0; pluginIndex < pluginCount; pluginIndex++) {
                    try {
                        plugin = sCachePlugins.getBroadcastItem(pluginIndex);
                        if (storageId == plugin.storageId()) {
                            break;
                        } else {
                            plugin = null;
                        }
                    } catch (RemoteException fault) {
                        fault.printStackTrace();
                    }
                }
                sCachePlugins.finishBroadcast();
                return plugin;
            }
        }

        private IVSD getDefaultStore() throws RemoteException {
            IVSD plugin = null;
            final int pluginCount = sCachePlugins.beginBroadcast();
            for (int pluginIndex = 0; pluginIndex < pluginCount; pluginIndex++) {
                try {
                    plugin = sCachePlugins.getBroadcastItem(pluginIndex);
                    if (plugin.name() == "Default Cache") {
                        break;
                    }
                } catch (RemoteException fault) {
                    fault.printStackTrace();
                }
            }
            sCachePlugins.finishBroadcast();
            return plugin;
        }

        public void registerCallback(IStorageManagerCallback cb) throws RemoteException {
            sAppCallbacks.register(cb);
        }

        public void unregisterCallback(IStorageManagerCallback cb) throws RemoteException {
            sAppCallbacks.unregister(cb);
        }
    };

    public void onCreate() {
        sVsdPluginManager = new VSDPluginManager();
        sVsdPluginManager.doDiscovery();
        sVsdPluginManager.doLaunch();
    }

    public void onDestroy() {
        if (sCachePlugins != null)
            sCachePlugins.kill();

        if (sAppCallbacks != null)
            sAppCallbacks.kill();
        
        sRandomGenerator = null;
        sVsdPluginManager = null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public IBinder onBind(Intent intent) {
        if (IStorageManager.class.getName().equals(intent.getAction())) {
            return sCacheManager;
        }
        
        return null;
    }

    public boolean onUnbind(Intent intent) {
        if (IStorageManager.class.getName().equals(intent.getAction())) {
            // when the last client has disconnected; this service is auto-closing
            stopSelf();
        }
        
        return false; // false = do not call rebind() later
    }
    
    //================================================================================
    private class VSDPluginManager {
        private static final String sTag = "VSDPluginManager";

        private static final String ACTION_HQME_VSD_PLUGIN = "HQME.intent.action.DISCOVER_VSD_PLUGIN";
//        private static final String CATEGORY_HQME_VSD_PLUGIN = "HQME.intent.category.DEFAULT_PLUGIN";
//        private static final String CATEGORY_HQME_VSD_PLUGIN = "HQME.intent.category.EXTERNAL_STORAGE_PLUGIN";
//        private static final String CATEGORY_HQME_VSD_PLUGIN = "HQME.intent.category.FILE_SYSTEM_PLUGIN";

        private static final String KEY_PKG = "pkg";
        private static final String KEY_SERVICENAME = "servicename";
        private static final String KEY_ACTIONS = "actions";
        private static final String KEY_CATEGORIES = "categories";

        private VSDPackageBroadcastReceiver mVsdPackageBroadcastReceiver;
        private ArrayList<PluginInfo> mPlugins;
        private Context mContext;

        private VSDPluginManager() {
            mContext = getApplicationContext();
    
            mVsdPackageBroadcastReceiver = new VSDPackageBroadcastReceiver();
            
            IntentFilter packageFilter = new IntentFilter();
            packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            packageFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packageFilter.addCategory(Intent.CATEGORY_DEFAULT);
            packageFilter.addDataScheme("package");
            
            mContext.registerReceiver(mVsdPackageBroadcastReceiver, packageFilter);
        }
        
        protected void finalize() //throws Throwable
        {
            mContext.unregisterReceiver(mVsdPackageBroadcastReceiver);
        } 

        /**
        * Helper class to hold together plug-in info.
        */
        private class PluginInfo {
            String name;
            String pkg;
            String permission;
            String processName;
            String rootDir;
            boolean enabled;
            boolean exported;
            ArrayList<String> categories;
        }
        
        public void doDiscovery() {
            mPlugins = new ArrayList<PluginInfo>();
            PackageManager packageManager = mContext.getPackageManager();
            Intent baseIntent = new Intent(ACTION_HQME_VSD_PLUGIN);
            List<ResolveInfo> list = packageManager.queryIntentServices(baseIntent, PackageManager.GET_RESOLVED_FILTER);
    
            for (int i = 0; i < list.size(); ++i) {
                ResolveInfo rInfo = list.get(i);
                ServiceInfo sInfo = rInfo.serviceInfo;
                IntentFilter filter = rInfo.filter;
    
                if (sInfo != null) {
                    PluginInfo plugin = new PluginInfo();
                    plugin.name = sInfo.name;
                    plugin.pkg = sInfo.packageName;
                    plugin.permission = sInfo.permission;
                    plugin.processName = sInfo.processName;
                    plugin.enabled = sInfo.enabled;
                    plugin.exported = sInfo.exported;
                    
                    if (filter != null) {
                        plugin.categories = new ArrayList<String>();
                        for (Iterator<String> categoryIterator = filter.categoriesIterator(); categoryIterator.hasNext();) {
                            String category = categoryIterator.next();
                            plugin.categories.add(category);
                        }
                    }
                    
                    mPlugins.add(plugin);
                }
            }
            
            for (PluginInfo plugin : mPlugins) {
                CmClientUtil.debugLog(sTag, "doDiscovery(): plugin.name = " + plugin.name);
                CmClientUtil.debugLog(sTag, "                     .pkg = " + plugin.pkg);
                CmClientUtil.debugLog(sTag, "                     .permission = " + plugin.permission);
                CmClientUtil.debugLog(sTag, "                     .processName = " + plugin.processName);
                CmClientUtil.debugLog(sTag, "                     .enabled = " + plugin.enabled);
                CmClientUtil.debugLog(sTag, "                     .exported = " + plugin.exported);
                for (int j = 0; j < plugin.categories.size(); j++) {
                    CmClientUtil.debugLog(sTag, "                     .categories[" + j + "] = " + plugin.categories.get(j));
                }
            }
        }
    
        public void doLaunch() {
            for (PluginInfo plugin : mPlugins) {
                if (plugin.enabled && plugin.exported) {
                    Intent i = new Intent();
                    i.setClassName(plugin.pkg, plugin.name);
                    startService(i);
                }                
            }
        }
    
        private class VSDPackageBroadcastReceiver extends BroadcastReceiver {
            public void onReceive(Context context, Intent intent) {
                mPlugins.clear();
                doDiscovery();
                doLaunch();
            }
        }
    }
}
