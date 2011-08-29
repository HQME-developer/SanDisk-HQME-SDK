/* NOT A CONTRIBUTION */

package com.hqme.cm.core;

import android.content.Context;
import android.content.Intent;

import com.hqme.cm.util.CmClientUtil;
import com.hqme.cm.util.CmDate;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/*
 RULE_MANDATORY_TIME is for downloads that must take place within specified time windows.
 WorkOrders requiring a mandatory download window are given the highest priority.
 
 Multiple windows may be set in the rule's value string.
 Maximum duration of a single time window is twenty four hours.
 If a duration is not specified the window ends at midnight (local-time).
   
 RULE_MANDATORY_TIME dateTime duration(,dateTime duration...)    
 As described in [cite http://www.w3.org/TR/xmlschema-2/#dateTime]above in section 2.5.8
 */

public class RULE_MANDATORY_TIME extends RuleBase {
    // ==================================================================================================================================
    private static RULE_MANDATORY_TIME sRULE_MANDATORY_TIME = new RULE_MANDATORY_TIME();
    // ==================================================================================================================================

    private static final int sMaxDuration = 24 * 60 * 60;
    private final String DAYS_PART = "\\d+[YMD]";
    private final String TIME_PART = "\\d+[HMS]";    
    private static final String DURATION = "-?P(\\d+Y)?(\\d+M)?(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+S)?)?"; 
    private static Pattern sDurationPattern;
    private static Pattern sDaysPattern;
    private static Pattern sTimePattern;

    private class TimeWindow {
        long startWindow;
        long endWindow;        

        public TimeWindow(String windowString) {
            super();

            String[] parts = windowString.split("\\s+");              
            GregorianCalendar startCal = CmDate.localizeDateTime(parts[0]);
            
            this.startWindow = startCal.getTimeInMillis();            
                                
            if (parts.length > 1) {
                // if currentTime < parts[0] create an alert at the parts[0] time
                // if currentTime < parts[1] create an alert at the parts[1] time
                int duration = duration(parts[1]);
                startCal.add(Calendar.SECOND, duration);                
                this.endWindow = startCal.getTimeInMillis();                    
            } else {
               // if currentTime < parts[0] create an alert at the parts[0] time
                // this is midnight, on the day specified in the start dateTime
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.add(Calendar.DATE, 1);

                this.endWindow = startCal.getTimeInMillis();    
            }
        }

    }
    
    @Override
    public void onReceive(Context arg0, Intent arg1) {
        if ((arg0.getPackageName() + ".MANDATORY_TIME_ALERT_START").equals(arg1.getAction()) || 
                (arg0.getPackageName() + ".MANDATORY_TIME_ALERT_END").equals(arg1.getAction())) {
            super.onReceive(arg0, arg1);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------------------------
    public static RULE_MANDATORY_TIME getInstance() {
        return sRULE_MANDATORY_TIME;
    }

    // ==================================================================================================================================
    // Rule Evaluations - supporting specific pre-defined rules
    // ==================================================================================================================================
    @Override
    public boolean evaluateRule(Rule rule, WorkOrder workOrder) {

        // multiple time periods per day may be specified
        // if the current time lies within one of those, the rule overall is satisfied
        if (rule.getValue() != null) {            
            String periods[] = rule.getValue().split(",");
            for (String period : periods) {
                TimeWindow window = getInstance().new TimeWindow(period.trim());
                if (inDownloadWindow(window.startWindow, window.endWindow))
                    return true;
            }
        }

        return false;
    }

    static int duration(String durationTime) {
        
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
    
    
    public static boolean inDownloadWindow(long startWindow, long endWindow) {
        try {                       
            long currentTime = new GregorianCalendar().getTimeInMillis();
            
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
            CmClientUtil.debugLog(getInstance().getClass(), "inDownloadWindow", fault);
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

        for (String period : value.split(",")) {
            String[] parts = period.split("\\s+");
            
            try {
                parsed = false;

                // parts[0] is the xs:time
                
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
                                                               
                parsed = CmDate.parsableDateTime(parts[0]);
            } catch (Exception fault) {
                CmClientUtil.debugLog(getClass(), "isValid", fault);
            } finally {
                if (!parsed)
                    return false;
            }

        }

        return parsed;
    }

    public static boolean createAlerts(String broadcastWindows, long woid ) {
        
        // multiple time periods per day may be specified
        // if the current time lies within one of those, the rule overall is
        // satisfied
        boolean alertsCreated = false;
        if (broadcastWindows != null) {            
            String periods[] = broadcastWindows.split(",");
            int i=0;
            for (String period : periods) {
                // we know that the string is correctly formatted at this point
                TimeWindow window = getInstance().new TimeWindow(period.trim());
                if (WorkOrderManager.getInstance().broadcastMandatoryTimeAlert(window.startWindow,
                        window.endWindow,i,woid))
                    alertsCreated = true;
                i++;
            }
        }
        
        return alertsCreated;
    }

}