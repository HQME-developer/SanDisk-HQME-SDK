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
import android.util.Xml;

import com.hqme.cm.core.policyParser.Expression;
import com.hqme.cm.core.policyParser.HqmePolicyException;
import com.hqme.cm.core.policyParser.PolicyElementParser;
import com.hqme.cm.util.CmClientUtil;

import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * The Policy class allows a "policy" to be specified.
 * Policies are combinations of individual Rule objects.
 * 
 * There is a Policy tag Download to specify the conditions for download
 * There is a Policy tag Cache to define whether or not Caching of the QueueRequest associated
 * with this Policy object should take place.
 *  
 * Rules may be combined to specify acceptable Download conditions using 
 * the "and"/"or" operators. 
 * 
 * Individual Rule tags themselves are evaluated by combining the list of Property (K,V) pairs within them,
 * evaluating each based on the condition of the device and application at the time the Rule is evaluated.
 * 
 */
public class Policy extends Record {
    // ==================================================================================================================================
    private String mDownload; // the string contained in the Download element
    private Expression mParsedDownloadExpression;
    private Expression mParsedCacheExpression;
    private String mName;  // the name attribute of the Policy Element
    private String mCache;  // the string contained in the Cache element
    private LinkedHashMap<String,RuleCollection> mRuleCollections; // all the Rule elements in the Policy, whether referenced by Download/Cache or not
    private WorkOrder mWorkOrder;
    // ----------------------------------------------------------------------------------------------------------------------------------
    public Policy(String contentXML) {
        super(contentXML, -1);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public Policy(String contentXML, long index) {
        super(contentXML, index);
    }

    // ==================================================================================================================================
    public Policy() {
        this.mRuleCollections = new LinkedHashMap<String,RuleCollection>(0);        
                
    }

    // ==================================================================================================================================
    // Serialization Methods
    // ==================================================================================================================================
    public static final String NAMESPACE = null;

    public static final String TAG_PROPERTY = "Property";

    public static final String TAG_DOWNLOAD = "Download";

    public static final String TAG_CACHE = "Cache";
    
    public static final String TAG_NAME = "name";

    public static final String TAG_RULE = "Rule";
    
    public static final String TAG_POLICY = Policy.class.getSimpleName();

    public void bodyToString(XmlSerializer serializer) {

        try {
            serializer.startTag(NAMESPACE, TAG_POLICY);
            if (mDownload != null) {
                serializer.startTag(NAMESPACE, TAG_DOWNLOAD);
                serializer.text(mDownload);
                serializer.endTag(NAMESPACE, TAG_DOWNLOAD);
            }
            if (mCache != null) {
                serializer.startTag(NAMESPACE, TAG_CACHE);
                serializer.text(mCache);
                serializer.endTag(NAMESPACE, TAG_CACHE);
            }
            
            if (mRuleCollections != null)
                for (Entry<String, RuleCollection> ruleSet : mRuleCollections.entrySet()) {
                    ruleSet.getValue().bodyToString(serializer);
                }

            serializer.endTag(NAMESPACE, TAG_POLICY);
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

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void fromString(String xmlContent) {
        if (xmlContent != null && xmlContent.length() > 0)
            try {
                PolicyParser parser = new PolicyParser();
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();
                XMLReader xr = sp.getXMLReader();
                xr.setContentHandler(parser);
                xr.parse(new InputSource(new StringReader(xmlContent)));
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "fromString", fault);
                throw new IllegalArgumentException(fault);
            }
    }

    boolean isValid() {

        if (mRuleCollections != null)
            for (Entry<String, RuleCollection> rc : mRuleCollections.entrySet()) {
                if (rc.getValue().getRules() != null)
                    for (Rule r : rc.getValue().getRules()) {
                        if (!r.isValid()) {
                            return false;
                        }
                    }
            }

        return true;

    }
    // ----------------------------------------------------------------------------------------------------------------------------------
    private class PolicyParser extends DefaultHandler {
        private StringBuilder mContentBuilder;
        private Rule mRuleBuilder;
        private ArrayList<Rule> mRulesList;       
        private String mRuleName;   
        boolean inRuleElement;
        boolean inPolicyElement;
        // --------------------------------------------------
        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                Attributes atts) throws SAXException {
            
            // don't support mixed content in any of our element types
            this.mContentBuilder.setLength(0);
            this.mContentBuilder.trimToSize();

            if (TAG_RULE.equals(localName)) {
                inRuleElement = true;                
                if (!inPolicyElement) 
                    throw new SAXException("Rule unexpected.");

                if (atts.getValue(TAG_NAME) != null && !"".equals(atts.getValue(TAG_NAME)))
                    this.mRuleName = atts.getValue(TAG_NAME);
                else 
                    throw new SAXException("\"name\" attribute expected for Rule element.");
                
               mRulesList = new ArrayList<Rule>(0);                
            } else if (TAG_POLICY.equals(localName)) {  
                inPolicyElement = true;
                // create a list of Rules element for this mLevel
                if (atts.getValue(TAG_NAME) != null)
                    mName = atts.getValue(TAG_NAME);              
            } else if (TAG_PROPERTY.equals(localName)) {
                if (!inRuleElement) 
                    throw new SAXException("Property unexpected.");
                
                this.mRuleBuilder = new Rule();
                this.mRuleBuilder.setValue(new String());

                //  key (required) 
                if (atts.getValue(Rule.TAG_KEY) != null && !"".equals(atts.getValue(Rule.TAG_KEY)))
                    this.mRuleBuilder.setName(atts.getValue(Rule.TAG_KEY));
                else
                    throw new SAXException("\"key\" attribute expected for Property element.");
            }
        }

        // --------------------------------------------------
        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            super.endElement(uri, localName, name);
            
            
            if (TAG_DOWNLOAD.equals(localName)) {
                if (!inPolicyElement) 
                    throw new SAXException("Download element unexpected.");
                            
                mDownload = mContentBuilder.toString().trim();                       
            } else if (TAG_CACHE.equals(localName)) {
                if (!inPolicyElement) 
                    throw new SAXException("Cache element unexpected.");                
                mCache = mContentBuilder.toString().trim(); 
            } else if (TAG_PROPERTY.equals(localName)) {
                if (!inRuleElement) 
                    throw new SAXException("Property unexpected.");                
                // add the newly created rule to a set of mRules for this
                // current
                // collection mLevel
                try {
                    if (this.mRuleBuilder == null) {
                        throw new SAXException("Mismatched Property tags.");                
                    } else {
                        if ("".equals(this.mRuleBuilder.getName()))
                            throw new SAXException("Property 'key' must be set");
                        
                        this.mRuleBuilder.setValue(mContentBuilder.toString().trim());
                        // instantiate the rule implementation and assign
                        // references
                        // to this rule's implementation and evaluate method
                        Class<?> thisRuleClass = Class.forName(Rule.class.getPackage().getName()
                                + "." + this.mRuleBuilder.getName());
                        Method getInstanceMethod = thisRuleClass.getMethod("getInstance",
                                new Class[0]);

                        // store for future reference
                        Object o = getInstanceMethod.invoke(thisRuleClass, new Object[0]);

                        // initialize the class representing this type of rule
                        Method isSInitMethod = thisRuleClass.getMethod("isSInitialized");
                        if (!(Boolean) isSInitMethod.invoke(o)) {
                            Method initMethod = thisRuleClass.getMethod("init", Context.class);
                            initMethod.invoke(o, CmClientUtil.getServiceContext());
                        }

                        // instance of the singleton rule object
                        this.mRuleBuilder.setRuleImplementationObject(o);

                        // for future reference
                        Method evaluateRuleMethod = thisRuleClass.getMethod("evaluateRule",
                                Rule.class, WorkOrder.class);
                        this.mRuleBuilder.setEvaluateRuleMethod(evaluateRuleMethod);
                        if (mRulesList != null)
                            mRulesList.add(this.mRuleBuilder);
                        
                        this.mRuleBuilder = null;
                    }
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "endElement = " + TAG_PROPERTY + " " + mContentBuilder.toString(), fault);
                    // currently unsupported rule is accepted here (perhaps a newer version would support it)
                }
            } else if (TAG_RULE.equals(localName)) { // Rule
                inRuleElement = false;
            	
                // here create a RuleCollection from the list mRulesList
                // add it to mRuleCollections - only add the first Rule with
                // this name attribute
                
                if (this.mRuleName == null || "".equals(this.mRuleName))                
                    throw new SAXException("\"name\" attribute expected for Rule element.");
                
                if (!mRuleCollections.containsKey(mRuleName)) {
                    // before adding to the mRuleCollections arraylist,
                    // resolve references to RULE_NETWORK_POLICY that may exist
                    // in the Policy
                    addNetworkPolicyRules(mRulesList);
                    mRuleCollections.put(mRuleName, new RuleCollection(mRuleName, mRulesList));
                }
                
                this.mRuleName = null;

            } else if (TAG_POLICY.equals(localName)) { // Policy
                inPolicyElement = false;
                
                if (mDownload == null | mCache == null) 
                    throw new SAXException("Cache and Download elements must be present.");
                try {                                                           
                    mParsedDownloadExpression = parsePolicyElement(mDownload);
                    mParsedCacheExpression = parsePolicyElement(mCache);
                } catch (SAXException e) {
                    CmClientUtil.debugLog(getClass(), "endElement=" + TAG_POLICY, e);
                    // rethrow the exception here
                    throw e;                    
                }
            }

            this.mContentBuilder.setLength(0);
            this.mContentBuilder.trimToSize();
        }

