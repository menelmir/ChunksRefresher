/* SafeLogger.java
 * Classe SafeLogger du plugin ChunksRefresher pour Spigot.
 * 23/10/2018. */

// D�finition du package.

package fr.huvecraft.plugins.chunksrefresher.util;

// Imports.

import java.util.logging.Level;
import java.util.logging.Logger;

// D�finition de la classe.

public class SafeLogger
{
    // Membres.
    
    final Logger logger; // Loggueur � utiliser, final pour thread-safe.
    
    // Constructeurs.
    
    public SafeLogger(Logger logger)
    {
        /* Constructeur par d�faut. */
        
        this.logger = logger; // Loggueur.
    }
    
    public synchronized void log(Level level, String message)
    {
        /* Enregistre un message dans le journal du niveau voulu.
         * Retour : aucun.
         * Param�tres : niveau de criticit� et message associ�. */
        
        logger.log(level, message);
    }
    
    public void logError(String message)
    {
        /* Emet un �venement au journal du type SEVERE. 
         * Retour : aucun.
         * Param�tres : aucun. */
        
        this.log(Level.SEVERE, message);
    }
    
    public void logInfo(String message)
    {
        /* Emet un �venement au journal du type INFO. 
         * Retour : aucun.
         * Param�tres : aucun. */
        
        this.log(Level.INFO, message);
    }
    
    public void logWarning(String message)
    {
        /* Emet un �venement au journal du type INFO. 
         * Retour : aucun.
         * Param�tres : aucun. */
        
        this.log(Level.WARNING, message);
    }
}
