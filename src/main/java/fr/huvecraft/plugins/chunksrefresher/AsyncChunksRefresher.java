/* AsyncChunksRefresher.java
 * Classe d'une t�che asynchrone de chargement des chunks du plugin AsyncChunksRefresher pour Spigot.
 * 01/12/2018. */

// D�finition du package.

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

// D�finition de la classe.

public final class AsyncChunksRefresher extends BukkitRunnable
{
    /* Classe d'une t�che asynchrone de chargement des chunks du plugin AsyncChunksRefresher pour Spigot. */
    
    // D�finitions de membres priv�s.
    
    private List<File> regionFilesList;           // Liste des fichiers de r�gions.
    private boolean mustStop;                     // Indique que cette t�che doit s'arr�ter.
    private boolean nextRegion;                   // Indique que la r�gion en cours a �t� trait�e.
    private BukkitScheduler scheduler;            // Scheduler Bukkit.
    private ChunksRefresher chunkRefresherPlugin; // Instance du plugin h�te.
    int currentRegionIndex;                       // Index du fichier de r�gion en cours.
    int nChunksRefreshedInWorld;                  // Nombre total de chunks trait�s dans le monde.
    int xCurrentChunkStart;                       // X de la rang�e de chunks en cours.
    int xCurrentRegion;                           // X de la r�gion en cours.
    int zCurrentRegion;                           // Z de la r�gion en cours.
    private Object mustStopLock;                  // Verrou d'acc�s � l'indicateur d'arr�t de t�che.
    private SafeLogger safeLogger;                // Loggueur thread-safe.
    private World world;                          // Monde concern�.
    private WorldData worldData;                  // Donn�es concernant le monde.
    
    // Constructeurs.
    
