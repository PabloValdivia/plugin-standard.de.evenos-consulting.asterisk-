package de.evenosconsulting.asterisk;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.AsteriskQueueEntry;
import org.asteriskjava.live.AsteriskServer;
import org.asteriskjava.live.AsteriskServerListener;
import org.asteriskjava.live.ChannelState;
import org.asteriskjava.live.DefaultAsteriskServer;
import org.asteriskjava.live.MeetMeUser;
import org.asteriskjava.live.OriginateCallback;
import org.asteriskjava.live.internal.AsteriskAgentImpl;
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

public class Asterisk implements AsteriskServerListener, EventListener<Event>, PropertyChangeListener {

	private final static String ON_ASTERISK_CHANNEL_STATE_CHANGED = "onASTERISK_CHANNEL_STATE_CHANGED";
	private final static String ON_ASTERISK_CHANNEL_CHANGED = "onASTERISK_CHANNEL_CHANGED";
	private final static String ON_ASTERISK_CALLER_ID_CHANGED = "onASTERISK_CALLER_ID_CHANGED";

	private static Map<String, Asterisk> asterisks = new HashMap<String, Asterisk>();
	private static CCache<Integer, MUser_Asterisk> users = new CCache<Integer, MUser_Asterisk>("MUser_Asterisk_Asterisk_Cache", 10);
	private static CCache<Integer, MSession> sessions = new CCache<Integer, MSession>("MSession_Asterisk_Cache", 10);

	private AsteriskServer server;
	private Desktop desktop;
	private Map<AsteriskChannel, CallPopup> popups = new HashMap<AsteriskChannel, CallPopup>();
	private CLogger log = CLogger.getCLogger(Asterisk.class);
	private static CLogger classLog = CLogger.getCLogger(Asterisk.class);

	private class AsteriskChannelSwitch {
		AsteriskChannel oldChannel;
		AsteriskChannel newChannel;

		public AsteriskChannelSwitch(AsteriskChannel oldChannel, AsteriskChannel newChannel) {
			this.oldChannel = oldChannel;
			this.newChannel = newChannel;
		}
	}

