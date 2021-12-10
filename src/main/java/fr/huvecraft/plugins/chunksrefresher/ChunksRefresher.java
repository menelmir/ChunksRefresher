/* ChunksRefresher.java
 * Classe principale du plugin ChunksRefresher pour Spigot.
 * 10/12/2021. */

// Définition du package.

package fr.huvecraft.plugins.chunksrefresher;

// Imports.

import java.util.HashMap;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import fr.huvecraft.plugins.chunksrefresher.util.ChkRefException;
import fr.huvecraft.plugins.chunksrefresher.util.SafeLogger;

import org.bukkit.Server;
import org.bukkit.World;

// Définition de la classe.

public final class ChunksRefresher extends JavaPlugin
{
    /* Classe principale du plugin ChunksRefresher pour Spigot. */
    
    // Définitions de membres privés.
    
    private HashMap<World, AsyncChunksRefresher> achunksRefreshers; // Raffraichisseurs asynchrones de chunks.
    private HashMap<World, BukkitTask> chunksRefreshersTasks;       // Tâches Bukkit des raffraichisseurs asynchrones de chunks.
    private BukkitScheduler bukkitScheduler;                        // Gestionnaire de tâches Bukkit.
    private SafeLogger safeLogger;                                  // Loggueur thread-safe.
    private Server server;                                          // Serveur Spigot.
    private Object tasksLock;
    
    // Constructeurs.
    
    public ChunksRefresher()
    {
        /* Constructeur par défaut.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        // Initialise les membres.
        
        achunksRefreshers     = new HashMap<World, AsyncChunksRefresher>();
        bukkitScheduler       = null;
        chunksRefreshersTasks = new HashMap<World, BukkitTask>();
        safeLogger            = null;
        server                = null;
        tasksLock             = new Object();
    }
    
    // Définitions de méthodes publiques de classe.
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, java.lang.String label, java.lang.String[] args)
    {
        /* Méthodes appelée lors de l'appel à une commande enregistrée du plugin.
         * Retour : boolean, commande traitée avec succès ou non.
         * Paramètres : -sender  : émetteur de la commande.
                        -command : commande en question.
                        -label   : alias de la commande utilisée.
                        -args    : arguments. */
        
        try
        {
            switch(label.toLowerCase())
            {
                case "chkref":
                case "chunksrefresher":
                    /* Commande principale du plugin. */
                    
                    // Traitement.
                    
                    commandChunkRefresher(sender, args);
                    
                    // Succès.
                    
                    return true;
            }
        }
        
        catch(ChkRefException error)
        {
            safeLogger.logWarning(error.getMessage());
        }
        
        // Commande non traitée : échec.
        
        return false;
    }

    public void onTaskEnd(World world)
    {
        /* Appelée lors de la fin d'une tâche de raffraichissement d'un monde. */

        synchronized(tasksLock)
        {
            safeLogger.logInfo("Refreshing task for " + world.getName() + " ended.");
            achunksRefreshers.remove(world);
            chunksRefreshersTasks.remove(world);
        }
    }
    
    @Override
    public void onDisable()
    {
        /* Méthodes appelée lors de la désactivation du plugin.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        boolean allTasksStopped  = false; // Toutes tâches stoppées ?
        boolean warningDisplayed = false; // Message d'alerte de non-stop affiché. 
        long stopAskedTime       = 0L;    // Timestamp de demande d'arrêt des tâches.
        
        // Demande la fin des tâches asynchrones en cours.
        
        synchronized(tasksLock)
        {
            for(AsyncChunksRefresher currentACR : achunksRefreshers.values())
            {
                currentACR.askForStop();
                chunksRefreshersTasks.get(currentACR.getWorld()).cancel(); // Pour éviter démarrage intempestif au prochain tick.
            }
        }

        stopAskedTime = System.currentTimeMillis();

        // Attend la fin des tâches asynchrones en cours.
        
        do
        {
            // Considère que tout est stoppé jusqu'à preuve du contraire.
            
            allTasksStopped = true;
                
            synchronized(tasksLock)
            {
                // Contrôle.
                
                for(BukkitTask currentACRT : chunksRefreshersTasks.values())
                {
                    if(bukkitScheduler.isCurrentlyRunning(currentACRT.getTaskId()))
                    {
                        // On arrête le contrôle dès qu'une tâche active est trouvée et on enregistre cet état.
                        
                        allTasksStopped = false;
                        
                        break;
                    }
                }
            }
                
            // Contrôle du temps depuis lequel on attend.
            
            if(((System.currentTimeMillis() - stopAskedTime) > 20000L) && (!warningDisplayed))
            {
                safeLogger.logWarning("Waiting for asynchronous tasks to stop since 20 seconds...");
                safeLogger.logWarning("Waiting 10 seconds more...");
                
                warningDisplayed = true;
            }
                
            else if((System.currentTimeMillis() - stopAskedTime) >= 30000L)
                break; // On interrompt l'attente après 30 secondes.
            
        } while(!allTasksStopped);
        
        // Arrête le serveur si une tâche asynchrone est toujours en cours.
        
        if(!allTasksStopped)
        {
            safeLogger.logError("Some asynchronous tasks not stopped. Killing server to avoid random issues.");
            
            getServer().shutdown();
        }
    }
    
    @Override
    public void onEnable()
    {
        /* Méthodes appelée lors de l'activation du plugin.
         * Retour : aucun.
         * Paramètres : aucun. */
    }
    
