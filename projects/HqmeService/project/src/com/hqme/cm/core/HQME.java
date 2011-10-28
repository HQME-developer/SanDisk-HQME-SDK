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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.hqme.cm.QueueRequestState;

import java.util.ArrayList;

final public class HQME {
    
    public static final String AUTHORITY = "com.hqme.cm.HQME";
    
    /**
     * The default sort order for this table
     */
    public static final String DEFAULT_SORT_ORDER = "modified DESC";
    
   
    public static int delete(Context context, Uri content_uri,Long id)
    {
        Uri uri = Uri.withAppendedPath(content_uri, id.toString());
        return context.getContentResolver().delete(uri, null, null);
    }
    //TODO change to WO object. there is no WOID here yet!!
    
   
    private static com.hqme.cm.core.Record[] getRecords(Context context, Uri content_uri, String[] projection, String id_field_desc, String data_field_desc)
    {
       
        com.hqme.cm.core.Record[] rec = null;
               
        Cursor managedCursor = context.getContentResolver().query(
                content_uri,
                projection, // Which columns to return                          
                null,       // Which rows to return (all rows)                         
                null,       // Selection arguments (none)                         
                // Put the results in ascending order by name                         
                null);//HQME.DEFAULT_SORT_ORDER);
        
        
        if (managedCursor.moveToFirst())
        {
            
            ArrayList<com.hqme.cm.core.Record> records = new ArrayList<com.hqme.cm.core.Record>();
            
            int dataColumnName = managedCursor.getColumnIndex(data_field_desc); 
            int idColumnName = managedCursor.getColumnIndex(id_field_desc);
                                 
            do{
                
                long woid = 0;
                if(idColumnName != -1)
                    //the column exists
                    woid = managedCursor.getLong(idColumnName);
                
                String data = managedCursor.getString(dataColumnName);
                
                records.add(new com.hqme.cm.core.Record(data, woid));
          
            }while(managedCursor.moveToNext());
            
            rec = (records.toArray(new com.hqme.cm.core.Record[records.size()]));
           
       } 
        
        managedCursor.close();
        
        return rec;
        
    }

    /**
     * WorkOrder table
     */
    public static final class WorkOrder implements BaseColumns {
        
        // The content:// style URL for this table
        public static final Uri CONTENT_URI
                = Uri.parse("content://" + AUTHORITY + "/workorder");
        
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.hqme.workorder";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.hqme.workorder";

         
        public static final String WOID = "id";

        
        public static final String STATE = "state";

        
        public static final String APP_UUID = "app_uid";
        
        public static final String USERPERMISSIONS = "userpermissions";
        
        public static final String GROUPPERMISSIONS = "grouppermissions";
        
        public static final String WORLDPERMISSIONS = "worldpermissions";
        
        public static final String GROUP = "groupOrigins";
        
        public static final String EXPIRATION = "expiration";
               
        public static final String DATA = "data";
        
    
        public static Long insert(Context context, com.hqme.cm.core.WorkOrder wo)
        {
            //insert wo
            Long woid = insert(context, wo.getQueueRequestState().name(), wo.getClientUid(), wo.getExpiration(), wo.toString(), wo.getUserPermissions(), wo.getGroupPermissions(), wo.getWorldPermissions(), wo.getGroupPropString());
            
            //insert packages
            for(com.hqme.cm.core.Package pack : wo.getPackages())
            {
                Package.insert(context, woid, pack);
            }
            
            //TODO insert metadata
            
            return woid;
            
        }
        
        public static Long insert(Context context, String queueRequestState, String uuid, long expiration, String data, int userpermissions, int grouppermissions, int worldpermissions, String group)
        {
            ContentValues values = new ContentValues();
            
            values.put(STATE, queueRequestState);
            values.put(APP_UUID, uuid.getBytes());
            values.put(USERPERMISSIONS, userpermissions);
            values.put(GROUPPERMISSIONS, grouppermissions);
            values.put(WORLDPERMISSIONS, worldpermissions);
            values.put(GROUP, group);
            values.put(EXPIRATION, expiration);
            values.put(DATA, data);
            
            Uri uri = context.getContentResolver().insert(CONTENT_URI, values);
            //will return the new woid
            return new Long(uri.getPathSegments().get(1)).longValue();
          
           
        }
        
