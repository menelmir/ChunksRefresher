ChunksRefresher_0_0_1-SNAPSHOT-2018-11-02
(c)Damien VERLYNDE. All rights reserved.
---------------
WARNING:
This plugin comes with no warranty and is provided "as-is".
Use at your own risks.
Save your whole server before installing and using.
---------------
DISTRIBUTION:
You can distribute this program freely, but only in is original zip package, including this readme file.
Why ? To make sure it's correctly used and don't make damage, and users can contact me if they want.
---------------
CONTACT:
For any question, suggestion : please contact Damien VERLYNDE at damien.verlynde@live.fr
---------------
USING :
-Important :
	-Save your whole server !
	-Close your server to players, suspend any task running.
	-Disable dynmap, completely (prefered) or making /dynmap pause all. Not doing that will conduce to a out of memory error.
-Type in CONSOLE only : chkref <worldname>
	-World refreshing will start, it depends on the size of your world and your server, it can be very long. All chunks are loaded and so unloaded.
	-Make this for all your worlds.
	-Note : you have to manualy process each environment of a world
		-Exemple : "chkref myworld", then "chkref myworld_nether", then "chkref myworld_the_end"
-Avoid starting several refresh at same time, except for very little worlds
-Restart server between each big world refresh, and monitor your logs
-Once all your worlds processed, enable dynmap and reopen your server
-Dynmap renders should works, try render your need to test