    @Override
    public void onLoad()
    {
        /* Méthodes appelée lors du chargement du plugin.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        Logger logger = null;
        
        // Obtient les objets de gestions de Bukkit.
        
        logger          = getLogger();
        safeLogger      = new SafeLogger(logger);
        server          = getServer();
        bukkitScheduler = server.getScheduler();
    }
    
     // Définitions de méthodes privées de classe.
    
    private void commandChunkRefresher(CommandSender sender, String[] args) throws ChkRefException, UnsupportedOperationException
    {
        /* Méthode de traitement de la commande de raffraichissement des chunks d'un monde.
         * Retour : aucun.
         * Paramètres : -sender  : émetteur de la commande.
                        -args    : arguments. */
        
        AsyncChunksRefresher achunksRefresher = null; // Tâches asynchrone de raffraichissement des chunks.
        BukkitTask chunksRefreshersTask       = null; // Tâches Bukkit de raffraichissement asynchrone des chunks.
        ChkRefOperation operation             = null;
        World world                           = null; // Monde concerné.
        
        try
        {
            // Contrôle des arguments.
            
            if(sender instanceof Player)
                throw new ChkRefException("This command can only be run from console.");
               
            if(args.length < 1)
                throw new ChkRefException("Invalid arguments.");
               
            if((world = server.getWorld(args[0])) == null)
                throw new ChkRefException("Invalid arguments, world \"" + args[0] + "\" doesn't exist or isn't loaded.");

            if((args.length >= 2) && !args[1].equalsIgnoreCase("nomemcheck"))
            {
                try
                {
                    operation = ChkRefOperation.valueOf("CHKREF_" + args[1].toUpperCase());
                }

                catch(IllegalArgumentException exception)
                {
                    throw new ChkRefException("Invalid argument, operation \"" + args[1] + "\" is unknown.");
                }
            }

            else
                operation = ChkRefOperation.CHKREF_CREATE;

            if(args.length > ((operation == ChkRefOperation.CHKREF_CREATE) ? 3 : 2))
                throw new ChkRefException("Too much arguments.");             
            
            synchronized(tasksLock)
            {
                // Exécution.

                switch(operation)
                {
                    case CHKREF_CREATE:
                        /* Créé une tâche asynchrone pour traiter les chunks du monde spécifié, reprenant éventuellement là où elle s'était arrêtée. */
                        
                        safeLogger.logInfo("Creating asynchronous task to refresh chunks of world \"" + args[0] + "\"...");
                        
                        achunksRefresher = new AsyncChunksRefresher(bukkitScheduler, safeLogger, this, world, args[args.length - 1].equalsIgnoreCase("nomemcheck"));
                                
                        achunksRefreshers.put(world, achunksRefresher);
                        
                        if((chunksRefreshersTask = achunksRefresher.runTaskAsynchronously(this)) == null)
                            throw new ChkRefException("Failed to run task to refresh chunks.");
                        
                        chunksRefreshersTasks.put(world, chunksRefreshersTask);

                        break;

                    case CHKREF_RESUME:
                    case CHKREF_PAUSE:
                        /* Continue ou pause une tâche asynchrone pour traiter les chunks du monde spécifié. */
                        
                        if(operation == ChkRefOperation.CHKREF_RESUME)
                            safeLogger.logInfo("Resuming asynchronous task to refresh chunks of world \"" + args[0] + "\"...");

                        else
                            safeLogger.logInfo("Pausing asynchronous task to refresh chunks of world \"" + args[0] + "\"...");

                        achunksRefresher = achunksRefreshers.get(world);
                        
                        if(achunksRefresher == null)
                        {
                            safeLogger.logWarning("No existing task found for world \"" + args[0] + "\"");

                            return;
                        }

                        else
                        {
                            if(operation == ChkRefOperation.CHKREF_RESUME)
                                achunksRefresher.resume();

                            else
                                achunksRefresher.pause();
                        }

                        break;

                    case CHKREF_CANCEL:                    
                        safeLogger.logInfo("Aborting asynchronous task to refresh chunks of world \"" + args[0] + "\"...");

                        achunksRefresher = achunksRefreshers.get(world);
                        
                        if(achunksRefresher == null)
                        {
                            safeLogger.logWarning("No existing task found for world \"" + args[0] + "\"");

                            return;
                        }

                        else
                            achunksRefresher.askForStop(true);

                        break;

                    default:
                        throw new UnsupportedOperationException();
                }
            }
        }
        
        catch(ChkRefException error)
        {
            // Signale l'erreur à un joueur s'il est l'émetteur.
            
            if(sender instanceof Player)
                ((Player)sender).sendRawMessage(error.getMessage());
            
            // Remonte l'erreur.
            
            throw error;
        }
    }

    // Enumérations membres.

    private enum ChkRefOperation
    {
        CHKREF_CREATE,
        CHKREF_PAUSE,
        CHKREF_RESUME,
        CHKREF_CANCEL;
    }
}