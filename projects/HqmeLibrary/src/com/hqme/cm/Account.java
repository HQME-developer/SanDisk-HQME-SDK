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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class Account {
    public String      origin;
    public OriginACL   acl;
    public Property[]  properties;
    
    public static String toBase64String(Account acc) {
        String str = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(acc);
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

    public static Account fromBase64String(String str) {
        Account acl = null;
        try {     
            byte[] val = android.util.Base64.decode(str, android.util.Base64.DEFAULT);
            ByteArrayInputStream bis = new ByteArrayInputStream(val);
            ObjectInputStream ois = new ObjectInputStream(bis);
            acl = (Account) ois.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }   
        return acl;
    }
};
