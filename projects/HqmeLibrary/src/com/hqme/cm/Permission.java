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

public final class Permission {
    static int PERMISSION_READ_MASK   = 0x00000001; 
    static int PERMISSION_MODIFY_MASK = 0x00000003; 
    static int PERMISSION_DELETE_MASK = 0x00000004;
    
    private final int mPermission;
    
    public Permission(int permission) {
        this.mPermission = permission;                   
    }

    public boolean canRead() {
        return (mPermission & PERMISSION_READ_MASK) != 0;
    }
    
    public boolean canModify() {
        return (mPermission & PERMISSION_MODIFY_MASK) != 0;
    }
    
    public boolean canDelete() {
        return (mPermission & PERMISSION_DELETE_MASK) != 0;
    }
};