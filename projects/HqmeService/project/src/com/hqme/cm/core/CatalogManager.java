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

import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmDate;
import com.hqme.cm.HqmeError;
import com.hqme.cm.IVSD;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

public class CatalogManager extends TimerTask {

    protected static TimerTask mTask = null;
    protected static Context mContext = null;
    final static String tag_Log = "CatalogManager";
    public static void scheduleCatalogClean(Context context) {
        mTask = new CatalogManager();
        mContext = context;
        Timer timer = new Timer();
        long interval = 1000 * 60 * 10;// 10 Minutes
        // long interval = 1000*10; //testing
        timer.schedule(mTask, 0, interval);
    }

    /**
     * Implements TimerTask's abstract run method.
     */
    public void run() {
                
        Long[] woids = HQME.WorkOrder.getRecordIds(mContext,
                new WorkOrder.Action[] {WorkOrder.Action.COMPLETED});
        if(woids != null)
            for(Long id : woids)
            {
                WorkOrder rec =  HQME.WorkOrder.getRecord(mContext, id);
                if(rec != null)
                    if (isExpired(rec)) 
                    {

                        if (cleanRecord( rec))
                            // finished deleting all the files
                            HQME.WorkOrder.delete(mContext, rec.getDbIndex());
                    }
            }
        
        //TODO implement this (missing context)
        
        
    }

    private boolean isExpired(WorkOrder item) {
        Date expiration = item.getExpiration();
        if (expiration != null) {
            Date now = new Date(System.currentTimeMillis());

            return expiration.compareTo(CmDate.EPOCH) == 0 ? false : now
                    .after(item.getExpiration());
        }
        return false;
    }

    private boolean cleanRecord(WorkOrder item) {
        boolean bRes = true;

        try {
            ArrayList<Package> packages = item.getPackages();
            int storeCount = WorkOrderManager.getContentProxy().VSDCount();
            if (storeCount > 0) {
                int[] storeIds = WorkOrderManager.getContentProxy().getStorageIds(null);
                if (storeIds != null && storeIds.length > 0) {
                    for (int storeId : storeIds) {
                        IVSD store = WorkOrderManager.getContentProxy().getStorage(storeId);
                        if (store != null) {

                            for (Package pack : packages) {
                                // get the content object associated with this
                                // workorder
                                // NOTE: if the VSD used for storage is not present, 
                                // we can not delete the content, but will return true here
                                if (store.getObject(pack.getSourceLocalPath()) != null)
                                    if (HqmeError.STATUS_SUCCESS.getCode() != store.removeObject(pack.getSourceLocalPath()))
                                        bRes = false;
                            }
                        }
                    }
                }
            }
        } catch (Exception exec) {
            CmClientUtil.debugLog(tag_Log, "cleanRecord", exec);
        }
        return bRes;
    }
}
