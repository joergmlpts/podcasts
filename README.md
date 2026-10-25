# Podcasts

This is a podcast player app for Android that only plays podcasts. It doesn't request permissions, show ads, steal information or solicit payments — it just plays podcasts.

## Podcasts
This podcast player app supports all the core functions. It allows you to search for and maintain a collection of podcasts. It also indicates when new episodes are available.

![podcasts.png](/screenshots/podcasts.png)

## Episodes
Episodes can be played either after downloading them for offline use or by streaming them in real time.

![episodes.png](/screenshots/episodes.png)

## Playing

You can pause, resume, speed up, slow down, skip ahead 30 seconds, or rewind 15 seconds.

## Build Instructions

The app can be built using Android Studio. A file named `debug.keystore` is expected in the top-level directory. This file can be created with a command like the following:

```
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000

```
