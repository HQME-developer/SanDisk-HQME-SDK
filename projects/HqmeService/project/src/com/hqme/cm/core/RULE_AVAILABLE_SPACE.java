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
import com.hqme.cm.VSDProperties;

public class RULE_AVAILABLE_SPACE extends RuleBase {
    // ==================================================================================================================================
    private static RULE_AVAILABLE_SPACE sRULE_AVAILABLE_SPACE_INSTANCE = new RULE_AVAILABLE_SPACE();
    
    @Override
    public void onReceive(Context arg0, Intent arg1) {
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_AVAILABLE_SPACE getInstance() {
        return sRULE_AVAILABLE_SPACE_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        
        // this tests that the rule is satisfied for the storage ID that is set as workOrder's StorageId value
        if (rule.getValue() != null) {            
            try {
                
                Long requiredAvailableSpace = Long.parseLong(rule.getValue());
                IVSD store = null;
                
                    try {
                        store = WorkOrderManager.getContentProxy() != null ? 
                                (WorkOrderManager.getContentProxy().VSDCount() > 0 ? 
                                    WorkOrderManager.getContentProxy().getStorage(workOrder.getStorageId()) : 
                                    null) : 
                                null;  
                        if (null != store) 
                            return satisfiesAvailableSpaceLimits(requiredAvailableSpace, store);
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), "evaluateRule", fault);
                    }
                
            } catch (NumberFormatException exec) {
                CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
            }       
        }

        // perhaps this VSD is no longer available
        return false;
    }

    private boolean satisfiesAvailableSpaceLimits(Long requiredAvailableSpace, IVSD store) {
        
        try {
            long availableCapacity = Long.parseLong(store.getProperty(VSDProperties.VSProperty.VS_AVAILABLE_CAPACITY.name()));
            if (availableCapacity > requiredAvailableSpace)
                return true;

        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "satisfiesAvailableSpaceLimits", fault);
        }

        return false;
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
            if (Long.parseLong(value) >= 0)
                parsed = true;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(), "RULE_FREE_SPACE", exec);
        } 

        return parsed;
    }
}
