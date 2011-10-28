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

package com.hqme.cm.core;

import android.content.Context;
import android.content.Intent;

import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmDate;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RULE_TIME extends RuleBase {
    // ==================================================================================================================================
    private static RULE_TIME sRULE_TIME_INSTANCE = new RULE_TIME();
    
    private static final int sMaxDuration = 24 * 60 * 60;
    private final String DAYS_PART = "\\d+[YMD]";
    private final String TIME_PART = "\\d+[HMS]";    
    private static final String DURATION = "-?P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+S)?)?"; 
    private static Pattern sDurationPattern;
    private static Pattern sDaysPattern;
    private static Pattern sTimePattern;

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        if ((arg0.getPackageName() + ".UPDATE_TIME").equals(arg1.getAction())
                | Intent.ACTION_TIMEZONE_CHANGED.equals(arg1.getAction())
                | Intent.ACTION_TIME_CHANGED.equals(arg1.getAction())
                | Intent.ACTION_DATE_CHANGED.equals(arg1.getAction())) {
            super.onReceive(arg0, arg1);
            
            
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_TIME getInstance() {
        return sRULE_TIME_INSTANCE;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {

        // multiple time periods per day may be specified
        // if the current time lies within one of those, the rule overall is satisfied
        if (rule.getValue() != null) {           
            String periods[] = rule.getValue().split("\\,");
            for (String period : periods) {                
                String[] parts = period.trim().split("\\s+");

                if (parts.length > 1) {                    
                        if (evaluateBetweenRule(parts[0].trim(), parts[1].trim()))
                            return true;                    
                } else {
                    if (evaluateBetweenRule(parts[0].trim(), null))
                        return true;
                }
            }
        }

        return false;
    }

    int duration(String durationTime) {
        
        String[] times = durationTime.split("T");
        
        Matcher mDays = sDaysPattern.matcher(times[0]); // get a matcher object
        int totalDuration = 0;
        while(mDays.find()) {
            int type = times[0].codePointAt(mDays.end()-1);
            Integer timeN = Integer.parseInt(times[0].substring(mDays.start(),mDays.end()-1));
            switch (type) {
                case 'D':
                    totalDuration += sMaxDuration * timeN;                                
                    break;                                
            }            
        }
        
        if (times.length > 1) {
            Matcher mTimes = sTimePattern.matcher(times[1]); 
            while (mTimes.find()) {
                int type = times[1].codePointAt(mTimes.end() - 1);
                Integer timeN = Integer.parseInt(times[1].substring(mTimes.start(),
                        mTimes.end() - 1));
                switch (type) {
                    case 'H':
                        totalDuration += timeN * 60 * 60;
                        break;
                    case 'M':
                        totalDuration += timeN * 60;
                        break;
                    case 'S':
                        totalDuration += timeN;
                        break;
                }

            }
        }
                
        return totalDuration;
    }
    
    
    public boolean evaluateBetweenRule(String startTime, String durationTime) {
        try {
            GregorianCalendar now = new GregorianCalendar();
            // this gives a calendar set at the time of day equivalent to the time string in
            // the startTime in the local timezone
            GregorianCalendar startCal = CmDate.localizeTime(startTime);            
            startCal.set(Calendar.DATE, now.get(Calendar.DATE));
            startCal.set(Calendar.MONTH, now.get(Calendar.MONTH));
            startCal.set(Calendar.YEAR, now.get(Calendar.YEAR));
            
            long startWindow = startCal.getTimeInMillis();
            long endWindow;
            
            if (durationTime != null) {
                int duration = duration(durationTime);
                if (duration == sMaxDuration) 
                    return true;
                startCal.add(Calendar.SECOND, duration);                
                // if date is > today, less one
                if (startCal.get(Calendar.DATE) > now.get(Calendar.DATE))
                    startCal.add(Calendar.DATE,-1);
                // end time today
                endWindow = startCal.getTimeInMillis();
                
            } else {
                // this is midnight, today
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.add(Calendar.DATE, 1);

                endWindow = startCal.getTimeInMillis();    
            }
            
            long currentTime = now.getTimeInMillis();
            
            if (startWindow <= currentTime) {
                if (endWindow > currentTime)
                    return true;
                if (endWindow < startWindow)
                    return true;
            } else if (endWindow < startWindow)
                if (currentTime < endWindow)
                    return true;
            
            return false;
        } catch (Exception fault) {
            CmClientUtil.debugLog(getClass(), "evaluateBetweenRule", fault);
        }
        
        return true;
    }


    @Override
    public void init(Context context) {
        
        sDurationPattern = Pattern.compile("-?P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+S)?)?");
        
        sDaysPattern = Pattern.compile(DAYS_PART);
        sTimePattern = Pattern.compile(TIME_PART);
        
        super.init(context);
    }

    @Override
    public boolean isValid(String value) {
        if (value == null)
            return false;

        boolean parsed = false;
        
        String periods[] = value.split("\\,");
        for (String period : periods) {
            String[] parts = period.trim().split("\\s+");
            
            try {
                parsed = false;

                // parts[0] is the xs:time
                if (parts.length > 2) 
					return false;

                if (parts.length == 2) { 
                    // parts[1] is the xs:duration (daytime)   , PnYnMnDTnHnMnS                    
                    // duration is limited to 24 hours.
                    // however, according to xs:duration spec, individual H/M/S values not limited 
                    // (could be whatever size).. it is the total that is limited here
                    
                    // The syntax is PnYnMnDTnHnMnS where each 'n' is a number.
                    // Y - years, M - months, D - days, H - hours, M - minutes, S -
                    // seconds.
                    // A minus sign prefix signifies a "negative duration".
                    // You can omit fields but if you mix years, months, or days
                    // with times, you need the 'T' separator.
                    // You need the 'T' to signify time periods.
                    // 30 minutes: PT30M
                    // 1 hour 30 minutes: PT1H30M
                    // PT12H30M
                    // P1D
                    
                    if (!sDurationPattern.matcher(parts[1]).matches())
                        return false;
                    // Split on T
                    String[] times = parts[1].split("T");
                    
                    if (times[0].startsWith("-"))
                        return false;
                    
                    
                    Matcher mDays = sDaysPattern.matcher(times[0]); // get a matcher object
                    int totalDuration = 0;
                    while(mDays.find()) {
                        int type = times[0].codePointAt(mDays.end()-1);
                        Integer timeN = Integer.parseInt(times[0].substring(mDays.start(),mDays.end()-1));
                        if (timeN <0)
                            return false;
                        switch (type) {
                            case 'Y':
                            case 'M':
                                if (timeN > 0)
                                    return false;
                                break;
                            case 'D':
                                if (timeN > 1) 
                                    return false;
                                totalDuration += sMaxDuration * timeN;                                
                                break;                                
                        }
                        
                    }
                    
                    if (times.length > 1) {
                        Matcher mTimes = sTimePattern.matcher(times[1]); 
                        while (mTimes.find()) {
                            int type = times[1].codePointAt(mTimes.end() - 1);
                            Integer timeN = Integer.parseInt(times[1].substring(mTimes.start(),
                                    mTimes.end() - 1));
                            if (timeN < 0)
                                return false;
                            switch (type) {
                                case 'H':
                                    totalDuration += timeN * 60 * 60;
                                    break;
                                case 'M':
                                    totalDuration += timeN * 60;
                                    break;
                                case 'S':
                                    totalDuration += timeN;
                                    break;
                            }

                            if (totalDuration > sMaxDuration)
                                return false;
                        }
                    }
                    
                    
                    
                }
                                                
                parsed = CmDate.parseableTime(parts[0]);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "isValid", fault);
            } finally {
                if (!parsed)
                    return false;
            }
        }

        return parsed;
    }

   
}
