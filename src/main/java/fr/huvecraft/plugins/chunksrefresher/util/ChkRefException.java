/* SafeLogger.java
 * Classe ChkRefException du plugin ChunksRefresher pour Spigot.
 * 30/11/2021. */

// Définition du package.

package fr.huvecraft.plugins.chunksrefresher.util;

// Imports.

import javax.annotation.Nullable;

// Définition de la classe.

public class ChkRefException extends Exception
{
    /* Classe ChkRefException du plugin ChunksRefresher pour Spigot. */

    public ChkRefException(@Nullable String message)
    {
        super(message);
    }
}
