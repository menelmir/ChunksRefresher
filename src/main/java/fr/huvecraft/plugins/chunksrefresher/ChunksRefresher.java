/* ChunksRefresher.java
 * Classe principale du plugin ChunksRefresher pour Spigot.
 * 23/10/2018. */

// Définition du package.

package fr.huvecraft.plugins.chunksrefresher;

// Imports.

import java.util.ArrayList;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import fr.huvecraft.plugins.chunksrefresher.util.SafeLogger;

import org.bukkit.Server;
import org.bukkit.World;

// Définition de la classe.

public final class ChunksRefresher extends JavaPlugin
{
    /* Classe principale du plugin ChunksRefresher pour Spigot. */
    
    // Définitions de membres privés.
    
    private static boolean loaded   = false;                   // Indique si le plugin existe déjà.
    private ArrayList<AsyncChunksRefresher> achunksRefreshers; // Raffraichisseurs asynchrones de chunks.
    private ArrayList<BukkitTask> chunksRefreshersTasks;       // Tâches Bukkit des raffraichisseurs asynchrones de chunks.
    private BukkitScheduler bukkitScheduler;                   // Gestionnaire de tâches Bukkit.
    private SafeLogger safeLogger;                             // Loggueur thread-safe.
    private Server server;                                     // Serveur Spigot.
    
    // Constructeurs.
    
    public ChunksRefresher() throws Error
    {
        /* Constructeur par défaut.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        // Instance unique ?
        
        if(loaded)
            throw new Error("This instance is not the first instance ! It will not be loaded."); // Oui.
        
        loaded = true;                                                                           // Non, on indique qu'une est désormais chargée.
        
        // Initialise les membres.
        
        achunksRefreshers     = new ArrayList<AsyncChunksRefresher>();
        bukkitScheduler       = null;
        chunksRefreshersTasks = new ArrayList<BukkitTask>();
        safeLogger            = null;
        server                = null;
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
        
        catch(Error error)
        {
            safeLogger.logWarning(error.getMessage());
        }
        
        // Commande non traitée : échec.
        
        return false;
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
        
        try
        {
            // Demande la fin des tâches asynchrones en cours.
            
            for(int currentACRIndex = 0; currentACRIndex < achunksRefreshers.size(); currentACRIndex++)
                achunksRefreshers.get(currentACRIndex).askForStop();
            
            stopAskedTime = System.currentTimeMillis();
            
            // Attend la fin des tâches asynchrones en cours.
            
            do
            {
                // Considère que tout est stoppé jusqu'à preuve du contraire.
                
                allTasksStopped = true;
                
                // Contrôle.
                
                for(int currentACRTIndex = 0; currentACRTIndex < chunksRefreshersTasks.size(); currentACRTIndex++)
                {
                    if(bukkitScheduler.isCurrentlyRunning(chunksRefreshersTasks.get(currentACRTIndex).getTaskId()))
                    {
                        // On arrête le contrôle dès qu'une tâche active est trouvée et on enregistre cet état.
                        
                        allTasksStopped = false;
                        
                        break;
                    }
                }
                
                // Contrôle du temps depuis lequel on attend.
                
                if(((System.currentTimeMillis() - stopAskedTime) > 10000L) && (!(warningDisplayed)))
                {
                    safeLogger.logWarning("Waiting for asynchronous tasks to stop since 10 seconds...");
                    safeLogger.logWarning("Waiting 10 seconds more...");
                    
                    warningDisplayed = true;
                }
                    
                else if((System.currentTimeMillis() - stopAskedTime) >= 20000L)
                    break; // On interrompt l'attente après 20 secondes.
                
            } while(!(allTasksStopped));
            
            // Interrompt les tâches asynchrones en cours. @@
            
            if(!(allTasksStopped))
            {
                safeLogger.logError("Some asynchronous tasks not stopped. Gonna kill them.");
                
                for(int currentACRTIndex = 0; currentACRTIndex < chunksRefreshersTasks.size(); currentACRTIndex++)
                    chunksRefreshersTasks.get(currentACRTIndex).cancel();
            }
        }
        
        catch(Error error)
        {
            safeLogger.logError("Error disabling ChunksRefresher");
        }
    }
    
    @Override
    public void onEnable()
    {
        /* Méthodes appelée lors de l'activation du plugin.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        try
        {
        }
        
        catch(Error error)
        {
            safeLogger.logError("Error enabling ChunksRefresher");
        }
    }
    
    @Override
    public void onLoad()
    {
        /* Méthodes appelée lors du chargement du plugin.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        Logger logger = null;
        
        try
        {
            // Obtient les objets de gestions de Bukkit.
            
            logger          = getLogger();
            safeLogger      = new SafeLogger(logger);
            server          = getServer();
            bukkitScheduler = server.getScheduler();
        }
        
        catch(Error error)
        {
            if(logger != null)
                logger.severe("Error loading ChunksRefresher");
        }
    }
    
     // Définitions de méthodes privées de classe.
    
    private void commandChunkRefresher(CommandSender sender, String[] args) throws Error
    {
        /* Méthode de traitement de la commande de raffraichissement des chunks d'un monde.
         * Retour : aucun.
         * Paramètres : -sender  : émetteur de la commande.
                        -args    : arguments. */
        
        AsyncChunksRefresher achunksRefresher = null; // Tâches asynchrone de raffraichissement des chunks.
        BukkitTask chunksRefreshersTask       = null; // Tâches Bukkit de raffraichissement asynchrone des chunks.
        World world                           = null; // Monde concerné.
        
        try
        {
            // Contrôle des arguments.
            
            if(sender instanceof Player)
                throw new Error("This command can only be run from console.");
               
            if((args.length != 1) || (!(args[0] instanceof String)))
                throw new Error("Invalid arguments.");
               
            if((world = server.getWorld(args[0])) == null)
                throw new Error("Invalid arguments, world \"" + args[0] + "\" doesn't exist or isn't loaded.");
            
            // Créé une tâche asynchrone pour traiter les chunks du monde spécifié.
            
            safeLogger.logInfo("Creating asynchronous task to refresh chunks of world \"" + args[0] + "\"...");
            
            achunksRefresher = new AsyncChunksRefresher(bukkitScheduler, safeLogger, this, world);
                    
            achunksRefreshers.add(achunksRefresher);
            
            if((chunksRefreshersTask = achunksRefresher.runTaskAsynchronously(this)) == null)
                throw new Error("Failed to run task to refresh chunks.");
            
            chunksRefreshersTasks.add(chunksRefreshersTask);
        }
        
        catch(Error error)
        {
            // Signale l'erreur à un joueur s'il est l'émetteur.
            
            if(sender instanceof Player)
                ((Player)sender).sendRawMessage(error.getMessage());
            
            // Remonte l'erreur.
            
            throw error;
        }
    }
}