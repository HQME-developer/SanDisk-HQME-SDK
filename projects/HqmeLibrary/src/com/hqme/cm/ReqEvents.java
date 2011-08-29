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

public class ReqEvents implements Parcelable, EventsNotify {
    public static enum ReqEvent {
    	NULL_EVENT(0),
    	REQUEST_COMPLETED(1),
    	REQUEST_SUSPENDED(2),         
        REQUEST_RESUMED(3),        
        REQUEST_CONTENT_AVAILABLE(4);
  
        private static final Map<Integer, ReqEvent> mapVals = new HashMap<Integer, ReqEvent>();
		static {
			for (ReqEvent s : EnumSet.allOf(ReqEvent.class))
				mapVals.put(s.getCode(), s);
		}

        private final int code;        
        
        ReqEvent(int val) {
            this.code = val;                   
        }
 
        public int getCode() { 
            return code; 
        } 
     
        @Override 
        public String toString() { 
            return this.name();            
        }
        
        public static ReqEvent get(int code) { 
            return mapVals.get(code); 
        }
    }
    
    private ReqEvent event; 
    private long requestId;
    
    public ReqEvent getEvent() {
        return event;
    }

    private int progressPercent;
    private String progressStatus;
    
    public ReqEvents(ReqEvent rEvent, long requestId,int progressPercent,String progressStatus) {
        this.event = rEvent; 
        this.requestId = requestId;
        this.progressPercent = progressPercent;
        this.progressStatus = progressStatus;
    } 
    
    public ReqEvents(ReqEvent rEvent, long requestId) {
        this.event = rEvent; 
        this.requestId = requestId;
        this.progressPercent = 0;
        this.progressStatus = null;
	}
   
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((event == null) ? 0 : event.hashCode());
		result = prime * result + (int) (requestId ^ (requestId >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ReqEvents other = (ReqEvents) obj;
		if (event == null) {
			if (other.event != null)
				return false;
		} else if (!event.equals(other.event))
			return false;
		if (requestId != other.requestId)
			return false;
		return true;
	}

	@Override 
    public String toString() { 
        return event + ": " + requestId; 
    }
                
    public int describeContents() {
        return 0;
    }

    public void readFromParcel(Parcel in)
    {
        event = ReqEvent.mapVals.get(in.readInt());
        requestId =in.readLong();
        setProgressPercent(in.readInt());
        progressStatus = in.readString();
    }
    
    public void writeToParcel(Parcel dest, int flags) {
        
        dest.writeInt(event != null ?  event.getCode() : 0);
        dest.writeLong(requestId);
        dest.writeInt(progressPercent);
        dest.writeString(progressStatus);
    }

    public static final Parcelable.Creator<ReqEvents> CREATOR = new Parcelable.Creator<ReqEvents>() {
        public ReqEvents createFromParcel(Parcel in) {
            return new ReqEvents(in);
        }

        public ReqEvents[] newArray(int size) {
            throw new UnsupportedOperationException(); 
        }
    };
    
    private ReqEvents(Parcel in) {
        readFromParcel(in);
    }  

    public ReqEvents() {
    }

	public void setProgressPercent(int progressPercent) {
		this.progressPercent = progressPercent;
	}

	public int getProgressPercent() {
		return progressPercent;
	}
	
	public void setProgressStatus(String progressStatus) {
		this.progressStatus = progressStatus;
	}

	public String getProgressStatus() {
		return progressStatus;
	}

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }
}