        public static int delete(Context context, Long id)
        {
            return HQME.delete(context, CONTENT_URI, id);
        }
        
        private static Long[] getRecordIds(Context context, Uri contentUri, String selection)
        {
           Long[] result = null;
            
            String[] projection = new String[] {
                    
                    WOID
            }; 
            
           
            Cursor managedCursor = context.getContentResolver().query(
                    contentUri,
                    projection, // Which columns to return                          
                    selection,                         
                    null,     
                    null);//HQME.DEFAULT_SORT_ORDER);
            
            if(managedCursor == null)
                return null;
            
            if (managedCursor.moveToFirst())
            {
                
                ArrayList<Long> records = new ArrayList<Long>();
                
                int woidColumnName = managedCursor.getColumnIndex(WOID);
              
                do{
                    long woid = managedCursor.getLong(woidColumnName);
                    
                    records.add( new Long(woid));
                    
                                      
                    
                }while(managedCursor.moveToNext());
                
                result = (records.toArray(new Long[records.size()]));
            }
            
            managedCursor.close();
            return result;
        }
        
        
        
        private static com.hqme.cm.core.WorkOrder[] getRecords(Context context, Uri contentUri, QueueRequestState[] filter, String permissionFilters)
        {
            com.hqme.cm.core.WorkOrder[] result = null;
            
            String[] projection = new String[] {
                    
                    WOID,                    
                    STATE,
                    APP_UUID,
                    USERPERMISSIONS,
                    GROUPPERMISSIONS,
                    WORLDPERMISSIONS,
                    GROUP,
                    EXPIRATION,
                    DATA
            }; 
            
            StringBuilder selection = null;
            // first adding the filter related to state of the work orders
            if(filter != null && filter.length > 0)
            {
               selection = new StringBuilder();
               
                
                for(int i=0;i< filter.length ;++i)
                {
                    if(selection.length() > 0)
                        selection.append(" or ");                    
                    selection.append(HQME.WorkOrder.STATE + " like '" + filter[i].name() +"'");
                }
            }
            
            // next, adding the filters related to the permissions necessary to return this content
            if(permissionFilters != null  && !"".equals(permissionFilters))
            {
                if (selection == null)
                    selection = new StringBuilder();
                else if(selection.length() > 0)
                    selection.append(" and ");

                selection.append(permissionFilters);
            }

            
            Cursor managedCursor = context.getContentResolver().query(
                    contentUri,
                    projection, // Which columns to return                          
                    selection != null ? selection.toString():null,                         
                    null,     
                    null);//HQME.DEFAULT_SORT_ORDER);
            
            if(managedCursor == null)
                return null;
            
            if (managedCursor.moveToFirst())
            {
                
                ArrayList<com.hqme.cm.core.WorkOrder> records = new ArrayList<com.hqme.cm.core.WorkOrder>();
                
                int woidColumnName = managedCursor.getColumnIndex(WOID);
               // int stateColumnName = managedCursor.getColumnIndex(STATE);
                int uuidColumnName = managedCursor.getColumnIndex(APP_UUID);
              //  int expColumnName = managedCursor.getColumnIndex(EXPIRATION);
                int dataColumnName = managedCursor.getColumnIndex(DATA);
                int userPermissionsColumnName = managedCursor.getColumnIndex(USERPERMISSIONS);
                int groupPermissionsColumnName = managedCursor.getColumnIndex(GROUPPERMISSIONS);
                int worldPermissionsColumnName = managedCursor.getColumnIndex(WORLDPERMISSIONS);
                int groupColumnName = managedCursor.getColumnIndex(GROUP);
                      
                                                    
                do{
                    long woid = 0;
                    if(woidColumnName != -1)
                        woid = managedCursor.getLong(woidColumnName);
                    
                    com.hqme.cm.core.WorkOrder wo = new com.hqme.cm.core.WorkOrder(managedCursor.getString(dataColumnName),woid);
                    byte[] blob = managedCursor.getBlob(uuidColumnName);
                    wo.setClientUid(new String(blob));
                    
                    int permission;
                    permission = managedCursor.getInt(userPermissionsColumnName);
                    wo.setUserPermissions(permission);
                    permission = managedCursor.getInt(groupPermissionsColumnName);
                    wo.setGroupPermissions(permission);
                    permission = managedCursor.getInt(worldPermissionsColumnName);
                    wo.setWorldPermissions(permission);
                    blob = managedCursor.getBlob(groupColumnName);
                    wo.setGroupProp(new String(blob));
                    
                    records.add( wo);
                    
                                      
                    
                }while(managedCursor.moveToNext());
                
                result = (records.toArray(new com.hqme.cm.core.WorkOrder[records.size()]));
               
           } 
            
            managedCursor.close();
            
            return result;
        }
              
