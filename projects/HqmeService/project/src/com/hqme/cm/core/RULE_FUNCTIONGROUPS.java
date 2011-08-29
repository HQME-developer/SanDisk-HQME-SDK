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

import java.util.ArrayList;

public class RULE_FUNCTIONGROUPS extends RuleBase {
    // ==================================================================================================================================
    private static RULE_FUNCTIONGROUPS sRULE_FUNCTIONGROUPS_INSTANCE = new RULE_FUNCTIONGROUPS();

    @Override
    public void onReceive(Context arg0, Intent arg1) {
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_FUNCTIONGROUPS getInstance() {
        return sRULE_FUNCTIONGROUPS_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {
        try {
            // parse the input string
            String[] functiongroups = rule.getValue().split("\\s+");

            if (functiongroups != null) {
                // strings have been defined

                ArrayList<Long> functiongps = new ArrayList<Long>(functiongroups.length);
                
                for (String value : functiongroups) {
                    functiongps.add(Long.decode(value));
                }

                IVSD store = null;
                store = WorkOrderManager.getContentProxy() != null ? 
                        (WorkOrderManager.getContentProxy().VSDCount() > 0 ? 
                            WorkOrderManager.getContentProxy().getStorage(workOrder.getStorageId()) : 
                            null) : 
                        null;  
                if (null != store) {
                    long[] fgValues = store.functionGroups();
                    ArrayList<Long> definedFgs = new ArrayList<Long>();  
                    // add the array to an ArrayList
                    for (Long val: fgValues) definedFgs.add(val);
                        
                    if (definedFgs.containsAll(functiongps))
                        return true;
                    
                }

                // the currently specified store either was not available or was and 
                // didn't satisfy functiongroup requirements
                return false;
            }
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "evaluateRule", fault);
        }

        // return true if no defined strings
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
        
        String[] functiongroups = value.split("\\s+");
        
        for (String fn : functiongroups) {
            boolean parsed = false;
            try {
                // should be able to parse each as long
                Long.decode(fn);
                parsed = true;
                
            } catch (NumberFormatException exec) {
                CmClientUtil.debugLog(getClass(), "isValid", exec);
            } finally {
                if (!parsed)
                    return false;
            }
        }

        return true;
    }
}
