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

import android.util.Xml;

import com.hqme.cm.util.CmClientUtil;

import org.xmlpull.v1.XmlSerializer;

import java.io.StringWriter;
import java.util.ArrayList;


/**
 * The RuleCollection class allows a "Rule Group" to be specified.
 * Rule Groups are combinations of individual Rule objects.
 * 
 * Rules are combined to form RuleCollection objects using 
 * the "and" operators. 
 *  
 *  The current XML specification of this would be:
 * <Rule name="uniqueID"> 
 *      <Property key="WifiRule">UMTS</Property>      
 *      <Property key="PowerRule">true</Property>
 *      <Property key="PowerLevel">50</Property>  
 * </RuleCollection>
 *      
 */
public class RuleCollection {
    // ==================================================================================================================================

    private ArrayList<Rule> mRules;
    private String mName;
    
    public String getName() {
        return mName;
    }

       // ==================================================================================================================================
    protected ArrayList<Rule> getRules() {
        return this.mRules;
    }

    protected void setRules(ArrayList<Rule> rules) {
        this.mRules = rules == null ? new ArrayList<Rule>(0) : rules;
    }

    // ==================================================================================================================================
    public RuleCollection(String name, ArrayList<Rule> ruleSet) {
        this.setRules(ruleSet);
        this.mName = name;
    }

    public RuleCollection() {        
        this.mRules = new ArrayList<Rule>(0);
        this.mName = new String();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public boolean evaluateRuleSet(WorkOrder workOrder) {
        for (Rule rule : this.mRules) {
            try {
                if (!rule.evaluateRule(workOrder)) {
                    CmClientUtil.debugLog(getClass(), "evaluateAndRuleSet @ this.rules",
                            "Stop rule = %s", rule);
                    return false;
                }
            } catch (Throwable fault) {
                CmClientUtil.debugLog(getClass(), "evaluateAndRuleSet @ this.rules", fault);
                return false;
            }
        }
        return true;
    }

    // ==================================================================================================================================
    // Serialization Methods
    // ==================================================================================================================================
    public static final String NAMESPACE = null;

    public static final String TAG_PROPERTY = "Property";

    public static final String TAG_RULE = "Rule";
    public static final String TAG_NAME = "name";

    public void bodyToString(XmlSerializer serializer) {

        try {
            serializer.startTag(NAMESPACE, TAG_RULE);
            if (mName != null)
                serializer.attribute(NAMESPACE, TAG_NAME , mName);
            if (mRules != null)
                for (Rule rule : mRules) {
                    rule.bodyToString(serializer);
                }
            
            serializer.endTag(NAMESPACE, TAG_RULE);
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "bodyToString", fault);
        }

    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public String toString() {
        try {
            StringWriter writer = new StringWriter();
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            bodyToString(serializer);
            serializer.endDocument();

            return writer.toString();
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "toString", fault);
        }
        return "";
    }

    boolean isValid() {

        if (mRules != null)
            for (Rule rule : mRules) {
                if (!rule.isValid()) {
                    return false;
                }
            }

        return true;

    }
    
   
}
