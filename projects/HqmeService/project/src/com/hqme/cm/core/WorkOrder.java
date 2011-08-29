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

import android.app.Application;
import android.os.RemoteException;
import android.util.Xml;
import com.hqme.cm.IContentObject;
import com.hqme.cm.IVSD;
import com.hqme.cm.QueueRequestState;
import com.hqme.cm.VSDProperties;
import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmDate;
import com.hqme.cm.util.CmNumber;
import com.hqme.cm.util.CmProperties;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlSerializer;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.PriorityBlockingQueue;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * 
 * Class used for internal representation of a QueueRequest.
 * 
 * A WorkOrder consists of a list of Properties, and may have a Policy limiting conditions when
 * the download may take place. The item which is downloaded by the WorkOrder is represented 
 * internally by a Package.
 * 
 */
public class WorkOrder extends Record implements Comparable<WorkOrder> {
    /***********************************************************************************************************************************
     * @note work-flow order (wake-up, go-to-work, work, done-working, go-home)
     * @see calculateWorkOrderExecutionPriority
     */
    protected static enum State {
        PENDING, LOGIN, EXECUTE, QUIT, LOGOUT;
    }

    /***********************************************************************************************************************************
     * @note execution priority order (NOT code-execution order)
     * @see calculateWorkOrderExecutionPriority
     */
    protected static enum Action {
        INTERNAL, EXECUTED, EXECUTING, RESUMING, REENABLING, PENDING, NEW, WAITING, CANCELING, SUSPENDING, SUSPENDED, COMPLETED, DISABLING, DISABLED;
    }

    /***********************************************************************************************************************************
     * @note used to map the state of single item WorkOrder (multiple package 
     *       object) to QueueRequest objects
     * @see
     */

    static HashMap<WorkOrder.Action, QueueRequestState> workOrderToQueueRequestStates = new HashMap<WorkOrder.Action, QueueRequestState>() {
        /**
         * 
         */
        private static final long serialVersionUID = 2205372340472085268L;

        {
            put(WorkOrder.Action.NEW, QueueRequestState.QUEUED);
            put(WorkOrder.Action.WAITING, QueueRequestState.WAITING);
            put(WorkOrder.Action.EXECUTING, QueueRequestState.ACTIVE);
            put(WorkOrder.Action.SUSPENDED, QueueRequestState.BLOCKED);
            put(WorkOrder.Action.RESUMING, QueueRequestState.BLOCKED);
            put(WorkOrder.Action.REENABLING, QueueRequestState.WAITING);
            put(WorkOrder.Action.SUSPENDING, QueueRequestState.BLOCKED);
            put(WorkOrder.Action.COMPLETED, QueueRequestState.COMPLETED);
            put(WorkOrder.Action.DISABLED, QueueRequestState.SUSPENDED);
            put(WorkOrder.Action.CANCELING, QueueRequestState.SUSPENDED);
            put(WorkOrder.Action.DISABLING, QueueRequestState.SUSPENDED);

        }
    };

    QueueRequestState getQueueRequestState() {
        return Action.PENDING.equals(getOrderAction()) ? workOrderToQueueRequestStates
                .get(getStatusOnPending()) : workOrderToQueueRequestStates.get(getOrderAction());
    }

    // ==================================================================================================================================
    private static final String sTag_Log = WorkOrder.class.getName();

    public CmProperties mProperties; // initialized in onCreate()

    // Policy related    
    private Policy mPolicy;

    @Override
    protected void onCreate() {
        // properties are the transient fields and others at the work order
        // level
        mProperties = new CmProperties();
        super.onCreate();
    }

