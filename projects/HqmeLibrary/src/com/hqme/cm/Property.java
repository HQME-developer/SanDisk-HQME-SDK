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

import android.os.Parcel;
import android.os.Parcelable;

public class Property implements Parcelable
{
    public String key;
    public String value;

    public Property() {
    	this.key = new String();
    	this.value = new String();
    }

    public Property(String key, String value) {
        this.key = key;
        this.value = value;
    }

    private Property(Parcel in) {
    	this();    	   	
        readFromParcel(in);
    }

    @Override
	public boolean equals(Object o) {
    	if (o != null && o instanceof Property) {
    		Property pOther = (Property) o;
    		return (pOther.getKey().equals(key) && pOther.getValue().equals(value));
    	} else
    		return super.equals(o);
	}

	public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String setValue(String k, String v) {
        String returnValue = value;
        key = k;
        value = v;
        return returnValue;
    }
	
	public int describeContents() {		
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(key);
        dest.writeString(value);
	}

	public void readFromParcel(Parcel in)
    {
        key=in.readString();
        value=in.readString();
    }

    public static final Parcelable.Creator<Property> CREATOR = new Parcelable.Creator<Property>()
    {
        public Property createFromParcel(Parcel in)
        {
            return new Property(in);
        }

        public Property[] newArray(int size)
        {
            return new Property[size]; 
        }
    };
    
}
