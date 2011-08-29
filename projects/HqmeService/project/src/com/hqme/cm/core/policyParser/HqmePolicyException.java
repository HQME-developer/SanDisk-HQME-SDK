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

package com.hqme.cm.core.policyParser;

import org.xml.sax.SAXException;

public class HqmePolicyException extends SAXException {
	 
    //==================================================================================================================================
    /**
     * serialVersionUID is needed for sub-class serialization
     */
    private static final long serialVersionUID = -3875970860513224406L;
    
    private int errCode;     
    public static final int INCOMPLETE = 0; // couldn't parse entire expression that was given
	public static final int INVALID_EXPRESSION = 1;
	public static final int UNEXPECTED_END_OF_INPUT = 2;
	public static final int UNEXPECTED_PARENTHESES = 3;	

	public HqmePolicyException(String complaint, int errCode) {
		super(complaint);		
		this.errCode = errCode;
	}
	
    public String toString() {
        return String.format("%s: %d \"%s\"", getClass().getName(), errCode, 
                 getMessage());
    }
}

