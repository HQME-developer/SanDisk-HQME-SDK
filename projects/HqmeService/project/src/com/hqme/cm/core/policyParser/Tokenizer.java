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

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Vector;

import com.hqme.cm.core.RuleCollection;

class Tokenizer extends Vector<Token> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String policyString;
	static private final String specialChars = "()";

	int index = -1;

	public Tokenizer(String string,HashMap<String, RuleCollection> rules ) {
		this.policyString = string;
		int i = 0;
		do {
			i = traverseString(i);
		} while (i < policyString.length());
		
		this.merge(rules);
	}

	// this method moves along the indices of the string identifying where combinations of characters in it 
	// represent one of the pieces of the string we want to use as a token.
	// in this case:
	// 1) ignore whitespace
	// 2) make tokens of ( or ) where they appear
	// 3) any other combination of character we will group together as a TOKEN. In fact we have four types 
	// of TOKEN we care about - 'not','and','or', and other expressions that we 
	// will evaluate later. These other expressions will allow us to directly reference 
	// a RuleCollection which we will be able to evaluate when needed, or one of the prefedined XPath functions 
	// (support only true() and false() for now)
	private int traverseString(int i) {
		
		// find first non-whitespace index
		while (i < policyString.length() && Character.isWhitespace(policyString.charAt(i)))
			++i;
		
		
		if (i == policyString.length()) {
		// all whitespace
			return i;
		} else if (0 <= specialChars.indexOf(policyString.charAt(i))) {
			// if the char at i is ( or ) make it a token
			addElement(new Token(policyString.charAt(i),policyString, i, i + 1));
			return i + 1;
		} else {
			// create a token out of the next non-whitespace and non '(' or ')' chars		
			return createToken(i);
		} 
	}

	private int createToken(int i) {
		int start = i;
		while (i < policyString.length() 
				&& !(0 <= specialChars.indexOf(policyString.charAt(i))) 
				&& !Character.isWhitespace(policyString.charAt(i))  // not one of the operator chars and not a white space character
				)
			++i;
		addElement(new Token(Token.TOKEN, policyString, start, i));
		return i;
	}
	
	public Token incrementTokenReference() {
		++index;
		// if we are already at the end of the Vector, can't increment, in which case return an End token
		if (size() <= index)
			return new Token(Token.ENDSTRING, policyString,
					policyString.length(), policyString.length());
		return (Token) elementAt(index);
	}

	// ==================================================================================================================================
    // These methods are used to merge tokens from the input string that were split, 
	// but we actually want to be stuck together (because we know that they do form 
	// a token identifier, e.g. true())
	private boolean references(String otherString) {
        for (Token t : this) {
            if (t.compareTo(otherString) == 0) {
                return true;
            }
        }
        return false;
	}
	
	private int getStartTokenIndex(int found) {
	    for (Token t : this) {
            if (t.position <= found && found < t.position + t.contents.length()) {
                return this.indexOf(t);
            }
        }
	    
	    return -1;
	}
	
	private int getEndTokenIndex(int found, int length) {
	    for (Token t : this) {
            if (t.position <= found+length && found+length <= t.position + t.contents.length()) {
                return this.indexOf(t);
            }
        }
	    
        return -1;        
	}
	
    private void merge(HashMap<String, RuleCollection> rules) {
        
        // now merge any instances of known PolicyExpression string values if they have been split
        if (rules!= null && rules.size() > 0)
            for (Entry<String, RuleCollection> re : rules.entrySet()) {
                mergeTokens(re.getKey());
            }
        
        // merge other supported expressions that would have been split 
        mergeTokens("false()");
        mergeTokens("true()");
    }

    private void mergeTokens(String re) {
        if (!this.references(re) && policyString.indexOf(re) != -1) {
            // means the expression exists in the original string and must have been split, 
            // so we want to merge
            int startIndex = 0;
            int found = policyString.substring(startIndex).indexOf(re);
             while(found != -1) {
                 startIndex = found+re.length();
                 int keep = getStartTokenIndex(found);
                 int last = getEndTokenIndex(found, re.length());
                 this.removeRange(keep + 1, last+1);  // removeRange does not remove the element at the last index specified (hence last+1)
                 this.setElementAt(new Token(Token.TOKEN, re, this.elementAt(keep)),
                         keep);
                 // find the first token that is after the end of this
                 // instance, but don't look past the end of the string      
                found = -1;
                if (startIndex < policyString.length())
                    if ((found = policyString.substring(startIndex).indexOf(re)) > -1)
                        found += startIndex;                                 
             }
        }
    }
	
}