        public static final QueueRequestState[] active_filter = {
            QueueRequestState.ACTIVE,
            QueueRequestState.WAITING,
            QueueRequestState.QUEUED,
            QueueRequestState.BLOCKED           
        };
      
        public static final QueueRequestState[] non_completed_filter = {
            QueueRequestState.ACTIVE,
            QueueRequestState.WAITING,
            QueueRequestState.QUEUED,
            QueueRequestState.BLOCKED,
            QueueRequestState.SUSPENDED
        };
      
        public static Long[] getRecordIds(Context context, String selection)
        {
            return getRecordIds(context, CONTENT_URI, selection);
        }
        
        
        public static Long getRecordId(Context context, Long Id,String selection)
        {
            Uri recordUri = Uri.withAppendedPath(CONTENT_URI, Id.toString());
            
            // filters are the Action and the visibility Filter
            Long[] res = getRecordIds(context, recordUri,  selection);
            
            if(res != null && res.length > 0)
            {
                return res[0];
            }
            else
                return null;                
        }
        
        public static Long[] getRecordIdsState(Context context, String queueRequestState, String permissionFilter)
        {
            
            StringBuilder selection = new StringBuilder();

            selection.append(HQME.WorkOrder.STATE + " like '" + queueRequestState +"'");
            // next, adding the filters related to the permissions necessary to return this content
            if(permissionFilter != null  && !"".equals(permissionFilter))
            {
                if(selection.length() > 0)
                    selection.append(" and ");

                selection.append("(" + permissionFilter + ")");
            }
            
            return getRecordIds(context, CONTENT_URI, selection.toString());
        }
        
        public static Long[] getRecordIds(Context context, QueueRequestState[] filter)
        {

            StringBuilder selection = null;

            if(filter != null && filter.length > 0)
            {

                selection = new StringBuilder();

                for(int i=0;i< filter.length ;++i)
                {
                    if(selection.length() > 0)
                        selection.append(" or ");
                    selection.append(HQME.WorkOrder.STATE + " like '" + filter[i].name() +"'");
                }
            }

            return getRecordIds(context, CONTENT_URI, selection.toString());
        }

       
        public static com.hqme.cm.core.WorkOrder getRecord(Context context, Long Id)
        {
            Uri recordUri = Uri.withAppendedPath(CONTENT_URI, Id.toString());
            
            // filters are the Action and the visibility Filter
            com.hqme.cm.core.WorkOrder[] res = getRecords(context, recordUri, null, null);
            
            if(res != null && res.length > 0)
            {
                return res[0];
            }
            else
                return null;
                
        }
                
