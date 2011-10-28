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

import com.hqme.cm.util.CmClientUtil;

public class RULE_BANDWIDTH_LIMIT extends RuleBase {
    // ==================================================================================================================================
    private static RULE_BANDWIDTH_LIMIT sRULE_BANDWIDTH_LIMIT_INSTANCE = new RULE_BANDWIDTH_LIMIT();    
    private IntentFilter mFilter = null;
    
    @Override
    public void onReceive(Context arg0, Intent arg1) {
        if ("com.hqme.cm.core.BANDWIDTH".equals(arg1.getAction())) {
            // gets the current value
            super.onReceive(arg0, arg1);
       }   
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_BANDWIDTH_LIMIT getInstance() {
        return sRULE_BANDWIDTH_LIMIT_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder wo) {
        
        if (RULE_CONNECTION_TYPE.isMobileSession()) {
            // only care if the download would be over the mobile network
            try {
                // the current download rate is only relevant to the executing download
                 if (wo.getOrderAction() == WorkOrder.Action.EXECUTING)
                    if (WorkOrderManager.getDownloadRate() > (Integer.parseInt(rule.getValue()) << 10))
                        return false;
                // measure bandwidth and compare with the limit the user has set
            } catch (Exception exec) {
                CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
            }
        }
        return true;
    }
    @Override
    public void init(Context context) {
        super.init(context);
    }

    @Override
    public boolean isValid(String value) {
        
        if (value == null)
            return false;
        
        boolean parsed = false;
        try {
            if (Integer.parseInt(value) >= 0) 
                parsed = true;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(),
                    "A rule specified does not exist", exec);
        } 
        
        return parsed;
    }
    
    // this is called at the start of a download involving a RULE_BANDWIDTH 
    public void register() {
        if (mFilter == null) {
            mFilter = new IntentFilter();
            mFilter.addAction("com.hqme.cm.core.BANDWIDTH");            
            CmClientUtil.getServiceContext().registerReceiver(this, mFilter);
        }
    }
    
    // ----------------------------------------------------------------------------------------------------------------------------------
    // this is called at the end of a download/partial download involving a RULE_BANDWIDTH
    public void unregister() {
        if (mFilter != null) {
            CmClientUtil.getServiceContext().unregisterReceiver(this);
            mFilter = null;
        }
    }
}
