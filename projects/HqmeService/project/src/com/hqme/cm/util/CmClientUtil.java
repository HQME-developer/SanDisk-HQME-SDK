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

package com.hqme.cm.util;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public class CmClientUtil extends Activity {
    public static boolean sIsDebugMode = false;

    // ==================================================================================================================================
    private static final String sTag_Log = "CmClientUtil";

    // ==================================================================================================================================

    public static final PrintWriter DEBUG_LOG_WRITER = new PrintWriter(new cmDebugLogWriter());

    private static final class cmDebugLogWriter extends Writer {
        @Override
        public void close() throws IOException {
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void write(char[] data, int start, int length) throws IOException {
            if (sIsDebugMode) {
                String message = String.valueOf(data, start, length).replaceAll("\n", "")
                        .replaceAll("\r", "");
                if (message.length() > 0) {
                    Log.println(Log.INFO, sTag_Log, message);
                }
            }
        }
    }

    public static synchronized void debugLog(Class<?> methodOwner, String methodName,
            String format, Object... args) {
        if (sIsDebugMode)
            try {
                String owner = methodOwner == null || methodName == null ? "" : methodOwner
                        .getSimpleName()
                        + "." + methodName;
                String message = String.format(format, args); // could throw
                // NullPointerException
                // or
                // IllegalFormatException
                Log.i(owner, message);
            } catch (NoClassDefFoundError fault) {
                Log.e(sTag_Log + ".debugLog()", "NoClassDefFoundError", fault);
            } catch (SecurityException fault) {
                Log.e(sTag_Log + ".debugLog()", "SecurityException", fault);
            } catch (Throwable fault) {
                fault.printStackTrace(DEBUG_LOG_WRITER);
            }
    }

    public static synchronized void debugLog(Class<?> methodOwner, String methodName,
            Throwable fault) {
        debugLog(methodOwner, methodName, "%s", fault);
        fault.printStackTrace(DEBUG_LOG_WRITER);
    }

    public static synchronized void debugLog(Class<?> methodOwner, String methodName) {
        debugLog(methodOwner, methodName, "");
    }

    public static void debugLog(String format, Object... args) {
        debugLog(null, null, format, args);
    }

    public static void debugLog(String tag, String method, String message) {
        if (sIsDebugMode) {
            Log.d(tag + "." + method, message);
        }
    }
    
    public static void debugLog(String tag, String message) {
        if (sIsDebugMode) {
            Log.d(tag, message);
        }
    }
    
    // ==================================================================================================================================
    private static Handler sUiThreadHandler = null;

    private static Context sUiThreadContext = null;

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static synchronized void setServiceContext(Context context, Handler handler) {
        sUiThreadHandler = handler;
        sUiThreadContext = context;
        sIsDebugMode = (sUiThreadContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static Context getServiceContext() {
        return sUiThreadContext;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static Handler getServiceHandler() {
        return sUiThreadHandler;
    }

    // ==================================================================================================================================
    private static class CmMediaHandler extends BroadcastReceiver {
        private IntentFilter mFilter = null;

        // ----------------------------------------------------------------------------------------------------------------------------------
        @Override
        public void onReceive(Context context, Intent intent) {
            CmClientUtil.debugLog(getClass(), "onReceive", "context = %s : intent = %s", context,
                    intent);

            if (Intent.ACTION_MEDIA_EJECT.equals(intent.getAction()))
                Message.obtain(getServiceHandler(), Intent.ACTION_MEDIA_EJECT.hashCode())
                        .sendToTarget();
            else if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction()))
                Message.obtain(getServiceHandler(), Intent.ACTION_MEDIA_MOUNTED.hashCode())
                        .sendToTarget();

        }

        // ----------------------------------------------------------------------------------------------------------------------------------
        public void register() {
            if (mFilter == null) {
                mFilter = new IntentFilter();
                mFilter.addAction(Intent.ACTION_MEDIA_EJECT);
                mFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
                mFilter.addDataScheme("file");
                // mFilter.setPriority(500);
                CmClientUtil.getServiceContext().registerReceiver(this, mFilter);
            }
        }

        // ----------------------------------------------------------------------------------------------------------------------------------
        public void unregister() {
            if (mFilter != null) {
                CmClientUtil.getServiceContext().unregisterReceiver(this);
                mFilter = null;
            }
        }
        // ----------------------------------------------------------------------------------------------------------------------------------
    }

    // ==================================================================================================================================
    private static boolean sIsConnectingOrDisconnecting = false;

    private static final CmMediaHandler sClientMediaHandler = new CmMediaHandler();

    // ==================================================================================================================================
    public static boolean startServiceClient() // all this really does now is
    // register the broadcast
    // receiver
    {
        final String tag_LogLocal = "startServiceClient";
        CmClientUtil.debugLog(CmClientUtil.class, tag_LogLocal);

        if (sIsConnectingOrDisconnecting)
            return true;

        if (sUiThreadContext == null) {
            CmClientUtil.debugLog(CmClientUtil.class, tag_LogLocal + " @ sUiThreadContext == null");
            return false;
        }

        synchronized (sTag_Log) {
            try {
                sIsConnectingOrDisconnecting = true;
                sClientMediaHandler.register();

                return true;
            } catch (Exception fault) {
                CmClientUtil.debugLog(CmClientUtil.class, tag_LogLocal
                        + " @ sUiThreadContext.bindService", fault);
            }

            sIsConnectingOrDisconnecting = false;
        }

        return false;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static void stopServiceClient() // TODO not used
    {
        final String tag_LogLocal = "stopService";
        CmClientUtil.debugLog(CmClientUtil.class, tag_LogLocal);

        synchronized (sTag_Log) {
            try {
                sIsConnectingOrDisconnecting = true;
                sClientMediaHandler.unregister();
            } catch (Exception fault) {
                CmClientUtil.debugLog(CmClientUtil.class, tag_LogLocal, fault);
            } finally {
                sIsConnectingOrDisconnecting = false;
            }
        }
    }

    // ==================================================================================================================================
    private static final char[] sHex = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 0
    };

    private static final String sSep = System.getProperty("line.separator") == null
            || System.getProperty("line.separator") == "" ? "\n" : System
            .getProperty("line.separator");

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static String toFormattedHexString(byte[] data) {
        return data == null ? "" : toFormattedHexString(new StringBuffer(data.length << 2), data)
                .toString();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static StringBuffer toFormattedHexString(StringBuffer text, byte[] data) {
        if (data != null)
            for (int index = 0; index < data.length; index++) {
                if (index > 0)
                    if (index % 8 == 0)
                        if (index % 32 == 0)
                            text.append(sSep);
                        else
                            text.append("  ");
                    else
                        text.append(' ');

                text.append(sHex[((int) data[index] >>> 4) & 0x0f]).append(
                        sHex[(int) data[index] & 0x0f]);
            }
        return text;
    }
    // ==================================================================================================================================

}
