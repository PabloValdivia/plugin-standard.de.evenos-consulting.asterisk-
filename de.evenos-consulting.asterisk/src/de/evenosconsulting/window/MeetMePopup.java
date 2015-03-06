package de.evenosconsulting.window;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import org.adempiere.webui.component.Window;
import org.adempiere.webui.panel.HeaderPanel;
import org.asteriskjava.live.MeetMeUser;
import org.compiere.util.Env;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Include;
import org.zkoss.zul.Label;
import org.zkoss.zul.Vbox;

public class MeetMePopup extends Window implements EventListener<Event> {

	private static final long serialVersionUID = -6843412694397210601L;

	public static final String ON_MEETMEPOPUP_UPDATE_EVENT = "onMEETMEPOPUP_UPDATE_EVENT";

	public static final String MEETMEPOPUP_BUTTON_TYPE = "MEETMEPOPUP_BUTTON_TYPE";
	public static final String MEETMEPOPUP_BUTTON_TYPE_KICK = "MEETMEPOPUP_BUTTON_TYPE_KICK";
	public static final String MEETMEPOPUP_BUTTON_TYPE_MUTE = "MEETMEPOPUP_BUTTON_TYPE_MUTE";

	private Desktop desktop;
	private MeetMeUser user;

	private Vbox layout = new Vbox();

	public MeetMePopup(final Desktop desktop, MeetMeUser user) {
		this.desktop = desktop;
		this.user = user;

		setTitle("Meet Me: " + user.getRoom().getRoomNumber());

		initComponents();
		appendToDesktop(desktop);

		addEventListener("onFocus", this);
		setPosition("center,center");
		setBorder(true);
		setShadow(false);
		doOverlapped();
		setClosable(true);
	}

	private Timer timer;

	private void initComponents() {
		this.appendChild(layout);
	}

	private Map<Button, MeetMeUser> btnUsrs = new HashMap<Button, MeetMeUser>();

	private void populateUsers() {

		// TODO: Improve layout. Maybe use table with custom table model
		this.removeChild(layout);
		layout = new Vbox();
		this.appendChild(layout);

		for (Button b : btnUsrs.keySet())
			b.removeEventListener(Events.ON_CLICK, this);
		btnUsrs.clear();

		if (this.user != null && this.user.getRoom() != null) {
			for (MeetMeUser user : this.user.getRoom().getUsers()) {
				Hbox b = new Hbox();
				layout.appendChild(b);

				String muteUnmute = user.isMuted() ? "Unmute" : "Mute";
				Button mute = new Button(muteUnmute);
				mute.addEventListener(Events.ON_CLICK, this);
				mute.setAttribute(MEETMEPOPUP_BUTTON_TYPE, MEETMEPOPUP_BUTTON_TYPE_MUTE);
				btnUsrs.put(mute, user);
				b.appendChild(mute);

				Button kick = new Button("Kick");
				kick.addEventListener(Events.ON_CLICK, this);
				kick.setAttribute(MEETMEPOPUP_BUTTON_TYPE, MEETMEPOPUP_BUTTON_TYPE_KICK);
				btnUsrs.put(kick, user);
				b.appendChild(kick);

				String name = user.getChannel().getCallerId().getName();
				if (name == null)
					name = user.getChannel().getName().substring(0, user.getChannel().getName().indexOf("-"));

				if(this.user.equals(user))
					name = Env.getContext(Env.getCtx(), "#AD_User_Name");
					
				
				Label lblName = new Label(name + " (" + user.getChannel().getCallerId().getNumber() + ")");
				b.appendChild(lblName);
			}
		}
	}

	private void appendToDesktop(Desktop desktop) {
		if (desktop != null) {
			Borderlayout layout = (Borderlayout) desktop.getFirstPage().getFellow("layout");
			Include northBody = (Include) layout.getFellow("northBody");
			HeaderPanel pnl = (HeaderPanel) northBody.getFellow("header");
			pnl.appendChild(this);
		}
	}

	@Override
	public void onEvent(Event event) throws Exception {
		if (event.getName().equals(ON_MEETMEPOPUP_UPDATE_EVENT)) {
			populateUsers();
		} else if (event.getName().equals(Events.ON_CLICK) && event.getTarget() instanceof Button) {
			Button button = (Button) event.getTarget();
			MeetMeUser user = btnUsrs.get(button);
			if (button.getAttribute(MEETMEPOPUP_BUTTON_TYPE).equals(MEETMEPOPUP_BUTTON_TYPE_MUTE)) {
				if (user != null) {
					if (user.isMuted()) {
						user.unmute();
						button.setLabel("Mute"); // TODO: I18n
					} else {
						user.mute();
						button.setLabel("Unmute");// TODO: I18n
					}
				}
			} else {
				user.kick();
			}
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
		user = null;
		desktop = null;
	}
}
