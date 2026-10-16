package com.sportzfy.live.core.player

/**
 * The activity's system picture-in-picture: a playing TV channel keeps going
 * in a floating window when the user leaves the app (Home, Recents).
 */
interface PictureInPictureHost {
    /** False on devices without picture-in-picture (most TVs, Android Go). */
    val isPictureInPictureSupported: Boolean

    /** Something worth watching is on screen; leaving the app moves it into a window. */
    fun setPictureInPictureSource(active: Boolean)

    /** Moves the app into a window now (the player's PiP button); false when it can't. */
    fun enterPictureInPicture(): Boolean
}
