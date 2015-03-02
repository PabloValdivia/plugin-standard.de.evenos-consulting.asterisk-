# Installation #
* Import the 4 Projects into your workspace
* Build the bundles via the updatefeature Feature Project (open the feature.xml and use the export wizzard)
* Install the de.evenos-consulting.asterisk Plug-In and the de.evenos-consulting.asterisk.images Fragment via Apache Felix Web Console. Use Start Level 4 for the Fragment. Start the Plug-In after you installed it so the 2Pack gets packed in
* Use the de.evenos-consulting.asterisk System Configurator Values and Messages to setup the Plug-In
* Reset the cache and log out and back in again so the changes can take effect

The 2Pack adds element/column/field for: AD_User.sipchannel
and the following Org System Configurator keys:
de.evenos-consulting.asterisk.siphost
de.evenos-consulting.asterisk.sipport
de.evenos-consulting.asterisk.sipuser
de.evenos-consulting.asterisk.sippassword
de.evenos-consulting.asterisk.sipcontext
de.evenos-consulting.asterisk.phoneprefix
