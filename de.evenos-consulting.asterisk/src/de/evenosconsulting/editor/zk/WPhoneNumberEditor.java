package de.evenosconsulting.editor.zk;

import java.beans.PropertyChangeEvent;

import org.adempiere.webui.ValuePreference;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.editor.WEditor;
import org.adempiere.webui.editor.WEditorPopupMenu;
import org.adempiere.webui.event.ContextMenuEvent;
import org.adempiere.webui.event.ContextMenuListener;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.window.FDialog;
import org.adempiere.webui.window.WFieldRecordInfo;
import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.ChannelState;
import org.asteriskjava.live.LiveException;
import org.asteriskjava.live.OriginateCallback;
import org.compiere.model.GridField;
import org.compiere.model.MCountry;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import de.evenosconsulting.asterisk.Asterisk;

/**
 * @author jan.thielemann@evenos.de www.evenos.de
 * 
 **/
public class WPhoneNumberEditor extends WEditor implements ContextMenuListener, OriginateCallback {

	// Events for Asterisk/worker thread
	public static String ON_CHANGE_CALL_ICON_TO_CALL_EVENT = "onCHANGE_CALL_ICON_TO_CALL_EVENT";
	public static String ON_CHANGE_CALL_ICON_TO_ENDCALL_EVENT = "onCHANGE_CALL_ICON_TO_ENDCALL_EVENT";
	public static String ON_CHANGE_CALL_ICON_ENABLE_EVENT = "onCHANGE_CALL_ICON_ENABLE";
	public static String ON_CHANGE_CALL_ICON_DISABLE_EVENT = "onCHANGE_CALL_ICON_DISABLE";

	// List of Event Listeners
	private static final String[] LISTENER_EVENTS = { Events.ON_CLICK, Events.ON_CHANGE, Events.ON_OK, ON_CHANGE_CALL_ICON_DISABLE_EVENT,
			ON_CHANGE_CALL_ICON_ENABLE_EVENT, ON_CHANGE_CALL_ICON_TO_CALL_EVENT, ON_CHANGE_CALL_ICON_TO_ENDCALL_EVENT, };

	// Logger
	private static CLogger log = CLogger.getCLogger(WPhoneNumberEditor.class);

	//
	private String oldValue;

	private AsteriskChannel asteriskChannel;

	boolean dialing;

	Desktop desktop = AEnv.getDesktop();

	/**
	 * 
	 * @param gridField
	 */
	public WPhoneNumberEditor(GridField gridField) {
		super(new PhoneNumberBox(), gridField);
		getComponent().setButtonImage("/theme/default/images/Call16.png");

		popupMenu = new WEditorPopupMenu(false, false, isShowPreference());
		popupMenu.addMenuListener(this);
		addChangeLogMenu(popupMenu);
	}

	@Override
	public String getDisplay() {
		return getComponent().getText();
	}

	@Override
	public Object getValue() {
		return getComponent().getText();
	}

	@Override
	public void setValue(Object value) {
		if (value == null) {
			oldValue = null;
			getComponent().setText("");
		} else {
			oldValue = String.valueOf(value);
			getComponent().setText(oldValue);
		}
	}

	@Override
	public PhoneNumberBox getComponent() {
		return (PhoneNumberBox) component;
	}

	@Override
	public boolean isReadWrite() {
		return getComponent().isEnabled();
	}

	@Override
	public void setReadWrite(boolean readWrite) {
		getComponent().setEnabled(readWrite);
	}

	public void onEvent(Event event) throws Exception {
		if (event == null)
			return;

		// When user enters information in the editor
		if (event.getName() != null && (Events.ON_CHANGE.equals(event.getName()) || Events.ON_OK.equals(event.getName()))) {

			String newValue = getComponent().getText();
			if (oldValue != null && newValue != null && oldValue.equals(newValue)) {
				return;
			}
			if (oldValue == null && newValue == null) {
				return;
			}

			String formattedNumber = getPhoneNumberFromText(newValue);
			formattedNumber = formattedNumber != null ? formattedNumber : newValue;
			ValueChangeEvent changeEvent = new ValueChangeEvent(this, this.getColumnName(), oldValue, formattedNumber);
			fireValueChange(changeEvent);
			oldValue = formattedNumber;
		}

		// Button event. Either call the number or hangup the phone if already calling
		if (event.getTarget() != null && event.getTarget().equals(getComponent().getButton())) {
			if (asteriskChannel == null && dialing == false) {
				if (Env.getCtx().get("#Asterisk_Connected") == null || Env.getCtx().get("#Asterisk_Connected").toString().equals("false")) {
					FDialog.error(0,Msg.getMsg(Env.getLanguage(Env.getCtx()), "de.evenos-consulting.asterisk.notconnected"));
					return;
				}
				initiateCall();
			} else {
				if (asteriskChannel != null) {
					dialing = false;
					asteriskChannel.hangup();
				}
			}
		}

		// Events from the callback for enabling/disabling the call button or change the icon.
		if (ON_CHANGE_CALL_ICON_DISABLE_EVENT.equals(event.getName()))
			getComponent().getButton().setEnabled(false);

		if (ON_CHANGE_CALL_ICON_ENABLE_EVENT.equals(event.getName()))
			getComponent().getButton().setEnabled(true);

		if (ON_CHANGE_CALL_ICON_TO_CALL_EVENT.equals(event.getName()))
			getComponent().setButtonImage("/theme/default/images/Call16.png");

		if (ON_CHANGE_CALL_ICON_TO_ENDCALL_EVENT.equals(event.getName()))
			getComponent().setButtonImage("/theme/default/images/endCall16.png");

	}

