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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hqme.cm.util.CmClientUtil;


import java.lang.ref.WeakReference;

/**
 * This class is a base class whose subclasses are used to evaluate all rules/policies based on 
 * underlying system properties. Each subclass is associated with a specific system property 
 * (or group of related properties).  
 * 
 * Its two main roles are:
 * 1) to provide a BroadcastReceiver function which incites the WorkOrderManager to re-evaluate the
 *  execution priority of pendingWorkOrders when some aspect of the system status changes
 * 2) to allow the creation of subclasses which are responsible for reacting to system state, and 
 * where relevant, storing the most recent state of the observed property for access by subsequent 
 * calls to evaluateRule   
 */
abstract class RuleBase extends BroadcastReceiver {    
    private WeakReference<WorkOrderManager> mWorkOrderManager = new WeakReference<WorkOrderManager>(
            WorkOrderManager.getInstance());
    private boolean sInitialized;
    // ==================================================================================================================================
    /**
     * Subclasses of RuleBase call this function to incite the WorkOrderManager to re-evaluate the pendingWorkOrders queue.
     * The onReceive function of each subclass gets called in response to some Intent triggered when a system property has changed.
     * Where relevant, the subclasses' onReceive also saves the value of relevant system properties for future use.
     * 
     */    
    @Override
    public void onReceive(Context arg0, Intent arg1) {
        final WorkOrderManager workOrderManager = mWorkOrderManager.get();

        if (workOrderManager != null) {
            if (WorkOrderManager.isActive()) {
                try {
                    workOrderManager.mInciteHysteresisTask.resume(false);
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "onReceive", fault);
                }
            } 
        } else {
            WorkOrderManager.startWorkOrderManager(arg0);    
        }
    }

    /**
     * Subclasses of RuleBase get initialized before use.
     * 
     * In the current implementation, init gets called when a Rule object 
     * containing a reference to one of those subclasses is parsed 
     * 
     * See RuleCollectionParser.endElement, initMethod.invoke
     * 
     */    
    public void init(Context context) {
        sInitialized = true;
    }

    /**
     *  
     * @return          <code>true</code> if the String provided is valid according to the definition of 
     * this rule given in the documentation, otherwise<code>false</code>
     * 
     * For instance, <RULE_FREE_SPACE/> should be an integer value between 0 and 100. 
     */
    public abstract boolean isValid(String value);

    /**
     * 
     * @param rule      The specific instance of a Rule that will be evaluated using a subclass of RuleBase
     * 
     * @return          <code>true</code> if the evaluation function based on the given Rule.mValues and Rule.mCondition 
     * returns true, otherwise <code>false</code>
     */
    public abstract boolean evaluateRule(Rule rule,WorkOrder workOrder);

    public boolean isSInitialized() {
        return sInitialized;
    }

}
