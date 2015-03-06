package de.evenosconsulting.asterisk;

import java.util.HashMap;
import java.util.Map;

import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.AsteriskServer;
import org.asteriskjava.live.AsteriskServerListener;
import org.asteriskjava.live.ChannelState;
import org.asteriskjava.live.DefaultAsteriskServer;
import org.asteriskjava.live.ManagerCommunicationException;
import org.asteriskjava.live.MeetMeUser;
import org.asteriskjava.live.MeetMeUserState;
import org.asteriskjava.live.OriginateCallback;
import org.asteriskjava.manager.action.OriginateAction;
import org.compiere.model.MSession;
import org.compiere.model.MSysConfig;
import org.compiere.util.CCache;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;

import de.evenosconsulting.model.MUser_Asterisk;
import de.evenosconsulting.window.CallPopup;
import de.evenosconsulting.window.MeetMePopup;

public class Asterisk implements EventListener<Event> {

	public static final String ON_ASTERISK_CHANNEL_STATE_CHANGED = "onASTERISK_CHANNEL_STATE_CHANGED";
	public static final String ON_ASTERISK_CHANNEL_CHANGED = "onASTERISK_CHANNEL_CHANGED";
	public static final String ON_ASTERISK_CALLER_ID_CHANGED = "onASTERISK_CALLER_ID_CHANGED";
	public static final String ON_ASTERSK_MEET_ME_CHANGE = "onASTERSK_MEET_ME_CHANGE";

	public static final String ASTERISK_SIP_HOST = "de.evenos-consulting.asterisk.siphost";
	public static final String ASTERISK_SIP_USER = "de.evenos-consulting.asterisk.sipuser";
	public static final String ASTERISK_SIP_PASSWORD = "de.evenos-consulting.asterisk.sippassword";
	public static final String ASTERISK_SIP_PORT = "de.evenos-consulting.asterisk.sipport";
	public static final String ASTERISK_SIP_CONTEXT = "de.evenos-consulting.asterisk.sipcontext";
	public static final String ASTERISK_SIP_TYPE = "de.evenos-conuslting.asterisk.siptype"; // SIP or PJSIP
	public static final String ASTERISK_PHONE_PREFIX = "de.evenos-consulting.asterisk.phoneprefix";
	public static final String ASTERISK_MEET_ME_ROOM_MIN = "de.evenos-consulting.asterisk.meetme.min";
	public static final String ASTERISK_MEET_ME_ROOM_MAX = "de.evenos-consulting.asterisk.meetme.max";
	public static final String ASTERISK_MEET_ME_EXTEN = "de.evenos-consulting.asterisk.meetme.exten";

	private static Map<String, Asterisk> asterisks = new HashMap<String, Asterisk>();
	private static CCache<Integer, MUser_Asterisk> users = new CCache<Integer, MUser_Asterisk>("MUser_Asterisk_Asterisk_Cache", 10);
	private static CCache<Integer, MSession> sessions = new CCache<Integer, MSession>("MSession_Asterisk_Cache", 10);

	private AsteriskServer server;
	private AsteriskServerListener callListener = new CallServerListener(this);
	private AsteriskServerListener meetMeListener = new MeetMeServerListener(this);

	private Desktop desktop;

	private Map<AsteriskChannel, CallPopup> callPopups = new HashMap<AsteriskChannel, CallPopup>();
	private Map<MeetMeUser, MeetMePopup> meetMePopups = new HashMap<MeetMeUser, MeetMePopup>();

	private CLogger log = CLogger.getCLogger(Asterisk.class);
	private static CLogger classLog = CLogger.getCLogger(Asterisk.class);

	public Desktop getDesktop() {
		return desktop;
	}

