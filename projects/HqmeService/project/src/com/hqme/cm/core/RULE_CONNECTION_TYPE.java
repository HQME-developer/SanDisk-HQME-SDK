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
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import com.hqme.cm.util.CmClientUtil;

import java.util.HashMap;

public class RULE_CONNECTION_TYPE extends RuleBase {
    private static RULE_CONNECTION_TYPE sRULE_CONNECTION_TYPE_INSTANCE = new RULE_CONNECTION_TYPE();

    private static boolean sMobileSession = false;

    public static boolean isMobileSession() {
        return sMobileSession;
    }

    private static String sNetworkTypeName = "";

    private static boolean sConnectivityExists = false;

    private static boolean sIsBackgroundDownloadEnabled;

    private static HashMap<Integer, String> networkSubTypes = new HashMap<Integer, String>() {
        /**
         * 
         */
        private static final long serialVersionUID = 7208714940148213311L;

        {
            put(TelephonyManager.NETWORK_TYPE_1xRTT, "CELL2G"); // CDMA2000 1X (IS-2000), also known as 1x and 1xRTT
            put(TelephonyManager.NETWORK_TYPE_CDMA, "CELL2G"); // Either IS95A or IS95B
            put(TelephonyManager.NETWORK_TYPE_EDGE, "CELL2G");
            put(TelephonyManager.NETWORK_TYPE_GPRS, "CELL2G");
            put(TelephonyManager.NETWORK_TYPE_IDEN, "CELL2G"); // API 8

            put(TelephonyManager.NETWORK_TYPE_EVDO_0, "CELL3G"); // CDMA2000 EV-DO Rel. 0
            put(TelephonyManager.NETWORK_TYPE_EVDO_A, "CELL3G"); // CDMA2000 EV-DO Rev. A
            put(12, "CELL3G");  // API 9, CDMA2000 EV-DO Rev. B, value = 12, TelephonyManager.NETWORK_TYPE_EVDO_B
            put(TelephonyManager.NETWORK_TYPE_HSDPA, "CELL3G");
            put(TelephonyManager.NETWORK_TYPE_HSPA, "CELL3G");
            put(TelephonyManager.NETWORK_TYPE_HSUPA, "CELL3G");
            put(TelephonyManager.NETWORK_TYPE_UMTS, "CELL3G");

            put(14, "CELL3G"); // API 11 , value = 14, TelephonyManager.NETWORK_TYPE_eHRPD
            put(13, "CELL4G");  // API 11, value = 13, TelephonyManager.NETWORK_TYPE_LTE
            put(15, "CELL3G");  // API 13, value = 15 (HSPA+), TelephonyManager.NETWORK_TYPE_HSPAP
            put(TelephonyManager.NETWORK_TYPE_UNKNOWN, "CELL");
        }
    };


    public static RULE_CONNECTION_TYPE getInstance() {
        return sRULE_CONNECTION_TYPE_INSTANCE;
    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        // state has changed - must let the Work Order Manager know
        // invoke calculateWorkOrderPriorities
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(arg1.getAction())) {
            
            sIsBackgroundDownloadEnabled = ((ConnectivityManager) arg0
                    .getSystemService(Context.CONNECTIVITY_SERVICE)).getBackgroundDataSetting();

            sConnectivityExists = !arg1.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            if (sConnectivityExists) {
                NetworkInfo networkInfo = arg1
                        .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    switch (networkInfo.getType()) {
                        case ConnectivityManager.TYPE_WIFI:
                            sNetworkTypeName = "WLAN";
                            sMobileSession = false;
                            break;
                        case ConnectivityManager.TYPE_WIMAX:
                            sNetworkTypeName = "CELL4G_WIMAX";
                            sMobileSession = true;
                            break;
                        case ConnectivityManager.TYPE_MOBILE:
                            sMobileSession = true;  
                            sNetworkTypeName = networkSubTypes.get(networkInfo.getSubtype());
                            break;
                        default:
                            sNetworkTypeName = networkInfo.getTypeName();
                            sMobileSession = true;
                            break;
                    }
                }

            } else {
                sMobileSession = false;               
            }

            super.onReceive(arg0, arg1);
        } else if (ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED.equals(arg1.getAction())) {
            sIsBackgroundDownloadEnabled = ((ConnectivityManager) arg0
                    .getSystemService(Context.CONNECTIVITY_SERVICE)).getBackgroundDataSetting();
            super.onReceive(arg0, arg1);
        }
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        // behaviour specified in white paper
        try {
            if (rule.getValue() == null) 
                return isDownloadPermitted();
            if ("".equals(rule.getValue())) 
                return isDownloadPermitted();

            if (isDownloadPermitted()) {                
                String[] networkTypes = rule.getValue().split("\\s+");
                // CELL can be any of CELL2G, CELL3G, CELL4G, CELL3G_Broadcast, CELL4G_Broadcast, CELL4G_WIMAX 
                for (String nT : networkTypes) {
                    if (nT.equals(sNetworkTypeName))
                        return true;
                    else if (nT.equals("CELL") && sNetworkTypeName.startsWith(nT))
                        return true;
                }
            }
        } catch (Exception exec) {
            CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
        }

        return false;
    }

    // this rule is initialized in WorkOrder manager's onCreate since it is used to check
    // the background data settings (an Android requirement for background download)
    @Override
    public void init(Context context) {       
        // for generic network types and subtypes
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        
     // this setting is in Accounts and sync settings, Background data
        sIsBackgroundDownloadEnabled = cm.getBackgroundDataSetting();
        if (networkInfo != null) {
            sConnectivityExists = networkInfo.isConnected();
            switch (networkInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    sNetworkTypeName = "WLAN";
                    break;
                case ConnectivityManager.TYPE_WIMAX:
                    sNetworkTypeName = "CELL4G_WIMAX";
                    sMobileSession = true;
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    sNetworkTypeName = networkSubTypes.get(networkInfo.getSubtype());                     
                    sMobileSession = true;                    
                    break;
                default:
                    sNetworkTypeName = networkInfo.getTypeName();
                    sMobileSession = true;
                    break;
            }
        } 
        super.init(context);
    }

    @Override
    public boolean isValid(String value) {
        return true;
    }
    
    public static boolean isDownloadPermitted() {
        return sConnectivityExists && sIsBackgroundDownloadEnabled;          
    }
}
