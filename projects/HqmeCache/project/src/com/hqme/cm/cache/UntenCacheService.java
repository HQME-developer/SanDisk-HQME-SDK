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

package com.hqme.cm.cache;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.StatFs;
import android.util.Log;

import com.hqme.cm.IVSD;
import com.hqme.cm.IStorageManager;
import com.hqme.cm.IContentObject;
import com.hqme.cm.HqmeError;
import com.hqme.cm.VSDEvent;
import com.hqme.cm.Property;
import com.hqme.cm.VSDProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

public class UntenCacheService extends Service {
    private static final String sTag = "UntenCacheService";
    private static final String[] EMPTY_STRINGS = new String[0];
    private static final long[] EMPTY_LONGS = new long[0];

    private static String sStorageName = "Default Cache";
    private static int sStorageId = -1;
    private static long[] sFunctionGroups = EMPTY_LONGS;

    private static HashMap<String, UntenCacheObject> sObjects = new HashMap<String, UntenCacheObject>();
    private static HashMap<String, Properties> sObjectProperties = new HashMap<String, Properties>();
    
    private static Properties sProperties = new Properties();
    private static File sMetaFile = null;

    protected static File sRoot = null;
    protected static IStorageManager sPluginManagerProxy = null;
    protected static boolean sLoaded = false;
   
    static Context sPluginContext = null;
    static boolean sIsDebugMode = false;

    private static final StreamingServer streamingServerHandler = new StreamingServer();
    private static Thread streamingServerWorker = null;
    