    // ==================================================================================================================================
    private WorkOrder() {
        super(null, -1);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected WorkOrder(State newState) {
        super(null, -1);
        setExecutionState(newState);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public WorkOrder(String contentXML) {
        super(contentXML, -1);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public WorkOrder(String contentXML, long index) {
        super(contentXML, index);
    }


    public String name() {
        return TAG_WORK_ORDER;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public WorkOrder(QueueRequestObject queueRequest) {
        super(null, -1);
        mPackageList = new ArrayList<Package>(0);
        // copies all HQME properties to the Package
        mPackageList.add(new Package(queueRequest.mProperties));
        // for the WorkOrder level properties we find in a QueueRequest object,
        // set these from mProperties
        setUrgent(queueRequest.getProperty(QueueRequestProperties.OptionalProperties.REQPROP_IMMEDIATE
                .name()));
        setExpiration(queueRequest
                .getProperty(QueueRequestProperties.OptionalProperties.REQPROP_EXPIRATION_DATE.name()));        
        setNotificationTarget(queueRequest
                .getProperty(QueueRequestProperties.OptionalProperties.REQPROP_BROADCAST_INTENT.name()));
        setClientUid(queueRequest
                .getProperty(QueueRequestProperties.TransientProperties.REQPROP_CALLING_UID.name()));
        // permissions functions
        setUserPermissions(queueRequest
                .getProperty(QueueRequestProperties.OptionalProperties.REQPROP_PERMISSIONS_USER.name()));
        setGroupPermissions(queueRequest
                .getProperty(QueueRequestProperties.OptionalProperties.REQPROP_PERMISSIONS_GROUP.name()));
        setWorldPermissions(queueRequest
                .getProperty(QueueRequestProperties.OptionalProperties.REQPROP_PERMISSIONS_WORLD.name()));
        setGroupProp(queueRequest
                .getProperty(QueueRequestProperties.OptionalProperties.REQPROP_GROUP.name()));
        
        if (queueRequest.getProperty(QueueRequestObject.TAG_POLICY) != "") {
            try {
                mPolicy = new Policy(queueRequest.mProperties
                        .get(QueueRequestObject.TAG_POLICY));
                int relativePriority = mPolicy.getRelativePriority();
                
                if (relativePriority != -1) {
                    setRelativePriority(relativePriority);
                }
                
                // if RULE_MANDATORY_TIME has been set, use XPath to identify all instances 
                // set an Alarm to be triggered at each of the start times
                // set Mandatory is what gives this request a highest order priority - 
                // not done if all periods have elapsed
                setMandatory(issueMandatoryTimeAlerts(queueRequest.getProperty(QueueRequestObject.TAG_POLICY)));               
            } 
            catch (Exception exec) {
                CmClientUtil
                .debugLog(getClass(), "Exception while parsing policy", exec);
            }
        }
    }     
 
    boolean issueMandatoryTimeAlerts(String policyString) {
        if (policyString.length() == 0)
            return false;
        
        // get the mProperties(REQPROP_POLICY) string
        // use Xpath request for all defined Mandatory rules: 
        // get NodeSet /Policy/Rule/Property[@name="RULE_MANDATORY_TIME"]
        
        // get the string corresponding to the value of that Property
        
        
        String expressionString = "/Policy/Rule/Property[@key=\"RULE_MANDATORY_TIME\"]";
        
        // does it have a priority rule and what is the first priority value in that rule?
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath(); 
        XPathExpression expression = null;  
        boolean alertsCreated = false;
        try {
                                
            expression = xpath.compile(expressionString);
            InputSource is2 = new InputSource(new StringReader(policyString)); 
            NodeList ruleNodes = (NodeList) expression.evaluate(is2,XPathConstants.NODESET);
            if (ruleNodes.getLength() > 0) {
                
                for (int i = 0; i < ruleNodes.getLength(); i++) {
                        Element element = (Element)ruleNodes.item(i);
                        alertsCreated |= RULE_MANDATORY_TIME.createAlerts(element.getTextContent(),this.getDbIndex());                 
                }                               
            } 
        } catch (XPathExpressionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    
        return alertsCreated;
    }

    // ==================================================================================================================================
    protected void processBegin(Application hostApp, PriorityBlockingQueue<WorkOrder> wo_queue) throws InterruptedException {
        final String tag_LogLocal = sTag_Log + ".processBegin";

        if (getDbIndex() > 0)
            switch (getOrderAction()) {
                // --------------------------------------------------
                case NEW:
                    try {
                        if (HQME.WorkOrder.update(hostApp.getApplicationContext(), this) == 0)
                            // !wo_db.updateRecord(this))
                            throw new Exception(
                                    "Unable to update work order record at processBegin case NEW.");
                    } catch (Exception fault) {
                        setOrderActionWithNotify(Action.SUSPENDED);
                        break;
                    }
                case RESUMING:
                case REENABLING:
                case SUSPENDING:
                case SUSPENDED:
                case WAITING:   
                    setStatusOnPending(getOrderAction());
                    if (!(getOrderAction().equals(Action.CANCELING) || getOrderAction().equals(Action.DISABLING)) )
                        setOrderActionWithNotify(Action.PENDING);
                case PENDING:
                case EXECUTING:
                    if (!(getOrderAction().equals(Action.CANCELING) || getOrderAction().equals(Action.DISABLING)) )
                        attemptToExecute();
                case EXECUTED:
                    try {
                        processEnd(hostApp, wo_queue);
                        if (HQME.WorkOrder.update(hostApp.getApplicationContext(), this) == 0)
                            // !wo_db.updateRecord(this))
                            throw new Exception(
                                    "Unable to update work order record at processBegin case EXECUTED.");
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ case EXECUTED", fault);
                    }
                    break;

                // --------------------------------------------------
                case CANCELING:
                    try {
                        CmClientUtil.debugLog(getClass(), tag_LogLocal,
                                "DELETING work order # %d from the data base.", getDbIndex());
                        HQME.WorkOrder.delete(hostApp.getApplicationContext(), getDbIndex());
                        // wo_db.deleteRecord(this);
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), tag_LogLocal
                                + " @ case CANCELING mWorkOrder_db.deleteRecord", fault);
                    }
                    break;

                case DISABLING:
                    setOrderActionWithNotify(Action.DISABLED);
                    break;
                // --------------------------------------------------
                // do nothing for all other actions at this level
                // --------------------------------------------------
                case INTERNAL:
                case COMPLETED:
                case DISABLED:
                default:
                    break;
            }
    }

    private void attemptToExecute() {
        final String tag_LogLocal = sTag_Log + ".attemptToExecute";

        // 1. Enforces pre-requisites for download: connectivity, Background data enabled, 
        // *and* at least one VSD
        // Without either of these, status will remain pending, but want to record it was "blocked"
        // by calling:       setStatusOnPending(Action.SUSPENDING);
        
        // 2. If there is no contentManager available, we try to bind to it, but set to  "blocked" as above
        
        // 3. If the available VSDs fail to satisfy the functiongroups and free space criteria, to "blocked" also        
        try {
            if (RULE_CONNECTION_TYPE.isDownloadPermitted()) {                        
                if (!(WorkOrderManager.getContentProxy()  == null || WorkOrderManager.getContentProxy().asBinder().isBinderAlive() == false)) { 
                    if (WorkOrderManager.getContentProxy().VSDCount() > 0) {                    
                        synchronized (WorkOrderManager.sAvailableVSDs) {
                            if (WorkOrderManager.sAvailableVSDs.size() == 0) {
                                WorkOrderManager.setAvailableVSDs();
                            }
                        }
                    
                        if (!mStorageIdSet) {                           
                            selectAvailableVSD();  // tries to select a previously used VSD or else one that meets the current Download Rules                           
                        }
                        
                        if (getUrgent() || evaluateRules()) {
                            if ((getRelativePriority() == 0 || getRelativePriority() > 100)
                                    || WorkOrderManager.getInstance()
                                            .isHighestPriorityExecutableRequest(
                                                    getRelativePriority(), getClientUid())) {
                                resume(); // synchronous blocking call, returns
                                          // only
                                return;
                            } else {
                                // set to waiting since would execute but for
                                // lesser priority
                                setStatusOnPending(Action.WAITING);
                                setOrderActionWithNotify(Action.WAITING);
                                WorkOrderManager.setPriorityBasedInciteRequired(true);
                                return;
                            }
  
                        } 
                    } 
                } else {
                    // suspending for lack of a content manager connection
                    setStatusOnPendingWithNotify(Action.SUSPENDING);                           
                    WorkOrderManager.getInstance().bindStorageManager();
                    return;
                }
            } 
        } catch (Exception fault) {
            CmClientUtil
                    .debugLog(getClass(), tag_LogLocal + " @ case EXECUTING", fault);
        }        
        
        setStatusOnPendingWithNotify(Action.SUSPENDING);       
        return;
    }
    

    public Action setStatusOnPending(Action newOrderAction) {
        setStatusOnPending(newOrderAction == null ? (newOrderAction = Action.NEW).toString()
                : newOrderAction.toString());
        return newOrderAction;
    }

    private Action getStatusOnPending() {        
        final String tag_LogLocal = "getStatusOnPending";
        /*synchronized (mProperties)*/ {
            try {
                return Action.valueOf(mProperties.get(TAG_ACTION_ON_PENDING, Action.PENDING));
            } catch (IllegalArgumentException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (NullPointerException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (Throwable fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            }
            return setStatusOnPending(Action.NEW);
        } 
    }
    
    // ----------------------------------------------------------------------------------------------------------------------------------
    private void processEnd(Application hostApp, PriorityBlockingQueue<WorkOrder> wo_queue) {
        final String tag_LogLocal = sTag_Log + ".processEnd";
        final long workOrderIndex = getDbIndex();

        switch (getOrderAction()) {
            // --------------------------------------
            case EXECUTING:
                int attemptNumber = getAttemptNumber() + 1;
                synchronized (wo_queue) {
                    if (attemptNumber > WorkOrderManager.MAX_EXECUTE_FAILURES)
                        setOrderActionWithNotify(Action.DISABLING);
                    else {
                        setAttemptNumber(attemptNumber);
                        setOrderActionWithNotify(Action.RESUMING);
                    }
                    wo_queue.put(this);
                }
                break;

            // --------------------------------------
            case COMPLETED:
                // cat_db.insertRecord(new CatalogItem(this, workOrderIndex));
                HQME.WorkOrder.update(hostApp.getApplicationContext(), this);
                CmClientUtil.debugLog(getClass(), tag_LogLocal, "COMPLETED work order # %d",
                        workOrderIndex);
                                
                WorkOrderManager.notifyProgressUpdate(this);
                WorkOrderManager.broadcastProgressUpdate(this);                    
                
                // if this work order had a relative priority, incite the queue
                // to re-execute, in case there are pending work order blocked on the basis
                // or priority                           
                if (getRelativePriority() != 0)
                    WorkOrderManager.setPriorityBasedInciteRequired(true);

                break;

            // --------------------------------------
            case SUSPENDING:
                synchronized (wo_queue) {
                    setOrderActionWithNotify(Action.SUSPENDED);
                    wo_queue.put(this);
                }
                CmClientUtil.debugLog(getClass(), tag_LogLocal,
                        "SUSPENDED work order # %d for future re-try.", workOrderIndex);
                break;

                // --------------------------------------
            case WAITING:
                synchronized (wo_queue) {                    
                    wo_queue.put(this);
                }
                CmClientUtil.debugLog(getClass(), tag_LogLocal,
                        "WAITING work order # %d for future re-try.", workOrderIndex);
                break;

            // --------------------------------------
            case CANCELING:
                synchronized (wo_queue) {
                    wo_queue.put(this);
                }
                CmClientUtil.debugLog(getClass(), tag_LogLocal, "CANCELING work order # %d.",
                        workOrderIndex);
                break;

            // --------------------------------------
            case DISABLING:
                synchronized (wo_queue) {
                    setOrderActionWithNotify(Action.DISABLED);
                    wo_queue.put(this);
                }
                CmClientUtil.debugLog(getClass(), tag_LogLocal,
                        "DISABLED work order # %d - may be resumed by call to resumeRequest", workOrderIndex);
             // --------------------------------------
             case DISABLED:
                // if this work order had a relative priority, incite the queue
                // to re-execute, in case there are pending work order blocked on the basis
                // of priority                           
                if (getRelativePriority() != 0)
                    WorkOrderManager.setPriorityBasedInciteRequired(true);

                break;
            // --------------------------------------
            // do nothing for all other actions
            // --------------------------------------
            case NEW:
            case RESUMING:
            case REENABLING:
            case SUSPENDED:
            case PENDING:
            case INTERNAL:
            
            default:
                break;
        }
    }

    // ==================================================================================================================================
    private Boolean isActive = false;

 
    protected void resume() {
        String tag_LogLocal = sTag_Log + ".resume";

        ArrayList<Package> packages = null;
        int packagesIndex = -1;
        long workOrderIndex = -1;
        int progressPercent = -1;

        synchronized (this) {
            isActive = true;
            setOrderActionWithNotify(Action.EXECUTING);

            packages = getPackages();

            packagesIndex = getPackagesIndex();
            if (packagesIndex < 0 || packagesIndex > packages.size())
                setPackagesIndex(packagesIndex = 0);

            workOrderIndex = getDbIndex();

            progressPercent = getProgressPercent();
            if (progressPercent < 0 || progressPercent > 100)
                setProgressPercent(progressPercent = 0);

            if (packagesIndex == 0)
                CmClientUtil.debugLog(getClass(), tag_LogLocal, "Executing work order # %d",
                        workOrderIndex);
            else
                CmClientUtil.debugLog(getClass(), tag_LogLocal,
                        "Resuming work order # %d @ package # %d of %d", workOrderIndex,
                        packagesIndex + 1, packages.size());
            
        }
        // ------------------------------
        // Unsynchronized Section Begin
        // ------------------------------
        if (packagesIndex >= packages.size())
            setOrderActionWithNotify(Action.COMPLETED);
        else
            while (packagesIndex < packages.size()
                    && downloadPackage(packages.get(setPackagesIndex(packagesIndex++)))) {
                if (packagesIndex == packages.size()) // ensures last element
                // success = 100%
                // completion (that is to say,
                // downloads intentionally
                // never exceed 99%
                // complete)
                {
                    setProgressPercent(progressPercent = 100);
                    setOrderActionWithNotify(Action.COMPLETED);
                }
                CmClientUtil.debugLog(getClass(), tag_LogLocal,
                        "Downloaded %s : %d%% of work order # %d completed.", packages.get(
                                packagesIndex - 1).getSourceLocalPath(), progressPercent,
                        workOrderIndex);
            }
        // ------------------------------
        // Unsynchronized Section End
        // ------------------------------
        synchronized (this) {
            try {
                isActive = false;
                CmClientUtil.debugLog(getClass(), tag_LogLocal, "Leaving work order # %d",
                        workOrderIndex);
                notifyAll();
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ notifyAll", fault);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void suspend(boolean abort) {
        String tag_LogLocal = sTag_Log + ".suspend";

        setOrderActionWithNotify(abort ? Action.CANCELING : Action.SUSPENDING);
        synchronized (this) {
            int tries = 2;
            while ((tries-- > 0) && isActive)
                try {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                            "Suspending work order processing...");

                    if (tries > 0)
                        wait(10 * 1000);
                    else
                        wait();

                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                            "Suspended work order processing.");
                    return;
                } catch (InterruptedException fault) {
                    if (mHandler != null)
                        mHandler.stopTransfer();

                    CmClientUtil.debugLog(getClass(), tag_LogLocal + "("
                            + (abort ? "abort" : "save") + ")", fault);
                } catch (Throwable fault) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal + "("
                            + (abort ? "abort" : "save") + ")", fault);
                }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void disable() {
        String tag_LogLocal = sTag_Log + ".disable";

        setOrderActionWithNotify(Action.DISABLING);
        synchronized (this) {
            int tries = 2;
            while ((tries-- > 0) && isActive)
                try {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                            "Disabling work order processing...");

                    if (tries > 0)
                        wait(10 * 1000);
                    else
                        wait();

                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                            "Disabled work order processing.");
                    return;
                } catch (InterruptedException fault) {
                    if (mHandler != null)
                        mHandler.stopTransfer();

                    CmClientUtil.debugLog(getClass(), tag_LogLocal + "("
                            + ("save") + ")", fault);
                } catch (Throwable fault) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal + "("
                            + ("save") + ")", fault);
                }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void suspend() {
        suspend(false);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void cancel() {
        suspend(true);
    }

    // ==================================================================================================================================
    protected Long executionPriority; // 0 = highest priority; N = lowest

    
     protected Long calculateWorkOrderExecutionPriority() {
      /*   synchronized (mProperties) */{
            // ----------------------------------------------------------------------------------------------------
            // 63-bit execution priority hash, for example, 0x 4 7 b c d 7
            // ffffffffff =
            // 0x47bcd7ffffffffff
            //
            // bits 63-60 : 4 --> 4 = marker of a valid execution priority value
            // bits 59-56 : a --> 7 = reserved
            // bits 55-52 : b --> execution state :
            // getExecutionState().ordinal() <<
            // 52
            // bits 51-48 : c --> level of urgency : 1 = mandatory, 2 = urgent; 3 = normal :
            // (getUrgent() + 1) << 48
            // bits 47-44 : d --> order action : getOrderAction().ordinal() <<
            // 44
            // bits 43-40 : e --> 7 = reserved
            // bits 39-0 : ffffffffff --> milliseconds since CmDate.EPOCH
            // (truncated
            // to fit into 40 bits)
            // this gives older work orders higher priority than more recent
            // work
            // orders (that is to say, values grow larger with time)
            // this avoids disturbing the current [oldest] work order whenever
            // new
            // work orders arrive within the same priority group
            //
            // 4.0.0.0.0.0.0000000000 = highest representable priority
            // 4.7.0.1.0.7.xxxxxxxxxx = urgent "pending" priority
            // 4.7.0.2.0.7.xxxxxxxxxx = normal "pending" priority
            // 4.f.f.f.f.f.ffffffffff = lowest representable priority
            // ----------------------------------------------------------------------------------------------------

            // bits 56-63 : 0x40
            executionPriority = (0x4700070000000000L);

            // bits 52-55 : work-flow order = PENDING, LOGIN, EXECUTE, QUIT,
            // LOGOUT
            executionPriority |= ((getExecutionState().ordinal() << 52) & 0x00f0000000000000L);

            // bits 48-51 : 1 = urgent; 2 = normal
            executionPriority |= (getMandatory() ?  0x0001000000000000L : (getUrgent() ? 0x0002000000000000L : 0x0003000000000000L));

            // bits 44-47 : execution priority order = INTERNAL, EXECUTING,
            // RESUMING, PENDING, NEW, CANCELING, SUSPENDING, SUSPENDED,
            // COMPLETED
            executionPriority |= ((getOrderAction().ordinal() << 44) & 0x0000f00000000000L);

            // bits 0-43 : creation date (older creation dates have higher
            // priority)
            executionPriority |= (getPriorityTime().getTime() & 0x000000ffffffffffL);

            if (this.getDbIndex() >= 0)
                CmClientUtil.debugLog(getClass(), "calculateWorkOrderExecutionPriority",
                        "Order # %5d : Execution Priority = 0x%16x", this.getDbIndex(),
                        executionPriority);

            return executionPriority;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    // default comparator sorts on work order priority property -- priority 0 is
    // highest, N is lowest, negative values are reserved
    //
    public int compareTo(WorkOrder otherWorkOrder) {
        return Long.signum(executionPriority - otherWorkOrder.executionPriority);
    }

    // ==================================================================================================================================
    public boolean evaluateRules() {
        final String tag_LogLocal = "evaluateRules @ sRuleMaps.get(getRuleSet()).evaluateRuleSet";
        try { 
            return getPolicy().evaluateDownloadElement(this);
        } catch (NullPointerException fault) {
            CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
        } catch (IndexOutOfBoundsException fault) {
            CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
        } catch (Throwable fault) {
            CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
        }
        return false;
    }

    private long mLastNotifyTime = 0;

    public long getLastNotifyTime() {
        return mLastNotifyTime;
    }

    private ProtocolHandler mHandler = null;

    private boolean isStopDownloadRequested() {
        // stop requested when isActive and order action changes
        return isActive && (mHandler != null) && !Action.EXECUTING.equals(getOrderAction()); 
    }

    protected boolean downloadPackage(Package pkg) {
        final String tag_LogLocal = "downloadPackage";

        InputStream responseStream = null;

        IContentObject targetObject = null;

        int numRetries = 1; // maximum number of times to re-try the mRequest
        // before giving up
        
        while (numRetries-- >= 0) {
            try {
                if (isStopDownloadRequested()) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                            "Cooperatively aborting download before remote mRequest...");
                    return false;
                }

                
                // Implementation Note: Currently Uri is a required field, and selection of 
                // the ProtocolHandler is based on this alone.
                // Future implementations of the ProtocolManager will select a ProtocolHandler based on
                // both the MIME type and, if-defined, the Uri of the Request.                 
                mHandler = ProtocolManager.getInstance().getProtocolHandler(
                        pkg.getSourceUri().toString());
                mHandler.intitializeRequest(pkg.getSourceUri().toString());

                try {
                    mHandler.startTransfer(pkg.getProgressBytes().intValue(), pkg.getModified());
                } catch (ProtocolException e) {
                    pkg.setProgressBytes(0L); // retry from the beginning
                    CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ " + e.getMessage(),
                            "Restarting download (%s)", numRetries == 1 ? "final attempt"
                                    : numRetries + " attempts remaining");
                    continue;
                }

                if (mHandler.getLastModified() != null)
                    pkg.setModified(CmDate.valueOf(mHandler.getLastModified()));

                // Obtain Content-Length ONLY on first-download mRequest (it's the REMAINING 
                // mContent length for progressive byte-range downloads) 
                if (pkg.getProgressBytes() == 0) 
                {
                    if (mHandler.getContentLength() != null)
                        pkg.setContentSize(mHandler.getContentLength());
                }

                responseStream = new ProtocolHandlerInputStream(mHandler);

                IVSD store = null;
                
                // TODO: store to be indicated in work order - for first release we only support single store                
                store = WorkOrderManager.getContentProxy() != null ? (WorkOrderManager
                        .getContentProxy().VSDCount() > 0 ? WorkOrderManager
                        .getContentProxy().getStorage(getStorageId()) : null) : null;
                        
                    
                if (store == null) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                    "Ohh - No VSD satisfying the function group and max size rules is available right now");
                }  else {
                    
                    if (pkg.getProgressBytes() == 0) {
                        // create new cache object and assign relevant QueueRequest properties to it 
                        targetObject = store.createObject(pkg.getSourceLocalPath());                        
                        assignObjectProperties(targetObject,pkg);                                                
                    } else {
                        targetObject = store.getObject(pkg.getSourceLocalPath());
                    }
                    return savePackage(pkg, responseStream, targetObject);
                }
            } catch (Throwable fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } finally {
                if (mHandler != null)
                    try {
                        mHandler.stopTransfer();
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(),
                                tag_LogLocal + " @ mHandler.stopTransfer", fault);
                    } finally {
                        mHandler = null;
                    }

                if (responseStream != null)
                    try {
                        responseStream.close();
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ responseStream.close",
                                fault);
                    } finally {
                        responseStream = null;
                    }

                if (targetObject != null)
                    try {
                        targetObject.close();
                    } catch (Exception fault) {
                        CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ targetObject.close",
                                fault);
                    } finally {
                        targetObject = null;
                    }
                    
                
            }
        }
        return false;
    }

    private void assignObjectProperties(IContentObject targetObject, Package pkg) throws RemoteException {
        
        targetObject.setProperty(VSDProperties.SProperty.S_STORE_NAME.name(), pkg.getSourceLocalPath());

        if (!"".equals(pkg.properties.get(VSDProperties.SProperty.S_STORE_SIZE.name())))
            targetObject.setProperty(VSDProperties.SProperty.S_STORE_SIZE.name(), pkg.properties.get(VSDProperties.SProperty.S_STORE_SIZE.name()));
        else 
            targetObject.setProperty(VSDProperties.SProperty.S_STORE_SIZE.name(),pkg.getContentSize().toString());
        
        targetObject.setProperty(VSDProperties.SProperty.S_SOURCEURI.name(), pkg.getSourceUri().toString());
        
        targetObject.setProperty(VSDProperties.SProperty.S_ORIGIN.name(),this.getClientUid());
        targetObject.setProperty(VSDProperties.SProperty.S_LOCKED.name(),"false");                                               
        targetObject.setProperty(VSDProperties.SProperty.S_TYPE.name(),pkg.getPackageMimeType());
        
        // if S_REDOWNLOAD_URI unset, use SOURCEURI here
        if (!"".equals(pkg.properties.get(VSDProperties.SProperty.S_REDOWNLOAD_URI.name())))
            targetObject.setProperty(VSDProperties.SProperty.S_REDOWNLOAD_URI.name(), pkg.properties.get(VSDProperties.SProperty.S_REDOWNLOAD_URI.name()));
        else 
            targetObject.setProperty(VSDProperties.SProperty.S_REDOWNLOAD_URI.name(), pkg.getSourceUri().toString());

        // no network policy at present, so effective policy is the same as the REQPROP_POLICY
        if (mPolicy!= null)
            targetObject.setProperty(VSDProperties.OptionalProperty.S_POLICY.name(),mProperties.get(QueueRequestProperties.OptionalProperties.REQPROP_POLICY.name()));

        // other optional fields
        if (!"".equals(pkg.properties.get(VSDProperties.OptionalProperty.S_METADATA.name())))
            targetObject.setProperty(VSDProperties.OptionalProperty.S_METADATA.name(), pkg.properties.get(VSDProperties.OptionalProperty.S_METADATA.name()));
        if (!"".equals(pkg.properties.get(VSDProperties.OptionalProperty.S_CONTENTPROFILE.name())))
            targetObject.setProperty(VSDProperties.OptionalProperty.S_CONTENTPROFILE.name(), pkg.properties.get(VSDProperties.OptionalProperty.S_CONTENTPROFILE.name()));                                               
        if (!"".equals(pkg.properties.get(VSDProperties.OptionalProperty.S_VALIDITYCHECK.name())))
            targetObject.setProperty(VSDProperties.OptionalProperty.S_VALIDITYCHECK.name(), pkg.properties.get(VSDProperties.OptionalProperty.S_VALIDITYCHECK.name()));                                                      
        if (!"".equals(pkg.properties.get(VSDProperties.OptionalProperty.S_RIGHTSCHECK.name())))
            targetObject.setProperty(VSDProperties.OptionalProperty.S_RIGHTSCHECK.name(), pkg.properties.get(VSDProperties.OptionalProperty.S_RIGHTSCHECK.name()));                                                      
    }

    private boolean savePackage(Package pkg, InputStream responseStream, IContentObject targetObject) {
        final String tag_LogLocal = "savePackage";
        boolean success = true;
        try {
            int count;
            byte[] buffer = newPoliteBuffer(64 * 1024);
            targetObject.open("rws", true);
            long offset = pkg.getProgressBytes();
            targetObject.seek(offset, 0);
                                    
            long cumulativeBytes = 0;         
            while ((count = responseStream.read(buffer)) > 0) {
                long written = (long) targetObject.write(buffer, count);
                cumulativeBytes += written;
                pkg.setProgressBytes(pkg.getProgressBytes() + written);
                // for RULE_DOWNLOAD_LIMIT, need to record this
                if (RULE_CONNECTION_TYPE.isMobileSession())
                    pkg.setMobileDownloadBytes(pkg.getMobileDownloadBytes() + written);
                
                if (isStopDownloadRequested()) {
                    success = false;
                    CmClientUtil.debugLog(getClass(), tag_LogLocal,
                            "Cooperatively aborting download...");
                    break;
                }

                try {
                    long now = new CmDate().getTime();
                    if (now - mLastNotifyTime > 2 * 1000) {
                        double packagesSize = getPackages().size();
                        double contentSize = pkg.getContentSize();
                        double progressPercent = contentSize == 0.0 ? 0.0 : pkg.getProgressBytes()
                                / contentSize;
                        progressPercent = ((double) getPackagesIndex() / packagesSize)
                                + ((1.0 / packagesSize) * progressPercent);
                        progressPercent = progressPercent > 0.99 ? 99.0 : 100.0 * progressPercent;
                        setProgressPercent((int) progressPercent);
                        setDownloadRate((cumulativeBytes / (now - mLastNotifyTime)));
                        cumulativeBytes = 0;
                        WorkOrderManager.notifyProgressUpdate(this);                                                
                        mLastNotifyTime = now;
                    }
                } catch (Throwable fault) {
                    CmClientUtil.debugLog(getClass(), tag_LogLocal + " @ read-write loop", fault);
                }
            }
            targetObject.close();
        } catch (Throwable fault) {
            success = false;
            CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
        }
        return success;
    }

    // ==================================================================================================================================
    private static final byte[] newPoliteBuffer(int requestedSizeInBytes) throws Throwable {
        byte[] buffer = null;
        while (buffer == null)
            try {
                buffer = new byte[requestedSizeInBytes];
            } catch (Throwable fault) {
                requestedSizeInBytes >>>= 1;
                if (requestedSizeInBytes == 0)
                    throw fault;
            }
        return buffer;
    }

    // ==================================================================================================================================
    protected Policy getPolicy() {
        return setPolicy(mPolicy);
    }

    protected Policy setPolicy(Policy rules) {
        return mPolicy = rules == null ? new Policy() : rules;
    }

    // ==================================================================================================================================
    public Boolean getUrgent() {
        return Boolean.valueOf(mProperties.get(TAG_IS_URGENT, Boolean.FALSE));
    }

    public Boolean setUrgent(Boolean isUrgent) {
        setUrgent(isUrgent == null ? (isUrgent = Boolean.FALSE).toString() : isUrgent.toString());
        return isUrgent;
    }

    public String setUrgent(String isUrgent) {
        return mProperties.set(TAG_IS_URGENT, isUrgent);
    }