        public static com.hqme.cm.core.WorkOrder getRecord(Context context, Long Id, String visibilityFilter)
        {
            Uri recordUri = Uri.withAppendedPath(CONTENT_URI, Id.toString());
            
            com.hqme.cm.core.WorkOrder[] res = getRecords(context, recordUri, null, visibilityFilter);
            
            if(res != null && res.length > 0)
            {
                return res[0];
            }
            else
                return null;
                
        }
        
        public static int update(Context context, com.hqme.cm.core.WorkOrder wo)
        {
            Long woid = wo.getDbIndex();
                        
            int result = update(context, woid,  wo.getQueueRequestState().name(), wo.getClientUid().getBytes(), wo.getExpiration(), wo.toString(), wo.getUserPermissions(), wo.getGroupPermissions(), wo.getWorldPermissions(), wo.getGroupPropString());
            
            //insert packages
            for(com.hqme.cm.core.Package pack : wo.getPackages())
            {
                Package.update(context, woid, pack);
            }
            
            //TODO insert metadata
            
            return result;
                       
        }
        
        public static int update(Context context, Long id, String queueRequestState, byte[] uuid, long expiration, String data, int userpermission, int grouppermission, int worldpermission, String group)
        {
            ContentValues values = new ContentValues();
            
            values.put(STATE, queueRequestState);
            values.put(APP_UUID, uuid);
            values.put(USERPERMISSIONS, userpermission);
            values.put(GROUPPERMISSIONS, grouppermission);
            values.put(WORLDPERMISSIONS, worldpermission);
            values.put(GROUP, group);
            values.put(EXPIRATION, expiration);
            values.put(DATA, data);
           
            Uri uri = Uri.withAppendedPath(CONTENT_URI, id.toString());
            
            return context.getContentResolver().update(uri, values, null, null);
        }
    }
    
    public static final class Package implements BaseColumns {

        // The content:// style URL for this table
        public static final Uri CONTENT_URI
                = Uri.parse("content://" + AUTHORITY + "/package");
        
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.hqme.package";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.hqme.package";

         
        public static final String WOID = "id";

        
        public static final String SOURCE_URL = "source_url";

        
        public static final String NAME = "name";
        
       
        public static final String METADATAID = "metadataid";
        
        public static final String PERMISSIONS = "permissions";
        
        public static final String DATA = "data";
        
        public static Long insert(Context context, Long woid, com.hqme.cm.core.Package pack)
        {
            //TODO missing permissions and metadatais
            return insert(context, woid, pack.getSourceUri().toString(), pack.getSourceLocalPath(), -1, null,pack.toString());
        }
        
        public static Long insert(Context context, Long woid, String source_url, String name, int metadataid, byte[] permissions,String data)
        {
            ContentValues values = new ContentValues();
            
            values.put(WOID, woid);
            values.put(SOURCE_URL, source_url);
            values.put(NAME, name);
            values.put(METADATAID, metadataid);
            values.put(PERMISSIONS, permissions);
            values.put(DATA, data);
            
            Uri uri = context.getContentResolver().insert(CONTENT_URI, values);
            
            return new Long(uri.getPathSegments().get(1)).longValue();
          
           
        }
        
        public static int delete(Context context, Long id)
        {
            return HQME.delete(context, CONTENT_URI, id);
        }
        
        public static int update(Context context, Long woid, com.hqme.cm.core.Package pack)
        {
            return update(context, pack.getDbIndex(),woid, pack.getSourceUri().toString(), pack.getSourceLocalPath(),0, null, pack.toString());
        }
        
        public static int update(Context context,Long id, Long woid, String source_url, String name, int metadataid, byte[] permissions,String data)
        {
            ContentValues values = new ContentValues();
            
            values.put(WOID, woid);
            values.put(SOURCE_URL, source_url);
            values.put(NAME, name);
            values.put(METADATAID, metadataid);
            values.put(PERMISSIONS, permissions);
            values.put(DATA, data);
           
            Uri uri = Uri.withAppendedPath(CONTENT_URI, id.toString());
            
            return context.getContentResolver().update(uri, values, null, null);
        }
        
    }
    
