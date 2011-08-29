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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class CmDate extends Date {
    private static final long serialVersionUID = 0xffffffff80000000L;

    public static final CmDate EPOCH = new CmDate(0L); // oldest representable

    // date

    public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.S'Z'"){/**
                 * 
                 */
                private static final long serialVersionUID = 5472802126875452124L;

            { this.setLenient(false);}}; // "2010-06-10T01:23:45.6Z

    public static final SimpleDateFormat HTTP_FORMATTER = new SimpleDateFormat(
            "EEE, d MMM yyyy HH:mm:ss z"){/**
                 * 
                 */
                private static final long serialVersionUID = 5609518106533259574L;

            { this.setLenient(false);}}; // "Thu, 10 Jun 2010 01:23:45 GMT";


    public static final SimpleDateFormat XSTIME_FORMATTER = new SimpleDateFormat("HH:mm:ss") {
        /**
         * 
         */
        private static final long serialVersionUID = 890826141419683983L;

        {
            this.setLenient(false);            
        }
    };

    public static final SimpleDateFormat XSTIME_TIMEZONE_FORMATTER = new SimpleDateFormat("HH:mm:ssZZ") {
        /**
         * 
         */
        private static final long serialVersionUID = -1862682906175075713L;

        {
            this.setLenient(false);            
        }
    };

    public static final SimpleDateFormat XSTIME_UTC_FORMATTER = new SimpleDateFormat("HH:mm:ss'Z'") {
        /**
         * 
         */
        private static final long serialVersionUID = -1862682906175075713L;

        {
            this.setLenient(false);
            this.setTimeZone(TimeZone.getTimeZone("UTC"));
            
        }
    };

    public static final SimpleDateFormat XSDATETIME_UTC_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") {
        /**
         * 
         */
        private static final long serialVersionUID = -1862682906175075713L;

        {
            this.setLenient(false);
            this.setTimeZone(TimeZone.getTimeZone("UTC"));
            
        }
    };

    public static final SimpleDateFormat XSDATETIME_TIMEZONE_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ssZZ") {
        /**
                 * 
                 */
        private static final long serialVersionUID = 7375898374243912577L;

        {
            this.setLenient(false); 
        }
    };

    public static final SimpleDateFormat XSDATETIME_FORMATTER = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss") {
        /**
         * 
         */
        private static final long serialVersionUID = -5943625063926869167L;

        {
            this.setLenient(false);            
        }
    };


    // ==================================================================================================================================
    public CmDate() {       
        super();
    }

    public CmDate(String date) {
        super(date == null ? HTTP_FORMATTER.format(EPOCH) : DATE_FORMATTER.format(date));

    }

    public CmDate(Date date) {
        super(date == null ? EPOCH.getTime() : date.getTime());
    }

    public CmDate(Long milliseconds) {
        super(milliseconds);
    }

    public CmDate(long milliseconds) {
        super(milliseconds);
    }

    public CmDate(int year, int month, int day) {
        super(year, month, day);
    }

    public CmDate(int year, int month, int day, int hour, int minute) {
        super(year, month, day, hour, minute);
    }

    public CmDate(int year, int month, int day, int hour, int minute, int second) {
        super(year, month, day, hour, minute, second);
    }

    // ==================================================================================================================================
    public static final String ensureFormattedDateString(String date) {
        try {
            return CmDate.valueOf(CmDate.valueOf(date));
        } catch (Exception fault) {
            CmClientUtil.debugLog(CmDate.class, "ensureFormattedDateString", fault);
        }
        return "";
    }

    // ==================================================================================================================================
    public static final CmDate valueOf(String date) {
        if (date != null && date.length() > 0)
            try { // used internally
                return new CmDate(DATE_FORMATTER.parse(date));
            } catch (ParseException firstFault) {
                try { // used for date Modified
                    return new CmDate(HTTP_FORMATTER.parse(date));
                } catch (ParseException sixthFault) {
                    CmClientUtil.debugLog(CmDate.class, "valueOf", sixthFault);
                }
            }
      
        return EPOCH;
    }

    // ==================================================================================================================================
    public static GregorianCalendar localizeDateTime(String date) {
        if (date != null && date.length() > 0) {
            GregorianCalendar cal = new GregorianCalendar();
            try { // used by RULE_SCHEDULE, RULE_MANDATORY_TIME
                Date formattedDate = XSDATETIME_UTC_FORMATTER.parse(date);         
                cal.setTime(formattedDate);
                return cal;
            } catch (ParseException firstFault) {
                try { // used by RULE_SCHEDULE, RULE_MANDATORY_TIME
                    Date formattedDate = XSDATETIME_TIMEZONE_FORMATTER.parse(date);                    
                    cal.setTime(formattedDate);
                    return cal;
                } catch (ParseException secondFault) {
                    try { // used by RULE_SCHEDULE, RULE_MANDATORY_TIME
                        Date formattedDate = XSDATETIME_FORMATTER.parse(date);                        
                        cal.setTime(formattedDate);
                        return cal;
                    } catch (ParseException thirdFault) {
                        CmClientUtil.debugLog(CmDate.class, "localizeDateTime", secondFault);
                    }
                }
            }
        }
            
        return null;
    }

    // ==================================================================================================================================
    public static boolean parsableDateTime(String date) {
        if (date != null)
            try { // used by RULE_SCHEDULE, RULE_MANDATORY_TIME
                XSDATETIME_UTC_FORMATTER.parse(date);
                return true;
            } catch (ParseException firstFault) {
                try { // used by RULE_SCHEDULE, RULE_MANDATORY_TIME
                    XSDATETIME_TIMEZONE_FORMATTER.parse(date);
                    return true;
                } catch (ParseException secondFault) {
                    try { // used by RULE_SCHEDULE, RULE_MANDATORY_TIME
                        XSDATETIME_FORMATTER.parse(date);
                        return true;
                    } catch (ParseException thirdFault) {
                        CmClientUtil.debugLog(CmDate.class, "parsableDateTime", secondFault);
                    }
                }
            }

        return false;
    }

    // ==================================================================================================================================
    public static GregorianCalendar localizeTime(String date) {
        if (date != null && date.length() > 0) {
            GregorianCalendar now = new GregorianCalendar();                
            try { // used by RULE_TIME                
                Date formattedDate = XSTIME_UTC_FORMATTER.parse(date);
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTime(formattedDate);
                if (now.getTimeZone().inDaylightTime(now.getTime()))
                    cal.add(Calendar.MILLISECOND, now.getTimeZone().getDSTSavings());
                return cal;
            } catch (ParseException firstFault) {
                try { // used by RULE_TIME
                    Date formattedDate = XSTIME_TIMEZONE_FORMATTER.parse(date);
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(formattedDate);                    
                    if (now.getTimeZone().inDaylightTime(now.getTime()))
                        cal.add(Calendar.MILLISECOND, now.getTimeZone().getDSTSavings());
                    return cal;
                } catch (ParseException secondFault) {
                    try { // used by RULE_TIME
                        Date localTime = XSTIME_FORMATTER.parse(date);                                                
                        now.setTime(localTime);                        
                        return now;
                    } catch (ParseException thirdFault) {
                        CmClientUtil.debugLog(CmDate.class, "localizeTime", thirdFault);
                    }
                }
            }
        }

        return null;
    }

    // ==================================================================================================================================
    public static boolean parseableTime(String date) {
        if (date != null)
            try { // used by RULE_TIME
                XSTIME_UTC_FORMATTER.parse(date);
                return true;
            } catch (ParseException firstFault) {
                try { // used by RULE_TIME
                    XSTIME_TIMEZONE_FORMATTER.parse(date);
                    return true;
                } catch (ParseException secondFault) {
                    try { // used by RULE_TIME
                        XSTIME_FORMATTER.parse(date);
                        return true;
                    } catch (ParseException thirdFault) {
                        CmClientUtil.debugLog(CmDate.class, "parseableTime", secondFault);
                    }
                }
            }

        return false;
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static final String valueOf(Date date) {
        return date == null || EPOCH.equals(date) ? "" : DATE_FORMATTER.format(date);
    }

    // ==================================================================================================================================
    @Override
    public String toString() {
        return CmDate.valueOf(this);
    }
    // ==================================================================================================================================
}