// ==================================================================================================================================
    public Boolean getMandatory() {
        return Boolean.valueOf(mProperties.get(TAG_IS_MANDATORY, Boolean.FALSE));
    }

    public Boolean setMandatory(Boolean isMandatory) {
        setMandatory(isMandatory == null ? (isMandatory = Boolean.FALSE).toString() : isMandatory.toString());
        return isMandatory;
    }

    public String setMandatory(String isMandatory) {
        return mProperties.set(TAG_IS_MANDATORY, isMandatory);
    }

 // ==================================================================================================================================
    public Integer getPackagesIndex() {
        return CmNumber.parseInt(mProperties.get(TAG_PACKAGES_INDEX, -1), -1);
    }

    public Integer setPackagesIndex(Integer newPackagesIndex) {
        setPackagesIndex(newPackagesIndex == null ? (newPackagesIndex = -1).toString()
                : newPackagesIndex.toString());
        return newPackagesIndex;
    }

    public String setPackagesIndex(String newPackagesIndex) {
        return mProperties.set(TAG_PACKAGES_INDEX, newPackagesIndex);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public Integer getProgressPercent() {
        return CmNumber.parseInt(mProperties.get(TAG_PROGRESS_PERCENT, 0), 0);
    }

    public Integer setProgressPercent(Integer newProgressPercent) {
        setProgressPercent(newProgressPercent == null ? (newProgressPercent = -1).toString()
                : newProgressPercent.toString());
        return newProgressPercent;
    }

    public String setProgressPercent(String newProgressPercent) {
        return mProperties.set(TAG_PROGRESS_PERCENT, newProgressPercent);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public Long getDownloadRate() {
        return CmNumber.parseLong(mProperties.get(TAG_MOBILE_DOWNLOAD_RATE, 0L), 0L);
    }

    public Long setDownloadRate(Long l) {
        setDownloadRate(l == null ? (l = -1L).toString()
                : l.toString());
        return l;
    }

    public String setDownloadRate(String newPackagesIndex) {
        return mProperties.set(TAG_MOBILE_DOWNLOAD_RATE, newPackagesIndex);
    }

    // ==================================================================================================================================
    public String getSummaryStatus() {
        StringBuilder info = new StringBuilder();
        try {
            long id = getDbIndex();

            ArrayList<Package> packages = getPackages();
            int packagesSize = packages.size();
            int packagesIndex = getPackagesIndex();

            String key = id + "-" + getAttemptNumber() + "-" + (packagesIndex + 1) + "/"
                    + packagesSize + (getUrgent() ? "! " : "  ")
                    + getQueueRequestState();

            if (id < 0)
                info.append('(').append(key).append(')');
            else {
                info.append(key);
                if (packagesIndex >= 0 && packagesIndex < packagesSize)
                    info.append(" : ").append(getProgressPercent()).append(" % complete"); // info.append(" : ").append(String.format("%,d",
                // packages.get(packagesIndex).getProgressBytes())).append(" bytes");
            }
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "getSummaryStatus", fault);
        }
        return info.toString();
    }

    // ==================================================================================================================================
    private ArrayList<Package> mPackageList;

    protected ArrayList<Package> getPackages() {
        return setPackages(mPackageList);
    }

    protected ArrayList<Package> setPackages(ArrayList<Package> newPackages) {
        return mPackageList = newPackages == null ? new ArrayList<Package>(0) : newPackages;
    }

    // ==================================================================================================================================
    public CmDate getCreation() {
        String value = mProperties.get(TAG_CREATION);
        return value == null || value.length() == 0 ? setCreation(new CmDate()) : CmDate
                .valueOf(value);
    }

    public CmDate setCreation(CmDate newDate) {
        setCreation(newDate == null ? (newDate = CmDate.EPOCH).toString() : newDate.toString());
        return newDate;
    }

    public String setCreation(String newDate) {
        return mProperties.set(TAG_CREATION, newDate);
    }

    // ==================================================================================================================================
    public CmDate getPriorityTime() {
        String value = mProperties.get(TAG_PRIORITY_TIME);
        return value == null || value.length() == 0 ? setPriorityTime(getCreation()) : CmDate
                .valueOf(value);
    }

    public CmDate setPriorityTime(CmDate newDate) {
        setPriorityTime(newDate == null ? (newDate = CmDate.EPOCH).toString() : newDate.toString());
        return newDate;
    }

    public String setPriorityTime(String newDate) {
        return mProperties.set(TAG_PRIORITY_TIME, newDate);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public CmDate getModification() {
        return CmDate.valueOf(mProperties.get(TAG_MODIFICATION));
    }

    public CmDate setModification(CmDate newDate) {
        setModification(newDate == null ? (newDate = CmDate.EPOCH).toString() : newDate.toString());
        return newDate;
    }

    public String setModification(String newDate) {
        return mProperties.set(TAG_MODIFICATION, newDate);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public CmDate getExpiration() {
        return CmDate.valueOf(mProperties.get(TAG_EXPIRATION));
    }

    public CmDate setExpiration(CmDate newDate) {
        setExpiration(newDate == null ? (newDate = CmDate.EPOCH).toString() : newDate.toString());
        return newDate;
    }

    public String setExpiration(String newDate) {
        return mProperties.set(TAG_EXPIRATION, newDate);
    }

    // ==================================================================================================================================
    public Action getOrderAction() {
        final String tag_LogLocal = "getOrderAction";
      /*  synchronized (mProperties) */{
            try {
                return Action.valueOf(mProperties.get(TAG_ORDER_ACTION, Action.PENDING));
            } catch (IllegalArgumentException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (NullPointerException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (Throwable fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            }
            return setOrderAction(Action.PENDING);
        }
    }

    public Action setOrderAction(Action newOrderAction) {
        setOrderAction(newOrderAction == null ? (newOrderAction = Action.PENDING).toString()
                : newOrderAction.toString());
        return newOrderAction;
    }

    public Action setOrderActionWithNotify(Action newOrderAction) {
     /*   synchronized (mProperties)*/ {
            newOrderAction = setOrderAction(newOrderAction);
            CmClientUtil.debugLog(getClass(), "setOrderActionWithNotify", "newOrderAction = %s",
                    newOrderAction);

            calculateWorkOrderExecutionPriority();
            WorkOrderManager.notifyProgressUpdate(this);            
            return newOrderAction;
        }
    }
    
    public Action setStatusOnPendingWithNotify(Action newOrderAction) {        
            setStatusOnPending(newOrderAction);
            WorkOrderManager.notifyProgressUpdate(this);
            return newOrderAction;        
    }


    public String setStatusOnPending(String newOrderAction) {
        return mProperties.set(TAG_ACTION_ON_PENDING, newOrderAction);
    }
    
    public String setOrderAction(String newOrderAction) {
        return mProperties.set(TAG_ORDER_ACTION, newOrderAction);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public synchronized State getExecutionState() {
        final String tag_LogLocal = "getExecutionState";
       /* synchronized (mProperties)*/ {
            try {
                return State
                        .valueOf(mProperties.get(TAG_EXECUTION_STATE, State.PENDING.toString()));
            } catch (IllegalArgumentException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (NullPointerException fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            } catch (Throwable fault) {
                CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
            }
            return setExecutionState(State.PENDING);
        }
    }

    public synchronized State setExecutionState(State newExecutionState) {
        setExecutionState(newExecutionState == null ? (newExecutionState = State.PENDING)
                .toString() : newExecutionState.toString());
        return newExecutionState;
    }

    public synchronized State setExecutionStateWithNotify(State newExecutionState) {
        /*synchronized (mProperties)*/ {
            setExecutionState(newExecutionState);
            CmClientUtil.debugLog(getClass(), "setExecutionStateWithNotify", "newOrderState = %s",
                    newExecutionState);

            calculateWorkOrderExecutionPriority();

            return newExecutionState;
        }
    }

    public String setExecutionState(String newExecutionState) {
        return mProperties.set(TAG_EXECUTION_STATE, newExecutionState);
    }

    // ==================================================================================================================================
    public Integer getAttemptNumber() {
        return CmNumber.parseInt(mProperties.get(TAG_ATTEMPT_NUMBER, 0), 0);
    }

    public Integer setAttemptNumber(Integer newAttemptNumber) {
        setAttemptNumber(newAttemptNumber == null ? (newAttemptNumber = 0).toString()
                : newAttemptNumber.toString());
        return newAttemptNumber;
    }

    public String setAttemptNumber(String newAttemptNumber) {
        return mProperties.set(TAG_ATTEMPT_NUMBER, newAttemptNumber);
    }

    // ==================================================================================================================================
    public Integer getStorageId() {
        return CmNumber.parseInt(mProperties.get(TAG_STORAGE_ID, 0), 0);// TODO: default is -1?
    }

    public Integer setStorageId(Integer storeId) {
        setStorageId(storeId == null ? (storeId = 0).toString()
                : storeId.toString());
        return storeId;
    }

    public String setStorageId(String storeId) {
        return mProperties.set(TAG_STORAGE_ID, storeId);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public String getNotificationTarget() {
        return mProperties.get(TAG_NOTIFICATION_TARGET);
    }

    public String setNotificationTarget(String newTarget) {
        return mProperties.set(TAG_NOTIFICATION_TARGET, newTarget);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    QueueRequestObject toQueueRequest() {

        CmProperties woProps = new CmProperties();
        
        if (mPackageList.size() == 1) {
            
            try {

                woProps.putAll(mPackageList.get(0).properties);
                // only include the REQPROP properties from the wo level
                synchronized (mProperties) {
                    for (Entry<String, String> entry : mProperties.entrySet()) {
                        String key = entry.getKey();
                        if (QueueRequestProperties.isOptional(key) || 
                                QueueRequestProperties.isRequired(key) || 
                                QueueRequestProperties.isTransient(key))
                            woProps.put(key,entry.getValue());                            
                    }
                }                
                
            } catch (Exception exec) {
                CmClientUtil.debugLog(getClass(), "toQueueRequest()", exec);
            }
            

            QueueRequestObject qRequest = new QueueRequestObject(woProps);
            qRequest.setRequestId(getDbIndex());
            qRequest.mProperties.set(QueueRequestProperties.TransientProperties.REQPROP_REQUEST_STATE.name(),
                    getQueueRequestState().name());
            return qRequest;
        } else {
            return null;
        }

    }

    // ==================================================================================================================================
    // Serialization Methods
    // ==================================================================================================================================
    public static final String NAMESPACE = null;

    public static final String TAG_ATTEMPT_NUMBER = "AttemptNumber";

    public static final String TAG_CREATION = "Creation";

    public static final String TAG_PRIORITY_TIME = "PriorityTime";

    public static final String TAG_CALLING_UID = QueueRequestProperties.TransientProperties.REQPROP_CALLING_UID.name();

    public static final String TAG_EXECUTION_STATE = "ExecutionState";

    public static final String TAG_RELATIVE_PRIORITY = "RelativePriority";
    
    public static final String TAG_FUNCTIONGROUPS = "FunctionGroups";
    
    public static final String TAG_FREE_SPACE_PERCENTAGE = "FreeSpacePercentage";

    public static final String TAG_EXPIRATION = QueueRequestProperties.OptionalProperties.REQPROP_EXPIRATION_DATE
            .name();

    public static final String TAG_IS_URGENT = QueueRequestProperties.OptionalProperties.REQPROP_IMMEDIATE
            .name();

    public static final String TAG_IS_MANDATORY = QueueRequestProperties.TransientProperties.REQPROP_MANDATORY
    .name();

    public static final String TAG_MODIFICATION = QueueRequestProperties.TransientProperties.REQPROP_LAST_MODIFICATION_DATE
            .name();

    public static final String TAG_NOTIFICATION_TARGET = QueueRequestProperties.OptionalProperties.REQPROP_BROADCAST_INTENT
            .name();

    public static final String TAG_ORDER_ACTION = "OrderAction";

    public static final String TAG_ACTION_ON_PENDING = "ActionOnPending";

    public static final String TAG_PACKAGE = Package.class.getSimpleName();

    public static final String TAG_PACKAGE_MIME_TYPE = QueueRequestProperties.RequiredProperties.REQPROP_TYPE
            .name();

    public static final String TAG_PACKAGES_INDEX = "PackagesIndex";

    public static final String TAG_PROGRESS_BYTES = QueueRequestProperties.TransientProperties.REQPROP_CURRENT_BYTES_TRANSFERRED
            .name();

    public static final String TAG_TRANSFER_BYTES_MOBILE = QueueRequestProperties.TransientProperties.REQPROP_TRANSFER_BYTES_MOBILE
    .name();

    public static final String TAG_PROGRESS_PERCENT = "ProgressPercent";
    
    public static final String TAG_MOBILE_DOWNLOAD_RATE = QueueRequestProperties.TransientProperties.REQPROP_DOWNLOAD_RATE.name();
    
    public static final String TAG_RULE_COLLECTION = "Rule";

    public static final String TAG_SOURCE_LOCAL_PATH = QueueRequestProperties.RequiredProperties.REQPROP_STORE_NAME
            .name();

    public static final String TAG_SOURCE_URI = QueueRequestProperties.RequiredProperties.REQPROP_SOURCE_URI
            .name();

    public static final String TAG_STORAGE_ID = "StorageId";

    public static final String TAG_GROUP = QueueRequestProperties.OptionalProperties.REQPROP_PERMISSIONS_GROUP.name();
    public static final String TAG_USER = QueueRequestProperties.OptionalProperties.REQPROP_PERMISSIONS_USER.name();
    public static final String TAG_WORLD = QueueRequestProperties.OptionalProperties.REQPROP_PERMISSIONS_WORLD.name();
    public static final String TAG_GROUP_PROP = QueueRequestProperties.OptionalProperties.REQPROP_GROUP.name();


    public static final String TAG_WORK_ORDER = WorkOrder.class.getSimpleName();

    // ----------------------------------------------------------------------------------------------------------------------------------
    public String toString() {
        return toString(false);
    }

    public synchronized String toString(boolean omitXMLDeclatation) {
        StringWriter writer = new StringWriter();
        try {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(writer);
            if (!omitXMLDeclatation)
                serializer.startDocument("UTF-8", true);

            serializer.startTag(NAMESPACE, name());

            serializer.attribute(NAMESPACE, TAG_EXPIRATION, CmDate.valueOf(getExpiration()));

            // the work order level properties as elements
            // (attributes cannot store all string types that may conceivably be
            // present in title/description)
            CmProperties propCopy = null;
            synchronized (mProperties) {
                propCopy = new CmProperties(mProperties);
            }
           
            for (String key : propCopy.keySet())
                serializer.startTag(NAMESPACE, key).text(propCopy.get(key)).endTag(NAMESPACE,
                        key);
            
            
            for (Package pkg : getPackages()) {
                serializer.startTag(NAMESPACE, TAG_PACKAGE);
                CmProperties props = pkg.properties;
                synchronized (props) {
                    for (String key : props.keySet())
                        serializer.startTag(NAMESPACE, key).text(props.get(key)).endTag(NAMESPACE,
                                key);
                    serializer.endTag(NAMESPACE, TAG_PACKAGE);
                }
            }

            serializer.endTag(NAMESPACE, name());
            serializer.endDocument();
        } catch (Throwable fault) {
            CmClientUtil.debugLog(getClass(), "toString", fault);
        }
        return writer.toString();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void fromString(String xmlContent) {
        if (xmlContent != null && xmlContent.length() > 0)
            try {
                WorkOrderParser parser = new WorkOrderParser();
                SAXParserFactory spf = SAXParserFactory.newInstance();
                SAXParser sp = spf.newSAXParser();
                XMLReader xr = sp.getXMLReader();
                xr.setContentHandler(parser);
                xr.parse(new InputSource(new StringReader(xmlContent)));
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "fromString", fault);
            }
        calculateWorkOrderExecutionPriority();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    private class WorkOrderParser extends DefaultHandler {
        private StringBuilder mContentBuilder;
        private Package mPackageBuilder;
        private boolean isPackageProperty = false;

        // --------------------------------------------------
        @Override
        public void startElement(String namespaceURI, String localName, String qName,
                Attributes atts) throws SAXException {

            if (TAG_PACKAGE.equals(localName)) {
                mPackageBuilder = new Package();
                isPackageProperty = true;
            } 
        }

        // --------------------------------------------------
        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            super.endElement(uri, localName, name);

            if (TAG_PACKAGE.equals(localName)) {
                if (mPackageBuilder != null) {
                    getPackages().add(mPackageBuilder);
                    mPackageBuilder = null;
                }
                isPackageProperty = false;
            } else if (QueueRequestObject.TAG_POLICY.equals(localName)) { // Rule
                try {
                    // here create a RuleCollection from the list mRulesList
                    // add it to mRuleCollections
                    mProperties.set(localName, mContentBuilder.toString());
                    mPolicy = new Policy(mContentBuilder.toString());   
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "endElement=" + QueueRequestObject.TAG_POLICY, fault);
                }
            } else if (isPackageProperty) {
                if (mPackageBuilder != null)
                    mPackageBuilder.properties.set(localName, mContentBuilder.toString());
            }
            else
                mProperties.set(localName, mContentBuilder.toString());

            mContentBuilder.setLength(0);
            mContentBuilder.trimToSize();
        }

        // --------------------------------------------------
        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            mContentBuilder = new StringBuilder();
            mPackageBuilder = null;
            setPackages(new ArrayList<Package>(1)); // assume the majority of            
        }

        // --------------------------------------------------
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            super.characters(ch, start, length);
            mContentBuilder.append(ch, start, length);
        }
    }

    // ==================================================================================================================================
    public String getClientUid() {
         return mProperties.get(TAG_CALLING_UID);
     }    

     public String setClientUid(String uid) {
         return mProperties.set(TAG_CALLING_UID, uid);
     }
     // ==================================================================================================================================
     
    public void setPriority(int relativePriority) {
        // This sets the application specific relative priority which is an
        // integer value used to
        // compare relative priorities of QueueRequests submitted by this
        // application

        // the bounds of 0-100 on the value have already been checked
        setRelativePriority(relativePriority);
        
    }

    

    // ==================================================================================================================================
   public Integer getRelativePriority() {
        return CmNumber.parseInt(mProperties.get(TAG_RELATIVE_PRIORITY, 0), 0);
    }

    public Integer setRelativePriority(Integer relPriority) {        
        setRelativePriority(relPriority == null ? (relPriority = 0).toString()
                : relPriority.toString());
        return relPriority;
    }

    public String setRelativePriority(String relPriority) {
        return mProperties.set(TAG_RELATIVE_PRIORITY, relPriority);
    }
   
     // ==================================================================================================================================
    
     
      private boolean selectAvailableVSD() {
          IVSD store = null;
          
          // try to retrieve the content object if we already saved it in the workOrder
          // and the object exists, the storageId is valid (0 is the VSD default)
          try {
              store = WorkOrderManager.getContentProxy() != null ? 
                      (WorkOrderManager.getContentProxy().VSDCount() > 0 ? 
                          WorkOrderManager.getContentProxy().getStorage(this.getStorageId()) : 
                          null) : 
                      null;
              if (store != null) {
                  IContentObject contentObject = store.getObject(this.mPackageList.get(0).getSourceLocalPath());
                  if (contentObject != null) {
                      // this was the content object (and store) we used previously!
                      this.mStorageIdSet = true;
                      return true;
                  }
              }
          } catch (RemoteException e) {
              e.printStackTrace();
          }

          this.mStorageIdSet = false;

          // if it is not available try to get it 
          synchronized (this) {
              HashMap<Integer, ArrayList<Long>> availableStorageIds = WorkOrderManager.sAvailableVSDs;
              if (availableStorageIds != null) {
                  if (availableStorageIds.size() > 0) {
                      // use existence of partial content object to decide on the
                      // store to use for us if possible
                    for (Integer storageId : availableStorageIds.keySet()) {
                        try {
                            store = WorkOrderManager.getContentProxy() != null ? (WorkOrderManager
                                    .getContentProxy().VSDCount() > 0 ? WorkOrderManager
                                    .getContentProxy().getStorage(storageId) : null) : null;
                            if (store == null)
                                continue;

                            IContentObject contentObject = store.getObject(this.mPackageList.get(0)
                                    .getSourceLocalPath());
                            if (contentObject != null
                                    && contentObject.size() == this.mPackageList.get(0)
                                            .getProgressBytes()) {
                                // this was the VSD we used
                                // previously!
                                this.setStorageId(storageId);
                                this.mStorageIdSet = true;
                                return this.mStorageIdSet;
                            }
                        } catch (Exception exec) {
                        }
                    }

                      // TODO: do NOT want to do this - change behaviour so this QR becomes blocked 
                      // will try to use an alternative VSD
                      Long bytesDownloaded = 0L;
                      Long bytesToDownload = 0L;

                      for (Package pkg : getPackages()) {
                          bytesDownloaded += pkg.getProgressBytes();
                          bytesToDownload += pkg.getContentSize();
                      }

                      store = null;
                      // use any of the VSDs (well check they have enough bytes)
                      for (Integer storageId : availableStorageIds.keySet()) {
                          try {
                              store = WorkOrderManager.getContentProxy() != null ? 
                                      (WorkOrderManager.getContentProxy().VSDCount() > 0 ? 
                                          WorkOrderManager.getContentProxy().getStorage(storageId) : 
                                          null) : 
                                      null;
                              if (store == null)
                                  continue;

                              String availableCapacity = store.getProperty("VS_AVAILABLE_CAPACITY");
                              if (Long.parseLong(availableCapacity) > bytesToDownload) {
                                  this.setStorageId(storageId);
                                  if (evaluateRules()) {
                                      // this means that the fgs and free space requirements are acceptable at this point in time
                                    this.mStorageIdSet = true;
                                    continue;
                                }
                              }
                          } catch (RemoteException e) {
                              e.printStackTrace();
                          }
                      }

                      if (this.mStorageIdSet) {
                          if (bytesDownloaded > 0) {
                              // we are restarting from the beginning (it's so sad)
                              for (Package pkg : getPackages()) {
                                  pkg.setProgressBytes(0L);
                              }
                          }
                          return this.mStorageIdSet;
                      }
                  }
              }
          }
          return false;
      }
      
    
    boolean mStorageIdSet = false; 

    
    //=====================================================================================
    // Permissions related
    
   
     
    public String getUserPermissions() {
        return mProperties.get(TAG_USER);
    }

    public String getWorldPermissions() {
        return mProperties.get(TAG_WORLD);    
        }

    public String getGroupPermissions() {
        return mProperties.get(TAG_GROUP);    
    }

    public String[] getGroupProp() {
        return mProperties.get(TAG_GROUP_PROP).trim().split("\\s+");
    }
    
    private String setGroupProp(String property) {
        return mProperties.set(TAG_GROUP_PROP, property);        
    }

    private String setWorldPermissions(String property) {
        return mProperties.set(TAG_WORLD, property);        
        
    }

    private String setGroupPermissions(String property) {
        return mProperties.set(TAG_GROUP, property);        
        
    }

    String setUserPermissions(String property) {
        return mProperties.set(TAG_USER, property);        
        
    }

    // TODO usage for USER/group/world, values should relate to the spec
    public enum Permission
    {
           
        PERMISSION_READ(1),  PERMISSION_MODIFY(2), PERMISSION_DELETE(3);
       
        private final int code;
        
        private static final Map<String, Permission> mapVals = new HashMap<String, Permission>();
        static {
            for (Permission p : EnumSet.allOf(Permission.class))
                mapVals.put(p.toString(), p);
        }
        
        Permission(int code)
        {
            this.code = code;
        }
        
        public int getCode()
        {
            return this.code;
        }
        
        public String toString()
        {
            return this.name();
        }
        
        public static int mask(List<String> permissions)
        {
            int permissionMask = 0;
            for(String permission : permissions)
            {
                permissionMask |= Permission.get(permission).getCode();
            }
            
            return permissionMask;
        }
        
        public static Permission get(String name) { 
            return mapVals.get(name); 
       }
    }
    
    static final List<String> sIsModifiable = Arrays.asList(Permission.PERMISSION_MODIFY.name(),Permission.PERMISSION_DELETE.name());
    static final List<String> sIsReadable = Arrays.asList(Permission.PERMISSION_MODIFY.name(),Permission.PERMISSION_READ.name(),Permission.PERMISSION_DELETE.name());
    static final List<String> sIsDeletable = Arrays.asList(Permission.PERMISSION_DELETE.name());

    public boolean isReadableWorkOrder(String origin) {
        return isRelevantWorkOrder(sIsReadable,origin);
    }
    
    public boolean isDeletableWorkOrder(String origin) {
        return isRelevantWorkOrder(sIsDeletable,origin);
    }
    
    public boolean isModifiableWorkOrder(String origin) {
        return isRelevantWorkOrder(sIsModifiable,origin);
    }
    
    public boolean isRelevantWorkOrder(List<String> isRelevantPermission, String origin) {
        
        // TODO this needs to use the WorkOrder.Permsission
        if (getClientUid().equals(origin)) {
            if (getUserPermissions().length() > 0) {
                if (isRelevantPermission.contains(getUserPermissions())) {                   
                    return true;
                }
            } else {                
                return true;
            }
        } 
    
        if (getWorldPermissions().length() > 0) {
            if (isRelevantPermission.contains(getWorldPermissions())) {                
                return true;
            }                        
        } 
        
        if (getGroupPermissions().length() > 0) {
            if (isRelevantPermission.contains(getGroupPermissions())) {
                String[] gProps = getGroupProp();
                if (gProps != null) {
                    for (String gProp : gProps) {
                        if (gProp.equals(origin))
                            return true;
                    }
                }
            }
        }

        return false;
    }
}