    public static final class Metadata implements BaseColumns {
        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI
                = Uri.parse("content://" + AUTHORITY + "/package");
        
        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.hqme.metadata";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.hqme.metadata";

           
        public static final String WOID = "woid";

        
        public static final String NAME = "name";

        
        public static final String DATA = "data";
        
                
        public static Long insert(Context context, Long woid, String name, String data)
        {
            ContentValues values = new ContentValues();
            
            values.put(WOID, woid);
            values.put(NAME, name);
            values.put(DATA, data);
            
            Uri uri = context.getContentResolver().insert(CONTENT_URI, values);
            
            return new Long(uri.getPathSegments().get(1)).longValue();
          
           
        }
        
        public static int delete(Context context, Long id)
        {
            return HQME.delete(context, CONTENT_URI, id);
        }
        
        public static int update(Context context,Long id, Long woid, String name, String data)
        {
            ContentValues values = new ContentValues();
            
            values.put(WOID, woid);
            values.put(NAME, name);
            values.put(DATA, data);
           
            Uri uri = Uri.withAppendedPath(CONTENT_URI, id.toString());
            
            return context.getContentResolver().update(uri, values, null, null);
        }
        
       
    }
    
   
    
    public static final class Policy implements BaseColumns {
        // The content:// style URL for this table
        public static final Uri CONTENT_URI
                = Uri.parse("content://" + AUTHORITY + "/policy");

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.hqme.policy";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.hqme.policy";
 
        
        /**
         * The Catalog id
         * <P>Type: TEXT</P>
         */
      //  public static final String RULEID = "id";

         /**
         * The work order itself
         * <P>Type: INTEGER (long)</P>
         */
        public static final String POLICY_DATA = "data";
        
        //TODO change to receive the Rule object
        public static Long insert(Context context, String data)
        {
            ContentValues values = new ContentValues();
            
            values.put(POLICY_DATA, data);
            
            Uri uri = context.getContentResolver().insert(CONTENT_URI, values);
            
            return new Long(uri.getPathSegments().get(1)).longValue();
           
        }
        
        public static int update(Context context, Long id, String data)
        {
            ContentValues values = new ContentValues();
            
            values.put(POLICY_DATA, data);
           
            Uri uri = Uri.withAppendedPath(CONTENT_URI, id.toString());
            
            return context.getContentResolver().update(uri, values, null, null);
        }
        
        public static int delete(Context context, Long id)
        {
            return HQME.delete(context, CONTENT_URI, id);
        }
        
        public static com.hqme.cm.core.Policy getRecord(Context context, Long Id)
        {
            String[] projection = new String[] {
                    
                    POLICY_DATA
            };
            
            Uri recordUri = Uri.withAppendedPath(CONTENT_URI, Id.toString());
            
            com.hqme.cm.core.Record[] res = HQME.getRecords(context, recordUri, projection, _ID, POLICY_DATA);
            
            if(res != null && res.length > 0)
            {
                return new com.hqme.cm.core.Policy(res[0].toString(),res[0].getDbIndex());
            }
            else
                return null;
        }
        
        public static com.hqme.cm.core.Policy[] getRecords(Context context)
        {
            String[] projection = new String[] {
                    
                    POLICY_DATA
            };
            
            Record[] records = HQME.getRecords(context, CONTENT_URI, projection,_ID, POLICY_DATA);
            
            if(records != null)
            {
                com.hqme.cm.core.Policy[] rules = new com.hqme.cm.core.Policy[records.length];
                for(int i = 0;i < records.length; ++i)
                {
                    rules[i] = new com.hqme.cm.core.Policy(records[i].toString(), records[i].getDbIndex());
                    String strXML = records[i].toString();
                    rules[i] = new com.hqme.cm.core.Policy(strXML, records[i].getDbIndex());
                }
                
                return rules;
            }
            else
                return null;
                        
        }
        
       
    }
   
}



