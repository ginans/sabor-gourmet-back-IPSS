package cl.ipss.saborgourmet.model;

/**
 * Estados operativos de un cliente: activo, baneado por incumplimientos o anonimizado tras soft-delete.
 */
public enum ClientStatus {
    ACTIVE,
    BANNED,
    DELETED
}
