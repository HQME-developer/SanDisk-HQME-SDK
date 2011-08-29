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

// The value is the limit, in bytes, that may be downloaded over a mobile network throughout the 
// duration of the QueueRequest.
public class RULE_DOWNLOAD_LIMIT extends RuleBase {
    // ==================================================================================================================================
    private static RULE_DOWNLOAD_LIMIT sRULE_DOWNLOAD_LIMIT_INSTANCE = new RULE_DOWNLOAD_LIMIT();

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        // check the connectivity status - if connected to mobile, we care        
            super.onReceive(arg0, arg1);        
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_DOWNLOAD_LIMIT getInstance() {
        return sRULE_DOWNLOAD_LIMIT_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================

    @Override
    public boolean evaluateRule(Rule rule, WorkOrder wo) {
        
        if (RULE_CONNECTION_TYPE.isMobileSession()) {
            // only care if the download would be over the mobile network
            try {
                if (rule.getValue() != null) {                    
                    Integer totalBytes = Integer.parseInt(rule.getValue());
                    return withinLimit(totalBytes, wo);
                }
            } catch (Exception exec) {
                CmClientUtil.debugLog(getClass(), "evaluateRule", exec);
            }
        }

        return true;
    }

    public boolean withinLimit(int bytes, WorkOrder wo) {
        try {            
            long mobileBytes = 0L;
            long bytesRemaining = 0L;
            for (Package p : wo.getPackages()) {
                mobileBytes += p.getMobileDownloadBytes();
                // bytesRemaining only taken into account when the content size of a package that is part 
                // of the download is known. Otherwise, just check the current values                
                if (p.getContentSize() > 0)
                    bytesRemaining += p.getContentSize()
                        - p.getProgressBytes();
            }

            if ((long) (bytes) < (mobileBytes + bytesRemaining))
                return false;

        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "Mobile Download Limit", fault);
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
            CmClientUtil.debugLog(getClass(), "A rule specified does not exist", exec);
        } 
        
        return parsed;
    }
}
