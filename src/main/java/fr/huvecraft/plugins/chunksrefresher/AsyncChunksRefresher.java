/* AsyncChunksRefresher.java
 * Classe d'une tâche asynchrone de chargement des chunks du plugin AsyncChunksRefresher pour Spigot.
 * 01/11/2018. */

// Définition du package.

package fr.huvecraft.plugins.chunksrefresher;

// Imports.

import fr.huvecraft.plugins.chunksrefresher.util.SafeLogger;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.World;
import org.bukkit.World.Environment;

// Définition de la classe.

public final class AsyncChunksRefresher extends BukkitRunnable
{
    /* Classe d'une tâche asynchrone de chargement des chunks du plugin AsyncChunksRefresher pour Spigot. */
    
    // Définitions de membres privés.
    
    private List<File> regionFilesList;           // Liste des fichiers de régions.
    private boolean mustStop;                     // Indique que cette tâche doit s'arrêter.
    private boolean nextRegion;                   // Indique que la région en cours a été traitée.
    private BukkitScheduler scheduler;            // Scheduler Bukkit.
    private ChunksRefresher chunkRefresherPlugin; // Instance du plugin hôte.
    int currentRegionIndex;                       // Index du fichier de région en cours.
    int nChunksRefreshedInWorld;                  // Nombre total de chunks traités dans le monde.
    int xCurrentChunkStart;                       // X de la rangée de chunks en cours.
    int xCurrentRegion;                           // X de la région en cours.
    int zCurrentRegion;                           // Z de la région en cours.
    private Object mustStopLock;                  // Verrou d'accès à l'indicateur d'arrêt de tâche.
    private SafeLogger safeLogger;                // Loggueur thread-safe.
    private World world;                          // Monde concerné.
    private WorldData worldData;                  // Données concernant le monde.
    
    // Constructeurs.
    
    public AsyncChunksRefresher(BukkitScheduler scheduler, SafeLogger safeLogger, ChunksRefresher chunkRefresherPlugin, World world) throws Error
    {
        /* Constructeur par défaut. */
        
        if(!(world instanceof World))
            throw new Error("Invalid world specified.");
        
        else if(!((scheduler instanceof BukkitScheduler) && (safeLogger instanceof SafeLogger) && (chunkRefresherPlugin instanceof ChunksRefresher)))
            throw new Error("Invalid parameters.");
        
        this.mustStop                = false;
        this.mustStopLock            = new Object();
        this.regionFilesList         = null;
        this.scheduler               = scheduler;
        this.chunkRefresherPlugin    = chunkRefresherPlugin;
        this.currentRegionIndex      = 0;
        this.nChunksRefreshedInWorld = 0;
        this.xCurrentChunkStart      = 0;
        this.xCurrentRegion          = 0;
        this.zCurrentRegion          = 0;
        this.safeLogger              = safeLogger;
        this.world                   = world;
        this.worldData               = null;
        
    }
    
    // Méthodes publiques de classe.
    
    public void askForStop()
    {
        /* Défini si la tâche doit s'arrêter.
         * Retour : aucun. 
         * Paramètres : oui ou non. */
        
        // Défini la valeur de manière thread-safe.
        
        synchronized(mustStopLock)
        {
            mustStop = true;
        }
    }
    
