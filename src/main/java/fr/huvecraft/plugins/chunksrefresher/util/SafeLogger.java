/* SafeLogger.java
 * Classe SafeLogger du plugin ChunksRefresher pour Spigot.
 * 23/10/2018. */

// Définition du package.

package fr.huvecraft.plugins.chunksrefresher.util;

// Imports.

import java.util.logging.Level;
import java.util.logging.Logger;

// Définition de la classe.

public class SafeLogger
{
    // Membres.
    
    final Logger logger; // Loggueur à utiliser, final pour thread-safe.
    
    // Constructeurs.
    
    public SafeLogger(Logger logger)
    {
        /* Constructeur par défaut. */
        
        this.logger = logger; // Loggueur.
    }
    
    public synchronized void log(Level level, String message)
    {
        /* Enregistre un message dans le journal du niveau voulu.
         * Retour : aucun.
         * Paramètres : niveau de criticité et message associé. */
        
        logger.log(level, message);
    }
    
    public void logError(String message)
    {
        /* Emet un évenement au journal du type SEVERE. 
         * Retour : aucun.
         * Paramètres : aucun. */
        
        this.log(Level.SEVERE, message);
    }
    
    public void logInfo(String message)
    {
        /* Emet un évenement au journal du type INFO. 
         * Retour : aucun.
         * Paramètres : aucun. */
        
        this.log(Level.INFO, message);
    }
    
    public void logWarning(String message)
    {
        /* Emet un évenement au journal du type INFO. 
         * Retour : aucun.
         * Paramètres : aucun. */
        
        this.log(Level.WARNING, message);
    }
}
