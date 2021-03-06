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
import com.hqme.cm.util.CmDate;

import java.util.GregorianCalendar;

public class RULE_EXPIRE extends RuleBase {
    // ==================================================================================================================================
    private static RULE_EXPIRE sRULE_EXPIRE = new RULE_EXPIRE();

    @Override
    public void onReceive(Context arg0, Intent arg1) {
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_EXPIRE getInstance() {
        return sRULE_EXPIRE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        try {                        
            GregorianCalendar cal = new GregorianCalendar();       
            GregorianCalendar timeLimit = CmDate.localizeDateTime(rule.getValue());
            if (cal.getTimeInMillis() > timeLimit.getTimeInMillis())
            {                
                return false;
            }
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "evaluateRule", fault);
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
        
        return CmDate.parsableDateTime(value);
    }
}
