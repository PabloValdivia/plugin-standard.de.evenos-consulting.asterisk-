package de.evenosconsulting.window;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;

import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.panel.HeaderPanel;
import org.adempiere.webui.window.FDialog;
import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.ChannelState;
import org.asteriskjava.live.MeetMeRoom;
import org.compiere.model.MSysConfig;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Div;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Include;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;

import de.evenosconsulting.asterisk.Asterisk;
import de.evenosconsulting.util.EvenosCommons;

public class CallPopup extends Window implements EventListener<Event> {

	private static final long serialVersionUID = -3600438144290393884L;

	public static final String ON_CALLPOPUP_UPDATE_TIME_EVENT = "onUPDATE_TIME_EVENT";
	public static final String ON_CALLPOPUP_UPDATE_STATUS_EVENT = "onUPDATE_STATUS_EVENT";
	public static final String ON_CALLPOPUP_UPDATE_TITLE_EVENT = "onUPDATE_TITLE_EVENT";
	public static final String ON_CALLPOPUP_ENABLE_TRANSFER = "onCALLPOPUP_ENABLE_TRANSFER";

	public static final String CALLPOPUP_TITLE_LABEL = "de.evenos-consulting.asterisk.title";
	public static final String CALLPOPUP_STATUS_LABEL = "de.evenos-consulting.asterisk.status";
	public static final String CALLPOPUP_TIME_LABEL = "de.evenos-consulting.asterisk.time";
	public static final String CALLPOPUP_MANUAL_NUMBER_LABEL = "de.evenos-consulting.asterisk.manualnumber";
	public static final String CALLPOPUP_ONLY_TRANSFER_PARTNER_LABEL = "de.evenos-consulting.asterisk.onlytransferpartner";
	public static final String CALLPOPUP_DYNAMIC_MEET_ME_LABEL = "de.evenos-consulting.asterisk.dynamicmeetme";
	public static final String CALLPOPUP_CHANNELSTATE_PREFIX = "de.evenos-consulting.asterisk.channelstate.";

	private Label lblTime = new Label();
	private Label lblStatus = new Label();

	private Button btnHangup = new Button();
	private Button btnTransfer = new Button();
	private Button btnConference = new Button();

	private ConfirmPanel confirmPanel = new ConfirmPanel(true);