	public Asterisk(Desktop desktop) {
		log.finest("Create new Asterisk for Desktop: " + desktop);
		this.desktop = desktop;
		if (server == null) {
			try {
				String siphost = MSysConfig.getValue(ASTERISK_SIP_HOST, "", Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));
				String sipuser = MSysConfig.getValue(ASTERISK_SIP_USER, "", Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));
				String sippassword = MSysConfig.getValue(ASTERISK_SIP_PASSWORD, "", Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));
				int sipport = MSysConfig.getIntValue(ASTERISK_SIP_PORT, 0, Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));

				// Connect to the asterisk server
				server = new DefaultAsteriskServer(siphost, sipport, sipuser, sippassword);
				server.addAsteriskServerListener(callListener);
				server.addAsteriskServerListener(meetMeListener);

				Env.getCtx().put("#Asterisk_Connected", true);
				log.info("Successfully connected to Asterisk Server");

				try {
					if (server.isModuleLoaded("app_meetme")) {
						Env.getCtx().put("#Asterisk_MeetMe_Enabled", true);
						log.info("Asterisk MeetMe Module is available");
					}
				} catch (ManagerCommunicationException e) {
					Env.getCtx().put("#Asterisk_MeetMe_Enabled", false);
					log.info("Asterisk MeetMe Module is not available!");
				}
			} catch (Exception excep) {
				// If an error occures while connecting, tell the context we are not connected to asterisk
				Env.getCtx().put("#Asterisk_Connected", false);
				Env.getCtx().put("#Asterisk_MeetMe_Enabled", false);
				log.warning("Couldn't connect to Asterisk. Check your configuration or contact an Administrator (Asterisk message: "
						+ excep.getLocalizedMessage() + ")");
			}
		}
	}

	public static Asterisk start(Desktop desktop, MSession po) {
		if (desktop == null || po == null)
			return null;
		Asterisk a = new Asterisk(desktop);
		asterisks.put(po.getWebSession(), a);
		return a;
	}

	public static void stop(MSession po) {
		Asterisk a = asterisks.get(po.getWebSession());
		if (a != null) {
			a.stop();
			asterisks.remove(po.getWebSession());
		}
	}

	public void stop() {
		log.finest("Stopping Asterisk: " + this);
		for (CallPopup popup : callPopups.values())
			popup.dispose();

		if (server != null) {
			server.removeAsteriskServerListener(callListener);
			server.removeAsteriskServerListener(meetMeListener);
			server.shutdown();
			server = null;
		}
	}

	private String getSIPChannelForCurrentUser(MSession optionalSession) {
		log.finest("Retrieving SIP Channel for Session: " + optionalSession);

		MSession session = optionalSession;
		if (session == null)
			session = getSession();

		MUser_Asterisk user = users.get(session.getCreatedBy());
		if (user == null) {
			user = new MUser_Asterisk(Env.getCtx(), session.getCreatedBy(), null);
			users.put(session.getCreatedBy(), user);
		}

		String sipchannel = user.getSIPChannel();
		String sipType = MSysConfig.getValue(ASTERISK_SIP_TYPE, "SIP") + "/";
		if (!sipchannel.startsWith(sipType)) {
			sipchannel = sipType + sipchannel;
		}

		log.finest("SIP-Channel for User " + user + " is " + sipchannel);

		return sipchannel;
	}

	private MSession getSession() {
		log.finest("Retrieving current Session");
		int ad_session_id = Env.getContextAsInt(Env.getCtx(), "#AD_Session_ID");

		MSession session = sessions.get(ad_session_id);
		if (session == null) {
			session = new MSession(Env.getCtx(), ad_session_id, null);
			if (!session.is_new())
				sessions.put(ad_session_id, session);
			else
				session = null;
		}

		log.finest("Current Session is " + session);

		return session;
	}

	public boolean isAsteriskChannelOfInterest(AsteriskChannel channel) {
		MSession session = getSession();
		String sipchannel = getSIPChannelForCurrentUser(session);
		boolean ofInterest = channel.getName().startsWith(sipchannel) || channel.getName().startsWith("AsyncGoto/" + sipchannel);
		log.finest("Interested in " + channel + ": " + ofInterest);
		return ofInterest;
	}

	public static void originateAsync(String numberToCall, OriginateCallback cb) {
		try {
			// Build a phone number which asterisk understands
			String callableNumber = getCallableNumber(numberToCall);

			String sipContext = MSysConfig.getValue(ASTERISK_SIP_CONTEXT, "", Env.getAD_Client_ID(Env.getCtx()),
					Env.getAD_Org_ID(Env.getCtx()));

			int ad_session_id = Env.getContextAsInt(Env.getCtx(), "#AD_Session_ID");
			MSession session = new MSession(Env.getCtx(), ad_session_id, null);
			Asterisk a = asterisks.get(session.getWebSession());

			OriginateAction action = new OriginateAction();
			action.setExten(callableNumber);
			String sipChannel = a.getSIPChannelForCurrentUser(session);
			action.setChannel(sipChannel);
			String sipExtension = sipChannel.substring(sipChannel.indexOf("/") + 1);
			action.setCallerId(Env.getContext(Env.getCtx(), "#AD_User_Name") + "<" + sipExtension + ">");
			action.setContext(sipContext);
			action.setPriority(1);
			action.setTimeout(20000L);

			a.server.originateAsync(action, cb);
		} catch (Exception e) {
			classLog.warning("Asterisk.originate() faild: " + e);
		}
	}

	public static String getCallableNumber(String numberToCall) {
		String callableNumber = null;
		if (!numberToCall.startsWith(MSysConfig.getValue(ASTERISK_SIP_TYPE, "SIP") + "/")) {
			String phonePrefix = MSysConfig.getValue(ASTERISK_PHONE_PREFIX, "", Env.getAD_Client_ID(Env.getCtx()),
					Env.getAD_Org_ID(Env.getCtx()));
			callableNumber = Util.isEmpty(phonePrefix, true) ? "" : phonePrefix;
			callableNumber += numberToCall;

			// FIXME: Remove this and let Asterisk Server decide how to handle international numbers (only for testing)
			callableNumber = callableNumber.replaceAll("[+]49", "0");

			// TODO: Use SysConfig switch to determine if + should get replaced e.g. de.evenos-consulting.asterisk.replacepluswithdoublezero
			callableNumber = callableNumber.replaceAll("[+]", "00");
			callableNumber = callableNumber.replaceAll("[^\\d]", "");
		} else {
			// Calling internal phones where SIP line equals the asterisk extension for internal calls, e. g. Phone2="SIP/50" then
			// extension 50 is called. Would also work with e. g. "SIP/John" and asterisk extension "John"
			int index = numberToCall.indexOf("/");
			if (index > -1)
				callableNumber = numberToCall.substring(index + 1);
		}
		return callableNumber;
	}

	@Override
	public void onEvent(Event evt) throws Exception {

		if (evt.getName().equals(ON_ASTERISK_CHANNEL_CHANGED)) {
			if (evt.getData() instanceof AsteriskChannelSwitch) {
				AsteriskChannelSwitch acswitch = (AsteriskChannelSwitch) evt.getData();
				CallPopup popup = callPopups.get(acswitch.oldChannel);
				if (popup != null) {
					callPopups.remove(acswitch.oldChannel);
					callPopups.put(acswitch.newChannel, popup);
					updateCallPopup(acswitch.newChannel);
				}
			}
		}

		if (evt.getName().equals(ON_ASTERISK_CHANNEL_STATE_CHANGED)) {
			updateCallPopup((AsteriskChannel) evt.getData());
		}

		if (evt.getName().equals(ON_ASTERISK_CALLER_ID_CHANGED)) {
			if (evt.getData() instanceof AsteriskChannelSwitch) {
				AsteriskChannelSwitch acswitch = (AsteriskChannelSwitch) evt.getData();
				updateCallPopup(acswitch.oldChannel);
			}
		}
		if (evt.getName().equals(ON_ASTERSK_MEET_ME_CHANGE)) {
			if (evt.getData() instanceof MeetMeUser) {
				MeetMeUser user = (MeetMeUser) evt.getData();
				if (isAsteriskChannelOfInterest(user.getChannel())) {
					updateMeetMePopup(user);
				}
				if (user.getRoom() != null)
					for (MeetMeUser usr : user.getRoom().getUsers()) {
						if (isAsteriskChannelOfInterest(usr.getChannel())) {
							updateMeetMePopup(usr);
						}
					}
			}
		}
	}

	private MeetMePopup createMeetMePopup(MeetMeUser user) {
		log.finest("Creating MeetMePopup for User: " + user);
		MeetMePopup popup = meetMePopups.get(user);
		if (popup == null) {
			popup = new MeetMePopup(desktop, user);
			meetMePopups.put(user, popup);
		}
		return popup;
	}

	private void updateMeetMePopup(MeetMeUser user) {
		log.finest("Updating MeetMePopup for User: " + user);
		MeetMePopup popup = meetMePopups.get(user);
		if (popup == null) {
			popup = createMeetMePopup(user);
		}

		Executions.schedule(desktop, popup, new Event(MeetMePopup.ON_MEETMEPOPUP_UPDATE_EVENT));

		if (user.getChannel().getState().equals(ChannelState.HUNGUP) || user.getState().equals(MeetMeUserState.LEFT))
			removeMeetMePopup(user);
	}

	private void removeMeetMePopup(MeetMeUser user) {
		log.finest("Removing MeetMePopup for User: " + user);
		MeetMePopup popup = meetMePopups.get(user);
		if (popup != null) {
			popup.dispose();
			popup = null;
		}
	}

	private CallPopup createCallPopup(AsteriskChannel channel) {
		log.finest("Creating CallPopup for Channel: " + channel);
		CallPopup popup = callPopups.get(channel);
		if (popup == null) {
			popup = new CallPopup(desktop, channel);
			callPopups.put(channel, popup);
		}
		return popup;
	}

	private void updateCallPopup(AsteriskChannel channel) {
		log.finest("Updating CallPopup for Channel: " + channel);
		CallPopup popup = callPopups.get(channel);
		if (popup == null) {
			popup = createCallPopup(channel);
		}

		Executions.schedule(desktop, popup, new Event(CallPopup.ON_CALLPOPUP_UPDATE_TITLE_EVENT));
		Executions.schedule(desktop, popup, new Event(CallPopup.ON_CALLPOPUP_UPDATE_STATUS_EVENT, null, channel.getState()));
		Executions.schedule(desktop, popup, new Event(CallPopup.ON_CALLPOPUP_ENABLE_TRANSFER, null, channel));

		if (channel.getState().equals(ChannelState.HUNGUP))
			removeCallPopup(channel);
	}

	private void removeCallPopup(AsteriskChannel channel) {
		log.finest("Removing CallPopup for Channel: " + channel);
		CallPopup popup = callPopups.get(channel);
		if (popup != null) {
			popup.dispose();
			popup = null;
		}
	}

}