    @Override
    public void run()
    {
        /* Fonction appellée à la création de la tâche.
         * Retour : aucun. 
         * Paramètres : aucun. */
        
        boolean completed            = false; // Tâche achevée.
        int nChunksRefreshedInRegion = 0;     // Nombre de chunks traités dans l'itération de la boucle de traitement.
        
        try
        {
            // Obtient la localisation du spawn du monde.
            
            safeLogger.logInfo("Getting world data...");
            
            if(!(getWorldData()))
                throw new Error("Failed to get world data.");
            
            safeLogger.logInfo("World spawn location is X:" + worldData.getXSpawnLocation() + " Y:" + worldData.getYSpawnLocation() + " Z:" + worldData.getZSpawnLocation() + ".");
            
            // Obtient les noms des fichiers de régions contenus dans le dossier du monde.
            
            listRegionFiles();
            
            // Boucle de découverte et d'actualisation des chunks, uniquement si au moins une région a été trouvée.
            
            if(regionFilesList.size() != 0)
            {
                // On s'assure de commencer par le 1er fichier de région.
                
                currentRegionIndex      = 0;
                nextRegion              = true; // Pour forcer l'exécution du du bloc déterminant X et Z de la région.
                
                // Boucle.
                
                while(!((isStopAsked()) || (completed)))
                {
                    // Détermine les coordonnées de la région associée, si son traitement débute.
                    
                    if(nextRegion)
                    {
                        determineRegionXZ();
                        
                        xCurrentChunkStart       = 0;     // On commence par la 1ère rangée de chunks.
                        nChunksRefreshedInRegion = 0;     // Réinitialise le nombre de chunks traités dans la région.
                        nextRegion               = false; // Traitement en cours de la région.
                        
                        // Traite chaque nom de fichier.
                        
                        safeLogger.logInfo("Refreshing chunks in region X:" + xCurrentRegion + " Z:" + zCurrentRegion + " of world \"" + worldData.getWorldName() + "\"...");
                    }
                    
                    // On traite un maximum de 64 chunks à chaque itération (2 rangées de 32 chunks).
                    
                    if((nChunksRefreshedInRegion += refreshChunksFromFilenames(2)) < 0)
                        throw new Error("Failed to discover and refresh chunks.");
                    
                    // Région en cours terminée ?
                    
                    if(nextRegion)
                    {
                        safeLogger.logInfo(nChunksRefreshedInRegion + " chunks discovered and refreshed in region X:" + xCurrentRegion + " Z:" + zCurrentRegion + " of world \"" + worldData.getWorldName() + "\".");
                        
                        // Incrémente l'index de la région en cours et ajoute le total des chunks traités dans la région à ceux du monde.
                        
                        currentRegionIndex++;
                        
                        nChunksRefreshedInWorld += nChunksRefreshedInRegion;
                        
                        // Toutes les régions ont été traitées ?
                        
                        if(currentRegionIndex == regionFilesList.size())
                            completed = true; // Arrête la boucle.
                    }
                    
                    // Pause 10ms.
                    
                    Thread.sleep(10L);
                }
            }
            
            safeLogger.logInfo("Total of " + nChunksRefreshedInWorld + " chunks discovered and refreshed in world \"" + worldData.getWorldName() + "\".");
            
            // Fin d'exécution.
            
            if((completed) || (isStopAsked()))
                safeLogger.logInfo("Chunks refresh " + (completed ? "completed" : "endded") + " for world \"" + worldData.getWorldName() + "\".");
            
            else
                safeLogger.logError("Chunks refresh endded for world \"" + worldData.getWorldName() + "\".");
        }
        
        catch(InterruptedException error)
        {
            // Interrompu.
            
            if(worldData instanceof WorldData)
                safeLogger.logInfo("Chunks refresh interrupted for world \"" + worldData.getWorldName() + "\": " + error.getMessage());
            
            else
                safeLogger.logInfo("Chunks refresh interrupted : " + error.getMessage());
        }
        
        catch(Exception|Error error)
        {
            // Echec.
            
            if(worldData instanceof WorldData)
                safeLogger.logError("Chunks refresh error for world \"" + worldData.getWorldName() + "\": " + error.getMessage());
            
            else
                safeLogger.logError("Chunks refresh error : " + error.getMessage());
        }
    }
    
    // Méthodes privées de classe.
    
    private void determineRegionXZ() throws Exception
    {
        /* Obtient détermine les coordonnées X et Z de la région indiquée par currentRegionIndex.
         * Retour : aucun.
         * Paramètres : aucun. */
        
        String regionFilenameParts[] = null;
        
        // Détermine les coordonnées de la région.
        
        regionFilenameParts = regionFilesList.get(currentRegionIndex).getName().split("\\.");
            
        xCurrentRegion = Integer.parseInt(regionFilenameParts[1]);
        zCurrentRegion = Integer.parseInt(regionFilenameParts[2]);
    }
    
    private boolean getWorldData() throws InterruptedException
    {
        /* Obtient les données du monde.
         * Retour : objet Location ou null si échec.
         * Paramètres : aucun. */
        
        Future<WorldData> futureWorldData = null; // Objet permettant d'obtenir l'information depuis Bukkit.
        
        try
        {
            // Obtient les données depuis Bukkit.
            
            if((futureWorldData = scheduler.callSyncMethod(chunkRefresherPlugin, new WorldData(safeLogger, world))) == null)
                throw new Error("Failed to get world data.");
            
            futureWaiter(futureWorldData);
            
            if((worldData = futureWorldData.get()) == null)
                throw new Error("Failed to get world data.");
        }        
        
        catch(ExecutionException|IllegalArgumentException|Error error)
        {
            // Echec.
            
            return false;
        }
        
        // Succès.
        
        return true;
    }
    
