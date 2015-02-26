package de.evenosconsulting.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.compiere.model.MUser;
import org.compiere.model.X_C_BPartner;
import org.compiere.util.CCache;
import org.compiere.util.Env;

/**
 * @author Jan Thielemann
 * @author jan.thielemann@evenos.de, www.evenos.de
 */

public class MUser_Asterisk extends MUser{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6820598929778013013L;

	public MUser_Asterisk(Properties ctx, int AD_User_ID, String trxName) {
		super(ctx, AD_User_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MUser_Asterisk(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}
	
	public MUser_Asterisk(X_C_BPartner partner) {
		super(partner);
		// TODO Auto-generated constructor stub
	}

	/**	Cache					*/
	static private CCache<Integer,MUser_Asterisk> s_cache = new CCache<Integer,MUser_Asterisk>(Table_Name, 30, 60);
	public static MUser_Asterisk get (Properties ctx, int AD_User_ID)
	{
		Integer key = new Integer(AD_User_ID);
		MUser_Asterisk retValue = (MUser_Asterisk)s_cache.get(key);
		if (retValue == null)
		{
			retValue = new MUser_Asterisk (ctx, AD_User_ID, null);
			if (AD_User_ID == 0)
			{
				String trxName = null;
				retValue.load(trxName);	//	load System Record
			}
			s_cache.put(key, retValue);
		}
		return retValue;
	}	//	get
	
	public static MUser_Asterisk get (Properties ctx)
	{
		return get(ctx, Env.getAD_User_ID(ctx));
	}	//	get
	

	/** Column name SIPChannel */
    public static final String COLUMNNAME_SIPChannel = "SIPChannel";
    
	/** Get SIP Channel.
	@return SIP Channel	  */
    public String getSIPChannel () 
	{
		return (String)get_Value(COLUMNNAME_SIPChannel);
	}

}
