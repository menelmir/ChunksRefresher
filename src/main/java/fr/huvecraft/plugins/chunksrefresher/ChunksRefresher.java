/* ChunksRefresher.java
 * Classe principale du plugin ChunksRefresher pour Spigot.
 * 23/10/2018. */

// D�finition du package.

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

// D�finition de la classe.

public final class ChunksRefresher extends JavaPlugin
{
    /* Classe principale du plugin ChunksRefresher pour Spigot. */
    
    // D�finitions de membres priv�s.
    
    private static boolean loaded   = false;                   // Indique si le plugin existe d�j�.
    private ArrayList<AsyncChunksRefresher> achunksRefreshers; // Raffraichisseurs asynchrones de chunks.
    private ArrayList<BukkitTask> chunksRefreshersTasks;       // T�ches Bukkit des raffraichisseurs asynchrones de chunks.
    private BukkitScheduler bukkitScheduler;                   // Gestionnaire de t�ches Bukkit.
    private SafeLogger safeLogger;                             // Loggueur thread-safe.
    private Server server;                                     // Serveur Spigot.
    
    // Constructeurs.
    
    public ChunksRefresher() throws Error
    {
        /* Constructeur par d�faut.
         * Retour : aucun.
         * Param�tres : aucun. */
        
        // Instance unique ?
        
        if(loaded)
            throw new Error("This instance is not the first instance ! It will not be loaded."); // Oui.
        
        loaded = true;                                                                           // Non, on indique qu'une est d�sormais charg�e.
        
        // Initialise les membres.
        
        achunksRefreshers     = new ArrayList<AsyncChunksRefresher>();
        bukkitScheduler       = null;
        chunksRefreshersTasks = new ArrayList<BukkitTask>();
        safeLogger            = null;
        server                = null;
    }
    
    // D�finitions de m�thodes publiques de classe.
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, java.lang.String label, java.lang.String[] args)
    {
        /* M�thodes appel�e lors de l'appel � une commande enregistr�e du plugin.
         * Retour : boolean, commande trait�e avec succ�s ou non.
         * Param�tres : -sender  : �metteur de la commande.
                        -command : commande en question.
                        -label   : alias de la commande utilis�e.
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
                    
                    // Succ�s.
                    
                    return true;
            }
        }
        
        catch(Error error)
        {
            safeLogger.logWarning(error.getMessage());
        }
        
        // Commande non trait�e : �chec.
        
        return false;
    }
    
    @Override
    public void onDisable()
    {
        /* M�thodes appel�e lors de la d�sactivation du plugin.
         * Retour : aucun.
         * Param�tres : aucun. */
        
        boolean allTasksStopped  = false; // Toutes t�ches stopp�es ?
        boolean warningDisplayed = false; // Message d'alerte de non-stop affich�. 
        long stopAskedTime       = 0L;    // Timestamp de demande d'arr�t des t�ches.
        
        try
        {
            // Demande la fin des t�ches asynchrones en cours.
            
            for(int currentACRIndex = 0; currentACRIndex < achunksRefreshers.size(); currentACRIndex++)
                achunksRefreshers.get(currentACRIndex).askForStop();
            
            stopAskedTime = System.currentTimeMillis();
            
            // Attend la fin des t�ches asynchrones en cours.
            
            do
            {
                // Consid�re que tout est stopp� jusqu'� preuve du contraire.
                
                allTasksStopped = true;
                
                // Contr�le.
                
                for(int currentACRTIndex = 0; currentACRTIndex < chunksRefreshersTasks.size(); currentACRTIndex++)
                {
                    if(bukkitScheduler.isCurrentlyRunning(chunksRefreshersTasks.get(currentACRTIndex).getTaskId()))
                    {
                        // On arr�te le contr�le d�s qu'une t�che active est trouv�e et on enregistre cet �tat.
                        
                        allTasksStopped = false;
                        
                        break;
                    }
                }
                
                // Contr�le du temps depuis lequel on attend.
                
                if(((System.currentTimeMillis() - stopAskedTime) > 10000L) && (!(warningDisplayed)))
                {
                    safeLogger.logWarning("Waiting for asynchronous tasks to stop since 10 seconds...");
                    safeLogger.logWarning("Waiting 10 seconds more...");
                    
                    warningDisplayed = true;
                }
                    
                else if((System.currentTimeMillis() - stopAskedTime) >= 20000L)
                    break; // On interrompt l'attente apr�s 20 secondes.
                
            } while(!(allTasksStopped));
            
            // Interrompt les t�ches asynchrones en cours. @@
            
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
        /* M�thodes appel�e lors de l'activation du plugin.
         * Retour : aucun.
         * Param�tres : aucun. */
        
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
        /* M�thodes appel�e lors du chargement du plugin.
         * Retour : aucun.
         * Param�tres : aucun. */
        
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
    
     // D�finitions de m�thodes priv�es de classe.
    
    private void commandChunkRefresher(CommandSender sender, String[] args) throws Error
    {
        /* M�thode de traitement de la commande de raffraichissement des chunks d'un monde.
         * Retour : aucun.
         * Param�tres : -sender  : �metteur de la commande.
                        -args    : arguments. */
        
        AsyncChunksRefresher achunksRefresher = null; // T�ches asynchrone de raffraichissement des chunks.
        BukkitTask chunksRefreshersTask       = null; // T�ches Bukkit de raffraichissement asynchrone des chunks.
        World world                           = null; // Monde concern�.
        
        try
        {
            // Contr�le des arguments.
            
            if(sender instanceof Player)
                throw new Error("This command can only be run from console.");
               
            if((args.length != 1) || (!(args[0] instanceof String)))
                throw new Error("Invalid arguments.");
               
            if((world = server.getWorld(args[0])) == null)
                throw new Error("Invalid arguments, world \"" + args[0] + "\" doesn't exist or isn't loaded.");
            
            // Cr�� une t�che asynchrone pour traiter les chunks du monde sp�cifi�.
            
            safeLogger.logInfo("Creating asynchronous task to refresh chunks of world \"" + args[0] + "\"...");
            
            achunksRefresher = new AsyncChunksRefresher(bukkitScheduler, safeLogger, this, world);
                    
            achunksRefreshers.add(achunksRefresher);
            
            if((chunksRefreshersTask = achunksRefresher.runTaskAsynchronously(this)) == null)
                throw new Error("Failed to run task to refresh chunks.");
            
            chunksRefreshersTasks.add(chunksRefreshersTask);
        }
        
        catch(Error error)
        {
            // Signale l'erreur � un joueur s'il est l'�metteur.
            
            if(sender instanceof Player)
                ((Player)sender).sendRawMessage(error.getMessage());
            
            // Remonte l'erreur.
            
            throw error;
        }
    }
}