        private void addNetworkPolicyRules(ArrayList<Rule> rulesList) {
            // TODO set values of Property other than networkPolicy in a list overrides
            // the individual Property values associated with that network policy
           
            
        }

        Expression  parsePolicyElement(String elementString) throws HqmePolicyException {
            String policyXml = elementString;
            for (Entry<String, RuleCollection> rc : mRuleCollections.entrySet()) {
                // identify the XPath function name in the
                // string and
                // replace it with the attribute from the element
                String ruleName = rc.getValue().getName();
                String expression = "//Rule[@name=\"" + ruleName + "\"]";
                String qt = Pattern.quote(expression);

                if (policyXml.matches(".*" + qt + ".*")) {
                    policyXml = policyXml.replaceAll(qt, ruleName);
                    // create a variable associated with this ruleName
                    // that we can substitute with later                    
                }

                String expression2 = "/Policy/Rule[@name=\"" + ruleName + "\"]";
                qt = Pattern.quote(expression2);
                if (policyXml.matches(".*" + qt + ".*")) {
                    policyXml = policyXml.replaceAll(qt, ruleName);
                    // create a variable associated with this ruleName
                    // that we can substitute with later                    
                }
            }
            return PolicyElementParser.parse(policyXml, mRuleCollections);   
        }
        