	public Asterisk(Desktop desktop) {
		log.finest("Create new Asterisk for Desktop: " + desktop);
		this.desktop = desktop;
		if (server == null) {
			try {
				String siphost = MSysConfig.getValue("de.evenos-consulting.asterisk.siphost", "", Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));
				String sipuser = MSysConfig.getValue("de.evenos-consulting.asterisk.sipuser", "", Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));
				String sippassword = MSysConfig.getValue("de.evenos-consulting.asterisk.sippassword", "",
						Env.getAD_Client_ID(Env.getCtx()), Env.getAD_Org_ID(Env.getCtx()));
				int sipport = MSysConfig.getIntValue("de.evenos-consulting.asterisk.sipport", 0, Env.getAD_Client_ID(Env.getCtx()),
						Env.getAD_Org_ID(Env.getCtx()));

				// Connect to the asterisk server
				server = new DefaultAsteriskServer(siphost, sipport, sipuser, sippassword);
				server.addAsteriskServerListener(this);
				Env.getCtx().put("#Asterisk_Connected", true);
				log.info("Successfully connected to Asterisk Server");
			} catch (Exception excep) {
				// If an error occures while connecting, tell the context we are not connected to asterisk
				Env.getCtx().put("#Asterisk_Connected", false);
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
		for (CallPopup popup : popups.values())
			popup.dispose();

		if (server != null) {
			server.removeAsteriskServerListener(this);
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
		if (!sipchannel.startsWith("SIP/") && !sipchannel.startsWith("PJSIP/"))
			sipchannel = "SIP/" + sipchannel;

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

	@Override
	public void onNewAsteriskChannel(AsteriskChannel channel) {
		if (isAsteriskChannelOfInterest(channel)) {
			log.finest("New AsteriskChannel of interest: " + channel);
			channel.addPropertyChangeListener(this);
		}
	}

	private boolean isAsteriskChannelOfInterest(AsteriskChannel channel) {
		MSession session = getSession();
		String sipchannel = getSIPChannelForCurrentUser(session);
		return channel.getName().startsWith(sipchannel);
	}

	@Override
	public void onNewMeetMeUser(MeetMeUser user) {
	}

	@Override
	public void onNewAgent(AsteriskAgentImpl agent) {
	}

	@Override
	public void onNewQueueEntry(AsteriskQueueEntry entry) {
	}

	public static void originateAsync(String numberToCall, OriginateCallback cb) {
		try {
			// Build a phone number which asterisk understands
			String phonePrefix = MSysConfig.getValue("de.evenos-consulting.asterisk.phoneprefix", "", Env.getAD_Client_ID(Env.getCtx()),
					Env.getAD_Org_ID(Env.getCtx()));
			String callableNumber = Util.isEmpty(phonePrefix, true) ? "" : phonePrefix;
			callableNumber += numberToCall;
			callableNumber = callableNumber.replaceAll("[+]49", "0"); // TODO: Remove this and let Asterisk Server decide how to handle
																		// international phone numbers
			callableNumber = callableNumber.replaceAll("[+]", "00");
			callableNumber = callableNumber.replaceAll("[^\\d]", "");

			String sipContext = MSysConfig.getValue("de.evenos-consulting.asterisk.sipcontext", "", Env.getAD_Client_ID(Env.getCtx()),
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

	@Override
	public void onEvent(Event evt) throws Exception {

		if (evt.getName().equals(ON_ASTERISK_CHANNEL_CHANGED)) {
			if (evt.getData() instanceof AsteriskChannelSwitch) {
				AsteriskChannelSwitch acswitch = (AsteriskChannelSwitch) evt.getData();
				CallPopup popup = popups.get(acswitch.oldChannel);
				if (popup != null) {
					popup.setAsteriskChannel(acswitch.newChannel);
					popups.remove(acswitch.oldChannel);
					popups.put(acswitch.newChannel, popup);
					String title = acswitch.newChannel.getCallerId().getNumber();
					updatePopup(acswitch.newChannel, title);
				}
			}
		}

		if (evt.getName().equals(ON_ASTERISK_CHANNEL_STATE_CHANGED)) {
			updatePopup((AsteriskChannel) evt.getData(), null);
		}

		if (evt.getName().equals(ON_ASTERISK_CALLER_ID_CHANGED)) {
			if (evt.getData() instanceof AsteriskChannelSwitch) {
				AsteriskChannelSwitch acswitch = (AsteriskChannelSwitch) evt.getData();
				String title = acswitch.newChannel.getCallerId().getNumber();
				updatePopup(acswitch.oldChannel, title);
			}
		}

	}

	private void removePopup(AsteriskChannel channel) {
		log.finest("Removing CallPopup for Channel: " + channel);
		CallPopup popup = popups.get(channel);
		if (popup != null) {
			popup.dispose();
			popup = null;
		}
	}

	private void updatePopup(AsteriskChannel channel, String title) {
		log.finest("Updating CallPopup for Channel: " + channel + " with Title: " + title);
		CallPopup popup = popups.get(channel);
		if (popup == null) {
			popup = createPopup(channel);
		}

		if (title != null && title.length() > 0)
			Executions.schedule(desktop, popup, new Event(CallPopup.ON_CALLPOPUP_UPDATE_TITLE_EVENT, null, title));

		Executions.schedule(desktop, popup, new Event(CallPopup.ON_CALLPOPUP_UPDATE_STATUS_EVENT, null, channel.getState()));

		if (channel.getState().equals(ChannelState.HUNGUP)) {
			removePopup(channel);
		}

	}

	private CallPopup createPopup(AsteriskChannel channel) {
		log.finest("Creating CallPopup for Channel: " + channel);
		CallPopup popup = popups.get(channel);
		if (popup == null) {
			popup = new CallPopup(desktop, channel);
			popups.put(channel, popup);
		}
		return popup;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {

		log.finest("Property " + evt.getPropertyName() + " changed to " + evt.getNewValue());

		final Desktop desktop = asterisks.get(getSession().getWebSession()).desktop;

		// For each state change of the channel we need to display the popup and/or change its state and time but
		// we only can manipulate UI components in onEvent() so here we just fire some events and handle them in onEvent()
		if (evt.getPropertyName().equals("state") && evt.getNewValue() instanceof ChannelState
				&& evt.getSource() instanceof AsteriskChannel) {
			AsteriskChannel channel = (AsteriskChannel) evt.getSource();
			ChannelState state = (ChannelState) evt.getNewValue();
			Executions.schedule(desktop, Asterisk.this, new Event(ON_ASTERISK_CHANNEL_STATE_CHANGED, null, evt.getSource()));
			if (state.equals(ChannelState.HUNGUP)) {
				channel.removePropertyChangeListener(this);
			}
		}

		// When we make a Call, we first register to the channel Asterisk-Server->Own Phone. As soon as the own phone is picked up,
		// we are interested in the channel Own Phone -> Destination Number. So here we simply switch the channels we are listening on
		if (evt.getPropertyName().equals("dialedChannel")) {
			if (evt.getSource() instanceof AsteriskChannel && evt.getNewValue() instanceof AsteriskChannel) {
				AsteriskChannel dialingChannel = (AsteriskChannel) evt.getSource();
				AsteriskChannel dialedChannel = (AsteriskChannel) evt.getNewValue();

				if (dialingChannel.getDialedChannel().equals(dialedChannel)) {
					dialingChannel.removePropertyChangeListener(this);
					dialedChannel.addPropertyChangeListener(this);
					Executions.schedule(desktop, Asterisk.this, new Event(ON_ASTERISK_CHANNEL_CHANGED, null, new AsteriskChannelSwitch(
							dialingChannel, dialedChannel)));
				}
			}
		}

		// When a call comes in, the AsteriskChannel is Extern->Server. The Server then creates a new channel Server->Own Phone. Since
		// we only register for channels which fits the currents users sip channel and the both channels are linked to each other only
		// as soon as we pickup the own phone, we need another way to get the callers number (for displaying and reverse lookup for example)
		// so all we have to do to identify the (extern) caller is to listen to the "dialingChannel" property of the Server->Own Phone
		// channel. But if we make a call to an external number this event gets also fired. Thats why we check if we have a asterisk channel
		// of interes (own channel) and only then update the caller id.
		if (evt.getPropertyName().equals("dialingChannel")) {
			if (evt.getSource() instanceof AsteriskChannel && evt.getNewValue() instanceof AsteriskChannel) {
				AsteriskChannel dialedChannel = (AsteriskChannel) evt.getSource();
				AsteriskChannel dialingChannel = (AsteriskChannel) evt.getNewValue(); // Here we have the caller id
				if (isAsteriskChannelOfInterest(dialedChannel))
					Executions.schedule(desktop, Asterisk.this, new Event(ON_ASTERISK_CALLER_ID_CHANGED, null, new AsteriskChannelSwitch(
							dialedChannel, dialingChannel)));
			}
		}

	}
}
