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

import com.hqme.cm.HqmeError;
import com.hqme.cm.IQueueRequest;
import com.hqme.cm.Property;
import com.hqme.cm.QueueRequestState;
import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmProperties;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class QueueRequestObject extends IQueueRequest.Stub {
    // ==================================================================================================================================
    CmProperties mProperties;
    String mName;

    private final static String tag_Log = "QueueRequestObject";
    
    // ==================================================================================================================================
    // Constructors that are required by HQME
    public QueueRequestObject(String content) {
        mProperties = new CmProperties();
        fromString(content);
    }
    
    protected QueueRequestObject() {
        mProperties = new CmProperties();
    }

    public QueueRequestObject(CmProperties woProperties) {
        mProperties = woProperties;
    }

    public QueueRequestObject(QueueRequestObject qro) {
        // here create a new QueueRequest with all but the transient properties
        this();     
       
        this.mProperties.putAll(qro.mProperties);
        for (QueueRequestProperties.TransientProperties property : QueueRequestProperties.TransientProperties
                .values()) {
            removeProperty(property.name());
        }
    }

    // ==================================================================================================================================
    // interface methods that we must support
    public long getRequestId() {
        final String tag_LogLocal = "getRequestId";
        
        try {
            String objectId = mProperties.get(QueueRequestProperties.TransientProperties.REQPROP_REQUEST_ID
                    .name());            
            long id = Long.valueOf(objectId);
            if (WorkOrderManager.getInstance().findWorkOrder(id) != null)
                return id;
        } catch (NumberFormatException exec) {
            CmClientUtil.debugLog(getClass(), tag_LogLocal, exec);
        }
        
        return HqmeError.ERR_NOT_FOUND.getCode();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------  
    public QueueRequestState getState() {
        QueueRequestState returnValue = QueueRequestState.UNDEFINED;
        
        if (getRequestId() > 0) {
            WorkOrder savedWO = WorkOrderManager.getInstance().findWorkOrder(getRequestId());
            if (savedWO!= null)
                returnValue = savedWO.getQueueRequestState();
        }

        return returnValue;
    }
  
    // ----------------------------------------------------------------------------------------------------------------------------------
    public int getProgress() {
        
        if (getRequestId() > 0) {
            WorkOrder savedWO = WorkOrderManager.getInstance().findWorkOrder(getRequestId());
            if (savedWO!= null)
                return savedWO.getProgressPercent();                
        }
        
        return HqmeError.ERR_NOT_FOUND.getCode();               
    }

    // ==================================================================================================================================
    public Property[] properties() {
        synchronized (mProperties) {
            Property[] propertyArray = new Property[mProperties.size()];
            Set<String> keys = mProperties.keySet();
            int i = 0;
            for (String key: keys) {
                propertyArray[i++] = new Property(key, mProperties.get(key));
            }
            return propertyArray;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public String getProperty(String key) {
        // the value *we* have, *not* the Database value
        return mProperties.get(key);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public int setProperty(String key, String value) {        
        mProperties.set(key, value);
        // allow user to set any property, including transient here            
        // NOTE: when the work order is inserted using submitRequest, any transient properties 
        // set in this way will *not* be added to the database
        return HqmeError.STATUS_SUCCESS.getCode();   
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public int removeProperty(String key) {
        if (mProperties.remove(key) != null)
            return HqmeError.STATUS_SUCCESS.getCode();
        else
            return HqmeError.ERR_NOT_FOUND.getCode();
    }

    // ==================================================================================================================================
    public String name() {
        return TAG_QUEUE_REQUEST;
    }

    // QueueRequests that are not Valid will not get inserted to the WorkOrder queue
    public int isValid() {
        synchronized (mProperties) {
        	
            Set<String> keys = mProperties.keySet();
            if (keys.contains(null))
                    return HqmeError.ERR_INVALID_PROPERTY.getCode();
            
            for (QueueRequestProperties.RequiredProperties reqProp : QueueRequestProperties.RequiredProperties
                    .values()) {
                if (!keys.contains(reqProp.name()))
                	return HqmeError.ERR_INVALID_ARGUMENT.getCode();
                String reqString = mProperties.get(reqProp.name());
                if ("".equals(reqString)) // one of the required properties
                    // does not have a value
                    return HqmeError.ERR_INVALID_PROPERTY.getCode();
            }

            // expect the client to pass a valid URI here
            int retval = HqmeError.ERR_INVALID_PROPERTY.getCode();
            try {
                // here we implicitly use RFC2396 as constituting an acceptable
                // string for REQPROP_SOURCE_URI
                java.net.URI uri = new java.net.URI(mProperties
                        .get(QueueRequestProperties.RequiredProperties.REQPROP_SOURCE_URI.name()));
                retval = HqmeError.STATUS_SUCCESS.getCode();
            } catch (URISyntaxException fault) {
                CmClientUtil.debugLog(getClass(), "isValid", fault);
            }

            if (retval != HqmeError.STATUS_SUCCESS.getCode())
                return retval;

         // also check that the REQPROP_PERMISSIONS_GROUP/WORLD/USER are acceptable
            if (checkValidPermissions() != HqmeError.STATUS_SUCCESS.getCode())
                return HqmeError.ERR_INVALID_PROPERTY.getCode();

            if (!isPolicyValid()) {
                return HqmeError.ERR_INVALID_POLICY.getCode();
            }

            return HqmeError.STATUS_SUCCESS.getCode();
        }
    }

    private int checkValidPermissions() {
        int retval = HqmeError.STATUS_SUCCESS.getCode();
                
        final String[] permissionsFields = new String[]{WorkOrder.TAG_USER,WorkOrder.TAG_GROUP, WorkOrder.TAG_WORLD};       
        
        // user, group, world
        for (String tag : permissionsFields) {
            if (mProperties.containsKey(tag)) {
                try {
                    // valid values are, e.g., REQPROP_USER, PERMISSION_READ, or PERMISSION_MODIFY or PERMISSION_DELETE
                    if (WorkOrder.Permission.get(mProperties.get(tag)) == null)
                        return HqmeError.ERR_INVALID_PROPERTY.getCode();

                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "checkValidPermissions", fault);
                }
            }
        }
        
        return retval;
    }

    void setRequestId(long l) {
        mProperties.set(QueueRequestProperties.TransientProperties.REQPROP_REQUEST_ID.name(), l);
    }

    // ==================================================================================================================================
    // Serialization Methods
    // ==================================================================================================================================
    public static final String NAMESPACE = null;

    public static final String TAG_QUEUE_REQUEST = "QueueRequest";
    public static final String TAG_PROPERTY = "Property";

    public static final String TAG_POLICY = QueueRequestProperties.OptionalProperties.REQPROP_POLICY.name();

    // ----------------------------------------------------------------------------------------------------------------------------------
    public String toString() {
        return toString(false);
    }

    public String toString(boolean omitXMLDeclaration) {
    	synchronized (mProperties) {
            StringWriter writer = new StringWriter();
            try {
                XmlSerializer serializer = Xml.newSerializer();
                serializer.setOutput(writer);
                if (!omitXMLDeclaration)
                    serializer.startDocument("UTF-8", true);

                if (mName == null)
                    serializer.startTag(NAMESPACE, TAG_QUEUE_REQUEST);
                else
                    serializer.startTag(NAMESPACE, TAG_QUEUE_REQUEST).attribute(null, "name", mName);
                
                // the package properties
                for (String key : mProperties.keySet()) {
                    serializer.startTag(NAMESPACE, TAG_PROPERTY).attribute(null,"key",key).text(mProperties.get(key).toString())
                            .endTag(NAMESPACE, TAG_PROPERTY);
                }

                serializer.endTag(NAMESPACE, TAG_QUEUE_REQUEST);
                serializer.endDocument();
            } catch (Throwable fault) {
                CmClientUtil.debugLog(getClass(), "toString", fault);
            }

            return writer.toString();
        }
    } 

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void fromString(String xmlContent) {
        if (xmlContent != null && xmlContent.length() > 0)
            try {
                QueueRequestObjectParser parser = new QueueRequestObjectParser();
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();
                XMLReader xr = sp.getXMLReader();
                xr.setFeature("http://xml.org/sax/features/namespaces", false);
                xr.setContentHandler(parser);
                xr.parse(new InputSource(new StringReader(xmlContent)));
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "fromString", fault);
                throw new IllegalArgumentException(fault);
            }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    private class QueueRequestObjectParser extends DefaultHandler {
        private StringBuilder mContentBuilder;

        private StringBuilder mElementBuilder;

        private CmProperties mPropertiesBuilder;
        
        private String mPropertyKey;

        private int mLevel = -1;
        private boolean inQueueRequest = false;
        private boolean inQueueRequestProperty = false;
        
        // TODO: maybe use XmlSerializer for writing complex elements?

        // --------------------------------------------------
        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                Attributes atts) throws SAXException {

            mLevel++;
            if (mLevel > 1) { // e.g. the Policy Property and so on...
                // preserve the attributes since we're ignoring these elements
                if (!inQueueRequestProperty)
                    throw new SAXException("This element should be a child of a Property element.");

                // supporting mixed content data (which is possible for these elements)
                // don't trim whitespace here (the format of the content may be user-defined,
                // so keep is as is)
                mElementBuilder.append(mContentBuilder.toString());
                mContentBuilder.setLength(0);
                mContentBuilder.trimToSize();                               
                
                mElementBuilder.append("<" + qName);
                for (int i = 0; i < atts.getLength(); i++) {
                    mElementBuilder.append(" " + atts.getLocalName(i) + "=\"" + atts.getValue(i)
                            + "\"");
                }
                mElementBuilder.append(">");
            } else if (mLevel == 1) { // these are the Property elements

                if (!(TAG_PROPERTY.equals(qName) && inQueueRequest)) 
                    throw new SAXException("Expected PropertyElement here");
                inQueueRequestProperty = true;
                
                if (!"".equals(mContentBuilder.toString().trim()))
                	throw new SAXException("Unexpected content.");
                
                // dump whitespace that was present in the body of the QueueRequest element
                mContentBuilder.setLength(0);
                mContentBuilder.trimToSize();               
                
                if (atts == null || atts.getIndex("", "key") == -1) {
                    throw new SAXException("\"key\" attribute expected for Property.");
                } else {
                    mPropertyKey = atts.getValue(atts.getIndex("", "key"));
                    if ("".equals(mPropertyKey))
                        throw new SAXException("\"key\" attribute should not be an empty string.");
                }

                mElementBuilder = new StringBuilder();
            } else if (TAG_QUEUE_REQUEST.equals(qName)) { // set the name of the QueueRequest
                inQueueRequest = true;
                if (atts == null || atts.getIndex("", "name") == -1) {
                    mName = "NewQueueRequest";  // Make it optional for now. ()
                    //throw new SAXException("\"name\" attribute expected for QueueRequest.");
                } else {
                    mName = atts.getValue(atts.getIndex("", "name"));
                }
            }
        }

        // --------------------------------------------------
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);

            if (TAG_QUEUE_REQUEST.equals(qName)) {
                inQueueRequest = false;
                mProperties = mPropertiesBuilder;
            } else {
                mLevel--;
                if (mLevel == 0) {
                    if (!(TAG_PROPERTY.equals(qName)))
                        throw new SAXException("Unexpected end tag.");
                    inQueueRequestProperty = false;
                    // all other package level data tags
                    try {
                        if (mPropertyKey == null || "".equals(mPropertyKey))
                            throw new SAXException("Property \"key\" attribute unset.");
                        
                        mPropertiesBuilder.set(mPropertyKey,
                                mElementBuilder.toString() +  mContentBuilder.toString());
                        
                        // supporting mixed content data
                        mElementBuilder.setLength(0);
                        mElementBuilder.trimToSize();
                        mContentBuilder.setLength(0);
                        mContentBuilder.trimToSize();
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), "endElement", fault);
                    }
                } else if (mLevel > 0) {
                	// these are end tags of the elements that are inside the property elements
                    if (!inQueueRequestProperty)
                        throw new SAXException("Unexpected end tag.");
                                        
                    // do not trim the content builder here, this may have meaning for embedded
                    // child tags of REQPROP_METADATA etc.
                    mElementBuilder.append(mContentBuilder.toString() + "</" + qName + ">");                    
                    
                    mContentBuilder.setLength(0);
                    mContentBuilder.trimToSize();
                }
            }
        }

        // --------------------------------------------------
        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            mContentBuilder = new StringBuilder();
            mElementBuilder = new StringBuilder();
            mPropertiesBuilder = new CmProperties();
        }

        // --------------------------------------------------
        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
            
            if (inQueueRequest)
                throw new SAXException("QueueRequest end tag expected.");
            
            if (inQueueRequestProperty)
                throw new SAXException("Property end tag expected.");
        }
        // --------------------------------------------------
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            mContentBuilder.append(ch, start, length);
        }
    }   

    boolean isPolicyValid() {            
    	String policy = getProperty(QueueRequestObject.TAG_POLICY);
        if (!"".equals(policy)) {
            try {
                Policy qrPolicy = new Policy(policy);
                return qrPolicy.isValid();
            } catch (Exception exec) {
                return false;
            }
        }
        return true;
    }
}
