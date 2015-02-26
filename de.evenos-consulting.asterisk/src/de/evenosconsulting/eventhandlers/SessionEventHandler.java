package de.evenosconsulting.eventhandlers;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.adempiere.webui.apps.AEnv;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.compiere.model.MSession;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.osgi.service.event.Event;

import de.evenosconsulting.asterisk.Asterisk;

public class SessionEventHandler extends AbstractEventHandler {

	private static CLogger log = CLogger.getCLogger(SessionEventHandler.class);

	@Override
	protected void doHandleEvent(Event event) {
		PO po = getPO(event);
		if (event.getTopic().equals(IEventTopics.PO_AFTER_NEW)) {
			log.finest("Connecting to Asterisk Server");
			Logger logger = Logger.getRootLogger();
			SimpleLayout layout = new SimpleLayout();
			ConsoleAppender consoleAppender = new ConsoleAppender(layout);
			logger.addAppender(consoleAppender);
			logger.setLevel(org.apache.log4j.Level.OFF);

			Asterisk.start(AEnv.getDesktop(), (MSession) po);
		} else if (event.getTopic().equals(IEventTopics.PO_AFTER_CHANGE)) {
			if (po instanceof MSession && ((MSession) po).isProcessed()) {
				Asterisk.stop((MSession) po);
			}
		}
	}

	@Override
	protected void initialize() {
		// Register events we are interested in
		registerTableEvent(IEventTopics.PO_AFTER_NEW, MSession.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, MSession.Table_Name);
	}

}
