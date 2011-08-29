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

package com.hqme.cm.core.policyParser;

import com.hqme.cm.core.RuleCollection;
import com.hqme.cm.core.WorkOrder;

import java.util.HashMap;

public class PolicyExpression extends Expression {	
    // each expression that would make up the <Download> or <Cache>
    // corresponds to:    
    // a) a Rule element to be evaluated and the RuleCollection that corresponds to it
    // (for which we may call evaluateRuleSet)    
    // b) true() or false() which are xPath reps of true/false
	private String name;
	private boolean value;
	protected RuleCollection mRules;
	public String getName() {
        return name;
    }

  
	public PolicyExpression(String name,HashMap<String, RuleCollection> ruleMap) {	 
	    this.name = name;
	    if (ruleMap.get(name) != null)
	        this.mRules = ruleMap.get(name);
	    
	    if ("true()".compareTo(name) == 0)
	        value = true;
	}

	public String toString() {
		return name;
	}

	public boolean evaluate(WorkOrder workOrder) {
	    if (this.mRules != null) {
	        if (workOrder!= null)
	            return mRules.evaluateRuleSet(workOrder);
	    } 	    
	    
	    return value;
	}
	
}