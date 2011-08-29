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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import android.os.Parcel;
import android.os.Parcelable;

public enum QueueRequestState {
    UNDEFINED(0), QUEUED(1), ACTIVE(2), BLOCKED(3), WAITING(4), COMPLETED(5), SUSPENDED(6);

    private final int state; 

    QueueRequestState(int val) {
        this.state = val;
    }

    public int state() {
        return state;
    }
    
    private static final Map<Integer, QueueRequestState> mapVals = new HashMap<Integer, QueueRequestState>();
    static {
        for (QueueRequestState s : EnumSet.allOf(QueueRequestState.class))
            mapVals.put(s.state(), s);
    }
    
    public static QueueRequestState get(int state) { 
        return mapVals.get(state); 
    }
    	               
    public int describeContents() {
        return 0;
    }    
    
    public void writeToParcel(Parcel dest, int flags) {       
        dest.writeInt(state);
    }

    public static final Parcelable.Creator<QueueRequestState> CREATOR = new Parcelable.Creator<QueueRequestState>() {
        public QueueRequestState createFromParcel(Parcel in) {
            return QueueRequestState.get(in.readInt());
        }

        public QueueRequestState[] newArray(int size) {
        	  return new QueueRequestState[size];
        }
    };
}
