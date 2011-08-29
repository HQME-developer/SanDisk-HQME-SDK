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
import com.hqme.cm.Property;

import org.xmlpull.v1.XmlSerializer;

import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * The Rule class is used to express a condition that will evaluate to a boolean
 * depending on some conditions and values.
 * 
 * Inputs are the rule "key" (required), a 
 * "value" (required). 
 * 
 * The combination of these properties is used to evaluate the 
 * rule at any given time. Reflection is used to invoke the relevant evaluateRule method,
 * that is, when a rule is created the fields mRuleImplementation
 * and mEvaluateRuleMethod get set depending on the value of Rule.mName
 *     
 *     For instance with the input
 *      <Property key="RULE_FREE_SPACE">50</Property>,
 *     mRuleImplementation is the singleton class RULE_FREE_SPACE
 *     and mEvaluateRuleMethod is RULE_FREE_SPACE.evaluateRule(Rule)
 *     
 *     
 * Additional system properties can be taken account of in system state evaluation
 * by adding additional classes as necessary.
 * 
 * In practice, Rules are always applied as part of an overall policy, which is 
 * implemented by specifying rules as part of a RuleCollection
 *      
 */
public class Rule {

    public static final String NAMESPACE = null;

    public static final String TAG_RULE = "Property";

    public static final String TAG_KEY = "key";

    public Rule() {
    }

    // ==================================================================================================================================
    private String mName;

    private Object mRuleImplementation;

    private Method mEvaluateRuleMethod;

    public String getName() {
        return mName;
    }

    public void setName(String ruleName) {
        mName = ruleName;
    }

    // ==================================================================================================================================
    private String mValue;

    public String getValue() {
        return this.mValue;
    }

    public void setValue(String value) {
        this.mValue = value == null ? new String() : value;
    }

    // ==================================================================================================================================
    // Evaluation of mRules
    // ==================================================================================================================================
    public boolean evaluateRule(WorkOrder workOrder) {
        try {
            Object o = mEvaluateRuleMethod.invoke(mRuleImplementation, this, workOrder);
            return (Boolean) o;
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "evaluateRule", fault);
        }
        return false;
    }

    // ==================================================================================================================================
    // Serialization Methods
    // ==================================================================================================================================

    public String toString() {
        XmlSerializer serializer = Xml.newSerializer();
        StringWriter writer = new StringWriter();
        try {
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            this.bodyToString(serializer);
            serializer.endDocument();
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void bodyToString(XmlSerializer serializer) {
        try {
            serializer.startTag(NAMESPACE, TAG_RULE);
            serializer.attribute(NAMESPACE, TAG_KEY, getName());

            if (mValue != null) 
                        serializer.text(mValue);
            serializer.endTag(NAMESPACE, TAG_RULE);
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "bodyToString", fault);
        }
    }

    public void setRuleImplementationObject(Object ruleObject) {
        this.mRuleImplementation = ruleObject;
    }

    public Object getRuleImplementationObject() {
        return mRuleImplementation;
    }

    public void setEvaluateRuleMethod(Method evaluateRuleMethod) {
        this.mEvaluateRuleMethod = evaluateRuleMethod;
    }

    public Method getEvaluateRuleMethod() {
        return mEvaluateRuleMethod;
    }

    public boolean isValid() {
        
        Property p = new Property(this.getName(),this.getValue());
        
        try {
            Class<?> thisRuleClass = Class.forName(Rule.class.getPackage().getName() + "." + p.key);

            Method getInstanceMethod = thisRuleClass.getMethod("getInstance", new Class[0]);

            // store for future reference
            Object o = getInstanceMethod.invoke(thisRuleClass, new Object[0]);

            // initialize the class representing this type of rule
            Method isValidMethod = thisRuleClass.getMethod("isValid", String.class);

            return (Boolean) isValidMethod.invoke(o, p.value);                
        } catch (InvocationTargetException exec) {
            CmClientUtil
                    .debugLog(getClass(), "isValid function does not exist for this rule", exec);
            
        } catch (ClassNotFoundException exec) {
            CmClientUtil.debugLog(getClass(), "testing rule key and value are valid", exec);
            
        } catch (Exception exec) {
            CmClientUtil.debugLog(getClass(), "isValid()", exec);
        }

        return false;
    }

}