    /**********************************************************************************************
     * UntenCache is the default IVSD plug-in implementation. The IVSD interface links 
     * [any of several] underlying physical or virtual storage device with the IVSD 
     * public interface. The UntenCacheService serves as the default external storage
     * adaptor; it uses the public file system partition of any external storage
     * card as the backing store.
     */
    protected static final IVSD.Stub sUntenCache = new IVSD.Stub() {
        @Override
        public int storageId() throws RemoteException {
            return sStorageId;
        }

        @Override
        public String name() throws RemoteException {
            return sStorageName;
        }

        @Override
        public long[] functionGroups() throws RemoteException {
            return sFunctionGroups;
        }

        @Override
        public String[] allObjects(String filter) throws RemoteException {
            synchronized (sObjects) {
                debugLog(sTag, "allObjects() : filter = " + filter + ", count = " + sObjects.size());
                
                ArrayList<String> paths = new ArrayList<String>();
                String _filter = normalize(filter);
                for (String key : sObjects.keySet()) {
                    if (key.contains(_filter)) {
                        paths.add(sObjects.get(key).mObjectPath);
                    }
                }
                return paths.toArray(EMPTY_STRINGS);
            }
        }
       
        @Override
        public IContentObject getObject(String name) throws RemoteException {
            synchronized (sObjects) {
                String key = normalize(name);
                if (sObjects.containsKey(key)) {
                    return new UntenCacheObject(name); // TODO: value reuse
                }
                return null;
            }
        }

        @Override
        public IContentObject createObject(String name) throws RemoteException {
            synchronized (sObjects) {
                UntenCacheObject obj = null;
                
                String key = normalize(name);
                if (sObjects.containsKey(key)) {
                    try {
                        int result = HqmeError.STATUS_SUCCESS.getCode();
                        obj = sObjects.get(key);
                        if (obj != null) {
                            result = obj.remove();
                            if (result != HqmeError.STATUS_SUCCESS.getCode()) {
                                Log.w(sTag, "createObject: cannot overwrite the existing object: " + name);
                            }
                        } else {
                            sObjects.remove(key);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        Log.e(sTag, "createObject: RemoteException fault:" + e);
                    }
                }

                obj = new UntenCacheObject(name);
                if (!obj.isValidObject()) {
                    Log.e(sTag, "createObject: cannot create object: " + name);
                    return null;
                } else {
                    sObjects.put(key, obj);
                    sPluginManagerProxy.updateStatus(VSDEvent.VSD_MODIFIED.getCode(), sStorageId);
                }
                return obj;
            }
        }

        @Override
        public int removeObject(String name) throws RemoteException {
            synchronized (sObjects) {
                String key = normalize(name);
                if (!sObjects.containsKey(key)) {
                    return HqmeError.ERR_NOT_FOUND.getCode();
                }

                try {
                    int result = HqmeError.STATUS_SUCCESS.getCode();
                    UntenCacheObject obj = sObjects.get(key);
                    if (obj != null) {
                        result = obj.remove();
                        if (result == HqmeError.STATUS_SUCCESS.getCode()) {
                            obj = null;
                        }
                    } else {
                        sObjects.remove(key);
                    }
                    sPluginManagerProxy.updateStatus(VSDEvent.VSD_MODIFIED.getCode(), sStorageId);
                    return result;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    Log.e(sTag, "removeObject: RemoteException fault:" + e);
                    return HqmeError.ERR_GENERAL.getCode();
                }
            }
        }

        @Override
        public String[] getPropertyKeys() throws RemoteException {
            ArrayList<String> arrayList = new ArrayList<String>();

            // add default properties
            arrayList.add("VS_ROOT_FOLDER");
            
            // add defined properties
            for (VSDProperties.VSProperty mandatoryKey : VSDProperties.VSProperty.values()) {
                arrayList.add(mandatoryKey.name());
            }

            // add user properties
            for (@SuppressWarnings("rawtypes")
            Enumeration e = sProperties.keys(); e.hasMoreElements();) {
                arrayList.add((String)e.nextElement());
            }

            return arrayList.toArray(EMPTY_STRINGS);
        }

        @Override
        public String getProperty(String key) throws RemoteException {
            if (key.equals("VS_ROOT_FOLDER")) {
                return sRoot.getAbsolutePath();
            } else if (key.equals(VSDProperties.VSProperty.VS_FN_GROUPS.name())) {
                return "";
            } else if (key.equals(VSDProperties.VSProperty.VS_TOTAL_CAPACITY.name())) {
                File root = Environment.getExternalStorageDirectory();
                // return root.getTotalSpace();
                StatFs fs = new StatFs(root.getAbsolutePath());
                if (fs != null) {
                    int totalBlocks = fs.getBlockCount();
                    int blockSize = fs.getBlockSize();
                    return Long.toString((long) totalBlocks * blockSize);
                }
            } else if (key.equals(VSDProperties.VSProperty.VS_AVAILABLE_CAPACITY.name())) {
                File root = Environment.getExternalStorageDirectory();
                // return root.getFreeSpace();
                StatFs fs = new StatFs(root.getAbsolutePath());
                if (fs != null) {
                    int freeBlocks = fs.getAvailableBlocks();
                    int blockSize = fs.getBlockSize();
                    return Long.toString((long) freeBlocks * blockSize);
                }
            } else if (key.equals(VSDProperties.VSProperty.VS_OBJECT_COUNT.name())) {
                return Integer.toString(sObjects.size());
            }
            
            synchronized (sProperties) {
                return sProperties.getProperty(key);
            }
        }

        @Override
        public int setProperty(String key, String value) throws RemoteException {
            // Backing store properties (none of these properties are writable)
            if (key.equals("VS_ROOT_FOLDER")) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals("VS_STORAGE_ID")) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_FN_GROUPS.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_TOTAL_CAPACITY.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_AVAILABLE_CAPACITY.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_OBJECT_COUNT.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            }

            synchronized (sProperties) {
                sProperties.put(key, value);
                if (doSaveProperties(sMetaFile, sProperties)) {
                    sPluginManagerProxy.updateStatus(VSDEvent.VSD_MODIFIED.getCode(), sStorageId);
                    return HqmeError.STATUS_SUCCESS.getCode();
                } else {
                    return HqmeError.ERR_GENERAL.getCode();
                }
            }
        }

        @Override
        public int removeProperty(String key) throws RemoteException {
            if (key.equals("VS_ROOT_FOLDER")) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals("VS_STORAGE_ID")) {
                    return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_FN_GROUPS.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_TOTAL_CAPACITY.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_AVAILABLE_CAPACITY.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            } else if (key.equals(VSDProperties.VSProperty.VS_OBJECT_COUNT.name())) {
                return HqmeError.ERR_PERMISSION_DENIED.getCode();
            }

