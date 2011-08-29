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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;

import com.hqme.cm.QueueRequestState;
import com.hqme.cm.ReqEvents.ReqEvent;
import com.hqme.cm.core.WorkOrder.Action;
import com.hqme.cm.core.WorkOrder.State;
import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmDate;
import com.hqme.cm.VSDEvent;
import com.hqme.cm.IVSD;
import com.hqme.cm.IStorageManager;
import com.hqme.cm.IStorageManagerCallback;
import com.hqme.cm.IQueueRequest;
import com.hqme.cm.HqmeError;
import com.hqme.cm.IRequestManager;
import com.hqme.cm.IRequestManagerCallback;
import com.hqme.cm.ReqEvents;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.PriorityBlockingQueue;

public class WorkOrderManager extends Service implements Runnable {
    // ==================================================================================================================================
    private static final String sTag_Log = WorkOrderManager.class.getName();

    protected static final int MAX_LOGIN_FAILURES = 4;

    protected static final int MAX_EXECUTE_FAILURES = 4;

    protected static final int CONTENT_STORE_MSG = 555;
    
    public static final String REQUEST_MANAGER_SERVICE_NAME = IRequestManager.class.getName(); // "com.sandisk.cm.IRequestManager";

    public static ComponentName sWorkOrderManagerComponentName = null;

    private static Thread sWorkOrderWorker = null;

    private static boolean sReloadedDatabaseRequests = false;
    
    private static boolean sWorkOrderWorkerIsActive = false;

    private static final String MANDATORY_START = "com.hqme.cm.core.MANDATORY_TIME_ALERT_START";
    private static final String MANDATORY_END = "com.hqme.cm.core.MANDATORY_TIME_ALERT_END";
    private static final String UPDATE_TIME = "com.hqme.cm.core.UPDATE_TIME";
    
    // store the available VSDs and their functiongroups
    static HashMap<Integer, ArrayList<Long>> sAvailableVSDs = new HashMap<Integer, ArrayList<Long>>();
    static Set<PendingIntent> sMandatoryAlerts = new HashSet<PendingIntent>();
    
    private static boolean sPriorityBasedInciteRequired = false;
    public static boolean isPriorityBasedInciteRequired() {
        return sPriorityBasedInciteRequired;
    }

    public static void setPriorityBasedInciteRequired(boolean priorityBasedInciteRequired) {
        sPriorityBasedInciteRequired = priorityBasedInciteRequired;
    }

    private static boolean sPrioritySaveRequired = false; 
    public static boolean isPrioritySaveRequired() {
        return sPrioritySaveRequired;
    }

    public static void setPrioritySaveRequired(boolean prioritySaveRequired) {
        sPrioritySaveRequired = prioritySaveRequired;
    }

    public static boolean isActive() {
        return sWorkOrderWorkerIsActive;
    }

    // interface to the ConnectedMemory IContentStorageManager service
    private static IStorageManager sPluginManager = null;

    private static WorkOrderManager sWorkOrderManagerInstance = null; // WorkOrderManager

    // Service instance when service is active; null when inactive
    public static WorkOrderManager getInstance() {
        return sWorkOrderManagerInstance;
    }

    public static IRequestManager getProxy() {
        return sWorkOrderManagerInstance == null ? null : sWorkOrderManagerInstance.requestManager;
    }
    
    public static IStorageManager getContentProxy() {
        return sWorkOrderManagerInstance == null ? null : sWorkOrderManagerInstance.sPluginManager;
    }

    public static RemoteCallbackList<IRequestManagerCallback> sClients = null;

    // ----------------------------------------------------------------------------------------------------------------------------------
    // ==================================================================================================================================
    // The IRemoteInterface for IRequestManager is defined through AIDL and
    // accessed via the static method WorkOrderManager.getProxy()
    //
    // The purpose of this interface is to provide a consistent API for use by
    // any application (including the hosting application itself)
    // to interface with WorkOrderManager functionality.
    //
    // The Work Order Manager [Service] maintains process priority elevation for
    // the duration of its lifetime, allowing its background sWorkOrderWorker
    // thread
    // to continue processing events reliably in the background, unattended...
    // for example, downloading new mContent from a remote server.
    //
    // The application is designed to receive Intents containing information
    // about the action to be performed and in many cases the data itself.
    // Processing of these intents is then performed within the sWorkOrderWorker
    // thread using an event-driven finite-state-machine model whose entry point
    // is the run() method below.
    //

