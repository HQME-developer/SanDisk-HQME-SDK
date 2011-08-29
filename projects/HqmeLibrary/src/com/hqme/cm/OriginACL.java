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

package com.hqme.cm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

import com.hqme.cm.Permission;

public final class OriginACL {
    private Permission mUserPermission;
    private Permission mGroupPermission;
    private Permission mWorldPermission;
    private String[] mGroupMemberOrigins;
    
    public OriginACL(Permission user, Permission group, Permission world, String[] group_members) {
        this.mUserPermission = user;
        this.mGroupPermission = group;
        this.mWorldPermission = world;
        this.mGroupMemberOrigins = group_members;
    }
    
    public boolean canUserRead() {
        return mUserPermission.canRead();
    }
    
    public boolean canUserModify() {
        return mUserPermission.canModify();
    }
    
    public boolean canUserDelete() {
        return mUserPermission.canDelete();
    }
    
    public boolean canGroupRead() {
        return mGroupPermission.canRead();
    }
    
    public boolean canGroupModify() {
        return mGroupPermission.canModify();
    }
    
    public boolean canGroupDelete() {
        return mGroupPermission.canDelete();
    }
    
    public boolean canWorldRead() {
        return mWorldPermission.canRead();
    }
    
    public boolean canWorldModify() {
        return mWorldPermission.canModify();
    }
    
    public boolean canWorldDelete() {
        return mWorldPermission.canDelete();
    }
    
    public boolean isGroupMember(String origin) {
        for (int i = 0; i < mGroupMemberOrigins.length; i++) {
            if (mGroupMemberOrigins[i].equals(origin))
                return true;
        }
        return false;
    }
    
    public static String toBase64String(OriginACL acl) {
        String str = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(acl);
            oos.flush(); 
            oos.close(); 
            bos.close();
            byte [] val = bos.toByteArray();
            str = android.util.Base64.encodeToString(val, android.util.Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str; 
    }

    public static OriginACL fromBase64String(String str) {
        OriginACL acl = null;
        try {     
            byte[] val = android.util.Base64.decode(str, android.util.Base64.DEFAULT);
            ByteArrayInputStream bis = new ByteArrayInputStream(val);
            ObjectInputStream ois = new ObjectInputStream(bis);
            acl = (OriginACL) ois.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }   
        return acl;
    }
};