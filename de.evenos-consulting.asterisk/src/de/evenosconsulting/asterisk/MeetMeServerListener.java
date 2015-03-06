package de.evenosconsulting.asterisk;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.AsteriskQueueEntry;
import org.asteriskjava.live.AsteriskServerListener;
import org.asteriskjava.live.MeetMeUser;
import org.asteriskjava.live.MeetMeUserState;
import org.asteriskjava.live.internal.AsteriskAgentImpl;
import org.compiere.util.CLogger;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;

public class MeetMeServerListener implements AsteriskServerListener {

	private CLogger log = CLogger.getCLogger(MeetMeServerListener.class);

	private Asterisk asterisk;

	public MeetMeServerListener(Asterisk asterisk) {
		this.asterisk = asterisk;
	}

	@Override
	public void onNewAsteriskChannel(AsteriskChannel channel) {

	}

	@Override
	public void onNewMeetMeUser(MeetMeUser user) {
		if(asterisk.isAsteriskChannelOfInterest(user.getChannel())){
			log.warning("User of interest: " + user);
			Executions.schedule(asterisk.getDesktop(), asterisk, new Event(Asterisk.ON_ASTERSK_MEET_ME_NEW_USER_OF_INTEREST, null, user));
		}else{
			Executions.schedule(asterisk.getDesktop(), asterisk, new Event(Asterisk.ON_ASTERSK_MEET_ME_NEW_USER, null, user));
		}		
		
		user.addPropertyChangeListener(new PropertyChangeListener() {			
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if(evt.getPropertyName().equals("state") && MeetMeUserState.LEFT.equals(evt.getNewValue())){
					MeetMeUser usr = (MeetMeUser) evt.getSource();
					Executions.schedule(asterisk.getDesktop(), asterisk, new Event(Asterisk.ON_ASTERSK_MEET_ME_USER_LEFT, null, usr));
					usr.removePropertyChangeListener(this);
				} 
			}
		});
	}

	@Override
	public void onNewAgent(AsteriskAgentImpl agent) {
	}

	@Override
	public void onNewQueueEntry(AsteriskQueueEntry entry) {
	}

}
