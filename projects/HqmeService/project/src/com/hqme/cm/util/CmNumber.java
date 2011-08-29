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

package com.hqme.cm.util;

import java.util.ArrayList;

public class CmNumber {
    public static Short parseShort(String number, Short parseFailedValue) {
        if (number != null && number.length() > 0)
            try {
                return Short.parseShort(number);
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(CmNumber.class, "parseShort", fault);
            }
        return parseFailedValue;
    }

    public static Integer parseInt(String number, Integer parseFailedValue) {
        if (number != null && number.length() > 0)
            try {
                return Integer.parseInt(number);
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(CmNumber.class, "parseInt", fault);
            }
        return parseFailedValue;
    }

    public static Integer decodeInt(String number, Integer parseFailedValue) {
        if (number != null && number.length() > 0)
            try {
                return Integer.decode(number);
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(CmNumber.class, "decodeInt", fault);
            }
        return parseFailedValue;
    }

    public static Long parseLong(String number, Long parseFailedValue) {
        if (number != null && number.length() > 0)
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(CmNumber.class, "parseLong", fault);
            }
        return parseFailedValue;
    }

    public static Float parseFloat(String number, Float parseFailedValue) {
        if (number != null && number.length() > 0)
            try {
                return Float.parseFloat(number);
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(CmNumber.class, "parseFloat", fault);
            }
        return parseFailedValue;
    }

    public static Double parseDouble(String number, Double parseFailedValue) {
        if (number != null && number.length() > 0)
            try {
                return Double.parseDouble(number);
            } catch (NumberFormatException fault) {
                CmClientUtil.debugLog(CmNumber.class, "parseDouble", fault);
            }
        return parseFailedValue;
    }

    protected static String toHexDotString(Object value) {
        StringBuilder result = new StringBuilder();
        if (value != null) {
            String valueHex = "";
            String valueClass = value.getClass().getSimpleName();

            // for efficiency, the following if-then-else tree is in order of
            // most commonly-used number class
            if ("Integer".equals(valueClass))
                valueHex = Integer.toHexString((Integer) value);
            else if ("Long".equals(valueClass))
                valueHex = Long.toHexString((Long) value);
            else if ("Short".equals(valueClass))
                valueHex = Integer.toHexString((Short) value);
            else if ("Boolean".equals(valueClass))
                valueHex = (Boolean) value ? "01" : "00";

            if ((valueHex.length() & 1) != 0)
                valueHex = "0".concat(valueHex);

            if (valueHex.length() > 0)
                result.append(valueHex.charAt(0));

            if (valueHex.length() > 1)
                result.append(valueHex.charAt(1));

            for (int i = 2; i < valueHex.length(); i += 2)
                result.append('.').append(valueHex.charAt(i)).append(valueHex.charAt(i + 1));
        }
        return result.toString();
    }

    public static int[] convertIntegers(ArrayList<Integer> integers) 
    { 
        int len = integers.size();
        int[] res = new int[len]; 
        for (int i = 0; i < len; i++) { 
            res[i] = integers.get(i).intValue(); 
        } 
        return res; 
    } 

}
