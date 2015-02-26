package de.evenosconsulting.window;

import java.util.Timer;
import java.util.TimerTask;

import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.panel.HeaderPanel;
import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.ChannelState;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Cell;
import org.zkoss.zul.Div;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Include;
import org.zkoss.zul.Vbox;

import de.evenosconsulting.util.EvenosCommons;

public class CallPopup extends Window implements EventListener<Event> {

	private static final long serialVersionUID = -3600438144290393884L;

	public static final String ON_CALLPOPUP_UPDATE_TIME_EVENT = "onUPDATE_TIME_EVENT";
	public static final String ON_CALLPOPUP_UPDATE_STATUS_EVENT = "onUPDATE_STATUS_EVENT";
	public static final String ON_CALLPOPUP_UPDATE_TITLE_EVENT = "onUPDATE_TITLE_EVENT";
	public static final String ON_CALLPOPUP_HANGUP_EVENT = "onCALLPOPUP_HANGUP_EVENT";

	public static final String CALLPOPUP_TITLE_LABEL = "de.evenos-consulting.asterisk.title";
	public static final String CALLPOPUP_STATUS_LABEL = "de.evenos-consulting.asterisk.status";
	public static final String CALLPOPUP_TIME_LABEL = "de.evenos-consulting.asterisk.time";
	public static final String CALLPOPUP_CHANNELSTATE_PREFIX = "de.evenos-consulting.asterisk.channelstate.";

	private Label lblTime = new Label();
	private Label lblStatus = new Label();
	private Button btnHangup = new Button();

	private boolean initialized;

	private long starttime;
	private ChannelState channelState = ChannelState.DOWN;

	private Timer timer;
	private Desktop desktop;
	private AsteriskChannel channel;
	private String titlePrefix;

	public void setAsteriskChannel(AsteriskChannel channel) {
		this.channel = channel;
	}

	public CallPopup(final Desktop desktop, AsteriskChannel channel) {
		super();
		this.desktop = desktop;
		this.channel = channel;

		titlePrefix = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_TITLE_LABEL);
		if (titlePrefix.equals(CALLPOPUP_TITLE_LABEL))
			titlePrefix = "Call: ";

		setCallPopupTitle(channel.getCallerId().getName() != null ? channel.getCallerId().getName() : channel.getCallerId().getNumber());

		if (desktop != null) {
			Borderlayout layout = (Borderlayout) desktop.getFirstPage().getFellow("layout");
			Include northBody = (Include) layout.getFellow("northBody");
			HeaderPanel pnl = (HeaderPanel) northBody.getFellow("header");
			pnl.appendChild(this);
		}
		initComponents();

		starttime = System.currentTimeMillis();
		final CallPopup thePopup = this;
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (desktop != null)
					Executions.schedule(desktop, thePopup, new Event(ON_CALLPOPUP_UPDATE_TIME_EVENT));
			}
		}, 0, 1000);
	}

	private void initComponents() {

		if (!initialized) {
			initialized = true;

			btnHangup.setImage("/theme/default/images/endCall16.png");
			btnHangup.addEventListener(Events.ON_CLICK, this);

			lblStatus.setText(channelState.toString());
			lblTime.setText("0:00:00");
			addEventListener("onFocus", this);
			setPosition("center,center");
			setBorder(true);
			setShadow(false);
			doOverlapped();
			setClosable(true);

			Hbox layout = new Hbox();
			this.appendChild(layout);

			Vbox labels = new Vbox();
			layout.appendChild(labels);
			layout.appendChild(btnHangup);

			Div div = new Div();
			Hbox northVLayout = new Hbox();
			northVLayout.setSpacing("6px");
			northVLayout.setWidth("98%");
			div.appendChild(northVLayout);
			labels.appendChild(div);

			div = new Div();
			Hbox southVLayout = new Hbox();
			southVLayout.setSpacing("6px");
			southVLayout.setWidth("98%");
			div.appendChild(southVLayout);
			labels.appendChild(div);

			String labelText = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_STATUS_LABEL);
			if (labelText.equals(CALLPOPUP_STATUS_LABEL))
				labelText = "Status: ";
			Cell cell = new Cell();
			northVLayout.appendChild(new Label(labelText));
			cell.setAlign("left");

			cell = new Cell();
			northVLayout.appendChild(lblStatus);
			cell.setAlign("left");

			labelText = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_TIME_LABEL);
			if (labelText.equals(CALLPOPUP_TIME_LABEL))
				labelText = "Time: ";
			cell = new Cell();
			southVLayout.appendChild(new Label(labelText));
			cell.setAlign("left");

			cell = new Cell();
			southVLayout.appendChild(lblTime);
			cell.setAlign("left");
		}
	}

	@Override
	public void onEvent(Event event) throws Exception {

		if (event.getName().equals(ON_CALLPOPUP_UPDATE_TIME_EVENT)) {
			lblTime.setText(EvenosCommons.timeAsStringSince(starttime));
		}

		if (event.getName().equals(ON_CALLPOPUP_UPDATE_TITLE_EVENT)) {
			if (event.getData() instanceof String) {
				String title = (String) event.getData();
				setCallPopupTitle(title);
			}
		}

		if (event.getName().equals(ON_CALLPOPUP_UPDATE_STATUS_EVENT)) {
			if (event.getData() instanceof ChannelState) {
				ChannelState newState = (ChannelState) event.getData();
				if (!newState.equals(channelState)) {
					channelState = newState;

					String stateText = Msg.getMsg(Env.getLanguage(Env.getCtx()), CALLPOPUP_CHANNELSTATE_PREFIX + newState);
					if (stateText.equals(CALLPOPUP_CHANNELSTATE_PREFIX + newState))
						stateText = newState.toString().toLowerCase();

					lblStatus.setText(stateText);
					starttime = System.currentTimeMillis();
					if (desktop != null)
						Executions.schedule(desktop, this, new Event(ON_CALLPOPUP_UPDATE_TIME_EVENT));
				}
			}
		}

		if (event.getName().equals(Events.ON_CLICK) && event.getTarget().equals(btnHangup)) {
			channel.hangup();
		}
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

	public void setCallPopupTitle(String title) {
		setTitle(titlePrefix + title);
	}
}
