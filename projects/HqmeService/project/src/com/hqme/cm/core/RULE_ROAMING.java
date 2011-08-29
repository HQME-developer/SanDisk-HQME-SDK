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

import com.hqme.cm.util.CmClientUtil;

public class RULE_ROAMING extends RuleBase {
    private static RULE_ROAMING sRULE_ROAMING = new RULE_ROAMING();

    private static boolean sIsConnected = false;

    private static boolean sIsRoaming = false;
    
    private static boolean sMobileSession = false;

    public static boolean isMobileSession() {
        return sMobileSession;
    }

    public static RULE_ROAMING getInstance() {
        return sRULE_ROAMING;
    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(arg1.getAction())) {

            sIsConnected = !arg1.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            
            if (sIsConnected) {
                NetworkInfo networkInfo = arg1
                        .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                int networkType = (networkInfo != null) ? networkInfo.getType() : -1;
                if (networkType == ConnectivityManager.TYPE_MOBILE) {
                    sMobileSession = true;
                } else {
                    sMobileSession = false;
                }

                if (networkInfo != null)
                    sIsRoaming = networkInfo.isRoaming();
                else
                    sIsRoaming = false;
            
            } else {
                sIsRoaming = false;
                sMobileSession = false;
            }

            super.onReceive(arg0, arg1);
        } 
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {

        // rule value false means download is forbidden when roaming
        try { 
            if (isMobileSession())
                if (!Boolean.parseBoolean(rule.getValue()))
                    return !sIsRoaming;   
        } catch (Exception exec) {
            CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
        }

        // if the value is true, or we are on wifi, we may download even when roaming 
        return true;
    }

    @Override
    public void init(Context context) {              
        
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();

        if (networkInfo != null) {
            sIsConnected = networkInfo.isConnected();
            if (sIsConnected) {
                if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    sMobileSession = true;
                }
                sIsRoaming = networkInfo.isRoaming();
            }
        }
        super.init(context);
    }

    @Override
    public boolean isValid(String value) {
        // true/false
        if (value == null)
            return false;
        
        if (!Boolean.parseBoolean(value)) {            
            if (!"false".equals(value.toLowerCase()))
                return false;
        }
        return true;
    }   
}
