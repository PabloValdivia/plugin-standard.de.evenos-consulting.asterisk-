package de.evenosconsulting.factories;

import java.sql.ResultSet;

import org.adempiere.base.IModelFactory;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.util.Env;

import de.evenosconsulting.model.MUser_Asterisk;

/**
 * @author Jan Thielemann
 * @author jan.thielemann@evenos.de, www.evenos.de
 */

public class AsteriskModelFactory implements IModelFactory{

	@Override
	public Class<?> getClass(String tableName) {
		if(tableName.equals(MUser.Table_Name))
			return MUser_Asterisk.class;
		
		return null;
	}

	@Override
	public PO getPO(String tableName, int Record_ID, String trxName) {
		if(tableName.equals(MUser.Table_Name))
			return new MUser_Asterisk(Env.getCtx(), Record_ID, trxName);
		
		return null;
	}

	@Override
	public PO getPO(String tableName, ResultSet rs, String trxName) {
		if(tableName.equals(MUser.Table_Name))
			return new MUser_Asterisk(Env.getCtx(), rs, trxName);
				
		return null;
	}

}
