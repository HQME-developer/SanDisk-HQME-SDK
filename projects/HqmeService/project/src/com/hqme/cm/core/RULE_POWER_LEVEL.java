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


public class RULE_POWER_LEVEL extends RuleBase {
    private static RULE_POWER_LEVEL sRULE_POWER_LEVEL = new RULE_POWER_LEVEL();

    private static int sBatteryLevel;
    private static int sBatteryMaxLevel;
    private IntentFilter mFilter = null;

    
    public static RULE_POWER_LEVEL getInstance() {
        return sRULE_POWER_LEVEL;
    }


    @Override
    public void onReceive(Context arg0, Intent arg1) {
        // state has changed - must let the Work Order Manager know
        // invoke calculateWorkOrderPriorities
       String action = arg1.getAction();
       if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
            // gets the current value
            sBatteryLevel = arg1.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            sBatteryMaxLevel = arg1.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            super.onReceive(arg0, arg1);
       }       
     
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        final String tag_LogLocal = RULE_POWER_LEVEL.class.getName() + ".evaluateRule";

        // if charging ignore battery percentage otherwise can't realistically 
        // combine this rule with RULE_CHARGING_STATE
        if (rule.getValue() != null) {
            try {
                int testval = Integer.parseInt(rule.getValue());
                if (testval == 0) return true;
                
                if (testval > 0 && testval <=100)
                    return ((sBatteryLevel * 100) / (float) sBatteryMaxLevel) >= testval;
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ parseInt", fault);
            }
        }

        // values <0 and > 100 are erroneous, return false
        return false;
    }

    // this is called in WorkOrderManager's onCreate
    @Override
    public void init(Context context) {
        IntentFilter pF = new IntentFilter();
        pF.addAction(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryChanged = context.registerReceiver(null, pF); // the current status
        sBatteryLevel = batteryChanged.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        sBatteryMaxLevel = batteryChanged.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        
        super.init(context);
    }
    
    // this is called in WorkOrderManager's onCreate
    public void register() {
        if (mFilter == null) {
            mFilter = new IntentFilter();
            mFilter.addAction(Intent.ACTION_BATTERY_CHANGED);            
            CmClientUtil.getServiceContext().registerReceiver(this, mFilter);
        }
    }
    
    // ----------------------------------------------------------------------------------------------------------------------------------
    public void unregister() {
        if (mFilter != null) {
            CmClientUtil.getServiceContext().unregisterReceiver(this);
            mFilter = null;
        }
    }

    @Override
    public boolean isValid(String value) {
        if (value == null)
            return false;

        boolean parsed = false;
        try {
            Integer.parseInt(value);
            parsed = true;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(), "RULE_POWER_LEVEL", exec);
        } 
        
        return parsed;
    }
}