    private boolean isStopAsked()
    {
        /* Indique si la tâche doit s'arrêter.
         * Retour : oui ou non. 
         * Paramètres : aucun. */
        
        boolean mustStopLocal = false; // Variable de retour locale.
        
        // Obtient la valeur de manière thread-safe.
        
        synchronized(mustStopLock)
        {
            mustStopLocal = mustStop;
        }
        
        return mustStopLocal;
    }
    
    private int refreshChunksFromFilenames(int nLinesOfChunks) throws InterruptedException
    {
        /* Découvre et raffrichi au maximum n rangées de 32 chunks dans la région en cours.
         * Retour : aucun.
         * Paramètre : nLinesOfChunks : nombre de rangées de 32 chunks à traiter. */
        
        
        Future<Integer> futureChunksRefreshed  = null; // Objet permettant l'obtention du nombre de chunks traités depuis Bukkit.
        Integer nChunksRefreshedRegion         = null; // Chunks raffraichis dans la région.
        
        try
        {
            if((nLinesOfChunks < 1) || (nLinesOfChunks > 32))
                throw new IllegalArgumentException();
            
            // Enumération chunks dans la région.
            
            if((futureChunksRefreshed = scheduler.callSyncMethod(chunkRefresherPlugin, new ChunksEnumerator(safeLogger, world, xCurrentRegion, zCurrentRegion, xCurrentChunkStart, nLinesOfChunks))) == null)
                throw new Error("Failed to refresh chunks.");
            
            futureWaiter(futureChunksRefreshed);
            
            if((nChunksRefreshedRegion = futureChunksRefreshed.get()) == -1)
                throw new Error("Failed to refresh chunks.");
            
            // Déplace le pointeur vers la prochaine rangée de chunks à traiter,
            // indique que la région a été totalement traitée si on a traité 31 rangées de celle-ci.
            
            if((xCurrentChunkStart += nLinesOfChunks) >= 32) // Ne devrait pas être supérieur mais bon... #CherchePasLaMisère
                nextRegion = true;
        }
        
        catch(ExecutionException|IllegalArgumentException|NullPointerException|Error error)
        {
            // Echec.
            
            return -1;
        }
        
        // Succès.
        
        return nChunksRefreshedRegion;
    }
    
    private void futureWaiter(Future<?> future) throws IllegalArgumentException, InterruptedException
    {
        /* Attend la fin d'une tâche synchrone du thread Bukkit.
         * Retour : aucun.
         * Paramètre : objet Future de la tâche en question. */
        
        // Contrôle.
        
        if(!(future instanceof Future<?>))
            throw new IllegalArgumentException();
        
        // Attente.
        
        while(!(future.isDone()))
        {
            if(future.isCancelled())
                throw new InterruptedException("Synchronous task canceled.");
            
            Thread.sleep(5L);
        }
    } 
    
    private void listRegionFiles() throws Exception, Error
    {
        /* Liste les fichiers regions situés dans le dossier du monde.
         * Retour : aucun.
         * Paramètre : aucun. */
        
        File[] regionsFiles = null; // Liste obtenue.
        
        try
        {
            // Liste les fichiers. 
            
            if((regionsFiles = worldData.getWorldRegionFolder().listFiles(new RegionFilesFilter())) == null)
                throw new Error("Can't get world region files list.");
            
            // Enregistre le résulat sous forme de List.
            
            regionFilesList = Arrays.asList(regionsFiles);
        }
        
        catch(Exception|Error error)
        {
            // Echec. 
            
            throw error;
        }
    }
}

// Définitions de classes associées privées.

final class ChunksEnumerator implements Callable<Integer>
{
    /* Classe énumérant et retournant les chunks à raffraichir. */
    
    // Membres.
    
    int nLinesOfChunks;            // Nombres de rangées de chunks à traiter.
    int xChunkStart;               // Coordonnée X de la rangée de chunks par laquelle débuter.
    int xRegion;                   // Coordonnée X de la région à traiter.
    int zRegion;                   // Coordonnée Z de la région à traiter.
    World world;                   // Monde concerné.
    @SuppressWarnings("unused")
    private SafeLogger safeLogger; // Loggeur thread-safe.
    
    // Constructeurs.
    