    public AsyncChunksRefresher(BukkitScheduler scheduler, SafeLogger safeLogger, ChunksRefresher chunkRefresherPlugin, World world) throws Error
    {
        /* Constructeur par d�faut. */
        
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
    
    // M�thodes publiques de classe.
    
    public void askForStop()
    {
        /* D�fini si la t�che doit s'arr�ter.
         * Retour : aucun. 
         * Param�tres : oui ou non. */
        
        // D�fini la valeur de mani�re thread-safe.
        
        synchronized(mustStopLock)
        {
            mustStop = true;
        }
    }
    
    @Override
    public void run()
    {
        /* Fonction appell�e � la cr�ation de la t�che.
         * Retour : aucun. 
         * Param�tres : aucun. */
        
        boolean completed            = false; // T�che achev�e.
        boolean outOfMemory          = false; // T�che interrompue par manque de m�moire.
        int nChunksRefreshedInRegion = 0;     // Nombre de chunks trait�s dans l'it�ration de la boucle de traitement.
        
        try
        {
            // Obtient la localisation du spawn du monde.
            
            safeLogger.logInfo("Getting world data...");
            
            if(!(getWorldData()))
                throw new Error("Failed to get world data.");
            
            safeLogger.logInfo("World spawn location is X:" + worldData.getXSpawnLocation() + " Y:" + worldData.getYSpawnLocation() + " Z:" + worldData.getZSpawnLocation() + ".");
            
            // Obtient les noms des fichiers de r�gions contenus dans le dossier du monde.
            
            listRegionFiles();
            
            // Boucle de d�couverte et d'actualisation des chunks, uniquement si au moins une r�gion a �t� trouv�e.
            
            if(regionFilesList.size() != 0)
            {
                // On s'assure de commencer par le 1er fichier de r�gion.
                
                currentRegionIndex      = 0;
                nextRegion              = true; // Pour forcer l'ex�cution du du bloc d�terminant X et Z de la r�gion.
                
                // Boucle.
                
                while(!((isStopAsked()) || (completed) || (outOfMemory)))
                {
                    // D�termine les coordonn�es de la r�gion associ�e, si son traitement d�bute.
                    
                    if(nextRegion)
                    {
                        determineRegionXZ();
                        
                        xCurrentChunkStart       = 0;     // On commence par la 1�re rang�e de chunks.
                        nChunksRefreshedInRegion = 0;     // R�initialise le nombre de chunks trait�s dans la r�gion.
                        nextRegion               = false; // Traitement en cours de la r�gion.
                        
                        // Traite chaque nom de fichier.
                        
                        safeLogger.logInfo("Refreshing chunks in region X:" + xCurrentRegion + " Z:" + zCurrentRegion + " of world \"" + worldData.getWorldName() + "\"...");
                    }
                    
                    // On traite un maximum de 64 chunks � chaque it�ration (2 rang�es de 32 chunks).
                    // -> Si un indicateur comme quoi ce fichier a d�j� �t� trait� existe, on ignore cette r�gion et
                    //    et on demande le passage � la suivante.
                    
                    if(!(isCurrentRegionAlreadyRefreshed()))
                    {
                        if((nChunksRefreshedInRegion += refreshChunksFromFilenames(2)) < 0)
                            throw new Error("Failed to discover and refresh chunks.");
                        
                        // Nettoyage.
                        
                        System.gc();
                    }
                    
                    else
                    {
                        safeLogger.logInfo("Region already refreshed ! Skipping...");
                        
                        nextRegion = true;
                    }
                    
                    // R�gion en cours termin�e ?
                    
                    if(nextRegion)
                    {
                        safeLogger.logInfo(nChunksRefreshedInRegion + " chunks discovered and refreshed in region X:" + xCurrentRegion + " Z:" + zCurrentRegion + " of world \"" + worldData.getWorldName() + "\".");
                        
                        // Toutes les r�gions ont �t� trait�es ?
                        
                        if(currentRegionIndex == regionFilesList.size() - 1) // Oui ?
                        {
                            completed = true; // Arr�te la boucle.
                        }
                        
                        else                                             // Non.
                        {
                        
                            // Cr�� un indicateur marquant la r�gion en cours comme totalement raffraichie.
                            
                            getCurrentRegionRefreshedIndicator().createNewFile(); // Pas de contr�le.
                            
                            // Contr�le m�moire disponible (1Go n�cessaire consid�r�).
                            
                            if(Runtime.getRuntime().freeMemory() < 1073741824L) // Insuffisante, arr�t de la t�che.
                            {
                                // Message console.
                                
                                safeLogger.logWarning("Server is running out of memory. For safety, chunk refreshing for world \"" + worldData.getWorldName() + "\" has been stopped.");
                                safeLogger.logWarning("Try restart task after clearing memory or restarting server, don't worry, it will resume on the first unrefreshed region ;)");
                                
                                // Arr�t de la boucle.
                                
                                outOfMemory = true;
                            }
                            
                            else                                                // Suffisante, on continue avec la r�gion suivante.
                            {
                                // Incr�mente l'index de la r�gion en cours et ajoute le total des chunks trait�s dans la r�gion � ceux du monde.
                                
                                currentRegionIndex++;
                                
                                nChunksRefreshedInWorld += nChunksRefreshedInRegion;
                            }
                        }
                    }
                    
                    // Pause 100ms (2 Bukkit ticks)
                    
                    Thread.sleep(50L);
                }
            }
            
            safeLogger.logInfo("Total of " + nChunksRefreshedInWorld + " chunks discovered and refreshed in world \"" + worldData.getWorldName() + "\".");
            
            // Nettoyage des fichiers indicateurs si la carte a �t� trait� compl�tement.
            
            if(completed)
                cleanRegionRefreshedIndicators();
            
            // Fin d'ex�cution.
            
            if((completed) || (isStopAsked()))
                safeLogger.logInfo("Chunks refresh " + (completed ? "completed" : "endded") + " for world \"" + worldData.getWorldName() + "\".");
            
            else if(outOfMemory)
                safeLogger.logWarning("Chunks refresh suspended for world \"" + worldData.getWorldName() + "\".");
            
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
    
    // M�thodes priv�es de classe.
    
    private void determineRegionXZ() throws Exception
    {
        /* Obtient d�termine les coordonn�es X et Z de la r�gion indiqu�e par currentRegionIndex.
         * Retour : aucun.
         * Param�tres : aucun. */
        
        String regionFilenameParts[] = null;
        
        // Contr�les.
        
        if(!(regionFilesList instanceof List<?>))
            throw new IllegalArgumentException("Object not ready to use.");
        
        // D�termine les coordonn�es de la r�gion.
        
        regionFilenameParts = regionFilesList.get(currentRegionIndex).getName().split("\\.");
            
        xCurrentRegion = Integer.parseInt(regionFilenameParts[1]);
        zCurrentRegion = Integer.parseInt(regionFilenameParts[2]);
    }
    
    private boolean getWorldData() throws InterruptedException
    {
        /* Obtient les donn�es du monde.
         * Retour : objet Location ou null si �chec.
         * Param�tres : aucun. */
        
        Future<WorldData> futureWorldData = null; // Objet permettant d'obtenir l'information depuis Bukkit.
        
        try
        {
            // Contr�les.
            
            if((!(safeLogger instanceof SafeLogger)) || (!(world instanceof World)))
                throw new IllegalArgumentException("Object not ready to use.");
            
            // Obtient les donn�es depuis Bukkit.
            
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
        
        // Succ�s.
        
        return true;
    }
    
    private boolean isCurrentRegionAlreadyRefreshed() throws Exception
    {
        /* Indique si la r�gion en cours a d�j� �t� totalement raffraichie, par existence ou non d'un fichier indicateur.
         * Retour : oui ou non. 
         * Param�tres : aucun. */
        
        // Contr�le l'existence d'un fichier indicateur dans le dossier du monde.
        
        return getCurrentRegionRefreshedIndicator().exists();
    }
    
    private boolean isStopAsked()
    {
        /* Indique si la t�che doit s'arr�ter.
         * Retour : oui ou non. 
         * Param�tres : aucun. */
        
        boolean mustStopLocal = false; // Variable de retour locale.
        
        // Obtient la valeur de mani�re thread-safe.
        
        synchronized(mustStopLock)
        {
            mustStopLocal = mustStop;
        }
        
        return mustStopLocal;
    }
    
    private File getCurrentRegionRefreshedIndicator() throws Exception
    {
        /* Retourne le fichier indicateur de r�gion raffraichie.
         * Param�tres : aucun.
         * Retour : fichier indicateur en question, existant ou non. */
        
        File currentRegionRefreshedIndicator = null; // Fichier indicateur.
        
        // Contr�le.
        
        if(!(worldData instanceof WorldData))
            throw new IllegalArgumentException("Object not ready to use.");
        
        // Obtention du fichier.
        
        currentRegionRefreshedIndicator = new File(worldData.getWorldRegionFolder(), "r." + xCurrentRegion + "." + zCurrentRegion + ".chkref");
        
        if((currentRegionRefreshedIndicator.exists()) && (((!(currentRegionRefreshedIndicator.isFile())) || (currentRegionRefreshedIndicator.length() > 0L))))
            throw new Exception("Invalid region refreshed indicator file.");
        
        // Retour, existant ou non.
        
        return currentRegionRefreshedIndicator;
    }
    
    private int refreshChunksFromFilenames(int nLinesOfChunks) throws InterruptedException
    {
        /* D�couvre et raffrichi au maximum n rang�es de 32 chunks dans la r�gion en cours.
         * Retour : aucun.
         * Param�tre : nLinesOfChunks : nombre de rang�es de 32 chunks � traiter. */
        
        
        Future<Integer> futureChunksRefreshed  = null; // Objet permettant l'obtention du nombre de chunks trait�s depuis Bukkit.
        Integer nChunksRefreshedRegion         = null; // Chunks raffraichis dans la r�gion.
        
        try
        {
            // Contr�les.
            
            if((!(safeLogger instanceof SafeLogger)) || (!(world instanceof World)))
                throw new IllegalArgumentException("Object not ready to use.");
            
            if((nLinesOfChunks < 1) || (nLinesOfChunks > 32))
                throw new IllegalArgumentException();
            
            // Enum�ration chunks dans la r�gion.
            
            if((futureChunksRefreshed = scheduler.callSyncMethod(chunkRefresherPlugin, new ChunksEnumerator(safeLogger, world, xCurrentRegion, zCurrentRegion, xCurrentChunkStart, nLinesOfChunks))) == null)
                throw new Error("Failed to refresh chunks.");
            
            futureWaiter(futureChunksRefreshed);
            
            if((nChunksRefreshedRegion = futureChunksRefreshed.get()) == -1)
                throw new Error("Failed to refresh chunks.");
            
            // D�place le pointeur vers la prochaine rang�e de chunks � traiter,
            // indique que la r�gion a �t� totalement trait�e si on a trait� 31 rang�es de celle-ci.
            
            if((xCurrentChunkStart += nLinesOfChunks) >= 32) // Ne devrait pas �tre sup�rieur mais bon... #CherchePasLaMis�re
                nextRegion = true;
        }
        
        catch(ExecutionException|IllegalArgumentException|NullPointerException|Error error)
        {
            // Echec.
            
            return -1;
        }
        
        // Succ�s.
        
        return nChunksRefreshedRegion;
    }
    
    private void cleanRegionRefreshedIndicators() throws Exception
    {
        /* Supprime tous les fichiers indicateurs de la carte concern�e.
         * Retour : aucun.
         * Param�tres : aucun. */
        
        File[] regionsRefreshedIndicators = null; // Liste obtenue.
        
        // Log. 

        safeLogger.logInfo("Cleaning region refreshed indicators...");
        
        // Contr�le.
        
        if(!(worldData instanceof WorldData))
            throw new IllegalArgumentException("Object not ready to use.");
        
        // Liste les fichiers indicateurs. 
        
        if((regionsRefreshedIndicators = worldData.getWorldRegionFolder().listFiles(new RegionRefreshedIndicatorsFilter())) == null)
            throw new Error("Can't get world region refreshed indicators list.");
        
        // Supprime les fichiers indicateurs.
        
        for(int indicatorIndex = 0; indicatorIndex < regionsRefreshedIndicators.length; indicatorIndex++)
        {
            if(!(regionsRefreshedIndicators[indicatorIndex].delete()))
            {
                safeLogger.logWarning("Region refreshed indicator file \"" + regionsRefreshedIndicators[indicatorIndex].getName() + "\" from world \"" + worldData.getWorldName() + "\" cannot be deleted.");
                safeLogger.logWarning("This file is not required anymore, you should delete it by yourself carrefully.");
            }
        }
        
        // Log. 

        safeLogger.logInfo("Region refreshed indicators cleaned.");
    }
    
    private void futureWaiter(Future<?> future) throws IllegalArgumentException, InterruptedException
    {
        /* Attend la fin d'une t�che synchrone du thread Bukkit.
         * Retour : aucun.
         * Param�tre : objet Future de la t�che en question. */
        
        // Contr�le.
        
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
        /* Liste les fichiers regions situ�s dans le dossier du monde.
         * Retour : aucun.
         * Param�tre : aucun. */
        
        File[] regionsFiles = null; // Liste obtenue.
        
        // Contr�le.
        
        if(!(worldData instanceof WorldData))
            throw new IllegalArgumentException("Object not ready to use.");
        
        // Liste les fichiers. 
        
        if((regionsFiles = worldData.getWorldRegionFolder().listFiles(new RegionFilesFilter())) == null)
            throw new Error("Can't get world region files list.");
        
        // Enregistre le r�sulat sous forme de List.
        
        regionFilesList = Arrays.asList(regionsFiles);
    }
}

// D�finitions de classes associ�es priv�es.

final class ChunksEnumerator implements Callable<Integer>
{
    /* Classe �num�rant et retournant les chunks � raffraichir. */
    
    // Membres.
    
    int nLinesOfChunks;            // Nombres de rang�es de chunks � traiter.
    int xChunkStart;               // Coordonn�e X de la rang�e de chunks par laquelle d�buter.
    int xRegion;                   // Coordonn�e X de la r�gion � traiter.
    int zRegion;                   // Coordonn�e Z de la r�gion � traiter.
    World world;                   // Monde concern�.
    @SuppressWarnings("unused")
    private SafeLogger safeLogger; // Loggeur thread-safe.
    
    // Constructeurs.
    
    public ChunksEnumerator(SafeLogger safeLogger, World world, int xRegion, int zRegion, int xChunkStart, int nLinesOfChunks) throws IllegalArgumentException
    {
        /* Constructeur par d�faut. */
        
        // Contr�le.
        
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
        /* Fonction obtenant les donn�es depuis le thread Bukkit.
         * Retour : ArrayList d'objets Chunk � raffraichir. 
         * Param�tres : aucun. */

        boolean chunkLoaded        = false; // Chunk � trait� d�j� charg� ?
        Chunk currentChunk         = null;  // Chunk en cours.
        int nRegionChunksRefreshed = 0;     // Chunks � d�couverts et raffraichis dans la r�gion en cours.
        int xChunk                 = 0;     // Coordonn�e X du chunk � traiter.
        int zChunk                 = 0;     // Coordonn�e Z du chunk � traiter.
        int xChunkIndex            = 0;     // Index X dans la r�gion du chunk � traiter.
        int zChunkIndex            = 0;     // Index Z dans la r�gion du chunk � traiter.
        
        try
        {            
            // Parcours des chunks.
            
            xChunkIndex = xChunkStart;
            
            for(xChunkIndex = xChunkStart; (xChunkIndex < 32) && (xChunkIndex < (xChunkStart + nLinesOfChunks)); xChunkIndex++)
            {
                // Coordonn�e X.
                
                xChunk = (xRegion * 32) + xChunkIndex;
                
                // Axe Z.
                
                for(zChunkIndex = 0; zChunkIndex < 32; zChunkIndex++)
                {
                    // Coordonn�e Z.
                    
                    zChunk = (zRegion * 32) + zChunkIndex;
                    
                    // Traite le chunk concern�, s'il existe.
                    
                    if(world.isChunkGenerated(xChunk, zChunk)) // Existe ?
                    {
                        // D�j� charg� ?
                        
                        chunkLoaded = world.isChunkLoaded(xChunk, zChunk);
                        
                        // Obtient le chunk aux coordonn�es indiqu�es.

                        if((currentChunk = world.getChunkAt(xChunk, zChunk)) == null)
                            throw new Error("Cannot refresh chunk.");
                        
                        // Le d�charge s'il n'�tait pas charg� avant l'op�ration.
                        
                        if(!(chunkLoaded))
                            currentChunk.unload(); // Pas de contr�le d'erreur.

                        // Incr�mente le nombre de chunks de la r�gion trait�s.
                        
                        nRegionChunksRefreshed++;
                    }
                }
            }
        }
        
        catch(Exception|Error exception)
        {   
            // Echec.
            
            return -1;
        }
        
        // Succ�s.
        
        return nRegionChunksRefreshed;
    }
}

final class RegionFilesFilter implements FilenameFilter
{
    /* Classe permettant de ne lister que les fichiers de r�gions lors de l'exploration du dossier du jeu. */
    
    // M�thodes publiques de classe.
    
    @Override
    public boolean accept(File hostDirectory, String filename) throws IllegalArgumentException
    {
        /* Fonction effectuant la s�lection.
         * Retour : fichier accept� ou non.
         * Param�tres : dossier h�te et nom du fichier en question. */
        
        // Contr�le.
        
        if(!(filename instanceof String))
            throw new IllegalArgumentException();
        
        // Filtrage.
        
        if(filename.matches("(?i)\\Ar\\.-?\\d+\\.-?\\d+\\.mca\\z")) // JS version : /^r\\.-?\\d+\\.-?\\d+\\.mca$/gi
            return true;
        
        return false;
    }
}

final class RegionRefreshedIndicatorsFilter implements FilenameFilter
{
    /* Classe permettant de ne lister que les fichiers indicateurs des r�gions d�j� trait�es lors de l'exploration du dossier du jeu. */
    
    // M�thodes publiques de classe.
    
    @Override
    public boolean accept(File hostDirectory, String filename) throws IllegalArgumentException
    {
        /* Fonction effectuant la s�lection.
         * Retour : fichier accept� ou non.
         * Param�tres : dossier h�te et nom du fichier en question. */
        
        // Contr�le.
        
        if(!(filename instanceof String))
            throw new IllegalArgumentException();
        
        // Filtrage.
        
        if(filename.matches("(?i)\\Ar\\.-?\\d+\\.-?\\d+\\.chkref\\z")) // JS version : /^r\\.-?\\d+\\.-?\\d+\\.chkref$/gi
            return true;
        
        return false;
    }
}

final class WorldData implements Callable<WorldData>
{
    /* Classe contenant les informations concernant le monde � traiter. */
    
    // Membres.
    
    private File worldRegionFolder;       // R�pertoire des r�gions du monde.
    private Location spawnLocation;       // Localisation du spawn du monde.
    private String worldName;             // Nom du monde.
    private World world;                  // Monde concern�.
    private Environment worldEnvironment; // Environement du monde concern�.
    private int xSpawnLocation;           // X du spawn.
    private int ySpawnLocation;           // Y du spawn.
    private int zSpawnLocation;           // Z du spawn.
    @SuppressWarnings("unused")
    private SafeLogger safeLogger;        // Loggeur thread-safe. 
    
    // Constructeurs.
    
    public WorldData(SafeLogger safeLogger, World world) throws IllegalArgumentException
    {
        /* Constructeur par d�faut. */
        
        // Contr�le.
        
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
         * Param�tres : aucun. */
        
        return worldEnvironment;
    }
    
    public File getWorldRegionFolder()
    {
        /* Retourne le r�pertoire du monde.
         * Retour : int de la donn�e en question.
         * Param�tres : aucun. */
        
        return worldRegionFolder;
    }
    
    public Location getSpawnLocation()
    {
        /* Retourne la localisation Z du spawn du monde.
         * Retour : int de la donn�e en question.
         * Param�tres : aucun. */
        
        return spawnLocation;
    }
    
    public String getWorldName()
    {
        /* Retourne le nom du monde.
         * Retour : cha�ne en question.
         * Param�tres : aucun. */
        
        return worldName;
    }
    
    @Override
    public WorldData call() throws Exception, Error
    {
        /* Fonction obtenant les donn�es depuis le thread Bukkit.
         * Retour : cet objet. 
         * Param�tres : aucun. */
        
        File worldFolder             = null; // Dossier du monde.
        String worldRegionFolderPath = null; // Chemin du dossier des r�gions du monde.
        
        // Obtient les donn�es. 
        
        worldEnvironment  = world.getEnvironment();    // Environnement du monde.
        worldFolder       = world.getWorldFolder();    // Dossier du monde.
        spawnLocation     = world.getSpawnLocation();  // Spawn du monde.
        worldName         = world.getName();           // Nom du monde.
        xSpawnLocation    = spawnLocation.getBlockX(); // Coordonn�es du spawn.
        ySpawnLocation    = spawnLocation.getBlockY();
        zSpawnLocation    = spawnLocation.getBlockZ();        
        
        // D�termine et ouvre le dossier des r�gion du monde.
        
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
        
        // Contr�le si le dossier "region" existe.
        
        if(!((worldRegionFolder.exists()) && (worldRegionFolder.isDirectory())))
            return null;
        
        return this;
    }
    
    public int getXSpawnLocation()
    {
        /* Retourne la localisation X du spawn du monde.
         * Retour : object Location.
         * Param�tres : aucun. */
        
        return xSpawnLocation;
    }
    
    public int getYSpawnLocation()
    {
        /* Retourne la localisation Y du spawn du monde.
         * Retour : int de la donn�e en question.
         * Param�tres : aucun. */
        
        return ySpawnLocation;
    }
    
    public int getZSpawnLocation()
    {
        /* Retourne la localisation Z du spawn du monde.
         * Retour : int de la donn�e en question.
         * Param�tres : aucun. */
        
        return zSpawnLocation;
    }
    
    public World getWorld()
    {
        /* Retourne le monde.
         * Retour : monde en question.
         * Param�tres : aucun. */
        
        return world;
    }
}