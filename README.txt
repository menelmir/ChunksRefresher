ChunksRefresher 0.0.6-SNAPSHOT
(c)Damien VERLYNDE and contributors. All rights reserved.
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
For any question, suggestion : please contact us on : https://www.huvecraft.fr/?page=contact
---------------
USING :
-Important :
	-Save your whole server !
	-Close your server to players, and you should suspend any task running.
	-Disable dynmap, completely (prefered) or making /dynmap pause all, for example. Not doing this can cause server overload.
-Type in CONSOLE only : chkref <worldname> [nomemcheck]
	-World refreshing will start, task duration depends on the size of your world and your server, it can be very long. All chunks are loaded and so unloaded.
	-Make this for all your worlds.
	-Note : you have to manualy process each environment of a world
		-Exemple : "chkref myworld", then "chkref myworld_nether", then "chkref myworld_the_end"
	-[nomemcheck] is an argument you can try if refreshing tasks goes each time out of memory, but it could result to data loss. Use it at your own risks.
-Avoid starting several refresh at same time, except for very little worlds. It will work, but it will go quickly near out of memory, so the tasks will all suspend
-Restart server between each big world refresh to clear memory, and monitor your logs
-Once all your worlds processed, you can reopen your server and resume other suspended tasks
-Dynmap renders should works, try render your need to test

*To suspend a task, type : chkref <worldname> pause ; to resume task, type : chkref <worldname> resume
    => Task will pause at the end of region currently refreshing
    => If server restart, you must type chkref <worldname>, because the task died
*To cancel a task, type : chkref <worldname> cancel
    => If you start a new task for this world, this will start from the begining again

Note : if your server is going near out of memory, current tasks will suspend and cannot be resumed. After restarting your server, start each task again from console.
Tasks will skip all regions already fully refreshed. This feature use .chkref in the map folder. Don't delete these file until full map is refreshed !
If after refresh completed, these files are not deleted by the plugin, you can do that by yourself.

Note : some warnings can appear while refreshing, about chunk data. It seems it happens with some old chunk data, so it's normal.
However, we insist on the importance of saving your worlds prior using this plugin and check your worlds after refreshing !