    public ChunksEnumerator(SafeLogger safeLogger, World world, int xRegion, int zRegion, int xChunkStart, int nLinesOfChunks) throws IllegalArgumentException
    {
        /* Constructeur par défaut. */
        
        // Contrôle.
        
        if((!(safeLogger instanceof SafeLogger)) || (!(world instanceof World)) || ((xChunkStart < 0) || (xChunkStart > 31)) || ((nLinesOfChunks < 1) || (nLinesOfChunks > 32)))
            throw new IllegalArgumentException();
        
        // Affectation.
        
        this.nLinesOfChunks = nLinesOfChunks;
        this.xChunkStart    = xChunkStart;
        this.xRegion        = xRegion;
        this.zRegion        = zRegion;
        this.safeLogger     = safeLogger;
        this.world          = world;
    }
    
    // Fonctions publiques de classe.
    
    @Override
    public Integer call()
    {
        /* Fonction obtenant les données depuis le thread Bukkit.
         * Retour : ArrayList d'objets Chunk à raffraichir. 
         * Paramètres : aucun. */

        boolean chunkLoaded        = false; // Chunk à traité déjà chargé ?
        Chunk currentChunk         = null;  // Chunk en cours.
        int nRegionChunksRefreshed = 0;     // Chunks à découverts et raffraichis dans la région en cours.
        int xChunk                 = 0;     // Coordonnée X du chunk à traiter.
        int zChunk                 = 0;     // Coordonnée Z du chunk à traiter.
        int xChunkIndex            = 0;     // Index X dans la région du chunk à traiter.
        int zChunkIndex            = 0;     // Index Z dans la région du chunk à traiter.
        
        try
        {            
            // Parcours des chunks.
            
            xChunkIndex = xChunkStart;
            
            for(xChunkIndex = xChunkStart; (xChunkIndex < 32) && (xChunkIndex < (xChunkStart + nLinesOfChunks)); xChunkIndex++)
            {
                // Coordonnée X.
                
                xChunk = (xRegion * 32) + xChunkIndex;
                
                // Axe Z.
                
                for(zChunkIndex = 0; zChunkIndex < 32; zChunkIndex++)
                {
                    // Coordonnée Z.
                    
                    zChunk = (zRegion * 32) + zChunkIndex;
                    
                    // Ajoute le chunk à la liste de ceux traités, s'il existe.
                    
                    if(world.isChunkGenerated(xChunk, zChunk)) // Existe ?
                    {
                        // Déjà chargé ?
                        
                        chunkLoaded = world.isChunkLoaded(xChunk, zChunk);
                        
                        // Obtient le chunk aux coordonnées indiquées.

                        if((currentChunk = world.getChunkAt(xChunk, zChunk)) == null)
                            throw new Error("Cannot refresh chunk.");
                        
                        // Le décharge s'il n'était pas chargé avant l'opération.
                        
                        if(!(chunkLoaded))
                            currentChunk.unload(); // Pas de contrôle d'erreur.

                        // Incrémente le nombre de chunks de la région traités.
                        
                        nRegionChunksRefreshed++;
                    }
                }
                
                // Pause 10ms.
                
                Thread.sleep(10L);
            }
        }
        
        catch(Exception|Error exception)
        {
            // Echec.
            
            return -1;
        }
        
        // Succès.
        
        return nRegionChunksRefreshed;
    }
}

final class RegionFilesFilter implements FilenameFilter
{
    /* Classe permettant de ne lister que les fichiers de régions lors de l'exploration du dossier du jeu. */
    
    // Méthodes publiques de classe.
    
    @Override
    public boolean accept(File hostDirectory, String filename) throws IllegalArgumentException
    {
        /* Fonction effectuant la sélection.
         * Retour : fichier accepté ou non.
         * Paramètres : dossier hôte et nom du fichier en question. */
        
        // Contrôle.
        
        if(!(filename instanceof String))
            throw new IllegalArgumentException();
        
        // Filtrage.
        
        if(filename.matches("(?i)\\Ar\\.-?\\d+\\.-?\\d+\\.mca\\z")) // JS version : /^r\\.-?\\d+\\.-?\\d+\\.mca$/gi
            return true;
        
        return false;
    }
}

final class WorldData implements Callable<WorldData>
{
    /* Classe contenant les informations concernant le monde à traiter. */
    
    // Membres.
    
