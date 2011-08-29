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

class Token {
    public static final int ENDSTRING = -1;
    public static final int TOKEN = -2;
    public final int type; // type is a +ve integer representing a char, 
    // or a negative value reflecting what kind of token it is (i.e. a regular TOKEN or a terminating ENDSTRING) 

    public final String contents;

    public final int position;

	public Token(int code, String input, int start, int end) {
		this.type = code;  		
		this.contents = input.substring(start, end);		
		this.position = start;				
	}

	Token(int code,String sval, Token token) {
		this.type = code;
		this.contents = sval;		
		this.position = token.position;
	}

	int compareTo(String otherString) {
	    return this.contents.compareTo(otherString);
	}
	
}