# DBXShell - DropBoX Shell

About
=============================

The DropBox Shell (DBXShell) is an application client for managing a DropBox account but in a client shell. I was motivated to develop the DBXShell for a tool that I can manage local files/folders on my own system with a DropBox account--but not using a visual app or web page...somewhat akin to an old-style FTP or telnet client. 

Features
========

* There are 34-commands to access, query, and manage files and folders both locally and remote.
* Local commands are prefixed with the 'l' letter.
* The DBXShell client is single-threaded, so all commands execute and the shell waits until a command finishes.
* The DBXShell initializes, read-evaluate-print-loop (REPL), and upon exit finalizes.
* The DBXShell is designed to fail-safe so that an exception is caught, reported, and the shell continues.
* The source code is contained in a BFG (big file gigantic) class of approximately 2000-lines of Java code.
* Implemented using the Java Development Kit release 1.8.
* Implemented using the DropBox Java Library 3.0.11.
* Built-in help to display the various commands and parameters for each command.

License
===============================

This library is licensed under the GNU General Public License version 2.

Usage
===============================

At the command-line, with the Java JAR file (with the external dependent libraries in the class path) and the DBXShell Java JAR file in the current directory type:
 
java -jar DBXShell.jar


External Dependencies
=====================================
This library requires:

* DropBox Java SDK v. 3.0.11 (dropbox-core-sdk-3.0.11.jar)
* FasterXML Jackson Core v. 2.9.8  (jackson-core-2.9.8.jar) 

Note: At the time of writing this READ.ME (December 20, 2018) both libraries are the latest versions.

Both libraries must be in the local system Java classpath. Also, a user must create an application in their DropBox account with an application name (appname) and access token. Both the appname and access token are required by the DBXShell to access the DropBox storage account.

The libraries are available on GitHub repos at:

* https://github.com/dropbox/dropbox-sdk-java
* https://github.com/FasterXML/jackson-core