	private Checkbox chkManualNumber = new Checkbox(Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_MANUAL_NUMBER_LABEL));
	private Textbox txtManualNumber = new Textbox();
	private Combobox cbChoseNumber = new Combobox();

	private Checkbox chkOnlyTransferPartner = new Checkbox(Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_ONLY_TRANSFER_PARTNER_LABEL));
	private Checkbox chkDynamicMeetMe = new Checkbox(Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_DYNAMIC_MEET_ME_LABEL));
	private Combobox cbMeetMeRooms = new Combobox();
	private Textbox txtMeetMeRoom = new Textbox();

	private Vbox layout = new Vbox(); // contains everything, transfer and conference divs are not visible until talking
	private Hbox header = new Hbox(); // contains labels and buttons

	private Div divTransConfBtns = new Div();// contains the transfer and conference button
	private Div divTransfer = new Div(); // contains buttons and stuff for transfering a call
	private Div divConference = new Div();// contains buttons and stuff for meet me

	private boolean initialized;
	private boolean isMeetMeAvailable;
	private boolean inTransfer;
	private boolean inConference;

	private long starttime;
	private ChannelState channelState = ChannelState.DOWN;
	private AsteriskChannel channel;

	private Timer timer;
	private Desktop desktop;
	private String titlePrefix;

	public CallPopup(final Desktop desktop, AsteriskChannel channel) {
		super();

		this.desktop = desktop;
		this.channel = channel;

		// TODO: Chose a better Title. Maybe use system message and allow variables like @CallerId.Name@ and @CallerId.Number@
		titlePrefix = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_TITLE_LABEL);
		if (titlePrefix.equals(CALLPOPUP_TITLE_LABEL))
			titlePrefix = "Call:";
		titlePrefix += " ";

		setCallPopupTitle();

		Object ctxMeetMe = Env.getCtx().get("#Asterisk_MeetMe_Enabled");
		isMeetMeAvailable = ctxMeetMe != null && Boolean.valueOf(ctxMeetMe.toString());

		initComponents();
		appendToDesktop(desktop);
		startTimer();
	}

	private void startTimer() {
		starttime = System.currentTimeMillis();
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (desktop != null)
					Executions.schedule(desktop, CallPopup.this, new Event(ON_CALLPOPUP_UPDATE_TIME_EVENT));
			}
		}, 0, 1000);
	}

	private void appendToDesktop(Desktop desktop2) {
		if (desktop != null) {
			Borderlayout layout = (Borderlayout) desktop.getFirstPage().getFellow("layout");
			Include northBody = (Include) layout.getFellow("northBody");
			HeaderPanel pnl = (HeaderPanel) northBody.getFellow("header");
			pnl.appendChild(this);
		}
	}

	private void initComponents() {

		if (!initialized) {
			initialized = true;

			this.appendChild(layout);
			layout.appendChild(header);

			initLabels();
			initButtons();
			initTransferPanel();
			initConferencePanel();

			confirmPanel.addActionListener(Events.ON_CLICK, this);
			confirmPanel.setVisible(false);
			layout.appendChild(confirmPanel);

			addEventListener("onFocus", this);
			setPosition("center,center");
			setBorder(true);
			setShadow(false);
			doOverlapped();
			setClosable(true);
		}
	}

	private void initLabels() {

		// Labels to show status of call (e.g. ringing or up)
		lblStatus.setText(statusTextForChannelState(channelState));
		String labelText = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_STATUS_LABEL);
		labelText = labelText.equals(CALLPOPUP_STATUS_LABEL) ? "Status: " : labelText;
		Label lblText = new Label(labelText);
		Hbox statusLabels = new Hbox();
		statusLabels.setSpacing("6px");
		statusLabels.setWidth("98%");
		statusLabels.setHflex("true");
		statusLabels.appendChild(lblText);
		statusLabels.appendChild(lblStatus);

		// Labels to show duration of call
		lblTime.setText("0:00:00");
		labelText = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_TIME_LABEL);
		labelText = labelText.equals(CALLPOPUP_TIME_LABEL) ? "Time: " : labelText;
		lblText = new Label(labelText);
		Hbox timeLabels = new Hbox();
		timeLabels.appendChild(lblText);
		timeLabels.appendChild(lblTime);

		// Vbox to store the two Hboxes
		Vbox boxLabels = new Vbox();
		boxLabels.appendChild(timeLabels);
		boxLabels.appendChild(statusLabels);

		// Add Vbox to header Hbox
		header.appendChild(boxLabels);
	}

	private void initButtons() {

		btnHangup.setImage("/theme/default/images/endCall16.png");
		btnHangup.addEventListener(Events.ON_CLICK, this);

		header.appendChild(btnHangup);
		header.appendChild(divTransConfBtns);
		divTransConfBtns.setVisible(false);

		btnTransfer.setImage("/theme/default/images/transferCall16.png");
		btnTransfer.addEventListener(Events.ON_CLICK, this);
		btnTransfer.setEnabled(false);
		divTransConfBtns.appendChild(btnTransfer);

		// Don't add conference if meet me is not available!
		if (isMeetMeAvailable) {
			btnConference.setImage("/theme/default/images/conferenceCall16.png");
			btnConference.addEventListener(Events.ON_CLICK, this);
			btnConference.setEnabled(false);
			divTransConfBtns.appendChild(btnConference);
		}
	}

	private void initTransferPanel() {

		layout.appendChild(divTransfer);
		divTransfer.setVisible(false);

		cbChoseNumber.setVisible(false);
		loadPhoneNumbers(cbChoseNumber);

		chkManualNumber.setChecked(true);
		chkManualNumber.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
			@Override
			public void onEvent(Event event) throws Exception {
				txtManualNumber.setVisible(!txtManualNumber.isVisible());
				cbChoseNumber.setVisible(!cbChoseNumber.isVisible());
			}
		});

		Hbox box = new Hbox();
		box.appendChild(chkManualNumber);
		box.appendChild(txtManualNumber);
		box.appendChild(cbChoseNumber);
		divTransfer.appendChild(box);
	}

	private void initConferencePanel() {
		layout.appendChild(divConference);
		divConference.setVisible(false);

		Vbox container = new Vbox();
		divConference.appendChild(container);
		Hbox chkBoxes = new Hbox();
		chkBoxes.appendChild(chkOnlyTransferPartner);

		cbMeetMeRooms.setVisible(false);
		/*
		 * Commented out because my asterisk server is not able to show me a list of inactive meet me rooms which results in an empty list
		 * chkBoxes.appendChild(chkDynamicMeetMe); loadMeetMeRooms(cbMeetMeRooms);
		 * 
		 * chkDynamicMeetMe.setChecked(true); chkDynamicMeetMe.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
		 * 
		 * @Override public void onEvent(Event event) throws Exception { txtMeetMeRoom.setVisible(!txtMeetMeRoom.isVisible());
		 * cbMeetMeRooms.setVisible(!cbMeetMeRooms.isVisible()); } });
		 */

		container.appendChild(chkBoxes);
		container.appendChild(cbMeetMeRooms);
		container.appendChild(txtMeetMeRoom);
	}

	private void loadPhoneNumbers(Combobox comboBox) {
		StringBuilder b = new StringBuilder();
		b.append("select name, phone from ad_user where phone is not null union ");
		b.append("select name, phone2 from ad_user where phone2 is not null union ");
		b.append("select b.name, l.phone from c_bpartner_location l join c_bpartner b on b.c_bpartner_id = l.c_bpartner_id where l.phone is not null union ");
		b.append("select b.name, l.phone2 from c_bpartner_location l join c_bpartner b on b.c_bpartner_id = l.c_bpartner_id where l.phone2 is not null	union ");
		b.append("select o.name, i.phone from ad_orginfo i join ad_org o on i.ad_org_id = o.ad_org_id where i.phone is not null	union ");
		b.append("select o.name, i.phone2 from ad_orginfo i join ad_org o on i.ad_org_id = o.ad_org_id where i.phone2 is not null order by name ");

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(b.toString(), null);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				String name = rs.getString(1);
				String number = rs.getString(2);
				Comboitem item = comboBox.appendItem(name + " (" + number + ")");
				item.setValue(number);
			}
		} catch (SQLException e) {

		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		if (comboBox.getItemCount() > 0)
			comboBox.setSelectedIndex(0);
	}

	private String statusTextForChannelState(ChannelState channelState) {
		String stateText = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_CHANNELSTATE_PREFIX + channelState);
		if (stateText.equals(CALLPOPUP_CHANNELSTATE_PREFIX + channelState))
			stateText = channelState.toString().toLowerCase();
		return stateText;
	}

	@Override
	public void onEvent(Event event) throws Exception {

		// Update duration label
		if (event.getName().equals(ON_CALLPOPUP_UPDATE_TIME_EVENT)) {
			lblTime.setText(EvenosCommons.timeAsStringSince(starttime));
		}
		// Update title label
		else if (event.getName().equals(ON_CALLPOPUP_UPDATE_TITLE_EVENT)) {
			setCallPopupTitle();
		}
		// Update status label
		else if (event.getName().equals(ON_CALLPOPUP_UPDATE_STATUS_EVENT)) {
			if (event.getData() instanceof ChannelState) {
				ChannelState newState = (ChannelState) event.getData();
				if (!newState.equals(channelState)) {
					channelState = newState;
					lblStatus.setText(statusTextForChannelState(channelState));
					starttime = System.currentTimeMillis();
					if (desktop != null)
						Executions.schedule(desktop, this, new Event(ON_CALLPOPUP_UPDATE_TIME_EVENT));
				}
			}
		}
		// Enable conference and transfer button div
		else if (event.getName().equals(ON_CALLPOPUP_ENABLE_TRANSFER)) {
			if (event.getData() instanceof AsteriskChannel && channel != null) {
				AsteriskChannel source = (AsteriskChannel) event.getData();
				AsteriskChannel destination = channel.getDialedChannel();
				destination = destination == null ? channel.getDialingChannel() : destination;

				if (destination != null && destination.getState().equals(ChannelState.UP) && source.getState().equals(ChannelState.UP)) {
					setTransConfDivEnabled(true);
				} else {
					setTransConfDivEnabled(false);
				}
			}
		}
		// Hang channel up if hangup button was pressed
		else if (event.getName().equals(Events.ON_CLICK) && event.getTarget().equals(btnHangup)) {
			channel.hangup();
		}
		// Show transfer div if transfer button was pressed. also hide transfer and conference button
		else if (event.getName().equals(Events.ON_CLICK) && event.getTarget().equals(btnTransfer)) {
			setTransConfDivEnabled(false);
			setTransferDivEnabled(true);
		}
		// Show conference div if conference button was pressed. also hide transfer and conference button
		else if (event.getName().equals(Events.ON_CLICK) && event.getTarget().equals(btnConference)) {
			setTransConfDivEnabled(false);
			setConferenceDivEnabled(true);
		}
		// Handle events from confirm panels
		else if (event.getName().equals(Events.ON_CLICK) && event.getTarget().equals(confirmPanel.getButton(ConfirmPanel.A_OK))) {
			if (inTransfer)
				transferCall();
			else if (inConference)
				conferenceCall();
		} else if (event.getName().equals(Events.ON_CLICK) && event.getTarget().equals(confirmPanel.getButton(ConfirmPanel.A_CANCEL))) {
			setTransConfDivEnabled(true);
			setTransferDivEnabled(false);
			setConferenceDivEnabled(false);
		}

	}

	private void conferenceCall() {

		// TODO: Implement static meet me. Problem here: asterisk doesn't provide a list of inactive rooms so we cannot show a list...
		// maybe read the extension.conf and search for meetme with regex and cache the rooms in static cache

		// Get Dynamic Meet Me Extension from System Configurator
		String exten = MSysConfig.getValue(Asterisk.ASTERISK_MEET_ME_EXTEN);

		// Get Min/Max meetme room number form System Configurator
		int min = MSysConfig.getIntValue(Asterisk.ASTERISK_MEET_ME_ROOM_MIN, -1);
		int max = MSysConfig.getIntValue(Asterisk.ASTERISK_MEET_ME_ROOM_MAX, -1);

		if (min == -1 || max == -1) {
			FDialog.error(0, "de.evenos-consulting.asterisk.nomeetmeeminmax");
			return;
		}

		int number;
		try {
			number = Integer.parseInt(txtMeetMeRoom.getText());
		} catch (Exception e) {
			number = -1;
		}

		// Validate entered room number
		if (number > -1) {
			number = number < min ? min : number;
			number = number > max ? max : number;

			String sipContext = MSysConfig.getValue(Asterisk.ASTERISK_SIP_CONTEXT, "", Env.getAD_Client_ID(Env.getCtx()),
					Env.getAD_Org_ID(Env.getCtx()));

			exten += number;

			// transfer to room (either partner only or both)
			if (chkOnlyTransferPartner.isChecked()) {
				// Only transfer partner

				// TODO: refactor since also used in transferCall()
				AsteriskChannel channelToTransfer = channel.getDialedChannel();
				channelToTransfer = channelToTransfer == null ? channel.getDialingChannel() : channelToTransfer;
				if (channelToTransfer == null) {
					FDialog.error(0, "de.evenos-consulting.asterisk.nochannelfortransfer"); // should never be reached, maybe remove
					return;
				}// TODO: refactor since also used in transferCall()

				channelToTransfer.redirect(sipContext, exten, 1);
			} else {
				// transfer both
				channel.redirectBothLegs(sipContext, exten, 1);
			}
		}
	}

	private void transferCall() {
		AsteriskChannel channelToTransfer = channel.getDialedChannel();
		channelToTransfer = channelToTransfer == null ? channel.getDialingChannel() : channelToTransfer;

		String sipContext = MSysConfig.getValue(Asterisk.ASTERISK_SIP_CONTEXT, "", Env.getAD_Client_ID(Env.getCtx()),
				Env.getAD_Org_ID(Env.getCtx()));

		if (channelToTransfer == null) {
			FDialog.error(0, "de.evenos-consulting.asterisk.nochannelfortransfer"); // should never be reached, maybe remove
			return;
		}

		String exten = null;

		if (chkManualNumber.isChecked() && !Util.isEmpty(txtManualNumber.getText(), true)) {
			exten = txtManualNumber.getText();
		} else if (!chkManualNumber.isChecked() && cbChoseNumber.getSelectedItem() != null) {
			exten = (String) cbChoseNumber.getSelectedItem().getValue();
		} else {
			FDialog.error(0, "de.evenos-consulting.asterisk.nonumberfortransfer");
			return;
		}
		channelToTransfer.redirect(sipContext, Asterisk.getCallableNumber(exten), 1);
	}

	private void setTransConfDivEnabled(boolean b) {
		divTransConfBtns.setVisible(b);
		btnTransfer.setEnabled(b);
		btnConference.setEnabled(b);
	}

	private void setTransferDivEnabled(boolean b) {
		inTransfer = b;
		divTransfer.setVisible(b);
		confirmPanel.setVisible(b);
	}

	private void setConferenceDivEnabled(boolean b) {
		inConference = b;
		divConference.setVisible(b);
		confirmPanel.setVisible(b);
	}

	@Override
	public void dispose() {
		tearDown();
		super.dispose();
	}

	public void tearDown() {
		// Cancel Timer
		if (timer != null) {
			timer.cancel();
			timer.purge();
			timer = null;
		}
		channel = null;
		desktop = null;
	}

	public void setCallPopupTitle() {
		if (channel == null) {
			setTitle("<>");
			return;
		}

		AsteriskChannel partner = channel.getDialedChannel();
		if (partner == null)
			partner = channel.getDialingChannel();
		if (partner == null)
			partner = channel;

		String title = null;
		if (channel.getMeetMeUser() != null && channel.getMeetMeUser().getRoom() != null)
			title = "Meet Me Room (" + channel.getMeetMeUser().getRoom().getRoomNumber() + ")";
		if (title == null && partner.getCallerId() != null)
			title = partner.getCallerId().getName();
		if (title == null && partner.getCallerId() != null)
			title = partner.getCallerId().getNumber();
		if (partner.getCallerId() != null && title.equals(partner.getCallerId().getName()))
			title += " (" + partner.getCallerId().getNumber() + ")";

		setTitle(titlePrefix + title);
	}
}
