package de.evenosconsulting.asterisk;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.asteriskjava.live.AsteriskChannel;
import org.asteriskjava.live.AsteriskQueueEntry;
import org.asteriskjava.live.AsteriskServerListener;
import org.asteriskjava.live.ChannelState;
import org.asteriskjava.live.MeetMeUser;
import org.asteriskjava.live.internal.AsteriskAgentImpl;
import org.compiere.util.CLogger;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;

public class CallServerListener implements AsteriskServerListener, PropertyChangeListener {

	private CLogger log = CLogger.getCLogger(CallServerListener.class);

	private Asterisk asterisk;

	public CallServerListener(Asterisk asterisk) {
		this.asterisk = asterisk;
	}

	@Override
	public void onNewAsteriskChannel(AsteriskChannel channel) {
		if (asterisk.isAsteriskChannelOfInterest(channel)) {
			log.finest("New AsteriskChannel of interest: " + channel);
			channel.addPropertyChangeListener(this);
		}
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

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		log.finest("Property " + evt.getPropertyName() + " changed to " + evt.getNewValue());

		// For each state change of the channel we need to display the popup and/or change its state and time but
		// we only can manipulate UI components in onEvent() so here we just fire some events and handle them in onEvent()
		if (evt.getPropertyName().equals("state") && evt.getNewValue() instanceof ChannelState
				&& evt.getSource() instanceof AsteriskChannel) {
			AsteriskChannel channel = (AsteriskChannel) evt.getSource();
			ChannelState state = (ChannelState) evt.getNewValue();
			Executions.schedule(CallServerListener.this.asterisk.getDesktop(), CallServerListener.this.asterisk, new Event(
					Asterisk.ON_ASTERISK_CHANNEL_STATE_CHANGED, null, evt.getSource()));
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
					Executions.schedule(CallServerListener.this.asterisk.getDesktop(), CallServerListener.this.asterisk, new Event(
							Asterisk.ON_ASTERISK_CHANNEL_CHANGED, null, new AsteriskChannelSwitch(dialingChannel, dialedChannel)));
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
				if (asterisk.isAsteriskChannelOfInterest(dialedChannel))
					Executions.schedule(CallServerListener.this.asterisk.getDesktop(), CallServerListener.this.asterisk, new Event(
							Asterisk.ON_ASTERISK_CALLER_ID_CHANGED, null, new AsteriskChannelSwitch(dialedChannel, dialingChannel)));
			}
		}

	}
}