	/**
	 * Uses the default Country in the Context to format the phone number
	 * 
	 * @param clear
	 * @return
	 */
	private String getPhoneNumberFromText(String clear) {

		String unformattedNumber = clear.replaceAll("[^\\d+]", "");
		String countryCode = new MCountry(Env.getCtx(), Env.getContextAsInt(Env.getCtx(), "C_Country_ID"), null).getCountryCode();
		PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
		PhoneNumber validatedNumber;
		String retVal = null;
		try {
			validatedNumber = phoneUtil.parse(unformattedNumber, countryCode);

			if (phoneUtil.isValidNumber(validatedNumber))
				retVal = phoneUtil.format(validatedNumber, PhoneNumberFormat.INTERNATIONAL);
		} catch (NumberParseException e) {
			log.severe("Error during phone number formatting: " + e);
		}

		return retVal;
	}

	/**
	 * return listener events to be associated with editor component
	 */
	public String[] getEvents() {
		return LISTENER_EVENTS;
	}

	@Override
	public void onMenu(ContextMenuEvent evt) {
		if (WEditorPopupMenu.CHANGE_LOG_EVENT.equals(evt.getContextEvent())) {
			WFieldRecordInfo.start(gridField);
		} else if (WEditorPopupMenu.PREFERENCE_EVENT.equals(evt.getContextEvent())) {
			if (isShowPreference())
				ValuePreference.start(getComponent(), getGridField(), getValue());
		}
	}

	@Override
	public void setTableEditor(boolean b) {
		super.setTableEditor(b);
		getComponent().setTableEditorMode(b);
	}

	private void initiateCall() {
		if(Util.isEmpty(oldValue, true))
			return;
		try {
			dialing = true;
			Asterisk.originateAsync(oldValue, this);

			// Disable Call button till call is instantiated
			Executions.schedule(desktop, this, new Event(ON_CHANGE_CALL_ICON_DISABLE_EVENT));
		} catch (Exception e) {
			log.severe("Error while initiating call to " + oldValue + ": " + e);
		}
	}

	@Override
	public void onDialing(AsteriskChannel channel) {
		asteriskChannel = channel;
		channel.addPropertyChangeListener(this);
	}

	@Override
	public void onSuccess(AsteriskChannel channel) {
	}

	@Override
	public void onNoAnswer(AsteriskChannel channel) {
	}

	@Override
	public void onBusy(AsteriskChannel channel) {
	}

	@Override
	public void onFailure(LiveException cause) {
		Executions.schedule(desktop, this, new Event(ON_CHANGE_CALL_ICON_ENABLE_EVENT));
		dialing = false;
		log.severe(cause.getLocalizedMessage());
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		// If the "state" property of the current channel changes, that normaly means that we picked or hung up the phone so
		// All we do here is switch icons of our editor, update the popup or remove the change listener from the channel when
		// we have a hungup event. Notice that you cant access ui components in here but in onEvent() so better fire some events
		// instead of trying to manipulate the components here
		if (evt != null && evt.getPropertyName() != null && evt.getNewValue() != null) {
			if (evt.getPropertyName().equals("state") && evt.getNewValue() instanceof ChannelState) {

				ChannelState newState = (ChannelState) evt.getNewValue();

				if (newState.equals(ChannelState.RINGING) || newState.equals(ChannelState.UP)) {
					dialing = false;
					Executions.schedule(desktop, this, new Event(ON_CHANGE_CALL_ICON_ENABLE_EVENT));
					Executions.schedule(desktop, this, new Event(ON_CHANGE_CALL_ICON_TO_ENDCALL_EVENT));
				}
				if (evt.getNewValue().equals(ChannelState.HUNGUP)) {
					dialing = false;
					if (asteriskChannel != null)
						asteriskChannel.removePropertyChangeListener(this);
					asteriskChannel = null;
					Executions.schedule(desktop, this, new Event(ON_CHANGE_CALL_ICON_ENABLE_EVENT));
					Executions.schedule(desktop, this, new Event(ON_CHANGE_CALL_ICON_TO_CALL_EVENT));
				}
			}
		}

		// Let WEditor process the event because it could be a ValueChangeEvent or something else we are not interested in
		super.propertyChange(evt);
	}

}
