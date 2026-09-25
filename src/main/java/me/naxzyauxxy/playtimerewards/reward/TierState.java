package me.naxzyauxxy.playtimerewards.reward;

/** Visual/interaction state of a tier for one player. Maps 1:1 to gui.states.* in config. */
public enum TierState {
    /** Already taken (green). */
    CLAIMED,
    /** Time reached, can be claimed. */
    READY,
    /** Not enough playtime yet (red "IN PROGRESS"). */
    LOCKED,
    /** Time reached but the tier is permission-gated and the player lacks the node. */
    RESTRICTED
}