    private final IRequestManager.Stub requestManager = new IRequestManager.Stub() {


        public IQueueRequest createQueueRequest() throws RemoteException {            
            QueueRequestObject obj = null;

            try {
                obj = new QueueRequestObject();
            } catch (Exception exec) {
                return null;
            }

            return obj;
            
        }
        
        public IQueueRequest createQueueRequestXml(String queueRequestXml) throws RemoteException {
            QueueRequestObject obj = null;

            try {
                obj = new QueueRequestObject(queueRequestXml);
            } catch (Exception exec) {
                return null;
            }

            return obj;

        }
        
        public long[] getRequestIdsState(int state) throws RemoteException {
            if (QueueRequestState.get(state) == null) 
                return new long[]{};
            
            synchronized (mPendingWorkOrders) {
                int uid = getCallingUid();

                HashSet<Long> existingIds = getReadableWorkOrderIds(getPackageManager().getNameForUid(uid));
                HashSet<Long> stateIds = new HashSet<Long>(0);

                for (long wo : existingIds) {
                    WorkOrder savedWO = findWorkOrder(wo);
                    if (savedWO.getQueueRequestState()
                            .state() == state)
                        stateIds.add(wo);
                }

                long[] queueRequestIdsArray = new long[stateIds.size()];
                if (stateIds.size() > 0) {
                    
                    int i = 0;

                    for (long woId : stateIds) {
                        queueRequestIdsArray[i] = woId;
                        i++;
                    }


                }
                return queueRequestIdsArray;

            }
        }

        public int requestCountState(int state) throws RemoteException {
            if (QueueRequestState.get(state) == null) 
                return 0;
                            
            synchronized (mPendingWorkOrders) {
                int uid = getCallingUid();

                HashSet<Long> existingIds = getReadableWorkOrderIds(getPackageManager().getNameForUid(uid));
                HashSet<Long> stateIds = new HashSet<Long>(0);

                for (long wo : existingIds) {
                    WorkOrder savedWO = findWorkOrder(wo);
                    if (state == savedWO.getQueueRequestState().state())
                        stateIds.add(wo);
                }

                return stateIds.size();
            }
        }

        public long[] getRequestIds() throws RemoteException {

            synchronized (mPendingWorkOrders) {
                int uid = getCallingUid();

                HashSet<Long> existingIds = getReadableWorkOrderIds(getPackageManager().getNameForUid(uid));
                long[] queueRequestIdsArray = new long[existingIds.size()];
                if (existingIds.size() > 0) {
             
                    int i = 0;

                    for (long woId : existingIds) {
                        queueRequestIdsArray[i] = woId;
                        i++;
                    }
                }
                return queueRequestIdsArray;

            }
        }

        public int requestCount() throws RemoteException {

            synchronized (mPendingWorkOrders) {
                int uid = getCallingUid();
                HashSet<Long> existingIds = getReadableWorkOrderIds(getPackageManager().getNameForUid(uid));
                return existingIds.size();
            }
        }

        public QueueRequestObject getRequest(long workOrderID) throws RemoteException {
            WorkOrder savedWO = findWorkOrder(workOrderID);
            
            if (savedWO != null)
                if (!savedWO.isReadableWorkOrder(getPackageManager().getNameForUid(getCallingUid())))                
                    return null;
            
            return (savedWO == null) ? null : savedWO.toQueueRequest();
        }

        // ==================================================
        // insert a QueueRequest into the work order queue
        // if the work order is urgent, incite the work order manager to
        // re-evaluate which work order it should be processing ASAP;
        // otherwise process this work order normally (that is, according to its
        // assigned rule-set + calculated priority among other work orders to be
        // processed)
        //
        // No progress updates will be passed back to the client application via
        // broadcasts if the notificationTarget has not been specified as one of
        // the
        // QueueRequest properties
        public long submitRequest(IQueueRequest queueRequest) throws RemoteException {
            return insertQueueRequest((QueueRequestObject)queueRequest, getCallingUid());
        }


        // --------------------------------------------------
        public int cancelRequest(long workOrderID) throws RemoteException {
            synchronized (mPendingWorkOrders) {
                WorkOrder order = findWorkOrder(workOrderID);
                if (order != null) {
                    if (!order.isDeletableWorkOrder(getPackageManager().getNameForUid(
                            getCallingUid())))
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();
                    else if (order.getOrderAction().ordinal() != WorkOrder.Action.COMPLETED
                            .ordinal()) {
                        if (!cancelingCurrentWorkOrder(workOrderID))
                            order.setOrderActionWithNotify(Action.CANCELING);
                    } else
                        return HqmeError.ERR_CANCEL_FAILED.getCode(); // request has already
                                                  // completed
                }
                
                return order != null ? HqmeError.STATUS_SUCCESS.getCode() : HqmeError.ERR_INVALID_ARGUMENT.getCode();
            }
        }

        private boolean cancelingCurrentWorkOrder(long workOrderID) {
            
            if (mCurrentWorkOrder == null || mPendingWorkOrders == null) 
                return false;
            
            synchronized (mCurrentWorkOrder) {
                long cbIndex = mCurrentWorkOrder.getDbIndex();
                if (workOrderID == cbIndex) {
                    cancelCurrentWorkOrder();
                    return true;
                }
            }
            return false;
        }

        // --------------------------------------------------
        public int suspendRequest(long workOrderID) throws RemoteException {

            synchronized (mPendingWorkOrders) {
                WorkOrder order = findWorkOrder(workOrderID);
                if (order != null) {
                    if (!order.isModifiableWorkOrder(getPackageManager().getNameForUid(
                            getCallingUid())))
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();
                    else if (order.getOrderAction().ordinal() < WorkOrder.Action.COMPLETED
                            .ordinal()) {
                        if (!suspendingCurrentWorkOrder(workOrderID))
                            order.setOrderActionWithNotify(Action.DISABLING);
                    } else 
                        return HqmeError.ERR_SUSPEND_FAILED.getCode(); // request may have already completed, or already be disabled
                }
                
                return order != null ? HqmeError.STATUS_SUCCESS.getCode() : HqmeError.ERR_INVALID_ARGUMENT.getCode();
            }

        }

        private boolean suspendingCurrentWorkOrder(long workOrderID) {
            
            if (mCurrentWorkOrder == null || mPendingWorkOrders == null) 
                return false;
            
            synchronized (mCurrentWorkOrder) {
                long cbIndex = mCurrentWorkOrder.getDbIndex();
                if (workOrderID == cbIndex) {
                    disableCurrentWorkOrder();
                    return true;
                }
            }
            return false;
        }

        // --------------------------------------------------
        public int resumeRequest(long workOrderID) throws RemoteException {

            synchronized (mPendingWorkOrders) {
                WorkOrder order = findWorkOrder(workOrderID);
                if (order != null) {
                    if (!order.isModifiableWorkOrder(getPackageManager().getNameForUid(
                            getCallingUid())))                
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();                     
                    // only resume requests that are in the disabling or disabled states
                    else if (order.getOrderAction().ordinal() > WorkOrder.Action.COMPLETED.ordinal()) {
                        order.setOrderActionWithNotify(Action.REENABLING);                        
                        restartCurrentWorkOrder(true);                        
                    } else 
                        return HqmeError.ERR_RESUME_FAILED.getCode(); // wasn't in a suspended state
                }
                
                return order != null ? HqmeError.STATUS_SUCCESS.getCode() :  HqmeError.ERR_INVALID_ARGUMENT.getCode();
            }
            
            
        }

        // ==================================================
        public int getProgress(long workOrderID) throws RemoteException // a
        // percentage
        {
            WorkOrder order = findWorkOrder(workOrderID);
            if (order != null) {
                if (!order.isReadableWorkOrder(getPackageManager().getNameForUid(
                        getCallingUid())))            
                    return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else
                return HqmeError.ERR_INVALID_ARGUMENT.getCode();

            return order.getProgressPercent() < 0 ? 0 : order.getProgressPercent();
        }

        // ==================================================
        public QueueRequestState getState(long workOrderID) throws RemoteException
        {
            WorkOrder order = findWorkOrder(workOrderID);
            if (order != null) 
                if (!order.isReadableWorkOrder(getPackageManager().getNameForUid(
                        getCallingUid())))            
                    return QueueRequestState.UNDEFINED;  
                else {
                    QueueRequestState qrs = order.getQueueRequestState();
                    return qrs == null ? QueueRequestState.UNDEFINED : qrs;
                }
            return QueueRequestState.UNDEFINED;
        }

        // ==================================================
        public int getPriority(long workOrderID) throws RemoteException 
        {
            WorkOrder order = findWorkOrder(workOrderID);
            if (order != null) {
                if (!order.isReadableWorkOrder(getPackageManager().getNameForUid(
                        getCallingUid())))            
                    return HqmeError.ERR_PERMISSION_DENIED.getCode();  
                else
                    try {
                        return order.getRelativePriority();
                    } catch (Exception exec) {
                        CmClientUtil.debugLog(getClass(), "getPriority()", exec);
                    }
            }
            return HqmeError.ERR_INVALID_ARGUMENT.getCode();
        }

        // ==================================================
        // This is an update of a work order that may already exist in the
        // database
        // by resetting a relative priority, the execution
        // order of multiple other workorders
        // may be affected
        public int setPriority(long workOrderID, int relativePriority) throws RemoteException {
            if (0 > relativePriority || relativePriority > 100)
                return HqmeError.ERR_GENERAL.getCode();

            synchronized (mPendingWorkOrders) {
                WorkOrder order = findWorkOrder(workOrderID);
                if (order != null)
                    if (!order.isModifiableWorkOrder(getPackageManager().getNameForUid(
                            getCallingUid())))
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();
                    else {
                        if (!modifyingCurrentWorkOrder(workOrderID, relativePriority)) {
                            order.setPriority(relativePriority);
                            HQME.WorkOrder.update(getApplicationContext(), order);
                        }

                        mInciteHysteresisTask.resume(order.getUrgent());
                    }

                return order != null ? relativePriority : HqmeError.ERR_INVALID_ARGUMENT.getCode();
            }
        }


        private boolean modifyingCurrentWorkOrder(long workOrderID, int relativePriority) {
            
            if (mCurrentWorkOrder == null || mPendingWorkOrders == null) 
                return false;
            
            synchronized (mCurrentWorkOrder) {
                long cbIndex = mCurrentWorkOrder.getDbIndex();
                if (workOrderID == cbIndex) {
                    suspendCurrentWorkOrder();
                    mCurrentWorkOrder.setPriority(relativePriority);
                    HQME.WorkOrder.update(getApplicationContext(),
                            mCurrentWorkOrder);
                    resumeCurrentWorkOrder();
                    return true;
                }
            }
            return false;
        }

        // ==================================================
        // insert a QueueRequest into the work order queue
        // if the work order is urgent, incite the work order manager to
        // re-evaluate which work order it should be processing ASAP;
        // otherwise process this work order normally (that is, according to its
        // assigned rule-set + calculated priority among other work orders to be
        // processed)
        // Progress updates will be passed back to the client application by
        // explicit intent using the component name provided
        private long insertQueueRequest(QueueRequestObject queueRequest, int uid) {

            if (queueRequest == null)
                return HqmeError.ERR_INVALID_ARGUMENT.getCode();

            // check validity of the object prior to insertion to the database
            int validity = queueRequest.isValid();
            if (validity != HqmeError.STATUS_SUCCESS.getCode())
                return validity;

            // always re-insert, creating a new QueueRequest - this
            // new QueueRequest discards perviously set transient properties
            // cancelling old QueueRequest is up to the client application
            return insertRequest(queueRequest,uid);

        }
            
        private long insertRequest(QueueRequestObject qro,int uid) {
            // this is an entirely new work order
            WorkOrder workOrder = null;

            synchronized (mPendingWorkOrders) {
                try {
                    // if the notification target is not null and passed as
                    // one
                    // of the QueuRequest properties,
                    // it is added to the workorder structure by this
                    // constructor

                    // this constructor creates a new qro with all but the transient properties
                    QueueRequestObject qroNew = new QueueRequestObject(qro);
                    qroNew.mProperties.set(QueueRequestProperties.TransientProperties.REQPROP_CALLING_UID
                            .name(), getPackageManager().getNameForUid(uid));
                    
                    workOrder = new WorkOrder(qroNew);
                    workOrder.setDbIndex(HQME.WorkOrder.insert(getApplicationContext(), workOrder));
                    // TODO need to change that when new WO class introduced
                    HQME.WorkOrder.update(getApplicationContext(), workOrder);
                    // DB_INTEGRATION
                    workOrder.setOrderAction(Action.NEW);

                    workOrder.calculateWorkOrderExecutionPriority();
                    mPendingWorkOrders.put(workOrder);
                    // inserting the work order using this task prevents
                    // excessive re-evaluation of work when several
                    // incite triggers occur in quick succession
                    mInciteHysteresisTask.resume(workOrder.getUrgent() || workOrder.getMandatory());

                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "insertQueueRequest", fault);
                }
            }

            return workOrder == null ? (long)HqmeError.ERR_GENERAL.getCode() : workOrder.getDbIndex();
        }

        // ==================================================
        public void registerCallback(IRequestManagerCallback callbackProxy) {
        	sClients.register(callbackProxy,getPackageManager().getNameForUid(getCallingUid()));
        }

        // --------------------------------------------------
        public void unregisterCallback(IRequestManagerCallback callbackProxy) {
            sClients.unregister(callbackProxy);
        }
    };

