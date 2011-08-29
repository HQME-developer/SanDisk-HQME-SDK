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

import com.hqme.cm.util.CmClientUtil;

public class RULE_MAX_SIZE extends RuleBase {
    // ==================================================================================================================================
    private static RULE_MAX_SIZE sRULE_MAX_SIZE_INSTANCE = new RULE_MAX_SIZE();

    @Override
    public void onReceive(Context arg0, Intent arg1) {
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_MAX_SIZE getInstance() {
        return sRULE_MAX_SIZE_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        if (rule.getValue() != null) {            
            try {
                Long totalBytes = Long.parseLong(rule.getValue());
                Long specifiedBytes = Long.parseLong(workOrder.getPackages().get(0).properties
                        .get(QueueRequestProperties.RequiredProperties.REQPROP_TOTAL_LENGTH));
                return specifiedBytes <= totalBytes;
            } catch (NumberFormatException exec) {
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
            Long.parseLong(value);
            parsed = true;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(), "RULE_MAX_SIZE", exec);
        } 
        return parsed;
    }
}