            synchronized (sProperties) {
                if (null == sProperties.remove(key)) {
                    return HqmeError.ERR_NOT_FOUND.getCode();
                } else if (doSaveProperties(sMetaFile, sProperties)) {
                    sPluginManagerProxy.updateStatus(VSDEvent.VSD_MODIFIED.getCode(), sStorageId);
                    return HqmeError.STATUS_SUCCESS.getCode();
                } else {
                    return HqmeError.ERR_GENERAL.getCode();
                }
            }
        }
        
        @Override
        public int issueCommand(int commandId, Property[] arguments) throws RemoteException {
            return HqmeError.ERR_NOT_SUPPORTED.getCode();
        }

        @Override
        public Property[] getCommandStatus(int commandId) throws RemoteException {
            return null; // HqmeError.ERR_NOT_SUPPORTED.getCode();
        }
    };

    static class UntenCacheObject extends IContentObject.Stub {
        String mObjectPath = null;
        private File mDataFile = null;
        private File mMetaFile = null;
        private FileLock mLock = null;
        private RandomAccessFile mAccessor = null;
        private Properties mProperties = null;

        /**
         * @param name
         */
        private UntenCacheObject(String name) {
            if (name.startsWith("/")) {
                mObjectPath = name.replaceFirst("/", "");
            } else {
                mObjectPath = name;
            }

            mDataFile = new File(sRoot, mObjectPath + ".data");
            if (!mDataFile.exists()) {
                File folder = mDataFile.getParentFile();
                if (!folder.exists()) {
                    if (!folder.mkdirs()) {
                        Log.e(sTag, "UntenCacheObject: cannot create parent folder " + folder.getAbsolutePath());
                    }
                }
                try {
                    mDataFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(sTag, "UntenCacheObject: data file creation failed, fault:" + e);
                    mDataFile = null;
                    return;
                }
            }

            mMetaFile = new File(sRoot, mObjectPath + ".meta");
            if (!mMetaFile.exists()) {
                try {
                    mMetaFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(sTag, "UntenCacheObject: metadata file creation failed, fault:" + e);
                    mMetaFile = null;
                    mDataFile.delete();
                    mDataFile = null;
                    return;
                }
            } 
            
            String key = normalize(name);
            if ((mProperties = sObjectProperties.get(key)) == null) {
                mProperties = new Properties();
                if (!doLoadProperties(mMetaFile, mProperties)) {
                    Log.e(sTag, "UntenCacheObject: metadata file loading failed");
                    sObjectProperties.remove(key);
                    mProperties = null;
                    mMetaFile.delete();
                    mMetaFile = null;
                    mDataFile.delete();
                    mDataFile = null;
                    return;
                } 
                mProperties.put(VSDProperties.SProperty.S_LOCKED.name(), "false");
                sObjectProperties.put(key, mProperties);
            }
        }

        private boolean isValidObject() {
            return (mDataFile != null && mMetaFile != null && mProperties != null);
        }
        
        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#properties()
         */ 
        @Override
        public Property[] properties() throws RemoteException {
            try {
                synchronized (mProperties) {
                    Property[] propertyArray = new Property[mProperties.size()];
                    String[] keys = getPropertyKeys();
                    for (int i = 0; i < keys.length; i++) {
                        propertyArray[i] = new Property(keys[i], mProperties.getProperty(keys[i]));
                    }
                    return propertyArray;
                }
            } catch (NullPointerException e) {
                Log.e(sTag, "properties: invalid object");
                return null;
            }
        }
        
        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#getPropertyKeys()
         * 
         * The mandatory properties associated with all content objects:
         *      S_STORE_NAME, S_STORE_SIZE, S_SOURCEURI, S_ORIGIN, S_LOCKED, S_TYPE, S_REDOWNLOAD_URI.
         *      
         * The optional properties are:
         *      S_ID, S_TITLE, S_DESCRIPTION, S_ALBUM, S_ARTIST, S_GENRE, S_COPYRIGHT, etc.
         */
        @Override
        public String[] getPropertyKeys() throws RemoteException {
            try {
                synchronized (mProperties) {
                    return mProperties.keySet().toArray(EMPTY_STRINGS);
                }
            } catch (NullPointerException e) {
                Log.e(sTag, "propertyKeys: invalid object");
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#getProperty(java.lang.String)
         */
        @Override
        public String getProperty(String key) throws RemoteException {
            try {
                synchronized (mProperties) {
                    if (key.equals(VSDProperties.SProperty.S_REDOWNLOAD_URI.name())) {
                        if (!mProperties.containsKey(VSDProperties.SProperty.S_REDOWNLOAD_URI.name()))
                            return mProperties.getProperty(VSDProperties.SProperty.S_SOURCEURI.name());
                    }

                    return mProperties.getProperty(key);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                Log.e(sTag, "getProperty: invalid " + key == null ? "key" : "object");
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#setProperty(java.lang.String, java.lang.String)
         */
        @Override
        public int setProperty(String key, String value) throws RemoteException {
            try {
                synchronized (mProperties) {
                    if (key.equals(VSDProperties.SProperty.S_LOCKED.name())) {
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();
                    } 
                    
                    mProperties.put(key, value);

                    if (doSaveProperties(mMetaFile, mProperties)) {
                        return HqmeError.STATUS_SUCCESS.getCode();
                    } else {
                        return HqmeError.ERR_GENERAL.getCode();
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                Log.e(sTag, "setProperty: invalid object");
                return HqmeError.ERR_NOT_FOUND.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#removeProperty(java.lang.String)
         */
        @Override
        public int removeProperty(String key) throws RemoteException {
            try {
                synchronized (mProperties) {
                    if (key.equals(VSDProperties.SProperty.S_LOCKED.name())) {
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();
                    } 
                    
                    if (null == mProperties.remove(key)) {
                        return HqmeError.ERR_NOT_FOUND.getCode();
                    } else if (doSaveProperties(mMetaFile, mProperties)) {
                        return HqmeError.STATUS_SUCCESS.getCode();
                    } else {
                        return HqmeError.ERR_GENERAL.getCode();
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                Log.e(sTag, "removeProperty: invalid " + key == null ? "key" : "object");
                return HqmeError.ERR_NOT_FOUND.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#size()
         */
        @Override
        public long size() throws RemoteException {
            try {
                return mDataFile.length();
            } catch (NullPointerException e) {
                e.printStackTrace();
                return HqmeError.ERR_NOT_FOUND.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#tell()
         */
        @Override
        public long tell() throws RemoteException {
            try {
                if (mAccessor != null) {
                    return mAccessor.getFilePointer();
                } else {
                    return HqmeError.ERR_IO.getCode();
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (IOException e) {
                e.printStackTrace();
                return HqmeError.ERR_IO.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#setPosition(long offset, int origin) 
         *      SEEK_SET (0) --- Beginning of file 
         *      SEEK_CUR (1) --- Current position of the file pointer 
         *      SEEK_END (2) --- End of file
         */
        @Override
        public long seek(long offset, int origin) throws RemoteException {
            try {
                if (mAccessor != null) {
                    long position = offset; // count from beginning of the file
                    if (origin == VSDProperties.SEEK_ORIGIN.SEEK_SET.ordinal()) {      // do nothing
                    } else if (origin == VSDProperties.SEEK_ORIGIN.SEEK_CUR.ordinal()) {
                        position += tell(); // offset by current position
                    } else if (origin == VSDProperties.SEEK_ORIGIN.SEEK_END.ordinal()) {
                        position += size(); // offset by file size
                    }
                    mAccessor.seek(position);
                    return position;
                } else {
                    return HqmeError.ERR_IO.getCode();
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (IOException e) {
                e.printStackTrace();
                return HqmeError.ERR_IO.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#open(String mode, boolean lock)
         */
        @Override
        public int open(String mode, boolean lock) throws RemoteException {
            try {
                if (mAccessor == null) {
                    if (mode == null) {
                        return HqmeError.ERR_INVALID_ARGUMENT.getCode();
                    }
                    if (mProperties.get(VSDProperties.SProperty.S_LOCKED.name()).equals("true")) {
                        return HqmeError.ERR_PERMISSION_DENIED.getCode();
                    }
                    mAccessor = new RandomAccessFile(mDataFile, mode);
                    if (lock) {
                        FileChannel channel = mAccessor.getChannel();
                        try {
                            mLock = channel.tryLock();
                            if (mLock != null && mLock.isValid()) {
                                mProperties.put(VSDProperties.SProperty.S_LOCKED.name(), "true");
                            }
                        } catch (OverlappingFileLockException e) {
                            Log.w(sTag, "open: Already locked by someone else");
                            return HqmeError.ERR_IO.getCode();
                        } catch (NonWritableChannelException e) {
                            Log.w(sTag, "open: Cannot acquire lock for read only files. mode: " + mode + " lock: " + lock);
                            return HqmeError.ERR_GENERAL.getCode();
                        }
                    }
                }
                return HqmeError.STATUS_SUCCESS.getCode();
            } catch (NullPointerException e) {
                e.printStackTrace();
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (IOException e) {
                e.printStackTrace();
                return HqmeError.ERR_GENERAL.getCode();
            } catch (IllegalArgumentException e) {
                // mode is not one of "r", "rw"
                e.printStackTrace();
                return HqmeError.ERR_INVALID_ARGUMENT.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#close()
         */
        @Override
        public int close() throws RemoteException {
            try {
                if (mAccessor != null) {
                    if (mLock != null && mLock.isValid()) {
                        mLock.release();
                        mLock = null;
                        mProperties.put(VSDProperties.SProperty.S_LOCKED.name(), "false");
                    }
                    mAccessor.close();
                    mAccessor = null;
                }
                return HqmeError.STATUS_SUCCESS.getCode();
            } catch (NullPointerException e) {
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (IOException e) {
                return HqmeError.ERR_GENERAL.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#remove()
         */
        @Override
        public int remove() throws RemoteException {
            try {
                boolean res = true;
                if (mDataFile != null && mDataFile.exists()) {
                    res = mDataFile.delete();
                }
                if (mMetaFile != null && mMetaFile.exists()) {
                    res &= mMetaFile.delete();
                }
                File parentFolder = mDataFile;
                while ((parentFolder != null ) && (parentFolder = parentFolder.getParentFile()) != null) {
                    if (parentFolder.exists() && parentFolder.isDirectory()) {
                        if (!parentFolder.delete()) {
                            break;
                        }
                    }
                }
                mDataFile = null;
                mMetaFile = null;
                parentFolder = null;
                mProperties = null;
                if (sObjects != null && mObjectPath != null) {
                    sObjects.remove(mObjectPath.toLowerCase());
                }
                this.linkToDeath(null, 0);
                return !res ? HqmeError.ERR_GENERAL.getCode() : HqmeError.STATUS_SUCCESS.getCode();
            } catch (NullPointerException e) { 
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (SecurityException e) {
                e.printStackTrace();
                Log.e(sTag, "remove: SecurityException fault: " + e);
            }
            return HqmeError.ERR_GENERAL.getCode();
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#read(byte[] buf, int count)
         */
        @Override
        public int read(byte[] buf, int count) throws RemoteException {
            try {
                int result = 0;
                if (mAccessor == null) {
                    result = HqmeError.ERR_IO.getCode();
                } else if (buf.length < count) {
                    return HqmeError.ERR_INVALID_ARGUMENT.getCode(); 
                } else {
                    result = mAccessor.read(buf, 0, count);
                    if (result == -1) {
                        return HqmeError.ERR_IO.getCode();
                    }
                }
                return result;
            } catch (NullPointerException e) {
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (IOException e) {
                e.printStackTrace();
                return HqmeError.ERR_IO.getCode();
            }
        }
        
        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#write(byte[] buf, int count)
         */
        @Override
        public int write(byte[] buf, int count) throws RemoteException {
            try {
                int result = 0;
                if (mAccessor == null) {
                    result = HqmeError.ERR_IO.getCode();
                } else if (buf.length < count) {
                    return HqmeError.ERR_INVALID_ARGUMENT.getCode(); 
                } else {
                    mAccessor.write(buf, 0, count);
                    result = count;
                }
                return result;
            } catch (NullPointerException e) {
                return HqmeError.ERR_NOT_FOUND.getCode();
            } catch (IOException e) {
                e.printStackTrace();
                return HqmeError.ERR_IO.getCode();
            }
        }

        /*
         * (non-Javadoc)
         * @see com.hqme.cm.IContentObject#getStreamingUri()
         */
        @Override
        public String getStreamingUri() throws RemoteException {
            try {
                if (!mDataFile.exists() || !mDataFile.isFile()) {
                    return null;
                } else {
                    //String uri = "file://" + mDataFile.getAbsolutePath();
                    String uri = String.format("http://localhost:%d/playback.jsp?token=%s", StreamingServer.getServerPortNumber(), PlaybackTokens.newPlaybackToken(this));
                    debugLog(sTag, "getStreamingUri: " + uri);
                    return uri;
                }
            } catch (NullPointerException e) {
                Log.w(sTag, "getStreamingUri: object not found");
                return null;
            } 
        }
    }

    private static final String normalize(String path) {
        String _path = path.toLowerCase();
        if (_path.startsWith("/")) {
            _path = _path.replaceFirst("/", "");
        }
        return _path;
    }

    final static ServiceConnection mPluginManagerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            debugLog(sTag, "onServiceConnected: className = " + className);
            sPluginManagerProxy = IStorageManager.Stub.asInterface(service);

            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                try {
                    debugLog(sTag, "onServiceConnected: registering plugin, myPid = " + Process.myPid());
                    int storageId = sPluginManagerProxy.registerPlugin(sUntenCache);

                    if (sStorageId != storageId) {
                        sStorageId = storageId;
                        sProperties.put("VS_STORAGE_ID", Integer.toString(sStorageId));
                        doSaveProperties(sMetaFile, sProperties);
                    }
                } catch (RemoteException fault) {
                    fault.printStackTrace();
                    Log.e(sTag, "onServiceConnected: RemoteExpection fault: " + fault);
                }
            } else {
                Log.w(sTag, "onServiceConnected: external storage is not mounted");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            debugLog(sTag, "onServiceDisconnected: className = " + className);
            sPluginManagerProxy = null;
        }
    };
    
    private static class UntenDefaultUncaughtExceptionHandler implements UncaughtExceptionHandler {
        private static UncaughtExceptionHandler sDefaultEH = Thread.getDefaultUncaughtExceptionHandler(); 
        public void uncaughtException(Thread thread, Throwable fault) {
            debugLog(sTag, "uncaughtException: Thread = %d \"%s\"", thread.getId(), thread.getName());
            debugLog(sTag, "uncaughtException: %s", fault);
            sDefaultEH.uncaughtException(thread, fault);
        }
    }
    
    public void onCreate() {
        Thread.setDefaultUncaughtExceptionHandler(new UntenDefaultUncaughtExceptionHandler());
        
        sIsDebugMode = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        sPluginContext = getApplicationContext();
        
        if (streamingServerWorker == null) {
            streamingServerWorker = new Thread(streamingServerHandler);
            streamingServerWorker.setDaemon(true);
            streamingServerWorker.start();
        }
        
        doLoad();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        debugLog(sTag, "Received start id " + startId + ": " + intent);
        
        if (sPluginManagerProxy == null || !sPluginManagerProxy.asBinder().isBinderAlive()) {
            bindService(new Intent(IStorageManager.class.getName()), mPluginManagerConnection, Context.BIND_AUTO_CREATE);
        }

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY;
    }

    public void onDestroy() {
        if (streamingServerWorker != null) {
            streamingServerHandler.stopServer();
            streamingServerWorker = null;
        }
        
        if (sPluginManagerProxy != null && sPluginManagerProxy.asBinder().isBinderAlive()) {
            try {
                sPluginManagerProxy.unregisterPlugin(sUntenCache);
            } catch (RemoteException fault) {
                fault.printStackTrace();
                Log.e(sTag, "onDestroy: RemoteException fault:" + fault);
            }
        } else {
            Log.w(sTag, "onDestroy: sPluginManagerProxy is invalid!");
        }

        unbindService(mPluginManagerConnection);
    }

    static void doUnload() {
        sProperties.clear();
        sObjects.clear();
        sLoaded = false;
    }
    
    static void doLoad() {
        if (sLoaded) {
            return;
        }

        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            debugLog(sTag, "doLoad: external storage is not mounted");
            return;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            debugLog(sTag, "doLoad: external storage is not writable");
            return;
        }

        sRoot = new File(Environment.getExternalStorageDirectory(), "HQME/Cache/Default/");
        if (!sRoot.exists()) {
            sRoot.mkdirs();
        }

        sMetaFile = new File(sRoot, "VSDProperties.meta");
        if (!sMetaFile.exists()) {
            try {
                sMetaFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(sTag, "doLoad: metadata file creation failed, fault:" + e);
            }
        } else {
            doLoadProperties(sMetaFile, sProperties);
        }
            
        try {
            String value = sProperties.getProperty("VS_STORAGE_ID", "-1");
            sStorageId = Integer.parseInt(value);
            debugLog(sTag, "doLoad: assigned storage id is " + sStorageId);
        } catch (NumberFormatException e) {
            sStorageId = -1;
        }

        debugLog(sTag, "doLoad: calling into doLoadObjects()...");
        UntenCacheService.doLoadObjects(sRoot);
        debugLog(sTag, "doLoad: doLoadObjects() called: count = " + sObjects.size());
        
        sLoaded = true;
    }

    private static void doLoadObjects(File dir) {
        if (dir.isDirectory()) {
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return !name.endsWith(".meta");
                }
            };
            String[] children = dir.list(filter);
            for (int i = 0; i < children.length; i++) {
                UntenCacheService.doLoadObjects(new File(dir, children[i]));
            }
        } else {
            String path = dir.getAbsolutePath();
            String root = sRoot.getAbsolutePath();
            path = path.substring(root.length() + 1, path.length() - 5);
            sObjects.put(path.toLowerCase(), new UntenCacheObject(path));
        }
    }

    private static boolean doLoadProperties(File metaFile, java.util.Properties prop) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(metaFile); 
            prop.load(fis);
            return true;
        } catch (FileNotFoundException e) {
            Log.e(sTag, "doLoadProperties: metadata file not found");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(sTag, "doLoadProperties: metadata file read failed, fault: " + e);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(sTag, "doLoadProperties: metadata file close failed, fault: " + e);
            }
        }
        return false;
    }
    
    private static boolean doSaveProperties(File metaFile, java.util.Properties prop) {
        FileOutputStream fos = null; 
        try {
            fos = new FileOutputStream(metaFile);
            prop.store(fos, "");
            return true;
        } catch (FileNotFoundException e) {
            Log.e(sTag, "doSaveProperties: metadata file not found");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(sTag, "doSaveProperties: metadata file write failed, fault: " + e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(sTag, "doSaveProperties: metadata file close failed, fault: " + e);
            }
        }
        return false;
    }
    
    public IBinder onBind(Intent intent) {
//        if (IVSD.class.getName().equals(intent.getAction())) {
//            return sUntenCacheProxy;
//        }
        
        // We don't provide binding, so return null      
        return null;
    }

    public boolean onUnbind(Intent intent) {
//        if (IVSD.class.getName().equals(intent.getAction())) {
//            stopSelf(); // We don't stop ourself either
//        }
        
        return false; // No re-binding since no binding
    }
    
    public static void debugLog(String tag, String message) {
        if (sIsDebugMode) {
            Log.d(tag, message);
        }
    }

    public static void debugLog(String tag, String format, Object... args) {
        String message = String.format(format, args);
        debugLog(tag, message);
    }
}
