package de.evenosconsulting.factories;

import org.adempiere.webui.editor.WEditor;
import org.adempiere.webui.factory.IEditorFactory;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;

import de.evenosconsulting.editor.zk.WPhoneNumberEditor;

/**
 * @author Jan Thielemann
 * @author jan.thielemann@evenos.de, www.evenos.de
 */

public class WAsteriskEditorFactory implements IEditorFactory{

	@Override
	public WEditor getEditor(GridTab gridTab, GridField gridField,	boolean tableEditor) {

		//No Field
		if (gridField == null)
			return null;
		
		//Not a Field
		if (gridField.isHeading())
			return null;
		
		WEditor editor = null;
		
		int displayType = gridField.getDisplayType();
		
		//Phone Number Editor
		if (displayType == DisplayTypeFactory.PhoneNumber)
		{
			editor = new WPhoneNumberEditor(gridField);
		}
				
		return editor;
	}

}
