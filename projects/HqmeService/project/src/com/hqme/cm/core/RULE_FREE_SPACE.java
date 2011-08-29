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
import com.hqme.cm.IVSD;

public class RULE_FREE_SPACE extends RuleBase {
    // ==================================================================================================================================
    private static RULE_FREE_SPACE sRULE_FREE_SPACE_INSTANCE = new RULE_FREE_SPACE();
    private final static String TOTAL_CAPACITY = "VS_TOTAL_CAPACITY";
    private final static String AVAILABLE_CAPACITY = "VS_AVAILABLE_CAPACITY";
    
    @Override
    public void onReceive(Context arg0, Intent arg1) {
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_FREE_SPACE getInstance() {
        return sRULE_FREE_SPACE_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        
        // this tests that the rule is satisfied for the storage ID that is set as workOrder's StorageId value
        if (rule.getValue() != null) {            
            try {
                // A value between 1 and 100 indicates the available free
                // storage space of
                // the device (as a percentage) required in order to execute the
                // request.

                // Integer value between 0 and 100. The value 0 has special
                // meaning and
                // indicates that this rule is not set. Any value above 100 is
                // erroneous
                // and ignored

                Integer percentage = Integer.parseInt(rule.getValue());
                IVSD store = null;
                if ((percentage > 0) && (percentage <= 100)) {
                    try {
                        store = WorkOrderManager.getContentProxy() != null ? 
                                (WorkOrderManager.getContentProxy().VSDCount() > 0 ? 
                                    WorkOrderManager.getContentProxy().getStorage(workOrder.getStorageId()) : 
                                    null) : 
                                null;  
                        if (null != store) 
                            if (!satisfiesFreeSpaceLimits(percentage, workOrder.getStorageId()))
                                return false;                        
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), "evaluateRule", fault);
                    }
                } else {
                    return false;
                }
            } catch (NumberFormatException exec) {
                CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
            }       
        }

        return true;
    }

    public boolean satisfiesFreeSpaceLimits(int percentage,int storageId) {
        try {
            IVSD store = WorkOrderManager.getContentProxy().getStorage(storageId);

            long totalCapacity = Long.parseLong(store.getProperty(TOTAL_CAPACITY));
            long availableCapacity = Long.parseLong(store.getProperty(AVAILABLE_CAPACITY));
            int freePercentage = (int)(100 * (availableCapacity / (double) totalCapacity));
            if (freePercentage <= percentage)
                return false;

        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "satisfiesFreeSpaceLimits", fault);
        }

        // if not exceeded return true
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
            Integer.parseInt(value);
            parsed = true;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(), "RULE_FREE_SPACE", exec);
        } 

        return parsed;
    }
}