        // --------------------------------------------------
        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            this.mContentBuilder = new StringBuilder();
            mRuleCollections = new LinkedHashMap<String,RuleCollection>(0);
            mRulesList = new ArrayList<Rule>(0);
            mRuleName = null;            
        }

        // --------------------------------------------------
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            this.mContentBuilder.append(ch, start, length);
        }

    }
    
    public synchronized boolean evaluateDownloadElement(WorkOrder workOrder) {
        if (mParsedDownloadExpression != null) {           
            return mParsedDownloadExpression.evaluate(workOrder);
        }
        
        return true;                
    }     
    
    public synchronized boolean evaluateCacheElement(WorkOrder workOrder) {
        if (mParsedCacheExpression != null) {          
            return mParsedCacheExpression.evaluate(workOrder);
        }
        
        return true;                
    }

    public int getRelativePriority() {
 
        // Only the priority Property in a Rule evaluated 
        //in the download expression (see 2.2) is considered 
        //for the purpose of prioritization. If multiple priority 
        //Properties are found in the download expression, only the 
        // first one in the XML structure is used.         

        for (Entry<String, RuleCollection> rc : mRuleCollections.entrySet()) {
            boolean usedInDownloadElement = false;
            String ruleName = rc.getValue().getName();
            String expression = "//Rule[@name=\"" + ruleName + "\"]";
            String qt = Pattern.quote(expression);

            if (mDownload.matches(".*" + qt + ".*"))
                usedInDownloadElement = true;

            String expression2 = "/Policy/Rule[@name=\"" + ruleName + "\"]";
            qt = Pattern.quote(expression2);
            if (mDownload.matches(".*" + qt + ".*"))
                usedInDownloadElement = true;

            if (usedInDownloadElement) {
                // does it have a priority rule and what is the first priority value in that rule?
                XPathFactory factory = XPathFactory.newInstance();
                XPath xpath = factory.newXPath(); 
                XPathExpression expression1 = null;
                String propertyExpressionString = "/Policy/Rule[@name=\""+ruleName + "\"][Property[@key=\"RULE_PRIORITY\"]]";
                try {
                    
                    
                    expression1 = xpath.compile(propertyExpressionString);
                    InputSource is2 = new InputSource(new StringReader(this.toString())); 
                    
                    NodeList ruleNodes = (NodeList) expression1.evaluate(is2,XPathConstants.NODESET);
                    if (ruleNodes.getLength() > 0) {
                        for (Rule rule : rc.getValue().getRules()) {
                            if ("RULE_PRIORITY".equals(rule.getName())) {
                                return Integer.parseInt(rule.getValue());
                            }
                        }
                    }
                } catch (XPathExpressionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return -1;
    }

    
}
