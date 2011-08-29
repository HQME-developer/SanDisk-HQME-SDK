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

package com.hqme.cm.sample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;

public class HqmeClientReceiver extends BroadcastReceiver {
    private static final String TAG = "DemoReceiver";

    private WeakReference<Handler> mHandler = new WeakReference<Handler>(HqmeClientActivity.getHandler());

    // ==================================================================================================================================
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // the demo manifest only registers for this intent
        if (action.equals(context.getPackageName() + ".QR_COMPLETED")) {
            final Handler handler = mHandler.get();

            if (handler != null) {
                Log.v(TAG, "Received intent: " + intent.toString());
                Log.v(TAG, "Received intent: " + intent.getAction());
                long queueRequestId = intent.getLongExtra("index", -1);
                int progressPercent = intent.getIntExtra("progressPercent", 0);
                String summaryStatus = intent.getStringExtra("summaryStatus");
                
                // Call the app-specific function to handle this event.
                updateStatus(queueRequestId, progressPercent, summaryStatus, handler);
            }

        }

    }

    public void updateStatus(long queueRequestId, int progressPercent, String progressStatus,
            Handler handler) {
        Message msg = handler.obtainMessage(HqmeClientActivity.NEW_CONTENT);
        Bundle statusBundle = new Bundle();
        statusBundle.putString("ProgressStatus", progressStatus);
        statusBundle.putLong("QueueRequestId", queueRequestId);
        statusBundle.putInt("ProgressPercent", progressPercent);
        msg.setData(statusBundle);
        handler.sendMessage(msg);
        msg = null;
    }

}
