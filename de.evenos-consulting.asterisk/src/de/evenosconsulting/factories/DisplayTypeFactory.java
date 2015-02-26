package de.evenosconsulting.factories;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import org.adempiere.base.IDisplayTypeFactory;
import org.compiere.util.Language;

/**
 * @author Jan Thielemann
 * @author jan.thielemann@evenos.de, www.evenos.de
 */

public class DisplayTypeFactory implements IDisplayTypeFactory{

	public static final int PhoneNumber = 200077;

	
	@Override
	public boolean isID(int displayType) {
		if(displayType == PhoneNumber)
			return false;
		return false;
	}

	@Override
	public boolean isNumeric(int displayType) {
		if(displayType == PhoneNumber)
			return false;
		return false;
	}

	@Override
	public Integer getDefaultPrecision(int displayType) {
//		if(displayType == PhoneNumber)
//			return new Integer(0);
		return null;
	}

	@Override
	public boolean isText(int displayType) {
		if(displayType == PhoneNumber)
			return true;
		return false;
	}

	@Override
	public boolean isDate(int displayType) {
		if(displayType == PhoneNumber)
			return false;
		return false;
	}

	@Override
	public boolean isLookup(int displayType) {
		if(displayType == PhoneNumber)
			return false;
		return false;
	}

	@Override
	public boolean isLOB(int displayType) {
		if(displayType == PhoneNumber)
			return false;
		return false;
	}

	@Override
	public DecimalFormat getNumberFormat(int displayType, Language language,
			String pattern) {
		return null;
	}

	@Override
	public SimpleDateFormat getDateFormat(int displayType, Language language,
			String pattern) {
		return null;
	}

	@Override
	public Class<?> getClass(int displayType, boolean yesNoAsBoolean) {
		if(displayType == PhoneNumber)
			return String.class;
		return null;
	}

	@Override
	public String getSQLDataType(int displayType, String columnName,
			int fieldLength) {
		if(displayType == PhoneNumber)		
			return "NVARCHAR2(" + fieldLength + ")";
		return null;
	}

	@Override
	public String getDescription(int displayType) {
		if(displayType == PhoneNumber)		
			return "String";
		return null;
	}

}
