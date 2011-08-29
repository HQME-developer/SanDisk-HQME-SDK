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


import com.hqme.cm.core.RuleCollection;
import com.hqme.cm.core.WorkOrder;

import java.util.HashMap;


/**
 * Parses strings representing logical combinations of PolicyExpressions (which themselves evaluate to Boolean)
 * 
 * This is what it would take to support the general syntax:
 * http://www.w3.org/TR/xquery-xpath-parsing/
 * http://www.antlr.org/grammar/1264460091565/XPath2.g (An ANTLR implementation of the XPath 2.0 Standard)
 * 
 * We don't do this!
 * 
 * What we do instead:
 * 1. Parse the document with the SAX parser. Create RuleCollection objects representing each of the Rules in a policy.
 * 
 * 2. Restrict the form of the XPath references we handle. They must be of the form:
 * a) String expression2 = "/Policy/Rule[@name=\"" + ruleName + "\"]";
 * b) String expression = "//Rule[@name=\"" + ruleName + "\"]";
 * c) true()
 * d) false()
 * 
 * 3. Identify which of the Rule elements in the Policy are used in either the Download or Cache elements.      
 *      Replace these XPath references with the string contained in the name attribute for that Rule element.
 * 4. This gives us the string that was given to this parser.
 * 5. Having traversed the string provided, we merge some tokens based on knowledge that certain name attribute 
 * strings exist that *ought* to be single tokens in this token vector.
 * 6. Parse string to create an Expression for which we can call the evaluate method later
 */
public class PolicyElementParser {
	public static Expression parse(String input, HashMap<String,RuleCollection> rules) throws HqmePolicyException {	    
		return new PolicyElementParser().parseString(input,rules);
	}

	Tokenizer tokens = null;
	private Token currentToken = null;	
	
	public Expression parseString(String input, HashMap<String,RuleCollection> rules) throws HqmePolicyException {
		tokens = new Tokenizer(input,rules);
		return parseTokens(rules);
	}
	
	private Expression parseTokens(HashMap<String,RuleCollection> rules ) throws HqmePolicyException {
		tokens.index = -1;
		getNextToken();  // increments tokens vector index and sets token to be the next token (the first in this case)		
		Expression expr = parseExpression(Precedence.NORMAL,rules);
		if (currentToken.type != Token.ENDSTRING)
			throw error("Could not parse the entire string provided", HqmePolicyException.INCOMPLETE);
		return expr;
	}

	private void getNextToken() {
		currentToken = tokens.incrementTokenReference();
	}

    private Expression parseExpression(Precedence precedence, HashMap<String, RuleCollection> rules) throws HqmePolicyException {
        Expression expr = parsePrimary(rules); // this advances the
        // token to the next position. If at the end of the list, the current token following
        // parsePrimary() will be an ENDSTRING
        while (true) {
            Operator operator;
            
            if (currentToken.type == Token.TOKEN && currentToken.contents.equals("and")) {                
                operator = Operator.AND;                
                if (operator.comparePrecedence(precedence)) {
                    break;
                }
            } else if (currentToken.type == Token.TOKEN && currentToken.contents.equals("or")) {                
                operator = Operator.OR;                
                if (operator.comparePrecedence(precedence)) {
                    break;
                }
            } else
                break;  // it is not one of our binary expressions
            getNextToken();
            expr = new LogicalExpression(operator, expr, parseExpression(operator.precedence(), rules));
        }
        

        return expr;
    }

    private Expression parsePrimary(HashMap<String, RuleCollection> rules) throws HqmePolicyException {
				
		switch (currentToken.type) {
		case Token.TOKEN: {
                if ("not".equals(currentToken.contents)) {
                    getNextToken();
                    checkOpenBracket();
                    Expression arg = parseExpression(Precedence.NORMAL, rules);
                    checkCloseBracket();
                    return new NegateExpression(arg);
                }
            
			PolicyExpression var = new PolicyExpression(currentToken.contents, rules);
			getNextToken();
			return var;
		}
		case '(': {
			// here we expect what would become the left hand expression, that is the token following '('
			// to be enclosed by a pair of
			// matching parentheses - if it isn't expect throws an Exception
			getNextToken();
			Expression enclosed = parseExpression(Precedence.NORMAL, rules);
			checkCloseBracket(); //  this throws an error if the following token is not a ')'
			return enclosed;  // normally would return the value of the parsed expression following a '('
		}
		case Token.ENDSTRING:
			throw error("Unexpected end of input", HqmePolicyException.UNEXPECTED_END_OF_INPUT);
		default:
			throw error("Not a valid expression", HqmePolicyException.INVALID_EXPRESSION);
		}
	}

	private HqmePolicyException error(String complaint, int reason) {
		return new HqmePolicyException(complaint, reason);
	}
	
	private void checkCloseBracket() throws HqmePolicyException {
		if (currentToken.type != ')')
			throw error("Expecting an ')' here",
					HqmePolicyException.UNEXPECTED_PARENTHESES);
		getNextToken();
	}
	
	// this is used with the 'not' NegateExpression elements
	private void checkOpenBracket() throws HqmePolicyException {
        if (currentToken.type != '(')
            throw error("Expecting an '(' here",
                    HqmePolicyException.UNEXPECTED_PARENTHESES);
        getNextToken();
    }

}
