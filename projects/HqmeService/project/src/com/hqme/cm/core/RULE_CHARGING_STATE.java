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
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.hqme.cm.util.CmClientUtil;


public class RULE_CHARGING_STATE extends RuleBase {
    private static RULE_CHARGING_STATE sCHARGINGRULE_INSTANCE = new RULE_CHARGING_STATE();

    private static boolean sIsCharging;

    public static RULE_CHARGING_STATE getInstance() {
        return sCHARGINGRULE_INSTANCE;
    }

    public void setIsCharging(boolean isCharging) {
        RULE_CHARGING_STATE.sIsCharging = isCharging;
    }

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        // state has changed - must let the Work Order Manager know
        // invoke calculateWorkOrderPriorities
        String action = arg1.getAction();
        if (Intent.ACTION_POWER_CONNECTED.equals(action))
            sIsCharging = true;
        else if (Intent.ACTION_POWER_DISCONNECTED.equals(action))
            sIsCharging = false;

        super.onReceive(arg0, arg1);
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        try {
            if (Boolean.parseBoolean(rule.getValue().toLowerCase()))
                return sIsCharging;
        } catch (Exception exec){
            CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
        }
        // if false is set, disregard the value of charging status
        return true;
    }

    @Override
    public void init(Context context) {
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryChanged = context.registerReceiver(null, intentFilter);         
        setIsCharging((batteryChanged.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)); // battery
        // = 0
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