    private File worldRegionFolder;       // Répertoire des régions du monde.
    private Location spawnLocation;       // Localisation du spawn du monde.
    private String worldName;             // Nom du monde.
    private World world;                  // Monde concerné.
    private Environment worldEnvironment; // Environement du monde concerné.
    private int xSpawnLocation;           // X du spawn.
    private int ySpawnLocation;           // Y du spawn.
    private int zSpawnLocation;           // Z du spawn.
    @SuppressWarnings("unused")
    private SafeLogger safeLogger;        // Loggeur thread-safe. 
    
    // Constructeurs.
    
    public WorldData(SafeLogger safeLogger, World world) throws IllegalArgumentException
    {
        /* Constructeur par défaut. */
        
        // Contrôle.
        
        if((!(safeLogger instanceof SafeLogger)) || (!(world instanceof World)))
            throw new IllegalArgumentException();
        
        // Affectation.
        
        this.safeLogger        = safeLogger;
        this.world             = world;
        this.worldEnvironment  = null;
        this.spawnLocation     = null;
        this.worldName         = null;
        this.worldRegionFolder = null;
        this.xSpawnLocation    = 0;
        this.ySpawnLocation    = 0;
        this.zSpawnLocation    = 0;
    }
    
    // Fonctions publiques de classe.
    
    public Environment getWorldEnvironment()
    {
        /* Retourne l'environnement du monde.
         * Retour : environnement du monde en question.
         * Paramètres : aucun. */
        
        return worldEnvironment;
    }
    
    public File getWorldRegionFolder()
    {
        /* Retourne le répertoire du monde.
         * Retour : int de la donnée en question.
         * Paramètres : aucun. */
        
        return worldRegionFolder;
    }
    
    public Location getSpawnLocation()
    {
        /* Retourne la localisation Z du spawn du monde.
         * Retour : int de la donnée en question.
         * Paramètres : aucun. */
        
        return spawnLocation;
    }
    
    public String getWorldName()
    {
        /* Retourne le nom du monde.
         * Retour : chaîne en question.
         * Paramètres : aucun. */
        
        return worldName;
    }
    
    @Override
    public WorldData call() throws Exception, Error
    {
        /* Fonction obtenant les données depuis le thread Bukkit.
         * Retour : cet objet. 
         * Paramètres : aucun. */
        
        File worldFolder             = null; // Dossier du monde.
        String worldRegionFolderPath = null; // Chemin du dossier des régions du monde.
        
        // Obtient les données. 
        
        worldEnvironment  = world.getEnvironment();    // Environnement du monde.
        worldFolder       = world.getWorldFolder();    // Dossier du monde.
        spawnLocation     = world.getSpawnLocation();  // Spawn du monde.
        worldName         = world.getName();           // Nom du monde.
        xSpawnLocation    = spawnLocation.getBlockX(); // Coordonnées du spawn.
        ySpawnLocation    = spawnLocation.getBlockY();
        zSpawnLocation    = spawnLocation.getBlockZ();        
        
        // Détermine et ouvre le dossier des région du monde.
        
        switch(worldEnvironment)
        {
            case NETHER:
                worldRegionFolderPath = "DIM-1/region";
                break;
                
            case NORMAL:
                worldRegionFolderPath = "region";
                
                break;
                
            case THE_END:
                worldRegionFolderPath = "DIM1/region";
                
                break;
                
            default:
                throw new Error("Unknown world environement");    
        }
        
        worldRegionFolder = new File(worldFolder, worldRegionFolderPath);
        
        // Contrôle si le dossier "region" existe.
        
        if(!((worldRegionFolder.exists()) && (worldRegionFolder.isDirectory())))
            return null;
        
        return this;
    }
    
    public int getXSpawnLocation()
    {
        /* Retourne la localisation X du spawn du monde.
         * Retour : object Location.
         * Paramètres : aucun. */
        
        return xSpawnLocation;
    }
    
    public int getYSpawnLocation()
    {
        /* Retourne la localisation Y du spawn du monde.
         * Retour : int de la donnée en question.
         * Paramètres : aucun. */
        
        return ySpawnLocation;
    }
    
    public int getZSpawnLocation()
    {
        /* Retourne la localisation Z du spawn du monde.
         * Retour : int de la donnée en question.
         * Paramètres : aucun. */
        
        return zSpawnLocation;
    }
    
    public World getWorld()
    {
        /* Retourne le monde.
         * Retour : monde en question.
         * Paramètres : aucun. */
        
        return world;
    }
}