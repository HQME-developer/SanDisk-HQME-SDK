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


import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;


public class HQMEProvider extends ContentProvider {

private static final String TAG = "HQMEProvider";
    
    private static final String DATABASE_NAME = "hqme.db";
    private static final int    DATABASE_VERSION = 3;
    private static final String WORKORDER_TABLE_NAME = "workorders";
    private static final String PACKAGE_TABLE_NAME = "packages";
    private static final String METADATA_TABLE_NAME = "metadata";
    private static final String RULES_TABLE_NAME = "rules";
    
    private static final int WO = 1;
    private static final int WOID = 2;
    private static final int RULE = 5;
    private static final int RULEID = 6;
    private static final int RESET_DB = 7;
    private static final int PACKAGE = 8;
    private static final int PACKAGEID = 9;
    private static final int METADATA = 10;
    private static final int METADATAID = 11;
    
    
    private static final UriMatcher sUriMatcher;
    
    private static HashMap<String, String> sWOProjectionMap;
    private static HashMap<String, String> sPackProjectionMap;
    private static HashMap<String, String> sMetaDProjectionMap;
    private static HashMap<String, String> sRULEProjectionMap;
    
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            
           
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            
            create(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + WORKORDER_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + PACKAGE_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + METADATA_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + RULES_TABLE_NAME);
            onCreate(db);
        }
        
        public static void create(SQLiteDatabase db)
        {
            db.execSQL("CREATE TABLE " + WORKORDER_TABLE_NAME + " ("
                    + HQME.WorkOrder._ID + " INTEGER PRIMARY KEY,"
                    + HQME.WorkOrder.WOID + " LONG,"
                   // + HQME.WorkOrder.SOURCE_URL + " TEXT,"
                    + HQME.WorkOrder.STATE + " TEXT,"
                    + HQME.WorkOrder.APP_UUID + " BLOB,"
                    + HQME.WorkOrder.USERPERMISSIONS + " INTEGER,"
                    + HQME.WorkOrder.GROUPPERMISSIONS + " INTEGER,"
                    + HQME.WorkOrder.WORLDPERMISSIONS + " INTEGER,"
                    + HQME.WorkOrder.GROUP + " TEXT,"
                    + HQME.WorkOrder.EXPIRATION + " INTEGER,"
                    + HQME.WorkOrder.DATA + " TEXT"
                    + ");");
            
            db.execSQL("CREATE TABLE " + PACKAGE_TABLE_NAME + " ("
                    + HQME.Package._ID + " INTEGER PRIMARY KEY,"
                    + HQME.Package.WOID + " LONG,"
                    + HQME.Package.SOURCE_URL + " TEXT,"
                    + HQME.Package.NAME + " TEXT,"
                    + HQME.Package.METADATAID + " INTEGER,"
                    + HQME.Package.PERMISSIONS + " BLOB,"
                    + HQME.Package.DATA + " TEXT"
                    + ");");
            
            db.execSQL("CREATE TABLE " + METADATA_TABLE_NAME + " ("
                    + HQME.Metadata._ID + " INTEGER PRIMARY KEY,"
                    + HQME.Metadata.WOID + " LONG,"
                    + HQME.Metadata.NAME + " TEXT,"
                    + HQME.Metadata.DATA + " TEXT" 
                    + ");");
          
            db.execSQL("CREATE TABLE " + RULES_TABLE_NAME + " ("
                    + HQME.Policy._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                 //   + HQME.Rule.RULEID + " INTEGER,"
                    + HQME.Policy.POLICY_DATA + " TEXT"
                    + ");");
        }
     
    }
    
    private DatabaseHelper mOpenHelper;
    
    /* (non-Javadoc)
     * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case WO:
            count = db.delete(WORKORDER_TABLE_NAME, selection, selectionArgs);
            break;

        case WOID:
            String woId = uri.getPathSegments().get(1);
            count = db.delete(WORKORDER_TABLE_NAME, HQME.WorkOrder.WOID + "=" + woId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
        case PACKAGE:
            count = db.delete(PACKAGE_TABLE_NAME, selection, selectionArgs);
            break;

        case PACKAGEID:
            woId = uri.getPathSegments().get(1);
            count = db.delete(PACKAGE_TABLE_NAME, HQME.Package.WOID + "=" + woId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
        case METADATA:
            count = db.delete(METADATA_TABLE_NAME, selection, selectionArgs);
            break;

        case METADATAID:
            String metadataid = uri.getPathSegments().get(1);
            count = db.delete(METADATA_TABLE_NAME, HQME.Metadata._ID + "=" + metadataid
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
     
        case RULE:
            count = db.delete(RULES_TABLE_NAME, selection, selectionArgs);
            break;

        case RULEID:
            String ruleId = uri.getPathSegments().get(1);
            count = db.delete(RULES_TABLE_NAME, HQME.Policy._ID + "=" + ruleId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
            
        case RESET_DB:
            {
                db.execSQL("DROP TABLE IF EXISTS " + WORKORDER_TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + PACKAGE_TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + METADATA_TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + RULES_TABLE_NAME);
                
                DatabaseHelper.create(db);
                
                return 1;
            }
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    

    /* (non-Javadoc)
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case WOID:
                return HQME.WorkOrder.CONTENT_ITEM_TYPE;
            case WO:
                return HQME.WorkOrder.CONTENT_TYPE;
            case PACKAGEID:
                return HQME.Package.CONTENT_ITEM_TYPE;
            case PACKAGE:
                return HQME.Package.CONTENT_TYPE;
            case METADATAID:
                return HQME.Metadata.CONTENT_ITEM_TYPE;
            case METADATA:
                return HQME.Metadata.CONTENT_TYPE;
            case RULEID:
                return HQME.Policy.CONTENT_ITEM_TYPE;
            case RULE:
                return HQME.Policy.CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        
        Uri recordUri = null;
        ContentValues localValues;
        
        if (values != null) {
            localValues = new ContentValues(values);
        } else {
            localValues = new ContentValues();
        }
        
        if (sUriMatcher.match(uri) == WO)
        {
            recordUri = insertWO(uri, localValues);
        }
        else if(sUriMatcher.match(uri) == PACKAGE)
        {
            recordUri = insertPackage(uri, localValues);
        }
        else if(sUriMatcher.match(uri) == METADATA)
        {
            recordUri = insertMetadata(uri, localValues);
        }
        else if(sUriMatcher.match(uri) == RULE ) 
        {
            recordUri = insertRule(uri, localValues);
        }
        else
            throw new IllegalArgumentException("Unknown URI " + uri);
        

        if(recordUri != null)
            return recordUri;
        else
            throw new SQLException("Failed to insert row into " + uri);
    }
    
 
    /* (non-Javadoc)
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        
        ArrayList<String> listSelectionArgs = new ArrayList<String>();
        if (selectionArgs != null)
            Collections.addAll(listSelectionArgs, selectionArgs);
        
        switch (sUriMatcher.match(uri)) {
        case WOID:
            // qb.appendWhere(HQME.WorkOrder.WOID + "=" + uri.getPathSegments().get(1));
            qb.appendWhere(HQME.WorkOrder.WOID + "=?");
            listSelectionArgs.add(uri.getPathSegments().get(1));
        case WO:
            qb.setProjectionMap(sWOProjectionMap);
            qb.setTables(WORKORDER_TABLE_NAME);
            break;
        case PACKAGEID:
            qb.appendWhere(HQME.Package.WOID + "=" + uri.getPathSegments().get(1));
        case PACKAGE:
            qb.setProjectionMap(sPackProjectionMap);
            qb.setTables(PACKAGE_TABLE_NAME);
            break;
        case METADATAID:
            qb.appendWhere(HQME.Metadata._ID + "=" + uri.getPathSegments().get(1));
        case METADATA:
            qb.setProjectionMap(sMetaDProjectionMap);
            qb.setTables(METADATA_TABLE_NAME);
            break;      
        case RULEID:
            qb.appendWhere(HQME.Policy._ID + "=" + uri.getPathSegments().get(1));
        case RULE:
            qb.setProjectionMap(sRULEProjectionMap);
            qb.setTables(RULES_TABLE_NAME);
            break;
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

      
        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, listSelectionArgs.toArray(new String[]{}), null, null, null);
        if(c == null)
            return null;
        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }



    /* (non-Javadoc)
     * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case WO:
            count = db.update(WORKORDER_TABLE_NAME, values, selection, selectionArgs);
            break;

        case WOID:
            String woId = uri.getPathSegments().get(1);
            count = db.update(WORKORDER_TABLE_NAME, values, HQME.WorkOrder.WOID + "=" + woId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
        case PACKAGE:
            count = db.update(PACKAGE_TABLE_NAME, values, selection, selectionArgs);
            break;

        case PACKAGEID:
            woId = uri.getPathSegments().get(1);
            count = db.update(PACKAGE_TABLE_NAME, values, HQME.Package.WOID + "=" + woId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
        case METADATA:
            count = db.update(METADATA_TABLE_NAME, values, selection, selectionArgs);
            break;

        case METADATAID:
            String packageid = uri.getPathSegments().get(1);
            count = db.update(METADATA_TABLE_NAME, values, HQME.Metadata._ID+ "=" + packageid
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
        
        case RULE:
            count = db.update(RULES_TABLE_NAME, values, selection, selectionArgs);
            break;

        case RULEID:
            String ruleId = uri.getPathSegments().get(1);
            count = db.update(RULES_TABLE_NAME, values, HQME.Policy._ID + "=" + ruleId
                    + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""), selectionArgs);
            break;
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    
    private Long createWOID()
    {
        return new Date().getTime();
    }
    
   private Uri insertWO(Uri uri, ContentValues values) {
        
        Long woid = createWOID();
        values.put(HQME.WorkOrder.WOID,woid);
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(WORKORDER_TABLE_NAME, HQME.WorkOrder.DATA, values);
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(HQME.WorkOrder.CONTENT_URI, woid);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }
        else
            return null;
        
    }
    
   
   private Uri insertPackage(Uri uri, ContentValues values) {
       
       SQLiteDatabase db = mOpenHelper.getWritableDatabase();
       long rowId = db.insert(PACKAGE_TABLE_NAME, HQME.Package.DATA, values);
       if (rowId > 0) {
           Uri noteUri = ContentUris.withAppendedId(HQME.Package.CONTENT_URI, (Long)values.get(HQME.Package.WOID));//returns the woid
           getContext().getContentResolver().notifyChange(noteUri, null);
           return noteUri;
       }
       else
           return null;
       
   }
   
 private Uri insertMetadata(Uri uri, ContentValues values) {
       
       SQLiteDatabase db = mOpenHelper.getWritableDatabase();
       long rowId = db.insert(METADATA_TABLE_NAME, HQME.Metadata.DATA, values);
       if (rowId > 0) {
           Uri noteUri = ContentUris.withAppendedId(HQME.Metadata.CONTENT_URI, rowId);//(Long)values.get(HQME.Metadata.PACKAGEID));//returns the package id
           getContext().getContentResolver().notifyChange(noteUri, null);
           return noteUri;
       }
       else
           return null;
       
   }
    private Uri insertRule(Uri uri, ContentValues values) {
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(RULES_TABLE_NAME, HQME.Policy.POLICY_DATA, values);
        if (rowId > 0) {
            //TODO check this, rule dont have woid in the values so we return db index
            Uri noteUri = ContentUris.withAppendedId(HQME.Policy.CONTENT_URI, rowId/*(Long) values.get(HQME.Rule.RULEID)*/);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }
        else
            return null;
        
    }

    
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        //WorkOrder match
        sUriMatcher.addURI(HQME.AUTHORITY, "workorder", WO);
        sUriMatcher.addURI(HQME.AUTHORITY, "workorder/#", WOID);
        
        
        //Package match
        sUriMatcher.addURI(HQME.AUTHORITY, "package", PACKAGE);
        sUriMatcher.addURI(HQME.AUTHORITY, "package/#", PACKAGEID);
        
        //Metadata match
        sUriMatcher.addURI(HQME.AUTHORITY, "metadata", METADATA);
        sUriMatcher.addURI(HQME.AUTHORITY, "metadata/#", METADATAID);
        
        //Rules match
        sUriMatcher.addURI(HQME.AUTHORITY, "rule", RULE);
        sUriMatcher.addURI(HQME.AUTHORITY, "rule/#", RULEID);
        
      //Reset DB match
        sUriMatcher.addURI(HQME.AUTHORITY, "reset_db", RESET_DB);
               
        sWOProjectionMap = new HashMap<String, String>();
        sPackProjectionMap = new HashMap<String, String>();
        sMetaDProjectionMap = new HashMap<String, String>();
        sRULEProjectionMap = new HashMap<String, String>();
        
        sWOProjectionMap.put(HQME.WorkOrder._ID, HQME.WorkOrder._ID);
        sWOProjectionMap.put(HQME.WorkOrder.WOID, HQME.WorkOrder.WOID);
        sWOProjectionMap.put(HQME.WorkOrder.STATE, HQME.WorkOrder.STATE);
        sWOProjectionMap.put(HQME.WorkOrder.DATA, HQME.WorkOrder.DATA);
        sWOProjectionMap.put(HQME.WorkOrder.APP_UUID, HQME.WorkOrder.APP_UUID);
        sWOProjectionMap.put(HQME.WorkOrder.USERPERMISSIONS, HQME.WorkOrder.USERPERMISSIONS);
        sWOProjectionMap.put(HQME.WorkOrder.GROUPPERMISSIONS, HQME.WorkOrder.GROUPPERMISSIONS);
        sWOProjectionMap.put(HQME.WorkOrder.WORLDPERMISSIONS, HQME.WorkOrder.WORLDPERMISSIONS);
        sWOProjectionMap.put(HQME.WorkOrder.GROUP, HQME.WorkOrder.GROUP);
        sWOProjectionMap.put(HQME.WorkOrder.EXPIRATION, HQME.WorkOrder.EXPIRATION);
        
        sPackProjectionMap.put(HQME.Package._ID, HQME.Package._ID);
        sPackProjectionMap.put(HQME.Package.WOID, HQME.Package.WOID);
        sPackProjectionMap.put(HQME.Package.SOURCE_URL, HQME.Package.SOURCE_URL);
        sPackProjectionMap.put(HQME.Package.NAME, HQME.Package.NAME);
        sPackProjectionMap.put(HQME.Package.DATA, HQME.Package.DATA);
        sPackProjectionMap.put(HQME.Package.PERMISSIONS, HQME.Package.PERMISSIONS);
        sPackProjectionMap.put(HQME.Package.METADATAID, HQME.Package.METADATAID);
        
        sMetaDProjectionMap.put(HQME.Metadata._ID, HQME.Metadata._ID);
        sMetaDProjectionMap.put(HQME.Metadata.DATA, HQME.Metadata.DATA);
        sMetaDProjectionMap.put(HQME.Metadata.NAME, HQME.Metadata.NAME);
        sMetaDProjectionMap.put(HQME.Metadata.NAME, HQME.Metadata.WOID);
     //   sMetaDProjectionMap.put(HQME.Metadata.PACKAGEID, HQME.Metadata.PACKAGEID);
        
     
        
        sRULEProjectionMap.put(HQME.Policy._ID, HQME.Policy._ID);
    //    sWOProjectionMap.put(HQME.Rule.RULEID, HQME.Rule.RULEID);
        sRULEProjectionMap.put(HQME.Policy.POLICY_DATA, HQME.Policy.POLICY_DATA);
    }

}
