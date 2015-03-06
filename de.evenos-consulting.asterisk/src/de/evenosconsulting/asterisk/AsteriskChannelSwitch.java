package de.evenosconsulting.asterisk;

import org.asteriskjava.live.AsteriskChannel;

public class AsteriskChannelSwitch {
	AsteriskChannel oldChannel;
	AsteriskChannel newChannel;

	public AsteriskChannelSwitch(AsteriskChannel oldChannel, AsteriskChannel newChannel) {
		this.oldChannel = oldChannel;
		this.newChannel = newChannel;
	}
}
