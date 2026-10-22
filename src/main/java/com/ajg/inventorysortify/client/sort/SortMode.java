package com.ajg.inventorysortify.client.sort;

public enum SortMode {
    /** Packed from the first slot, in item order. */
    DEFAULT,
    /** Each kind of item starts a new row when there is room. */
    ROWS,
    /** Filled column by column, each kind of item starting a new column when there is room. */
    COLUMNS
}
