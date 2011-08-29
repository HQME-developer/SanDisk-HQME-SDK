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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.VideoView;

import com.hqme.cm.IContentObject;
import com.hqme.cm.IQueueRequest;
import com.hqme.cm.IRequestManager;
import com.hqme.cm.IRequestManagerCallback;
import com.hqme.cm.IStorageManager;
import com.hqme.cm.IStorageManagerCallback;
import com.hqme.cm.IVSD;
import com.hqme.cm.ReqEvents;
import com.hqme.cm.VSDEvent;
import com.hqme.cm.sample.R;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class HqmeClientActivity extends Activity {

    private static final String sTag = "HqmeClientActivity";

    protected static final int QUEUE_REQUEST_MGR_CONNECTED = 111;

    protected static final int QUEUE_REQUEST_ID_MSG = 222;

    protected static final int NEW_CONTENT = 333;

    protected static final int REQ_CALLBACK_MSG = 444;

    protected static final int CONTENT_STORE_MSG = 555;

    // interface to the IRequestManager service
    protected static IRequestManager sRequestManagerProxy = null;

    // interface to the IStorageManager service
    protected static IStorageManager sStorageManagerProxy = null;

    private static boolean sIsActive_RequestManager = false;

    private static boolean sIsActive_ContentManager = false;

    // ==================================================================================================================================

    static VideoView sVideoView;

    static boolean sIsPaused = false;

    Button btnSubmit;
    RadioButton radioWiFiOnly;
    RadioButton radioWiFiAnd3G;
    EditText editURI;

    private static Random sRandomGenerator = new Random();

    // ----------------------------------------------------------------------------------------------------------------------------------

    protected static IRequestManager getProxy() {
        return sRequestManagerProxy;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    protected static IStorageManager getPluginProxy() {
        return sStorageManagerProxy;
    }

    // ==================================================================================================================================
    private static Handler uiThreadHandler = null;

    private static Context uiThreadContext = null;

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static synchronized void setContext(Context context, Handler handler) {
        uiThreadContext = context;
        uiThreadHandler = handler;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static Context getContext() {
        return uiThreadContext;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static Handler getHandler() {
        return uiThreadHandler;
    }

    // ==================================================================================================================================

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the RequestManager service.
        if (sIsActive_RequestManager == false)
            bindService(new Intent(IRequestManager.class.getName()),
                    mRequestManagerConnection, Context.BIND_AUTO_CREATE);

        // Bind to the ContentManager service
        if (sIsActive_ContentManager == false)
            bindService(new Intent(IStorageManager.class.getName()),
                    mStorageManagerConnection, Context.BIND_AUTO_CREATE);

        sVideoView = (VideoView) findViewById(R.id.videoView);
        sVideoView.setOnCompletionListener(mediaItemCompleted);
        sVideoView.setMediaController(new MediaController(this));
    }

    @Override
    protected void onDestroy() {
        try {
            // Unbind from the ContentManager and the
            // ReqeuestManager services and unregister callbacks
            if (sIsActive_RequestManager) {
                if (mRequestManagerConnection != null) {
                    if (sRequestManagerProxy != null
                            && sRequestManagerProxy.asBinder().isBinderAlive())
                        sRequestManagerProxy.unregisterCallback(requestManagerCallbackProxy);
                    unbindService(mRequestManagerConnection);
                }
                sIsActive_RequestManager = false;
            }

            if (sIsActive_ContentManager) {
                if (mStorageManagerConnection != null) {
                    if (sStorageManagerProxy != null
                            && sStorageManagerProxy.asBinder().isBinderAlive())
                        sStorageManagerProxy.unregisterCallback(mStorageManagerCallback);
                    unbindService(mStorageManagerConnection);
                }
                sIsActive_ContentManager = false;
            }

        } catch (Throwable fault) {
            Log.d(sTag, "onDestroy" + fault);
        } finally {
            super.onDestroy();
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    @Override
    protected void onStop() {
        super.onStop();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    @Override
    protected void onPause() {

        sIsPaused = true;
        if (sVideoView != null) {
            if (sVideoView.isPlaying()) {
                sVideoView.pause();
            }
        }        
        
        super.onPause();
    }

    @Override
    protected void onResume() {

        sIsPaused = false;        
        super.onResume();
    }

    // ----------------------------------------------------------------------------------------------------------------------------------

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use the Application context here
        setContext(getApplicationContext(), new Handler() {
            public void handleMessage(Message msg) {
                if (msg != null) {

                    switch (msg.what) {
                        case QUEUE_REQUEST_MGR_CONNECTED:
                            Log.v(sTag, "Idle waiting for pushed QueueRequests...\n");
                            break;
                        case QUEUE_REQUEST_ID_MSG:
                            Bundle bdl = msg.getData();
                            Log.v(sTag, "\nInsert QueueRequest to Hqme - got id: "
                                    + bdl.getLong("QueueRequestId"));
                            break;
                        case NEW_CONTENT:
                            // this handles the message passed on from the
                            // broadcast receiver (which was a sticky broadcast)
                            try {
                                if (getProxy() != null) {
                                    long queueRequestId  = msg.getData().getLong("QueueRequestId");
                                    IQueueRequest qr = getProxy().getRequest(queueRequestId);
                                    if (qr != null) {
                                        String path = qr.getProperty("REQPROP_STORE_NAME");
                                        Log.v(sTag, "Most recent content that arrived: (broadcast intent)" + path);

                                        if (msg.getData().getString("ProgressStatus") != null)
                                            Log.v(sTag, msg.getData().getString("ProgressStatus"));

                                        boolean found = false;

                                        // New content is available. Find it using the VSD APIs
                                        int[] storageIds = getPluginProxy().getStorageIds(null);
                                        if (storageIds != null) {
                                            for (int i = 0; i < storageIds.length; i++) {
                                                IVSD store = getPluginProxy().getStorage(storageIds[i]);
                                                if (store != null) {
                                                    // Get a ContentObject reference to the newly available object.
                                                    IContentObject targetObject = store.getObject(path);
                                                    if (targetObject != null) {
                                                        // Get the straming URI and playback using embedded player.
                                                        String streamingUri = targetObject.getStreamingUri();
                                                        Log.v(sTag, "streamingUri = " + streamingUri);
                                                        sVideoView.setVideoPath(streamingUri);
                                                        if (!sIsPaused) {
                                                            sVideoView.start();
                                                            Log.v(sTag, "Playing " + path + " from " + store.name());
                                                            Toast.makeText(
                                                                    getContext(),
                                                                    "Playing " + path + " from " + store.name(),
                                                                    Toast.LENGTH_SHORT).show();
                                                        }
                                                        found = true;
                                                        break;
                                                    }
                                                }
                                            }

                                            if (found == false) {
                                                Log.w(sTag,path
                                                        + " not found. Current available content stores are: ");
                                                for (int i = 0; i < storageIds.length; i++) {
                                                    IVSD store = getPluginProxy().getStorage(storageIds[i]);
                                                    Log.w(sTag, "    " + store.name());
                                                }
                                            }

                                        } else {
                                            Log.i(sTag, "No content store available");
                                            Toast.makeText(getContext(),
                                                    "No content store available",
                                                    Toast.LENGTH_SHORT).show();
                                        }

                                    }
                                }
                            } catch (Exception e1) {
                                Log.d(sTag, "handleMessage, NEW_CONTENT",
                                        e1);
                            }
                            break;
                        case CONTENT_STORE_MSG:
                            try {
                                // from ContentManager
                                if (msg.arg1 == VSDEvent.VSD_REMOVED.getCode()) {
                                    sVideoView.pause();
                                    sVSDs.remove(msg.arg2);
                                } else if (msg.arg1 == VSDEvent.VSD_ADDED.getCode()) {
                                    Integer Id = msg.arg2;
                                    try {
                                        IVSD store = getPluginProxy() != null ? (getPluginProxy()
                                                .VSDCount() > 0 ? getPluginProxy()
                                                .getStorage(Id) : null)
                                                : null;
                                        if (store != null) {
                                            ArrayList<Long> fgLong = new ArrayList<Long>();
                                            if (store.functionGroups() != null)
                                                for (Long fg : store.functionGroups()) {
                                                    fgLong.add(fg);
                                                }

                                            sVSDs.put(Id, fgLong);
                                        }
                                    } catch (Exception fault) {
                                        Log.d(sTag, "handleMessage, CONTENT_STORE_MSG",
                                                fault);
                                    }

                                }
                            } catch (Exception e) {
                                Log.d(sTag, "handleMessage, CONTENT_STORE_MSG", e);
                            }
                            break;
                        case REQ_CALLBACK_MSG:
                            // this case handles messages from the RequestManager
                            Bundle statusBundle = msg.getData();
                            ReqEvents reqEvent = statusBundle.getParcelable("reqEvent");
                            if (reqEvent != null) {

                                Log.v(sTag, "reqCallbackMethod invoked...\n");
                                Log.v(sTag, "Received from hqme core: " + reqEvent.toString());

                                if (ReqEvents.ReqEvent.REQUEST_COMPLETED
                                        .equals(reqEvent.getEvent())) {

                                    long qrid = reqEvent.getRequestId();
                                    try {
                                        IQueueRequest qr = getProxy().getRequest(qrid);
                                        if (qr != null) {
                                            Log.i(sTag, "Most recent content that arrived:"
                                                    + qr.getProperty("REQPROP_STORE_NAME"));
                                        }
                                    } catch (Exception e1) {
                                        Log.d(sTag, "handleMessage, REQ_CALLBACK_MSG",
                                                e1);
                                    }

                                }
                            }
                            break;
                        default:
                            super.handleMessage(msg);
                    }
                }
            }

        });

        // UI
        setContentView(R.layout.submit_view);
        btnSubmit = (Button) findViewById(R.id.btnSubmitRequest1);
        btnSubmit.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                submitRequest();
            }
        });
        
        radioWiFiOnly = (RadioButton) findViewById(R.id.radioWiFiOnly);
        radioWiFiAnd3G = (RadioButton) findViewById(R.id.radioWiFi3G);
        editURI = (EditText) findViewById(R.id.editURI);
        
        editURI.setText(getText(R.string.DefaultURI));

    }

    // ==================================================================================================================================
    /**
     * A client application may wish to keep track of the currently available
     * content stores. Here we map content store Id and the associated function
     * groups, and modify this map as content stores are added or removed
     */
    protected static HashMap<Integer, ArrayList<Long>> sVSDs = new HashMap<Integer, ArrayList<Long>>();

    // ==================================================================================================================================
    private static MediaPlayer.OnCompletionListener mediaItemCompleted = new MediaPlayer.OnCompletionListener() {

        public void onCompletion(MediaPlayer mp) {
            mp.seekTo(0);
        }

    };

    // ==================================================================================================================================

    /**
     * The service connection for interacting with the RequestManager.
     */
    private ServiceConnection mRequestManagerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            sRequestManagerProxy = IRequestManager.Stub.asInterface(service);
            sIsActive_RequestManager = true;

            sVideoView = (VideoView) findViewById(R.id.videoView);
            if (sIsActive_ContentManager)
                btnSubmit.setEnabled(true);

            try {
                sRequestManagerProxy.registerCallback(requestManagerCallbackProxy);
            } catch (RemoteException fault) {
                Log.d(sTag, "onServiceConnected", fault);
            }

            Toast.makeText(HqmeClientActivity.this, "RequestManager proxy is connected.",
                    Toast.LENGTH_SHORT).show();

            getHandler().sendMessage(getHandler().obtainMessage(QUEUE_REQUEST_MGR_CONNECTED, 0, 0));
        }

        public void onServiceDisconnected(ComponentName className) {
            sRequestManagerProxy = null;
            sIsActive_RequestManager = false;

            btnSubmit.setEnabled(false);

            Toast.makeText(HqmeClientActivity.this, "RequestManager proxy is disconnected.",
                    Toast.LENGTH_SHORT).show();
        }
    };

    // ==================================================================================================================================
    /**
     * The service connection for interacting with the ContentManager.
     */
    private ServiceConnection mStorageManagerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(sTag, "mStorageManagerConnection.onServiceConnected called");
            sStorageManagerProxy = IStorageManager.Stub.asInterface(service);
            sIsActive_ContentManager = true;

            if (sIsActive_RequestManager)
                btnSubmit.setEnabled(true);

            synchronized (sVSDs) {
                sVSDs.clear();
                // check the ContentManager
                try {
                    int[] Ids = sStorageManagerProxy.VSDCount() > 0 ? sStorageManagerProxy
                            .getStorageIds(null) : null;
                            
                    if (Ids != null) {
                        for (int Id : Ids) {
                            long[] fgs = sStorageManagerProxy.getFunctionGroups(Id);
                            if (fgs != null) {
                                ArrayList<Long> functionGroups = new ArrayList<Long>();
                                for (long fg : fgs)
                                    functionGroups.add(fg);
                                sVSDs.put(Id, functionGroups);
                            } else
                                sVSDs.put(Id, null);

                        }
                    }

                } catch (Exception exec) {
                    Log.d(sTag, "onServiceConnected", exec);
                }
            }
            try {
                sStorageManagerProxy.registerCallback(mStorageManagerCallback);
            } catch (RemoteException fault) {
                Log.d(sTag, "onServiceConnected", fault);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            synchronized (sVSDs) {
                sVSDs.clear();
            }
            Log.d(sTag, "mStorageManagerConnection.onServiceDisconnected called");
            sStorageManagerProxy = null;
            sIsActive_ContentManager = false;
        }
    };

    // ==================================================================================================================================
    /**
     * This implementation can be used to receive callbacks from the RequestManager
     * service.
     */
    private IRequestManagerCallback requestManagerCallbackProxy = new IRequestManagerCallback.Stub() {

        public void handleEvent(ReqEvents reqEvent) throws RemoteException {

            Message msg = getHandler().obtainMessage(REQ_CALLBACK_MSG);
            Bundle statusBundle = new Bundle();

            statusBundle.putParcelable("reqEvent", reqEvent);
            msg.setData(statusBundle);
            getHandler().sendMessage(msg);
        }
    };

    /**
     * This implementation can be used to receive callbacks from the
     * ContentManager service.
     */
    private IStorageManagerCallback mStorageManagerCallback = new IStorageManagerCallback.Stub() {
        public void handleEvent(int eventCode, int storageID) {
            getHandler().sendMessage(getHandler().obtainMessage(CONTENT_STORE_MSG,
            eventCode, storageID));
        }
    };

    
    private String getQRTemplate() {
        try {
            AssetManager assets = getAssets();
            InputStream is = assets.open("qr-template.xml");
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            return new String(bytes, "UTF8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    // ==================================================================================================================================
    /**
     * This is called on clicking the submitRequest button. This method creates
     * a simple QueueRequest, applies a simple policy for when it may download,
     * and submits the request to the RequestManager.
     */
    public void submitRequest() {
        try {

            // get QueueRequest xml template from assets.
            String queueRequestXml = getQRTemplate();
            
            String uri = editURI.getText().toString(); // Get URI from the UI.
            
            // Guess extension and mime type from URI. (App should know this by default)
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

            // Replace fields
            queueRequestXml = queueRequestXml.replace("#URI#", uri);
            queueRequestXml = queueRequestXml.replace("#RECEIVER#", getPackageName() + ".QR_COMPLETED");
            queueRequestXml = queueRequestXml.replace("#FILENAME#", "sample_" + Integer.toString(sRandomGenerator.nextInt(0x7fffffff)) + "." + extension);
            queueRequestXml = queueRequestXml.replace("#MIMETYPE#", mime);
            
            if (radioWiFiAnd3G.isChecked())
                queueRequestXml = queueRequestXml.replace("#RULENAME#", "WiFi3GNoRoaming");
            else
                queueRequestXml = queueRequestXml.replace("#RULENAME#", "WiFiOnly");
            
            // Submit QueueRequest
            IQueueRequest qr = getProxy().createQueueRequestXml(queueRequestXml);
            long queueRequestId  = getProxy().submitRequest(qr);
            
            Message msg = Message.obtain(getHandler(), QUEUE_REQUEST_ID_MSG);
            Bundle b = new Bundle();
            b.putLong("QueueRequestId", queueRequestId);
            b.putString("QueueRequest", qr.toString());
            msg.setData(b);
            getHandler().sendMessage(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.w(sTag, "RemoteException: " + e.toString());
        } catch (Exception e) {
            e.printStackTrace();
            Log.w(sTag, "Exception: " + e.toString());
        }
    }

}