    // ----------------------------------------------------------------------------------------------------------------------------------

    protected static void notifyProgressUpdate(WorkOrder workOrder) {

        ReqEvents reqEvent = null;
        if (sClients != null) {
            Action woAction = workOrder.getOrderAction();

            ReqEvent rEvent = null;

            switch (woAction) {
                case REENABLING:
                    // This event occurs when a QueueRequest is resumed.
                    // The request is resumed explicitly by an application
                    rEvent = ReqEvent.REQUEST_RESUMED;
                    break;
                case DISABLING:
                case CANCELING:
                    // This event occurs when a QueueRequest is suspended.
                    // The request may be suspended explicitly by the
                    // application, or
                    // may occur as a result of an error condition
                    rEvent = ReqEvent.REQUEST_SUSPENDED;
                    break;
                case COMPLETED:
                    // This event occurs when an application?s QueueRequest has
                    // completed
                    rEvent = ReqEvent.REQUEST_COMPLETED;
                    break;
                case EXECUTING:
                    // issue this event only on the content object creation
                    // This event occurs when the a ContentObject has been
                    // created on the
                    // VSD for the content requested in the
                    // QueueRequest.
                    // This event is beneficial for applications intending to
                    // perform a Progressive QueueRequest
                    if (workOrder.getLastNotifyTime() == 0)
                        rEvent = ReqEvent.REQUEST_CONTENT_AVAILABLE;
                    break;
                default:
                    rEvent = ReqEvent.NULL_EVENT;
            }

            long index = workOrder.getDbIndex();
            int progressPercent = workOrder.getProgressPercent() < 0 ? 0 : workOrder
                    .getProgressPercent();
            String summaryStatus = workOrder.getSummaryStatus();
            reqEvent = new ReqEvents(rEvent, index, progressPercent, summaryStatus);
            try {
                WorkOrderManager workOrderManager = WorkOrderManager.getInstance();
                // TODO:
                // expensive
                // operation
                // for
                // debugging
                // purposes
                // only
                if (workOrderManager != null)
                    HQME.WorkOrder.update(workOrderManager.getApplicationContext(), workOrder);
            } catch (Exception fault) {
                CmClientUtil.debugLog(WorkOrderManager.class,
                        "notifyProgressUpdate @ workOrderManager.wo_db.updateRecord", fault);
            }
        }
        
        String woClientUid = workOrder.getClientUid();
        if (sClients != null && reqEvent != null)
            synchronized (sClients) {
                final int newClientCount = sClients.beginBroadcast();

                for (int clientIndex = 0; clientIndex < newClientCount; clientIndex++)
                    try {
                        if (woClientUid.equals(
                                (String) sClients.getBroadcastCookie(clientIndex))) {
                            sClients.getBroadcastItem(clientIndex).handleEvent(reqEvent);
                        }
                    } catch (Exception fault) {
                        CmClientUtil
                                .debugLog(
                                        WorkOrderManager.class,
                                        "notifyProgressUpdate @ sClients.getBroadcastItem(clientIndex).updateStatus",
                                        fault);
                    }
                sClients.finishBroadcast();
            }

    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected static void broadcastProgressUpdate(WorkOrder workOrder) {
        // Use the notification target of this work order to
        // issue an intent to the receiver that the calling application
        // specified
        if (workOrder.getNotificationTarget() != null) {
            try {
                Intent intent = new Intent(workOrder.getNotificationTarget());
                // what we wish to tell the third party app
                intent.putExtra("index", workOrder.getDbIndex());
                intent.putExtra("progressPercent", workOrder.getProgressPercent());
                intent.putExtra("summaryStatus", workOrder.getSummaryStatus());

                CmClientUtil.getServiceContext().sendBroadcast(intent);
            } catch (Exception fault) {
                CmClientUtil.debugLog(WorkOrderManager.class, "broadcastProgressUpdate", fault);
            }
        }
    }

    // ==================================================================================================================================
    // ==================================================================================================================================

    // protected DBHelper mWorkOrder_db = null;

    // private DBHelper mCatalog_db = null;

    // private DBHelper mRule_db = null;

    // top level rules
    protected WorkOrder mCurrentWorkOrder = null;

    protected final PriorityBlockingQueue<WorkOrder> mPendingWorkOrders = new PriorityBlockingQueue<WorkOrder>();

    protected InciteHysteresis mInciteHysteresisTask = new InciteHysteresis();

    protected class InciteHysteresis extends TimerTask {
        private Timer mTimer = null;

        public synchronized void resume(boolean isUrgent) {
            if (this.mTimer == null) // activate hysteresis mTimer only when its
                // inactive
                try {
                    this.mTimer = new Timer(sTag_Log + ".inciteHysteresisTaskTimer", true);
                    this.mTimer.schedule(mInciteHysteresisTask, isUrgent ? 1000 : 5 * 1000); // trigger
                    // in
                    // 1
                    // second
                    // if
                    // urgent;
                    // otherwise
                    // trigger
                    // in
                    // 5
                    // seconds
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "resume", fault);
                }
        }

        @Override
        public synchronized void run() {
            mInciteHysteresisTask = new InciteHysteresis(); // NOTE: instances
            // of
            // TimerTask cannot
            // be re-scheduled
            // once triggered due
            // to the way
            // TimerTask is
            // implemented
            resumeCurrentWorkOrder();
        }

        @Override
        public synchronized boolean cancel() {
            mInciteHysteresisTask = new InciteHysteresis(); // NOTE: instances
            // of
            // TimerTask cannot
            // be re-scheduled
            // once canceled due
            // to the way
            // TimerTask is
            // implemented
            return super.cancel();
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    // Runnable interface main entry point for sWorkOrderWorker background
    // thread.
    //
    private int mLoginAttemptNumber = 0;

    static boolean sStorageManagerIsActive = false;    

    private static final UncaughtExceptionHandler sUncaughtExceptionHandler = new UncaughtExceptionHandler() {
        public void uncaughtException(Thread thread, Throwable fault) {
            final String tag_LogLocal = "uncaughtException";

            CmClientUtil.debugLog(getClass(), tag_LogLocal, "Thread = %d (0x%08x) \"%s\"", thread
                    .getId(), thread.getId(), thread.getName());
            CmClientUtil.debugLog(getClass(), tag_LogLocal, fault);
        }
    };

    private boolean enqueueExecutionState(State state) {
        if (sWorkOrderWorkerIsActive)
            synchronized (mPendingWorkOrders) {
                mPendingWorkOrders.put(new WorkOrder(state));
                return true;
            }
        return false;
    }

    public void run() {
        Thread.setDefaultUncaughtExceptionHandler(sUncaughtExceptionHandler);

        synchronized (mPendingWorkOrders) {
            sWorkOrderWorkerIsActive = true;
            CmClientUtil.debugLog(getClass(), "run", "Starting up...");

            mLoginAttemptNumber = 0;
            enqueueExecutionState(State.LOGIN);

            mPendingWorkOrders.notifyAll();
        }
        /*
         * try { HQME.copyDataBase(getApplicationContext()); } catch
         * (IOException e) { // TODO Auto-generated catch block
         * e.printStackTrace(); }
         */
        
        CatalogManager.scheduleCatalogClean(getApplicationContext());
        
        while (sWorkOrderWorkerIsActive)
            try {
                mCurrentWorkOrder = mPendingWorkOrders.take();

                switch (mCurrentWorkOrder.getExecutionState()) {
                    // --------------------------------------------------
                    case PENDING:
                        mCurrentWorkOrder.setExecutionStateWithNotify(State.LOGIN);
                        // --------------------------------------------------
                        // NO BREAK
                        // --------------------------------------------------
                    case LOGIN:
                        /*
                         * if (mWorkOrder_db == null) { //DB_INTEGRATION
                         * CmClientUtil.startServiceClient(); // really only //
                         * registers for // connect // events/eject
                         * mWorkOrder_db = new DBHelper();
                         * mWorkOrder_db.open(DBHelper.DB_NAME.WORK_ORDER);
                         * mCatalog_db = new DBHelper();
                         * mCatalog_db.open(DBHelper.DB_NAME.CATALOG); mRule_db
                         * = new DBHelper();
                         * mRule_db.open(DBHelper.DB_NAME.RULE);
                         * CatalogManager.scheduleCatalogClean();
                         * createDefaultRules(); // creates // some
                         * RuleCollections }
                         */

                       
                        // end of DB_INTEGRATION
                        // --------------------------------------------------
                        // CONDITIONAL BREAK
                        // --------------------------------------------------

                    case EXECUTE:
                        try { 
                            mCurrentWorkOrder.processBegin(getApplication(),
                                    mPendingWorkOrders);
                            break;
                        } catch (InterruptedException fault) {
                            sWorkOrderWorkerIsActive = false;
                            CmClientUtil.debugLog(getClass(), "run @ case EXECUTE", fault);
                            // --------------------------------------------------
                            // NO BREAK
                            // --------------------------------------------------
                        } catch (Throwable fault) {
                            CmClientUtil.debugLog(getClass(), "run @ case EXECUTE", fault);
                            break;
                        } finally {
                            // in previous resort, setPriority has been called, but the database not updated, so do this here
                            synchronized (mPendingWorkOrders) {
                                if (mPendingWorkOrders.size() > 0) {
                                    if (isPrioritySaveRequired()) {
                                        for (WorkOrder order : mPendingWorkOrders) {
                                            if (order.getDbIndex() != -1)
                                                HQME.WorkOrder.update(getApplicationContext(),
                                                        order);
                                        }
                                        setPrioritySaveRequired(false);
                                    }
                                }
                            }

                            mCurrentWorkOrder = null;
                        }
                        // --------------------------------------------------
                        // NO BREAK
                        // --------------------------------------------------
                    case QUIT:
                        sWorkOrderWorkerIsActive = false;
                        // --------------------------------------------------
                        // NO BREAK
                        // --------------------------------------------------
                    case LOGOUT:
                        try {// DB_INTEGRATION
                            /*
                             * if (mWorkOrder_db != null) {
                             * mWorkOrder_db.close(); mWorkOrder_db = null; }
                             */
                        } catch (Exception fault) {
                            CmClientUtil.debugLog(getClass(), "run @ case LOGOUT", fault);
                        }
                        break;

                    // --------------------------------------------------
                    default:
                        break;
                }
            } catch (InterruptedException fault) {
                sWorkOrderWorkerIsActive = false;
                CmClientUtil.debugLog(getClass(), "run", fault);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "run", fault);
            } finally {
                synchronized (mPendingWorkOrders) {
                    if (!sWorkOrderWorkerIsActive) {
                        sWorkOrderWorker = null;
                        mPendingWorkOrders.clear();
                        
                       
                    }  else {
                        if (isPriorityBasedInciteRequired()) {
                            restartCurrentWorkOrder(false);
                            setPriorityBasedInciteRequired(false);
                        }
                    }
                    mPendingWorkOrders.notifyAll();                   
                }
            }
    }

 // ----------------------------------------------------------------------------------------------------------------------------------
    private class WorkOrderPriority implements Comparable<WorkOrderPriority> {
        public WorkOrderPriority(WorkOrder workOrder, int relativePriority) {
            super();
            mWorkOrder = workOrder;
            mRelativePriority = relativePriority;
        }


        public WorkOrder mWorkOrder;
        public int mRelativePriority;
        
        public int compareTo(WorkOrderPriority otherWorkOrderPriority) {
            int result = Integer.signum(otherWorkOrderPriority.mRelativePriority - mRelativePriority);
            return result == 0 ? Long.signum(this.mWorkOrder.getCreation().getTime()
                    - otherWorkOrderPriority.mWorkOrder.getCreation().getTime()) : result;
        }

    }
    

    // ----------------------------------------------------------------------------------------------------------------------------------
    // reload latest work orders from the data base, recalculate their
    // priorities, sort the list and resume processing
    //
    // if the head element in workOrdersToSort is not mCurrentWorkOrder, stop
    // the
    // current work order, then
    // pop and resume the highest-priority work order; otherwise just ensure the
    // current work order is running
    //
    protected void calculateWorkOrderPriorities(boolean suspend) {
        synchronized (mPendingWorkOrders) {
            PriorityBlockingQueue<WorkOrder> sortedWorkOrders = new PriorityBlockingQueue<WorkOrder>();
            PriorityBlockingQueue<WorkOrder> currentWorkOrders = new PriorityBlockingQueue<WorkOrder>();
            PriorityBlockingQueue<WorkOrder> priorityWorkOrders = new PriorityBlockingQueue<WorkOrder>();

            HashMap<String, ArrayList<WorkOrderPriority>> uuidPriorityMaps = new HashMap<String, ArrayList<WorkOrderPriority>>();
            HashMap<String, ArrayList<CmDate>> uuidPriorityTimeMaps = new HashMap<String, ArrayList<CmDate>>();

            for (WorkOrder workOrder : mPendingWorkOrders) {
                try {
                    String uid = workOrder.getClientUid();

                    if (workOrder.getRelativePriority() == 0 || "".equals(uid) || workOrder.getOrderAction().ordinal() >= Action.COMPLETED.ordinal()) {
                        workOrder.calculateWorkOrderExecutionPriority();
                        sortedWorkOrders.put(workOrder);
                        currentWorkOrders.put(workOrder);
                    } else {
                        currentWorkOrders.put(workOrder);
                        priorityWorkOrders.put(workOrder);
                    }
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "calculateWorkOrderPriorities @ first loop",
                            fault);

                }
            }

            Long[] woids = HQME.WorkOrder.getRecordIds(getApplicationContext(),  
                                        HQME.WorkOrder.non_completed_filter);              


            if (woids != null)
                for (Long id : woids) {
                    WorkOrder workOrder = HQME.WorkOrder.getRecord(getApplicationContext(), id);
                    if (workOrder != null)
                        try {
                            String uid = workOrder.getClientUid();

                            if (Action.COMPLETED.equals(workOrder.getOrderAction()))
                                continue;

                            if (isWorkOrderInQueue(workOrder, currentWorkOrders))
                                continue;

                            // here, set Mandatory time alerts when necessary,
                            // and set priority accordingly
                            workOrder.setMandatory(workOrder
                                    .issueMandatoryTimeAlerts(workOrder.mProperties
                                            .get(QueueRequestObject.TAG_POLICY)));

                            if (workOrder.getRelativePriority() == 0
                                    || "".equals(uid)
                                    || workOrder.getOrderAction().ordinal() > Action.COMPLETED
                                            .ordinal()) {
                                workOrder.calculateWorkOrderExecutionPriority();
                                sortedWorkOrders.put(workOrder);
                            } else {
                                priorityWorkOrders.put(workOrder);
                            }

                        } catch (Exception fault) {
                            CmClientUtil.debugLog(getClass(),
                                    "calculateWorkOrderPriorities @ second loop", fault);

                        }
                }
                    
            for (WorkOrder workOrder : priorityWorkOrders) {
                String uid = workOrder.getClientUid();

                if (uuidPriorityMaps.get(uid) == null) {
                    ArrayList<CmDate> sortedDates = new ArrayList<CmDate>();
                    ArrayList<WorkOrderPriority> relativePrioritySortedWos = new ArrayList<WorkOrderPriority>();
                    relativePrioritySortedWos.add(new WorkOrderPriority(workOrder, workOrder
                            .getRelativePriority()));
                    sortedDates.add(workOrder.getPriorityTime());
                    uuidPriorityTimeMaps.put(uid, sortedDates);
                    uuidPriorityMaps.put(uid, relativePrioritySortedWos);
                } else {
                    ArrayList<CmDate> sortedDates = uuidPriorityTimeMaps.get(uid);
                    ArrayList<WorkOrderPriority> relativePrioritySortedWos = uuidPriorityMaps
                            .get(uid);
                    sortedDates.add(workOrder.getPriorityTime());
                    relativePrioritySortedWos.add(new WorkOrderPriority(workOrder, workOrder
                            .getRelativePriority()));
                    uuidPriorityTimeMaps.put(uid, sortedDates);
                    uuidPriorityMaps.put(uid, relativePrioritySortedWos);
                }

            }

            // now get all the work orders that are in the maps, set the
            // priorityTime of each
            // to be the value of the appropriate prioritytime and calculate a
            // new execution priority
            // based on that
            int j=0;
            for (Entry<String, ArrayList<WorkOrderPriority>> uidWoPriority : uuidPriorityMaps
                    .entrySet()) {
                ArrayList<CmDate> uidWoPriorityTime = uuidPriorityTimeMaps.get(uidWoPriority
                        .getKey());
                Collections.sort(uidWoPriorityTime);
                ArrayList<WorkOrderPriority> uidWoPriorities = uidWoPriority.getValue();
                Collections.sort(uidWoPriorities);                
                CmDate[] priorityTimes = new CmDate[uidWoPriorityTime.size()];
                uidWoPriorityTime.toArray(priorityTimes);
                int i = 0;
                for (WorkOrderPriority woPriority : uidWoPriorities) {
                    woPriority.mWorkOrder.setPriorityTime(priorityTimes[i]);
                    woPriority.mWorkOrder.calculateWorkOrderExecutionPriority();                    
                    sortedWorkOrders.put(woPriority.mWorkOrder);
                    i++;
                }
                j += i;
            }

            if (j > 0)
                setPrioritySaveRequired(true);
            // check to see if the current work order is the same as the head
            // entry in workOrderQueue
            // suspend the current work order when a [new] higher priority work
            // order is encountered
            //
            if (suspend && isCurrentWorkOrderSuspendSuggested(sortedWorkOrders))
                this.suspendCurrentWorkOrder();

            // efficiently replace the existing work order queue with the
            // reconstructed queue
            mPendingWorkOrders.clear();
            sortedWorkOrders.drainTo(mPendingWorkOrders); // more efficient
            // version of
            // "for (WorkOrder order : sortedWorkOrders) mPendingWorkOrders.put(order);"
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    private boolean isCurrentWorkOrderSuspendSuggested(
            PriorityBlockingQueue<WorkOrder> workOrderQueue) {
        if (mCurrentWorkOrder == null || workOrderQueue == null)
            return false;

        synchronized (mCurrentWorkOrder) {
            //  suspend *may* be needed, but only if the current WorkOrder is still executing
            if (!Action.EXECUTING.equals(mCurrentWorkOrder.getOrderAction()))
                return false;

            if (!mCurrentWorkOrder.evaluateRules())
                return true;

            if (workOrderQueue.size() == 0)
                return false;

            // the work order is still meant to be executing, and the queue is not empty apart from the current workOrder
            long cbIndex = mCurrentWorkOrder.getDbIndex();
            WorkOrder headWo = workOrderQueue.peek();
            long dbIndex = headWo.getDbIndex();
            if (dbIndex == cbIndex)  // top of the queue is the current wo
                return false;
            
            // if the current work order has higher priority than the top of the
            // queue
            if (mCurrentWorkOrder.compareTo(headWo) <= 0) {
                if (!mCurrentWorkOrder.getClientUid().equals(headWo.getClientUid())
                        || mCurrentWorkOrder.getRelativePriority() == 0) {
                    // the work orders are from different origins or currently
                    // executing work order does not have a relative priority set
                    return false;
                } else {
                    if (mCurrentWorkOrder.getRelativePriority() >= headWo
                            .getRelativePriority())
                        // the work orders are from the same origin and the
                        // current work order has equal or greater relative
                        // priority
                        return false;
                }
            }
            
            return true;
        }
    }

    // ==================================================================================================================================
    protected void resumeCurrentWorkOrder() {
        synchronized (mPendingWorkOrders) {
            try {
                if (sWorkOrderWorker == null) {
                    sWorkOrderWorker = new Thread(this, getClass().getName() + ".workOrderWorker");
                    sWorkOrderWorker.setPriority(Thread.MIN_PRIORITY);
                    sWorkOrderWorker.start();
                    mPendingWorkOrders.wait();
                } else
                    calculateWorkOrderPriorities(true);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "resumeCurrentWorkOrder", fault);
            }
        }
    }

    // this function is used where a client application deliberately resumes a request that was previously
    // disabled 
    protected void restartCurrentWorkOrder(boolean suspend) {
        synchronized (mPendingWorkOrders) {
            try {
                if (sWorkOrderWorker == null) {
                    sWorkOrderWorker = new Thread(this, getClass().getName() + ".workOrderWorker");
                    sWorkOrderWorker.setPriority(Thread.MIN_PRIORITY);
                    sWorkOrderWorker.start();
                    mPendingWorkOrders.wait();
                    calculateWorkOrderPriorities(suspend);
                } else
                    calculateWorkOrderPriorities(suspend);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "resumeCurrentWorkOrder", fault);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void suspendCurrentWorkOrder(boolean abort) {
        synchronized (mPendingWorkOrders) {
            if (sWorkOrderWorkerIsActive && mCurrentWorkOrder != null)
                try {
                    if (abort)
                        mCurrentWorkOrder.cancel();
                    else
                        mCurrentWorkOrder.suspend();
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "suspendCurrentWorkOrder("
                            + (abort ? "abort" : "save") + ")", fault);
                }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void disableCurrentWorkOrder() {
        synchronized (mPendingWorkOrders) {
            if (sWorkOrderWorkerIsActive && mCurrentWorkOrder != null)
                try {                    
                   mCurrentWorkOrder.disable();
                } catch (Exception fault) {
                    CmClientUtil.debugLog(getClass(), "disableCurrentWorkOrder("
                            + ("save") + ")", fault);
                }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void suspendCurrentWorkOrder() {
        this.suspendCurrentWorkOrder(false);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    protected void cancelCurrentWorkOrder() {
        this.suspendCurrentWorkOrder(true);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    // search the current work order and pending work orders queue for the
    // specified work order (their workOrderIndex values must match)
    //
    private boolean isWorkOrderInQueue(WorkOrder workOrder,
            PriorityBlockingQueue<WorkOrder> workOrderQueue) {
        synchronized (mPendingWorkOrders) {
            long workOrderIndex = workOrder.getDbIndex();

            if (mCurrentWorkOrder != null && mCurrentWorkOrder.getDbIndex() == workOrderIndex)
                return true;

            for (WorkOrder order : workOrderQueue)
                if (order.getDbIndex() == workOrderIndex)
                    return true;

            return false;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    // search current work order, pending work orders queue, and work order data
    // base for a work order with the specified workOrderIndex
    //
    public WorkOrder findWorkOrder(long workOrderIndex) {
        synchronized (mPendingWorkOrders) {
            if (mCurrentWorkOrder != null && mCurrentWorkOrder.getDbIndex() == workOrderIndex)
                return mCurrentWorkOrder;

            for (WorkOrder workOrder : mPendingWorkOrders)
                if (workOrder.getDbIndex() == workOrderIndex)
                    return workOrder;

            // DB_INTEGRATION
            /*
             * return mWorkOrder_db == null ? null : (WorkOrder) mWorkOrder_db
             * .getRecord(workOrderIndex);
             */
            return HQME.WorkOrder.getRecord(getApplicationContext(), workOrderIndex);

            // end DB_INTEGRATION
        }
    }
    
     
    // ----------------------------------------------------------------------------------------------------------------------------------
    // search current work order, pending work orders queue, and work order data
    // base for workOrderIds that are visible/modifiable/deletable by the client application
    // valid permission strings: "HQME_PERMISSION_READ","HQME_PERMISSION_DELETE","HQME_PERMISSION_MODIFY"
    // Note: owner client applications may restrict even their own ability to delete etc. using these permissions    
    public HashSet<Long> getRelevantWorkOrderIds(List<String> permissions, String groupProp) {
        synchronized (mPendingWorkOrders) {
            HashSet<Long> existingIds = new HashSet<Long>();
            for (WorkOrder workOrder : mPendingWorkOrders) {
                // user
                if (workOrder.isRelevantWorkOrder(permissions,groupProp))
                    existingIds.add(workOrder.getDbIndex());
            }

            // TODO extend permissions implementation in DB
            //String selection = "`" + HQME.WorkOrder.APP_UUID+"`" + " like '" + groupProp +"'";
            // permissions are not insertet by WO and `"+HQME.WorkOrder.PERMISSIONS+"` like '"+Permission.mask(permissions) + "'";            
            //Long[] woids = HQME.WorkOrder.getRecordIds(getApplicationContext(), selection);//still missing permission 
            
            String selection = null;  // just get all the records for now
            Long[] woids = HQME.WorkOrder.getRecordIds(getApplicationContext(), selection); 
            
            if (woids != null) {
                for (int i = 0; i < woids.length; i++) {
                    if (woids[i] != null) {
                        if (HQME.WorkOrder.getRecord(getApplicationContext(), woids[i])
                                .isRelevantWorkOrder(permissions, groupProp))
                            existingIds.add(woids[i]);
                    }
                }
            }

            return existingIds;
        }
    }

    public HashSet<Long> getReadableWorkOrderIds(String groupProp) {
        return getRelevantWorkOrderIds(WorkOrder.sIsReadable,groupProp);       
    }
    
    public HashSet<Long> getDeletableWorkOrderIds(String groupProp) {
        return getRelevantWorkOrderIds(WorkOrder.sIsDeletable,groupProp);
    }
    
    public HashSet<Long> getModifiableWorkOrderIds(String groupProp) {
        return getRelevantWorkOrderIds(WorkOrder.sIsModifiable,groupProp);        
    }
 // ----------------------------------------------------------------------------------------------------------------------------------
    // check the pending work orders queue, and the database to see if any work orders
    // with the same uid, that are currently executable have a higher priority    
    public boolean isHighestPriorityExecutableRequest(int relativePriority, String uid) {
        synchronized (mPendingWorkOrders) {
            for (WorkOrder workOrder : mPendingWorkOrders) {
                int priority = workOrder.getRelativePriority();
                if (workOrder.getClientUid().equals(uid) && (priority > 0 && priority <=100))
                    if (priority > relativePriority
                            && workOrder.evaluateRules() && workOrder.getOrderAction().ordinal() < Action.COMPLETED.ordinal())
                        return false;
            }

            Long[] woids = HQME.WorkOrder.getRecordIds(getApplicationContext(), 
                    HQME.WorkOrder.non_completed_filter);

            if(woids != null) 
                for(Long id : woids) {
                    WorkOrder workOrder =  HQME.WorkOrder.getRecord(getApplicationContext(), id); 
                    if(workOrder != null) {
                        int priority = workOrder.getRelativePriority();
                        if (workOrder.getClientUid().equals(uid)
                                && (priority > 0 && priority <= 100))
                            if (priority > relativePriority
                                    && workOrder.evaluateRules() && workOrder.getOrderAction().ordinal() < Action.COMPLETED
                                            .ordinal())
                                return false;
                    }
                }

            return true;
        }
    }

    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
                
        // if there is already a current work order executing, don't call restartCurrentWorkOrder,
        // queue may proceed as normal
        if (sReloadedDatabaseRequests == false) {
            // calling this command here will cause any non-completed work orders to 
            // added to the mPendingWorkOrders queue and downloaded if not blocked
            try {
                mReloadWorkOrders.execute();
                sReloadedDatabaseRequests = true;
            } catch (IllegalStateException exec) {
                CmClientUtil.debugLog(getClass(), "onStartCommand()", exec);
            }
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    
    protected RestartNonCompletedWorkOrders mReloadWorkOrders = new RestartNonCompletedWorkOrders();
    
    private class RestartNonCompletedWorkOrders extends AsyncTask<Void, Void, Void> {       

        @Override
        protected Void doInBackground(Void... arg0) {
            restartCurrentWorkOrder(true);
            return null;        
        }           
    }

    
    // ==================================================================================================================================
    // ==================================================================================================================================
    @Override
    public void onCreate() {
        super.onCreate();
        CmClientUtil.debugLog(getClass(), "onCreate");

        sWorkOrderManagerInstance = this;
        sWorkOrderWorkerIsActive = false;

        sClients = new RemoteCallbackList<IRequestManagerCallback>();

        // to do periodically set an alarm to awake the app in case some time schedule based QueueRequests
        // have not become possible to execute
        broadcastRepeatingIntent();        
        
        // TODO: what to do in the instance that we can not bind to the storage manager
        bindStorageManager();
     
        CmClientUtil.setServiceContext(getApplicationContext(), new Handler() {
            private static final String sTag_StorageManagerMessage = "StorageManagerMessage";
            
            // instead here, we would want a message from the receiver
            public void handleMessage(Message msg) {
                if (msg != null) {
                    switch (msg.what) {
                        case CONTENT_STORE_MSG:
                            // from StorageManager
                            if (msg.arg1 == VSDEvent.VSD_REMOVED.getCode()) {
                                synchronized (sAvailableVSDs) {
                                    Integer Id = msg.arg2;
                                    sAvailableVSDs.remove(Id);
                                    if (mCurrentWorkOrder != null) {
                                        synchronized (mCurrentWorkOrder) {
                                            if (Action.EXECUTING.equals(mCurrentWorkOrder
                                                    .getOrderAction())) {
                                                if (mCurrentWorkOrder.getStorageId() == msg.arg2)
                                                    suspendCurrentWorkOrder();
                                            }
                                        }
                                    }
                                    restartCurrentWorkOrder(false);
                                }
                            } else if (msg.arg1 == VSDEvent.VSD_ADDED.getCode()) {
                                synchronized (sAvailableVSDs) {
                                    Integer Id = msg.arg2;
                                    try {
                                        IVSD store = WorkOrderManager.getContentProxy() != null ? 
                                                (WorkOrderManager.getContentProxy().VSDCount() > 0 ? 
                                                        WorkOrderManager.getContentProxy().getStorage(Id) : 
                                                        null) : 
                                                null;
                                        if (store != null) {
                                            ArrayList<Long> fgLong = new ArrayList<Long>();
                                            if (store.functionGroups()!= null) {
                                                for (Long fg : store.functionGroups()) {
                                                    fgLong.add(fg);
                                                }
                                            }
                                            sAvailableVSDs.put(Id, fgLong);
                                        }
                                    } catch (Exception fault) {
                                    }
                                }
                                mInciteHysteresisTask.resume(true);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        });

        RULE_POWER_LEVEL.getInstance().register();
        RULE_POWER_LEVEL.getInstance().init(CmClientUtil.getServiceContext());
        RULE_CONNECTION_TYPE.getInstance().init(CmClientUtil.getServiceContext());

    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    private ServiceConnection mPluginManagerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            sPluginManager = IStorageManager.Stub.asInterface(service);
            sStorageManagerIsActive = true;
            
            synchronized (WorkOrderManager.sAvailableVSDs) {
                WorkOrderManager.sAvailableVSDs.clear();                  
                setAvailableVSDs();
            }
                
            try {
                sPluginManager.registerCallback(mStorageManagerCallback);
            } catch (RemoteException fault) {
                CmClientUtil.debugLog(getClass(), "onServiceConnected", fault);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            synchronized (WorkOrderManager.sAvailableVSDs) {
                WorkOrderManager.sAvailableVSDs.clear();        
            }
            sPluginManager = null;
            sStorageManagerIsActive = false;
        }
    };

    /**
     * This implementation is used to receive callbacks from the remote service.
     */
    private static IStorageManagerCallback mStorageManagerCallback = new IStorageManagerCallback.Stub() {
        public void handleEvent(int eventCode, int storageID) {
            CmClientUtil.getServiceHandler().sendMessage(CmClientUtil.getServiceHandler().obtainMessage(CONTENT_STORE_MSG, eventCode, storageID));
        }
    };
    // ----------------------------------------------------------------------------------------------------------------------------------
    @Override
    public IBinder onBind(Intent intent) {
        try {
            startWorkOrderManager(CmClientUtil.getServiceContext());
        } catch (Exception exec) {
            CmClientUtil.debugLog(getClass(), "onBind()", exec);
        }
        
        if (REQUEST_MANAGER_SERVICE_NAME.equals(intent.getAction())) {
            if (sReloadedDatabaseRequests == false) {
                // calling this command here will cause any non-completed work orders to 
                // added to the mPendingWorkOrders queue and downloaded if not blocked
                try {
                    mReloadWorkOrders.execute();
                    sReloadedDatabaseRequests = true;
                } catch (IllegalStateException exec) {
                    CmClientUtil.debugLog(getClass(), "onBind()", exec);
                }
            }
            return requestManager;
        }

        return null;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    @Override
    public boolean onUnbind(Intent intent) {
        CmClientUtil.debugLog(getClass(), "onUnbind", "Intent = %s", intent);

        // TODO: determine if we want to auto-stop the work order manager
        // service, for example, on a specific "terminate" Intent perhaps?
        //
        // if (WORK_ORDER_MANAGER_SERVICE_NAME.equals(intent.getAction())) //
        // for example,
        // when the last client has disconnected; this service is auto-closing
        // stopSelf();
        return true;  // rebind later
    }
    
    // ----------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onRebind(Intent intent) {
        CmClientUtil.debugLog(getClass(), "onRebind", "Intent = %s", intent);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void onDestroy() {
        try {
            RULE_POWER_LEVEL.getInstance().unregister();

            ConnectivityManager cm = (ConnectivityManager) CmClientUtil.getServiceContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            synchronized (mPendingWorkOrders) {
                cancelRepeatingIntent();
                cancelMandatoryTimeAlert();
                suspendCurrentWorkOrder();

                if (enqueueExecutionState(State.QUIT))
                    mPendingWorkOrders.wait();
            }
            
            // unbind from the StorageManager
            if (mPluginManagerConnection != null)
                unbindService(mPluginManagerConnection);
            // Unregister all callbacks.
            sClients.kill();
            CmClientUtil.getServiceHandler().removeMessages(CONTENT_STORE_MSG);
            
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "onDestroy", fault);
        } finally {
            sWorkOrderManagerComponentName = null;
            sWorkOrderManagerInstance = null;

            super.onDestroy();
        }
    }

    // ==================================================================================================================================
    // ==================================================================================================================================
    public static boolean startWorkOrderManager(Context context) {
        CmClientUtil.debugLog(WorkOrderManager.class, "startWorkOrderManager",
                "sWorkOrderManagerComponentName = %s : context = %s",
                sWorkOrderManagerComponentName, context);

        synchronized (REQUEST_MANAGER_SERVICE_NAME) {
            if (context != null)
                try {
                    sWorkOrderManagerComponentName = context.startService(new Intent(
                            WorkOrderManager.REQUEST_MANAGER_SERVICE_NAME));
                } catch (Exception fault) {
                    CmClientUtil.debugLog(WorkOrderManager.class, "startWorkOrderManager", fault);
                }
        }
        return sWorkOrderManagerComponentName != null;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static void stopWorkOrderManager() {
        Context context = CmClientUtil.getServiceContext();
        CmClientUtil.debugLog(WorkOrderManager.class, "stopWorkOrderManager",
                "sWorkOrderManagerComponentName = %s : context = %s",
                sWorkOrderManagerComponentName, context);

        synchronized (REQUEST_MANAGER_SERVICE_NAME) {
            if (context != null)
                if (context.stopService(new Intent(WorkOrderManager.REQUEST_MANAGER_SERVICE_NAME)))
                    sWorkOrderManagerComponentName = null;
        }
    }

    // ==================================================================================================================================
    private void broadcastRepeatingIntent() {
        Intent intent = new Intent(UPDATE_TIME, null, WorkOrderManager.this,
                RULE_TIME.class);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);

        long firstTime = SystemClock.elapsedRealtime();
        // 30 seconds since boot
        firstTime += 30 * 1000;

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        // every 15 minutes for testing - inexact to conserve battery life
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, sender);
    };

    // ----------------------------------------------------------------------------------------------------------------------------------
    private void cancelRepeatingIntent() {
        Intent intent = new Intent(UPDATE_TIME, null, WorkOrderManager.this,
                RULE_TIME.class);
        PendingIntent sender = PendingIntent.getBroadcast(this, 0, intent, 0);

        // And cancel the alarm.
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.cancel(sender);
    }
   
    // ==================================================================================================================================
    protected boolean broadcastMandatoryTimeAlert(long broadcastTime, long endTime, int i,long woid) {
        // Make some unique Uri for each end of the time period (duplicate alarms set for 
        // same intent get overwritten)
        Uri intentUri
                = Uri.parse("hqme://" + getApplicationInfo().className + "/" + woid + ";" + i);

        // if the window has passed, do not issue alarms for this
        Date now = new Date(System.currentTimeMillis());
        if (now.getTime() < endTime) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(MANDATORY_END, intentUri,
                    WorkOrderManager.this, RULE_MANDATORY_TIME.class);
            PendingIntent endPI = PendingIntent.getBroadcast(this, 0, intent, 0);            
            am.set(AlarmManager.RTC_WAKEUP, endTime, endPI);
            sMandatoryAlerts.add(endPI);
            if (now.getTime() < broadcastTime) {
                Intent startIntent = new Intent(MANDATORY_START, intentUri,
                        WorkOrderManager.this, RULE_MANDATORY_TIME.class);
                PendingIntent startPI = PendingIntent.getBroadcast(this, 0, startIntent, 0);
                am.set(AlarmManager.RTC_WAKEUP, broadcastTime, startPI);
                sMandatoryAlerts.add(startPI);
            }
            return true;
        } else         
            return false;
    };

    // ----------------------------------------------------------------------------------------------------------------------------------
    private void cancelMandatoryTimeAlert() {               
        // And cancel the alarms.
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        for (PendingIntent pi : sMandatoryAlerts) 
            am.cancel(pi);
        
    }
    // ----------------------------------------------------------------------------------------------------------------------------------
    protected boolean bindStorageManager() {
        // Start VSD storage manager
        boolean connected = true;
        if (sStorageManagerIsActive == false)
            connected = bindService(new Intent(IStorageManager.class.getName()), 
                    mPluginManagerConnection, Context.BIND_AUTO_CREATE);
        
        return connected;
    }
    // ==================================================================================================================================
    
    static void setAvailableVSDs() {
        synchronized (sAvailableVSDs) {
            // check the StorageManager
            try {
                int[] Ids = sPluginManager.VSDCount() > 0 ? sPluginManager.getStorageIds(null) : null;
                for (int Id : Ids) {
                    long[] fgs = sPluginManager.getFunctionGroups(Id);
                    if (fgs != null) {
                        ArrayList<Long> functionGroups = new ArrayList<Long>();
                        for (long fg : fgs)
                            functionGroups.add(fg);
                        WorkOrderManager.sAvailableVSDs.put(Id, functionGroups);
                    } else
                        WorkOrderManager.sAvailableVSDs.put(Id, null);
                }
            } catch (Exception exec) {
                CmClientUtil.debugLog(sTag_Log, "setAvailableVSDs: " + exec);
            }
        }

    }
}
