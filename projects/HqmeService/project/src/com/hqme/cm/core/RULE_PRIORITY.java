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


public class RULE_PRIORITY extends RuleBase {
    // ==================================================================================================================================
    private static RULE_PRIORITY sRULE_PRIORITY_INSTANCE = new RULE_PRIORITY();

    @Override
    public void onReceive(Context arg0, Intent arg1) {      
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_PRIORITY getInstance() {
        return sRULE_PRIORITY_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
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
            int val = Integer.parseInt(value);
            parsed = true;
            if (val < 0)  // we ignore values > 100
                return false;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(), "RULE_PRIORITY", exec);
        } 
        
        return parsed;
    }
}
