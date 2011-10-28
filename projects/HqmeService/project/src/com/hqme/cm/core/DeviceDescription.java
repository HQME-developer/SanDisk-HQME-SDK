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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings.Secure;

import com.hqme.cm.IVSD;

import java.util.ArrayList;
import java.util.UUID;

public class DeviceDescription {
    
    private static DeviceDescriptionInfo ddi = null;
    
    public static String getDeviceDescriptionXml(Context ctx) {
        if (ddi == null)
            ddi = new DeviceDescriptionInfo(ctx);
        else
            ddi.update();
        
        return ddi.getXML();
    }
    

    private static class NetworkInterfaceInfo {
        String networkAccessType;

        public NetworkInterfaceInfo(String networkAccessType) {
            this.networkAccessType = networkAccessType;
        }
    }
    
    private static class DeviceDescriptionInfo {
        private Context ctx;
        private SharedPreferences sharedPrefs;
        private Editor prefsEditor;

        private static final String pref_uniqueDeviceName = "uniqueDeviceName";
        
        private final static String TOTAL_CAPACITY = "VS_TOTAL_CAPACITY";
        private final static String AVAILABLE_CAPACITY = "VS_AVAILABLE_CAPACITY";
        
        String deviceID;
        String uniqueDeviceName;
        String currentPowerSource;
        String chargingStatus;
        String batteryPowerLevel;
        String storageCapacity;
        String storageUsage;
        String storageVSDnumbers;
        ArrayList<NetworkInterfaceInfo> niis;
        
        DeviceDescriptionInfo(Context ctx) {
            this.ctx = ctx;
            
            sharedPrefs = ctx.getSharedPreferences(DeviceDescriptionInfo.class.getName(), Context.MODE_PRIVATE);
            
            deviceID = Secure.getString(ctx.getContentResolver(), Secure.ANDROID_ID);
            if (deviceID == null)
                deviceID = "";
            
            uniqueDeviceName = sharedPrefs.getString(pref_uniqueDeviceName, null);
            if (uniqueDeviceName == null) {
                uniqueDeviceName = UUID.randomUUID().toString();
                prefsEditor = sharedPrefs.edit();
                prefsEditor.putString(pref_uniqueDeviceName, uniqueDeviceName);
                prefsEditor.commit();
            }
            
            update();
        }
        
        /** Update the transient information. 
         */
        void update() {
            currentPowerSource = "Battery"; // TODO: Spec is not clear here.
            chargingStatus = RULE_CHARGING_STATE.sIsCharging ? "Chraging" : "Available";
            batteryPowerLevel = Integer.toString(RULE_POWER_LEVEL.sBatteryLevel);

            storageVSDnumbers = "0";
            storageUsage="0";
            storageCapacity="0";

            try {
                int[] storeids = WorkOrderManager.getContentProxy().getStorageIds(null);
                if (storeids != null && storeids.length > 0)
                {
                    storageVSDnumbers = Integer.toString(storeids.length);
                    
                    // TODO: Spec is not clear here. How do we accumulate results from all VSDs? 
                    // Return the results for first VSD only.
                    IVSD store = WorkOrderManager.getContentProxy().getStorage(storeids[0]);
                    if (store != null) {
                        long totalCapacity = Long.parseLong(store.getProperty(TOTAL_CAPACITY));
                        long availableCapacity = Long.parseLong(store.getProperty(AVAILABLE_CAPACITY));
                        int freePercentage = (int)(100 * (availableCapacity / (double) totalCapacity));
                        
                        storageCapacity = Long.toString(availableCapacity);
                        storageUsage = Integer.toString(freePercentage);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            niis = new ArrayList<NetworkInterfaceInfo>();
            // TODO: Spec is not clear about active versus available interfaces. (Use NetworkInterface for the other case.)
            ConnectivityManager connMgr = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] allni = connMgr.getAllNetworkInfo();
            
            if (allni != null) {
                for (NetworkInfo ni: allni) {
                    int type = ni.getType();
                    int subType = ni.getSubtype();
                    
                    if (type == ConnectivityManager.TYPE_WIFI)
                        niis.add(new NetworkInterfaceInfo("WLAN"));
                    else if (type == ConnectivityManager.TYPE_MOBILE)
                        niis.add(new NetworkInterfaceInfo("CELL"));
                    else
                        niis.add(new NetworkInterfaceInfo(ni.getTypeName()));
                }
            }
        }
        
        String getXML() {
            // TODO: Spec has some inconsistencies with the naming of tags.
            String result
                = "<deviceInfo>\n"
                + "\t<deviceId>" + deviceID + "</deviceId>\n"
                + "\t<uniqueDeviceName>" + uniqueDeviceName + "</uniqueDeviceName>\n"
                + "\t<currentPowerSource>" + currentPowerSource + "</currentPowerSource>\n"
                + "\t<chargingStatus>" + chargingStatus + "</chargingStatus>\n"
                + "\t<batteryPowerLevel>" + batteryPowerLevel + "</batteryPowerLevel>\n"
                + "\t<storageCapacity>" + storageCapacity + "</storageCapacity>\n"
                + "\t<storageUsage>" + storageUsage + "</storageUsage>\n"
                + "\t<storageVSDnumbers>" + storageVSDnumbers + "</storageVSDnumbers>\n"
                + "\t<NetworkInterfaceList>\n"
                + "\t\t<NetworkInterfaceNumberOfEntries>" + niis.size() + "</NetworkInterfaceNumberOfEntries>\n";
            
            for (int i=0; i<niis.size(); i++) {
                result += "\t\t<NetworkInterfaceNumber>" + Integer.toString(i+1) + "</NetworkInterfaceNumber>\n";
                result += "\t\t<networkAccessType>" + niis.get(i).networkAccessType + "</networkAccessType>\n";
            }
                
            result += "\t</NetworkInterfaceList>\n";
            result += "</deviceInfo>\n";
            
            return result;
        }
    }
}
