package de.evenosconsulting.util;

import java.util.concurrent.TimeUnit;

import org.compiere.util.CLogger;

/**
 * @author Jan Thielemann
 * @author jan.thielemann@evenos.de, www.evenos.de
 */

public class EvenosCommons {
	
	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(EvenosCommons.class);
	
	public static String timeAsStringSince(long starttime){
		long duration = System.currentTimeMillis() - starttime;
		long hours = TimeUnit.HOURS.convert(duration, TimeUnit.MILLISECONDS);
		long minutes = TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS)%60;
		long seconds = TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS)%60;
		
		//Hours
		String retVal = hours + ":";
		
		//Minutes
		if(minutes < 10){
			retVal += "0" + minutes + ":"; 
		}else{
			retVal += minutes + ":";
		}
		
		//Seconds
		if(seconds < 10){
			retVal += "0" + seconds; 
		}else{
			retVal += seconds;
		}
		
		log.finest("Time since " + starttime + " is " + retVal);
		
		return retVal;
	}

}
