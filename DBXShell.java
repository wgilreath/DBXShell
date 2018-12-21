/**
 * DBXShell.java
 *
 * Title: DBXShell - the DropBox Shell Client.
 *
 * Description: The DropBox shell connects to a DropBox storage account, and
 *     perform file and folder operations in a command-line interface client.
 *
 * Author: William F. Gilreath (wgilreath@gmail.com)
 *
 * Copyright (C) 2018 All Rights Reserved.
 *
 * This file is part of DropBox shell software project. The DropBox shell is
 * free software; you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package will.dropbox;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.regex.PatternSyntaxException;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;

import com.dropbox.core.v2.DbxClientV2;

import com.dropbox.core.v2.files.CreateFolderErrorException;
import com.dropbox.core.v2.files.CreateFolderResult;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.DeleteResult;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.RelocationErrorException;
import com.dropbox.core.v2.files.SearchMatch;
import com.dropbox.core.v2.files.SearchResult;

import com.dropbox.core.v2.users.FullAccount;
import com.dropbox.core.v2.users.SpaceUsage;
import com.dropbox.core.v2.userscommon.AccountType;

public final class DBXShell {
    
    public final static String DBX_WELCOME_MESSAGE   = "Welcome to the Dropbox Shell! Use 'help' to get started.";
    public final static String DBX_APP_ABOUT_MESSAGE = "DBXShell: A DropBox Command Line Interface Command Shell.";
    public final static String DBX_COPYRIGHT_MESSAGE = "Copyright (C) December 2018 William F. Gilreath.         ";
    public final static String DBX_LICENSE_MESSAGE   = "Release under terms of the GNU General Public License.";

    public final static String DBX_START_MESSAGE = "Start DropBox Shell";
    public final static String DBX_CLOSE_MESSAGE = "Close DropBox Shell";

    public final static String DBX_VERSION_INFO = "1.00";
    public final static String DBX_INPUT_PROMPT = "%s:%s:>";

    private final static String getDefaultFileName(final String fileNamePrefix) {

        final StringBuilder str = new StringBuilder(fileNamePrefix);
        
        str.append("_");
        
        String timeStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        
        str.append(timeStamp);
        str.append(".txt");

        return str.toString();
        
    }// end getDefaultFileName

    public final static void main(final String[] args) {

        final DBXShell dbx = new DBXShell();

        dbx.initialize();
        dbx.evaluate();
        dbx.finalizer();

        System.exit(0);

    }// end main

    private boolean scriptFlag;
    private boolean exitFlag;
    private boolean readyFlag;
    private boolean teamFlag;

    private String dbxCurrentWorkDir;
    private String localCurrentWorkDir;

    private DbxRequestConfig config;
    private DbxClientV2      client;
    private FullAccount      account;

    private String appName = "";
    private String access  = null;

    private Scanner scan = null;

    private ArrayList<String> history = null;

    private final String LOCAL_HOME_DIR = System.getProperty("user.dir");

    private PrintWriter scriptFile = null;

    private File outputFile;

    private long totalDataGet = 0L;
    private long totalDataPut = 0L;

    private int shellCommandCounter = 0;

    private String startDateTime = new Date().toString();

    private String closeDateTime = "";

    private long startTimer = System.currentTimeMillis();
    private long closeTimer = 0;

    public DBXShell() {

        this.exitFlag   = false;
        this.readyFlag  = false;
        this.scriptFlag = false;

        this.dbxCurrentWorkDir   = "";
        this.localCurrentWorkDir = System.getProperty("user.dir");

        this.scan    = new Scanner(System.in);
        this.history = new ArrayList<String>(64);

    }// end DBXShell

    private final void commandLocalChangeDir(final String[] param) {

        if (param.length == 1) {
            this.localCurrentWorkDir = this.LOCAL_HOME_DIR;
            this.writef("Change local directory to %s%n", this.localCurrentWorkDir);
            return;
        } // end if

        if (param[1].equals("..")) {

            if (this.localCurrentWorkDir.equals("/")) {
                this.writef("Change local directory: already at root '/' directory.%n");
                return;
            } // end if

            int last = this.localCurrentWorkDir.lastIndexOf('/');
            String newDir = this.localCurrentWorkDir.substring(0, last);
            this.localCurrentWorkDir = newDir;
            if (this.localCurrentWorkDir.equals("")) {
                this.localCurrentWorkDir = "/"; // constant LOCAL_ROOT_DIR
            } // end if

            this.writef("Change local directory to %s%n", this.localCurrentWorkDir);
            return;
        } // end if

        File dir = new File(param[1]);
        if (param[1].charAt(0) != '/') {
            dir = new File(this.localCurrentWorkDir + '/' + param[1]);
        }//end if

        if (!dir.exists()) {
            this.writef("Error: Path '%s' does not exist!%n", dir.getPath());
            return;
        } // end if

        this.localCurrentWorkDir = dir.getAbsolutePath();

    }// end commandLocalChangeDir

    private final void commandLocalCopy(final String[] param) {

        if (param.length != 3) {
            this.writef("Error: The command 'lcp' requires two path parameters!%n");
            return;
        } // end if

        try {

            File srcfile = new File(this.localCurrentWorkDir + "/" + param[1]);
            File tgtfile = new File(this.localCurrentWorkDir + "/" + param[2]);

            if (!srcfile.exists()) {
                this.writef("Local source file: %s not found!%n", param[1]);
                return;
            } // end if

            if (tgtfile.exists()) {
                this.writef("Local target file: %s exists!%n", param[1]);
                return;
            } // end if

            Files.copy(srcfile.toPath(), tgtfile.toPath());

            if (tgtfile.exists()) {
                this.writef("Local file: %s copied %s.%n", srcfile.getName(), tgtfile.getName());
            } else {
                this.writef("Problem in copying local file: %s to %s.%n", srcfile.getName(), tgtfile.getName());
            } // end if

        } catch (Exception ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } // end try

    }// end commandLocalCopy

    private final void commandLocalDeleteFile(final String[] param) {
        
        if (param.length != 2) {
            this.writef("Error: The command 'ldel' requires one path parameter!%n");
            return;
        } // end if

        try {

            File file = new File(this.localCurrentWorkDir + "/" + param[1]);

            if (!file.exists()) {
                this.writef("Local file: %s not found!%n", param[1]);
                return;
            } // end if

            if (file.delete()) {
                this.writef("Local file: %s deleted.%n", file.getName());
            } else {
                this.writef("Problem in deleting local file: %s.%n", file.getName());
            }//end if

        } catch (Exception ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } // end try

    }// end commandLocalDeleteFile

    private final void commandLocalDir(final String[] param) {

        this.listFilesAndFilesSubDirectories(this.localCurrentWorkDir);

        if (param.length == 1) {
            this.listFilesAndFilesSubDirectories(this.localCurrentWorkDir);
        } else if (param.length > 1) {
            this.writef("List local directory: Extra parameters ignored!%n");
        } // end if

    }// end commandLocalDir

    private final void commandLocalFind(final String[] param) { // lfind <path> <glob>

        if (param.length != 3) {
            this.writef("Error: The command 'lfind' requires two parameters!%n");
            return;
        } // end if

        final File dir = new File(param[1]);

        if (!dir.exists()) {
            this.writef("Error: Path '%s' does not exist!%n", dir.getPath());
            return;
        } // end if

        Path startDir = Paths.get(param[1]);

        String pattern = param[2];

        FileSystem fs = FileSystems.getDefault();

        try {

            final PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);

            FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
        
                    Path name = file.getFileName();

                    if (matcher.matches(name)) {
                        System.out.print(String.format("Found:    %s%n", file));
                    }// end if
                    return FileVisitResult.CONTINUE;
                }// end visitFile
            };//end new SimpleFileVisitor

            Files.walkFileTree(startDir, matcherVisitor);

        } catch (PatternSyntaxException ex) {
            System.out.printf("Local find pattern syntax error: %s%n", ex.getMessage());
            return;
        } catch (Exception ex) {
            System.out.printf("Local find unknown error: %s%n", ex.getMessage());
        } // end try

    }// end commandLocalFind

    private final void commandLocalGet(final String[] param) {
        if (param.length != 2) {
            this.writef("Get download file requires local file path!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        if (!this.dbxHasFile("/" + param[1])) {
            this.writef("File with name %s does not exist!%n", param[1]);
            return;
        } // end if

        // check if file exists locally?? no clobber ??
        this.downloadFromDropbox(param[1]);

    }// end commandLocalGet

    private final void commandLocalMakeDir(final String[] param) {
        if (param.length != 2) {
            this.writef("Error: The command 'lmdir' requires one path parameter!%n");
            return;
        } // end if

        File dir = new File(param[1]);

        if (dir.exists()) {
            if (dir.isDirectory()) {
                this.writef("Error: Path '%s' already exists as directory!%n", dir.getPath());
            } else {
                this.writef("Error: Path '%s' already exists as file!%n", dir.getPath());
            }//end if
            return;
        } // end if

        // local make directory
        if (dir.mkdir()) {
            this.writef("Created local directory: '%s'.%n", dir.getPath());
        } else {
            this.writef("Error: failed to create local directory: '%s'%n", dir.getPath());
        } // end if

    }// end commandLocalMakeDir

    private final void commandLocalPut(final String[] param) {
        if (param.length != 2) {
            this.writef("Put upload file requires local file path!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        if (this.dbxHasFile("/" + param[1])) {
            this.writef("File with name %s already exists!%n", param[1]);
            return;
        } // end if

        this.uploadToDropbox(param[1]);

    }// end commandLocalPut

    private final void commandLocalRemoveDir(final String[] param) {

        if (param.length != 2) {
            this.writef("Error: The command 'lrdir' requires one path parameter!%n");
            return;
        } // end if

        File dir = new File(this.localCurrentWorkDir + "/" + param[1]);

        if (!dir.exists()) {
            this.writef("Error: Path '%s' directory does not exist!%n", dir.getPath());
            return;
        } // end if

        if (dir.listFiles().length > 0) {
            this.writef("Error: Path '%s' directory is not empty!!%n", dir.getPath());
            return;
        } // end if

        if (dir.delete()) {
            this.writef("Directory: '%s' removed.%n", dir.getPath());
        } else {
            this.writef("Directory: '%s' not removed!%n", dir.getPath());
        } // end if

    }// end commandLocalRemoveDir

    private final void commandLocalRenameFile(final String[] param) {

        if (param.length != 3) {
            this.writef("Error: The command 'lrn' requires two path parameters!%n");
            return;
        } // end if

        try {

            File srcfile = new File(this.localCurrentWorkDir + "/" + param[1]);
            File tgtfile = new File(this.localCurrentWorkDir + "/" + param[2]);

            if (!srcfile.exists()) {
                this.writef("Local source file: %s not found!%n", param[1]);
                return;
            } // end if

            if (tgtfile.exists()) {
                this.writef("Local target file: %s exists!%n", param[2]);
                return;
            } // end if

            if (srcfile.renameTo(tgtfile)) {
                this.writef("Local file: %s renamed %s.%n", srcfile.getName(), tgtfile.getName());
            } else {
                this.writef("Problem in deleting local file: %s to %s.%n", srcfile.getName(), tgtfile.getName());
            } // end if

        } catch (Exception ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } // end try

    }// end localRenameFile

    private final void commandLocalWorkingDir(final String[] param) // lwd
    {
        this.writef("%s%n", this.localCurrentWorkDir); // lwd =
    }// end commandLocalWorkingDir

    private void commandRemoteAccount(final String[] param) // boolean flag, store information in record var
    {
        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        try {

            this.writef("%s %s %s User: %s %s E-mail: %s%n", account.getCountry(), account.getLocale(),
                    account.getAccountType().toString(), account.getName().getAbbreviatedName(),
                    account.getName().getDisplayName(), account.getEmail());

            if (this.teamFlag) {
                this.writef("Id: %s Space: %d-Gb Using: %d-Mb%n", account.getAccountId(),
                        client.users().getSpaceUsage().getAllocation().getTeamValue().getAllocated()
                        / (1024 * 1024 * 1024),
                        client.users().getSpaceUsage().getUsed() / (1024 * 1024));

            } else {
                this.writef("Id: %s Space: %d-Mb Using: %d-Mb%n", account.getAccountId(),
                        client.users().getSpaceUsage().getAllocation().getIndividualValue().getAllocated()
                        / (1024 * 1024),
                        client.users().getSpaceUsage().getUsed() / (1024 * 1024));
            }// end if
        } catch (Exception ex) {
            this.writef("Error: %s %n", ex.getMessage());
        } // end try

    }// end commandAccount

    private void commandRemoteChangeDir(final String[] param) {

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        if (param.length == 1 || param[1].equals("/")) {

            if (this.dbxCurrentWorkDir.equals("")) {
                this.writef("Change remote directory: already at root '/' directory.%n");
                return;
            } // end if

            this.dbxCurrentWorkDir = "";
            this.writef("Change directory to %s.%n", this.getDbxDir());

            return;
        } // end if

        if (param[1].equals("..")) {

            if (this.dbxCurrentWorkDir.equals("")) // root dir
            {
                this.writef("Change remote directory: already at root '/' directory.%n");
                return;
            } // end if

            int last = this.dbxCurrentWorkDir.lastIndexOf('/');
            String newDir = this.dbxCurrentWorkDir.substring(0, last);

            this.dbxCurrentWorkDir = newDir;
            this.writef("Change directory to %s.%n", this.getDbxDir());

            return;
        } // end if

        String path = this.dbxGetPathAbsolute(param[1]);

        if (this.dbxHasFolder(path)) {

            this.dbxCurrentWorkDir = path;
            this.writef("Change directory to %s.%n", this.dbxCurrentWorkDir);

        } else {
            this.writef("Change directory: directory '%s' does not exist!%n", path);
        } // end if

    }// end commandRemoteChangeDir

    private final void commandRemoteClose(final String[] param) {
        if (!this.readyFlag) {
            this.writef("Cannot close; not connected to DropBox!%n");
            return;
        } // end if

        try {

            this.client    = null;
            this.config    = null;
            this.account   = null;
            this.readyFlag = false;

            this.appName   = ""; 
            this.access    = null;

            this.writef("Connection to DropBox disconnected.%n");
        } catch (Exception ex) {
            this.writef("General error close connection: %s%n", ex.getMessage());
        } // end try

    }// end commandRemoteClose

    private final void commandRemoteCopyFile(final String[] param) {

        if (param.length != 3) {
            this.writef("Error: The command 'cp' requires two path parameters!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        String srcPath, tgtPath;

        if (this.dbxCurrentWorkDir.equals("")) {
            if (param[1].charAt(0) != '/') {
                srcPath = '/' + param[1];
            } else {
                srcPath = param[1];
            }//end if

            if (param[2].charAt(0) != '/') {
                tgtPath = '/' + param[2];
            } else {
                tgtPath = param[2];
            }//end if

        } else {
            if (param[1].charAt(0) != '/') {
                srcPath = this.getDbxDir() + '/' + param[1];
            } else {
                srcPath = param[1];
            }//end if

            if (param[2].charAt(0) != '/') {
                tgtPath = this.getDbxDir() + '/' + param[2];
            } else {
                tgtPath = param[2];
            }//end if

        } // end if

        this.writef("Copy srcPath: %s tgtPath: %s   %n", srcPath, tgtPath);

        if (!this.dbxHasPath(srcPath)) {
            this.writef("Copy remote source path: %s not found!%n", srcPath);
            return;
        } // end if

        if (this.dbxHasPath(tgtPath)) {
            this.writef("Copy remote target path: %s exists!  %n", tgtPath);
            return;
        } // end if

        try {

            this.client.files().copyV2(srcPath, tgtPath);

            if (this.dbxHasPath(tgtPath)) {
                this.writef("Remote path: %s copied to %s.%n", srcPath, tgtPath);
            } else {
                this.writef("Problem in copying remote file: %s to %s.%n", srcPath, tgtPath);
            } // end if

        } catch (RelocationErrorException err) {
            this.writef("Copy path: some other relocation error in renaming occurred! %s. %n", err.getMessage());
        } catch (DbxException err) {
            this.writef("Copy path: some other DropBox remote error in copy occurred! %s. %n", err.getMessage());
        } catch (Exception err) {
            this.writef("Copy path: some other unknown error in copy occurred! %s. %n", err.getMessage());
        } // end try

    }// end commandRemoteCopyFile

    private void commandRemoteDir(final String[] param) {

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        if (param.length > 1) {
            this.writef("List Remote directory: Extra parameters ignored!%n");
        } // end if

        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm:ss:a");

        try {

            ListFolderResult builder = client.files().listFolderBuilder(this.dbxCurrentWorkDir)
                    .withIncludeDeleted(false).withRecursive(false).withIncludeMediaInfo(false).start();

            List<Metadata> entries = builder.getEntries();

            for (Metadata metadata : entries) {

                if (metadata instanceof FolderMetadata) {
                    FolderMetadata folder = (FolderMetadata) metadata;

                    this.writef("....................... ------------- [%s]%n", folder.getName());

                } else if (metadata instanceof FileMetadata) {
                    FileMetadata file = (FileMetadata) metadata;

                    this.writef("%22s %13d %s%n", sdf.format(file.getClientModified()), file.getSize(),
                            file.getPathDisplay().replace("/", ""));

                } // end if

            } // end for

        } catch (Exception ex) {
            this.writef("dbxClient: %s %n", ex.getMessage());
        } // end try

    }// end commandRemoteDir

    private void commandRemoteFind(final String[] param) {
        if (param.length != 3) {
            this.writef("Error: The command 'find' requires two parameters!%n");
            return;
        } // end if

        // check if connected to Dropbox
        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        try {

            String path = "";

            if (!param[1].equalsIgnoreCase("/") && !param[1].equalsIgnoreCase(".")) {
                path = param[1];
            } // end if

            SearchResult result = this.client.files().search(path, param[2]);

            List<SearchMatch> list = result.getMatches();
            if (list.isEmpty()) {
                this.writef("No matches found!%n");
                return;
            } // end if

            this.writef("Found %d match in path for query!%n", list.size());
            for (SearchMatch item : list) {

                Metadata meta = item.getMetadata();
                if (meta instanceof FileMetadata) {
                    this.writef("    File:    %s %n", meta.getPathLower());
                } else if (meta instanceof FolderMetadata) {
                    this.writef("    Dir:     %s %n", meta.getPathLower());
                } else {
                    this.writef("Unknown: %s %n", meta.getPathLower());
                }//end if

            } // end for

        } catch (DbxException err) {
            this.writef("Find: some other DropBox remote error find in path occurred!%n");
            this.writef("Error: %s%n", err.getMessage());
        } catch (Exception err) {
            this.writef("Find: some other unknown error find in path occurred!%n");
            this.writef("Error: %s%n", err.getMessage());
        } // end try

    }// end commandRemoteFind

    private void commandRemoteInfo(final String[] param) {

        if (param.length != 2) {
            this.writef("Error: The command 'info' requires one path parameter!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        try {

            if (this.getDbxDir().equalsIgnoreCase("/")) {
                if (param[1].charAt(0) == '/') {
                    this.getMetadata(param[1]);
                } else {
                    this.getMetadata(this.getDbxDir() + param[1]); // '/' + param[1]
                }//end if
            } else {
                if (param[1].charAt(0) == '/') {
                    this.getMetadata(param[1]);
                } else {
                    this.getMetadata(this.getDbxDir() + "/" + param[1]);
                } // end if

            } // end if

        } catch (Exception ex) {
            this.writef("Error 'info' : %s%n", ex.getMessage());
            ex.printStackTrace();
        }//end try

    }// end commandRemoteInfo

    private final void commandRemoteMakeDirectory(final String[] param) {

        if (param.length != 2) {
            this.writef("Make directory error requires directory path!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        String path = this.dbxGetPathAbsolute(param[1]);

        try {
            
            CreateFolderResult cfr = client.files().createFolderV2(path); // cat remote dir path ??

            this.writef("Created %s remote directory.%n", cfr.getMetadata().getPathDisplay());

        } catch (CreateFolderErrorException err) {
            if (err.errorValue.isPath() && err.errorValue.getPathValue().isConflict()) {
                this.writef("Make directory: file or directory already exists at the directory path.%n");
            } else {
                this.writef("Make directory: some other error creating directory occurred: %s%n",
                        err.errorValue.getPathValue().toString());
            }// end if
        } catch (DbxException err) {
            this.writef("Make directory: some other DropBox remote error creating directory occurred! %s.%n",
                    err.getMessage());
        } catch (Exception err) {
            this.writef("Make directory: some other unknown error creating directory occurred! %s.%n",
                    err.getMessage());
        } // end try

    }// end commandRemoteMakeDirectory

    private final void commandRemoteOpen(final String[] param) {

        if (this.readyFlag) {
            this.writef("Already connected to DropBox!%n");
            return;
        } // end if

        if (param.length != 3) {

            if (this.appName == null || this.access == null) {
                this.writef("Error: The command 'open' requires two parameters!%n");
                return;
            }//end if
            
        } else {
            this.appName = param[1];
            this.access = param[2];
        } // end if

        this.dbxCreateClient(this.appName, this.access);

        if (account.getAccountType() == AccountType.BASIC) {
            this.teamFlag = false;
        } else {
            this.teamFlag = true;
        } // end if

    }// end commandRemoteOpen

    private final void commandRemoteRemoveDirectory(final String[] param) {

        if (param.length != 2) {
            this.writef("Remove directory error requires directory path!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        String path = this.dbxGetPathAbsolute(param[1]);

        if (!this.dbxHasFolder(path)) {
            this.writef("Path: '%s' entry is not folder!%n", path);
            return;
        } // end if

        try {

            DeleteResult dr = client.files().deleteV2(path);

            this.writef("Removed directory: %s%n", dr.getMetadata().getPathDisplay());

        } catch (DeleteErrorException err) {

            if (err.errorValue.isPathLookup()) {

                if (err.errorValue.getPathLookupValue().isMalformedPath()) {
                    this.writef("Remove directory: path lookup; directory path is malformed.%n");
                } else if (err.errorValue.getPathLookupValue().isNotFolder()) {
                    this.writef("Remove directory: path lookup; directory path is not directory folder.%n");
                } else if (err.errorValue.getPathLookupValue().isNotFound()) {
                    this.writef("Remove directory: path lookup; directory path is not found.%n");
                }//end if

            } else if (err.errorValue.isPathWrite()) {

                if (err.errorValue.getPathWriteValue().isConflict()) {
                    this.writef("Remove directory: path write; directory path is in conflict.%n");
                } else if (err.errorValue.getPathWriteValue().isDisallowedName()) {
                    this.writef("Remove directory: path write; directory path is disallowed name.%n");
                } else if (err.errorValue.getPathWriteValue().isInsufficientSpace()) {
                    this.writef("Remove directory: path write; directory path is insufficient space.%n");
                } else if (err.errorValue.getPathWriteValue().isMalformedPath()) {
                    this.writef("Remove directory: path write; directory path is malformed.%n");
                } else if (err.errorValue.getPathWriteValue().isNoWritePermission()) {
                    this.writef("Remove directory: path write; directory path has no write permission.%n");
                } else if (err.errorValue.getPathWriteValue().isOther()) {
                    this.writef("Remove directory: path write; is other.%n");
                }//end if

            } else if (err.errorValue.isOther()) {

                this.writef("Remove directory: some other error deleting directory occurred: %s%n",
                        err.errorValue.toString());
            } else {
                this.writef("Remove directory: some unknown error deleting directory occurred: %s%n",
                        err.errorValue.toString());
            } // end if

        } catch (DbxException err) {
            this.writef("Remove directory: some other DropBox remote error deleting directory occurred!%n");
        } catch (Exception err) {
            this.writef("Remove directory: some other unknown error deleting directory occurred!%n");
        } // end try

    }// end commandRemoteRemoveDirectory

    private final void commandRemoteRemoveFile(final String[] param) {

        if (param.length != 2) {
            this.writef("Remove file error requires entry path!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        if (!this.dbxHasFile(this.dbxGetPathAbsolute(param[1]))) {
            this.writef("Path: '%s' entry is not file!%n", param[1]);
            return;
        } // end if

        try {

            if (param[1].charAt(0) == '/') {
                DeleteResult dr = client.files().deleteV2(param[1]);

                this.writef("Deleted file: %s%n", dr.getMetadata().getPathLower());
            } else {

                DeleteResult dr = client.files().deleteV2(this.dbxCurrentWorkDir + "/" + param[1]);

                this.writef("Deleted file: %s%n", dr.getMetadata().getPathLower());
            }//end if

        } catch (DeleteErrorException err) {

            if (err.errorValue.isPathLookup()) {

                if (err.errorValue.getPathLookupValue().isMalformedPath()) {
                    this.writef("Remove file: path lookup; file entry path is malformed.%n");
                } else if (err.errorValue.getPathLookupValue().isNotFolder()) {
                    this.writef("Remove file: path lookup; file entry path is not directory folder.%n");
                } else if (err.errorValue.getPathLookupValue().isNotFound()) {
                    this.writef("Remove file: path lookup; file entry path is not found.%n");
                }//end if

            } else if (err.errorValue.isPathWrite()) {

                if (err.errorValue.getPathWriteValue().isConflict()) {
                    this.writef("Remove file: path write; file entry path is in conflict.%n");
                } else if (err.errorValue.getPathWriteValue().isDisallowedName()) {
                    this.writef("Remove file: path write; file entry path is disallowed name.%n");
                } else if (err.errorValue.getPathWriteValue().isInsufficientSpace()) {
                    this.writef("Remove file: path write; file entry path is insufficient space.%n");
                } else if (err.errorValue.getPathWriteValue().isMalformedPath()) {
                    this.writef("Remove file: path write; file entry path is malformed.%n");
                } else if (err.errorValue.getPathWriteValue().isNoWritePermission()) {
                    this.writef("Remove file: path write; file entry path has no write permission.%n");
                } else if (err.errorValue.getPathWriteValue().isOther()) {
                    this.writef("Remove file: path write; is other.%n");
                }//end if

            } else if (err.errorValue.isOther()) {

                this.writef("Remove directory: some other error deleting directory occurred: %s%n",
                        err.errorValue.toString());
            } else {
                this.writef("Remove directory: some unknown error deleting directory occurred: %s%n",
                        err.errorValue.toString());
            } // end if

        } catch (DbxException err) {
            this.writef("Remove directory: some other DropBox remote error creating directory occurred!%n");

        } catch (Exception err) {
            this.writef("Remove directory: some other unknown error creating directory occurred!%n");
        } // end try

    }// end commandRemoteRemoveFile

    private final void commandRemoteRenameFile(final String[] param) {

        if (param.length != 3) {
            this.writef("Error: The command 'rn' requires two path parameters!%n");
            return;
        } // end if

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        String srcPath, tgtPath;

        if (this.dbxCurrentWorkDir.equals("")) {
            if (param[1].charAt(0) != '/') {
                srcPath = '/' + param[1];
            } else {
                srcPath = param[1];
            }//end if

            if (param[2].charAt(0) != '/') {
                tgtPath = '/' + param[2];
            } else {
                tgtPath = param[2];
            }//end if

        } else {

            if (param[1].charAt(0) != '/') {
                srcPath = this.getDbxDir() + '/' + param[1];
            } else {
                srcPath = param[1];
            }//end if

            if (param[2].charAt(0) != '/') {
                tgtPath = this.getDbxDir() + '/' + param[2];
            } else {
                tgtPath = param[2];
            }//end if

        } // end if

        // check source path does exist to rename
        if (!this.dbxHasPath(srcPath)) {
            this.writef("Remote source path: %s not found!%n", srcPath);
            return;
        } // end if

        // check target path not exists to rename
        if (this.dbxHasPath(tgtPath)) {
            this.writef("Remote target path: %s exists!  %n", tgtPath);
            return;
        } // end if

        try {

            this.client.files().moveV2(srcPath, tgtPath);

            if (this.dbxHasPath(tgtPath)) {
                this.writef("Remote path: %s renamed %s.%n", srcPath, tgtPath);
            } else {
                this.writef("Problem in renaming remote file: %s to %s.%n", srcPath, tgtPath);
            } // end if

        } catch (RelocationErrorException err) {
            this.writef("Rename path: some other relocation error in renaming occurred! %s. %n", err.getMessage());
        } catch (DbxException err) {
            this.writef("Rename path: some other DropBox remote error in renaming occurred!%n");
        } catch (Exception err) {
            this.writef("Rename path: some other unknown error in renaming occurred!%n");
        } // end try

    }// end commandRemoteRenameFile

    private void commandRemoteSpace(final String[] param) {

        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        try {

            final SpaceUsage use = this.client.users().getSpaceUsage();

            long total = 0L;
            if (teamFlag) {
                total = use.getAllocation().getTeamValue().getAllocated();
            } else {
                total = use.getAllocation().getIndividualValue().getAllocated();
            }//end if

            final long using = use.getUsed();
            final long space = total - using;

            this.writef("DropBox Storage Space Utilization:%n%n");
            this.writef("    Total:%16d-bytes     Used:%16d-bytes     Free:%16d-bytes%n", total, using, space);
            this.writef("    Total:%16d-Kb        Used:%16d-Kb        Free:%16d-Kb   %n", total / 1024, using / 1024,
                    space / 1024);
            this.writef("    Total:%16d-Mb        Used:%16d-Mb        Free:%16d-Mb   %n", total / (1024 * 1024),
                    using / (1024 * 1024), space / (1024 * 1024));
            this.writef("    Total:%16d-Gb        Used:%16d-Gb        Free:%16d-Gb   %n", total / (1024 * 1024 * 1024),
                    using / (1024 * 1024 * 1024), space / (1024 * 1024 * 1024));

            this.writef("%n");

        } catch (Exception ex) {
            this.writef("Error: %s %n", ex.getMessage());
            ex.printStackTrace();
            System.out.println();
        } // end try

    }// end commandRemoteSpace

    private void commandRemoteWorkingDir(final String[] param) {
        if (!this.readyFlag) {
            this.writef("Not connected to DropBox!%n");
            return;
        } // end if

        if (this.dbxCurrentWorkDir.equals("")) {
            this.writef("/%n"); // rwd =
        } else {
            this.writef("%s%n", this.dbxCurrentWorkDir); // rwd =
        }//end if

    }// end commandRemoteWorkingDir

    private final void commandShellAccess(final String[] param) {
        if (param.length == 1) {
            if (this.access != null) {
                this.writef("Access token set.%n");
            } else {
                this.writef("Access token is not set!%n");
            } // end if
            return;
        } else if (param.length == 2) {
            if (this.access != null) {
                this.writef("Already set access token!%n");
            } else {
                this.writef("Set access to '%s' token.%n", param[1]); // use first chars ... last chars ??
                this.access = param[1];
            } // end if

        } // end if

    }// end commandShellAccess

    private final void commandShellAppName(final String[] param) {
        
        if (param.length == 1) {
            if (!this.appName.equals("")) {
                this.writef("The app name is %s%n", this.appName);
            } else {
                this.writef("The app name is not set!%n");
            } // end if
            return;
        } else if (param.length == 2) {
            if (this.appName != null) {
                this.writef("Already set app name!%n");
            } else {
                this.writef("Set app name to %s%n", param[1]);
                this.appName = param[1];
            } // end if

        } // end if

    }// end commandShellAppName

    private void commandShellBye(final String[] param) {

        this.closeDateTime = new Date().toString();
        this.closeTimer = System.currentTimeMillis();

        if (this.readyFlag) {
            this.commandRemoteClose(param);
        } // end if

        this.exitFlag = true;
        this.writef("Goodbye!%n"); // give user name ??

        this.commandShellReport(null);

        if (this.scriptFlag) {
            this.scriptClose();
        } // end if

    }// end commandShellBye

    private void commandShellHelp(final String[] param) {

        this.writef(
                "DropBox Shell Commands and Parameters are:                                                      %n");
        this.writef(
                "                                                                                                         %n");
        this.writef(
                "    access [<access-token>]                      - report or set account access token.                   %n");
        this.writef(
                "    account                                      - print account status information.                     %n");
        this.writef(
                "    appname [<application-name>]                 - get or set account application name.                  %n");
        this.writef(
                "    bye                                          - exit shell and if connected close.                    %n");
        this.writef(
                "    (cd | chdir | cdir) ( <path> | .. )          - change remote directory.                              %n");
        this.writef(
                "    close                                        - close account connnection.                            %n");
        this.writef(
                "    cp <source-path> <target-path>               - copy remote file or directory.                        %n");
        this.writef(
                "    (del | rm) <path>                            - delete remote file entry.                             %n");
        this.writef(
                "    (dir | ls)                                   - list remote directories and files.                    %n");
        this.writef(
                "    find <path> <glob>                           - search in remote path for file or directory that matches query.%n");
        this.writef(
                "    get  <path>                                  - get download remote file to local directory.          %n");
        this.writef(
                "    help                                         - list shell commands or details about a valid command. %n");
        this.writef(
                "    history                                      - list the valid shell commands entered                 %n");
        this.writef(
                "    info <path>                                  - print metadata information about entry at path.       %n");
        this.writef(
                "    lcd ( <path> | .. )                          - change local current working directory.               %n");
        this.writef(
                "    lcp <source-path> <target-path>              - copy local file or directory.                         %n");
        this.writef(
                "    ldel <path>                                  - delete local file.                                    %n");
        this.writef(
                "    ldir                                         - list local directories and files.                     %n");
        this.writef(
                "    lfind <path> <glob>                          - search in local path for file or directory that matches query.%n");
        this.writef(
                "    lmdir <path>                                 - create local directory.                               %n");
        this.writef(
                "    lrdir <path>                                 - remove local directory.                               %n");
        this.writef(
                "    lrn <source-path> <target-path>              - rename local file or directory.                       %n");
        this.writef(
                "    lwd                                          - print local current working directory.                %n");
        this.writef(
                "    (mdir | mkdir) <path>                        - make remote directory.                                %n");
        this.writef(
                "    open [<application-name> <access-token>]     - connect shell with application name and access token. %n");
        this.writef(
                "    put  <path>                                  - put upload local file to remote directory.            %n");
        this.writef(
                "    pwd                                          - print remote current working directory.               %n");
        this.writef(
                "    (rdir | rmdir) <path>                        - remove remote directory.                              %n");
        this.writef(
                "    (rn | ren | mv) <source-path> <target-path>  - rename remote file or directory.                      %n");
        this.writef(
                "    (ready | status)                             - print ready status of shell.                          %n");
        this.writef(
                "    report                                       - report on shell summaries and totals.                 %n");
        this.writef(
                "    script [<filename>]                          - make transcript of shell session to file.             %n");
        this.writef(
                "    space                                        - print storage space utilization.                      %n");
        this.writef(
                "    (ver | version)                              - print shell version information.                      %n");
        this.writef(
                "                                                                                                         %n");
        System.out.flush();

        return;

    }// end commandHelp

    private final void commandShellHistory(final String[] param) {
        this.writef("Shell Command History:%n");
        for (int x = 0; x < this.history.size(); x++) {
            this.writef("    % 3d  %s%n", x, history.get(x));
        } // end for

    }// end commandShellHistory

    private void commandShellReady(final String[] param) {

        if (this.readyFlag) {
            this.writef("Ready! Shell is connected to DropBox!%n");
        } else {
            this.writef(
                    "Not Ready! Shell is not connected to DropBox! Use command 'open' to open connection with shell.%n");
        } // end if

        if (this.scriptFlag) {
            this.writef("Creating a transcript of shell session command usage to file: %s.%n",
                    this.outputFile.getName());
        } else {
            this.writef(
                    "No current transcript of shell session. Use command 'script' <filename> to create a transcript.%n");
        } // end if

    }// end commandShellReady

    private final void commandShellReport(final String[] param) {

        this.writef("%n");
        this.writef("    [============>>> DropBox Shell Report <<<============]%n");
        this.writef("%n");

        this.writef("      Platform: %s %s v.%s %n", System.getProperty("os.name"), System.getProperty("os.arch"),
                System.getProperty("os.version"));
        this.writef("      Userinfo: %s:[%s]    %n", System.getProperty("user.name"), System.getProperty("user.home"));
        this.writef("      Home Dir: %s         %n", System.getProperty("user.dir"));
        this.writef("%n");

        this.writef("      Session Begin: %s%n", this.startDateTime);
        this.writef("      Session Close: %s%n", this.closeDateTime);
        this.writef("%n");
        this.writef("           === Total for Session ===%n");
        this.writef("%n");

        this.closeTimer = System.currentTimeMillis();

        this.writef("      Total command count:%4d-commands %n", this.shellCommandCounter);
        this.writef("      Total session timer:%4d-seconds  %n", (this.closeTimer - this.startTimer) / 1000L);

        this.writef("%n");
        this.writef("           === Total Data Traffic ===%n");
        this.writef("%n");

        this.writef("      Total bytes data get:%10d-bytes%n", this.totalDataGet);
        this.writef("      Total bytes data put:%10d-bytes%n", this.totalDataPut);

        this.writef("%n");
        this.writef("    [<<<=========----------------------------=========>>>]%n");
        this.writef("%n");

    }// end commandShellReport

    private void commandShellScript(final String[] param) {
        if (this.scriptFlag) {
            this.scriptClose();
        } else {

            if (param.length != 2) {
                String fileName = DBXShell.getDefaultFileName("dbx_shell_transcript");
                this.writef("Using default filename: %s for transcript.%n", fileName);
                this.scriptBegin(fileName); // default transcript name
                return;
            } // end if

            this.scriptBegin(param[1]);

        } // end if

    }// end commandShellScript

    private void commandShellUnknown(final String[] param) // use Infocom error for unrecognized command? word?
    {
        this.writef("I don't understand!%n");
        this.writef("The command: '%s' is unknown. Try 'help' for list of shell commands.%n", param[0]);

    }// end commandShellUnknown

    private void commandShellVersion(final String[] param) {
        this.writef("DropBox shell version %s.%n", DBXShell.DBX_VERSION_INFO);
    }// end commandShellReady

    private final void dbxCreateClient(final String appname, final String access) {

        try {

            this.config = DbxRequestConfig.newBuilder("dropbox/" + appname).build();

            this.client = new DbxClientV2(config, access);

            this.account = client.users().getCurrentAccount();

            this.writef("Connected '%s' to Dropbox.%n", account.getName().getDisplayName());

            this.readyFlag = true;

        } catch (Exception ex) {
            this.writef("dbxCreateClient: %s %s %n", ex.getMessage(), ex.getClass().getName());
            ex.printStackTrace();
            this.writef("%n%n");
        } // end try

    }// end dbxCreateClient

    private final boolean dbxHasFile(final String path) {
        try {
            Metadata meta = client.files().getMetadata(path);
            if (meta instanceof FileMetadata) {
                return true;
            } else {
                return false;
            }//end if

        } catch (GetMetadataErrorException gme) {
            return false;
        } catch (DbxException dbxe) {
            return false;
        } catch (Exception ex) {
            return false;
        } // end try

    }// end hasMetadata

    private final boolean dbxHasFolder(final String path)
    {
        if (!this.readyFlag) {
            // this.writef("Not connected to DropBox!%n");
            return false;
        } // end if

        try {
            
            Metadata meta = this.client.files().getMetadata(path); // prefix "/" ??

            if (meta instanceof FolderMetadata) {
                return true;
            } else {
                return false;
            }//end if
            
        } catch (GetMetadataErrorException e) {
            return false;
        } catch (DbxException e1) {
            return false;
        } catch (Exception ex) {
            return false;
        } // end try

    }// end dbxHasFolder

    private final boolean dbxHasPath(final String path) {

        try {
            Metadata meta = this.client.files().getMetadata(path); // prefix "/" ??

            if (meta instanceof FolderMetadata) {
                return true;
            } else if (meta instanceof FileMetadata) {
                return true;
            } else {
                return false;
            } // end if
        } catch (GetMetadataErrorException e) {
            return false;
        } catch (DbxException e1) {
            return false;
        } catch (Exception ex) {
            return false;
        } // end try

    }// end dbxHasPath

    private final void downloadFromDropbox(final String fileName) {
        FileOutputStream fos;

        try {

            DbxDownloader<FileMetadata> download = this.client.files().download("/" + fileName); // use dbxCWD

            fos = new FileOutputStream(fileName);

            long startTime = System.currentTimeMillis();

            FileMetadata metadata = download.download(fos);

            long closeTime = System.currentTimeMillis();

            double bytesTime = (double) metadata.getSize() / (double) (closeTime - startTime);

            this.writef("Get downloaded file: '%s' total bytes: %d time: %d seconds at %4.3f bytes per second.%n",
                    metadata.getName(), metadata.getSize(), ((closeTime - startTime) / 1000), bytesTime);

            fos.close();

            this.totalDataGet = metadata.getSize() + this.totalDataGet;

        } catch (DbxException ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } catch (FileNotFoundException ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } catch (IOException ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } catch (Exception ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } // end try

    }// end downloadFileFromDropbox

    private final void evaluate() {

        while (!this.exitFlag) {

            try {

                this.writef(DBXShell.DBX_INPUT_PROMPT, this.appName, this.getDbxDir());

                final String[] param = this.getLine();

                if (param.length == 0 || param[0].length() == 0) // no input, loop for input
                {
                    this.writef("%s%n", "Huh? What!");
                    this.history.remove(this.history.size() - 1);
                    continue;
                } // end if

                this.process(param);
                this.shellCommandCounter++;

            } catch (Exception ex) {
                this.writef("Evaluate error: %s %s%n!", ex.getClass().getName(), ex.getMessage());
                ex.printStackTrace();
                this.writef("%n%n");
            } // end try

        } // end while

    }// end evaluate

    private final void finalizer() {
        this.writef("%n%s%n", DBXShell.DBX_CLOSE_MESSAGE);
        System.out.flush();
    }// end finalizer

    private final String getDbxDir() {

        if (!this.readyFlag) {
            return ""; // not connected, not at root
        }//end if

        if (this.dbxCurrentWorkDir.equalsIgnoreCase("")) {
            return "/";
        }//end if

        return this.dbxCurrentWorkDir;

    }// end getDbxDir

    private final String[] getLine() {

        String input = this.scan.nextLine();
        this.history.add(input);

        String delim = "\\s";

        if (input.contains("'")) {
            delim = "'";
        } else if (input.contains("\"")) {
            delim = "\"";
        } // end if

        String[] param = input.split(delim);

        for (int x = 0; x < param.length; x++) {
            param[x] = param[x].trim();
        } // end for

        return param;

    }// end getLine

    private final void getMetadata(final String path) // return Metadata[] if size = 1, size = 0
    {

        if (!this.hasMetadata(path)) {
            this.writef("%nInfo not available for path: '%s' entry!%n", path);
            return;
        } // end if

        try {
            Metadata meta = client.files().getMetadata(path);

            this.writef("%n");

            if (meta instanceof FileMetadata) {

                FileMetadata fmeta = (FileMetadata) meta;

                this.writef("Info for file:   %s%n%n", path);

                this.writef("Server Modified: %s%n", fmeta.getServerModified());
                this.writef("Client Modified: %s%n", fmeta.getClientModified());
                this.writef("Revision:        %s%n", fmeta.getRev());

                this.writef("Client Modified: %s%n", fmeta.getClientModified());
                this.writef("Ident:           %s%n", fmeta.getId().split(":")[1]);
                this.writef("Name:            %s%n", fmeta.getName());
                this.writef("Path:            %s%n", fmeta.getPathDisplay());
                this.writef("Revision:        %s%n", fmeta.getRev());
                this.writef("Server Modified: %s%n", fmeta.getServerModified());
                this.writef("Size:            %s-bytes %s-Kbytes %s-Mbytes%n", fmeta.getSize(), fmeta.getSize() / 1024,
                        fmeta.getSize() / (1024 * 1024));

            } else if (meta instanceof FolderMetadata) {

                FolderMetadata fmeta = (FolderMetadata) meta;

                this.writef("Info for folder: %s%n%n", path);
                this.writef("Ident:           %s%n", fmeta.getId().split(":")[1]);
                this.writef("Name:            %s%n", fmeta.getName());
                this.writef("Path Display:    %s%n", fmeta.getPathDisplay());

                ListFolderResult result = client.files().listFolder(path);

                this.writef("Entry Count:     %-4d%n", result.getEntries().size()); // files or elements/entries?

            } // end if

            this.writef("%n");

        } catch (GetMetadataErrorException gme) {
            this.writef("%n");            
        } catch (DbxException dbxe) {
            this.writef("%n");
        } // end try

    }// end getMetadata

    private final String dbxGetPathAbsolute(final String param) {

        String path = "";
        if (param.charAt(0) == '/') {
            path = param;
        } else {
            path = this.dbxCurrentWorkDir + "/" + param;
        }//end if

        return path;
    }// end dbxGetPathAbsolute

    private final boolean hasMetadata(final String path) // return Metadata[] if size = 1, size = 0
    {
        try {

            client.files().getMetadata(path);
            return true;
        } catch (GetMetadataErrorException gme) {

            return false;
        } catch (DbxException dbxe) {
            // dbxe.printStackTrace();
            return false;
        } // end try

    }// end hasMetadata

    private final void header() {
        this.writef("%s Version %s%n", DBXShell.DBX_APP_ABOUT_MESSAGE, 
                                       DBXShell.DBX_VERSION_INFO);
        
        this.writef("%s%n", DBXShell.DBX_COPYRIGHT_MESSAGE);
        this.writef("%s%n", DBXShell.DBX_LICENSE_MESSAGE);
        this.writef("%n");
    }//end header

    private final void initialize() {

        this.header();

        this.writef("%s%n%n", DBXShell.DBX_START_MESSAGE);

        this.writef("%s%n",   DBXShell.DBX_WELCOME_MESSAGE);
        this.writef("%n");

    }// end initialize

    private final void listFilesAndFilesSubDirectories(final String directoryName) {

        final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm:ss:a");

        final File directory = new File(directoryName);

        if (!directory.exists()) {
            this.writef("Error: Path '%s' does not exist!%n", directory.getPath());
            return;
        } // end if

        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                this.writef("%20s  %12d %s %n", sdf.format(file.lastModified()), file.length(), file.getName());

            } else if (file.isDirectory()) {
                this.writef("%20s  %12d [%s]%n", sdf.format(file.lastModified()), file.length(), file.getName());

            } // end if
        } // end for

    }// end listFilesAndFilesSubDirectories

    private final void process(final String[] param) {

        switch (param[0]) {

            case "access":
                this.commandShellAccess(param);
                break;

            case "account":
                this.commandRemoteAccount(param);
                break;

            case "appname":
                this.commandShellAppName(param);
                break;

            case "exit":
            case "quit":
            case "bye":
                this.commandShellBye(param);
                break;

            case "chdir":
            case "cd":
            case "cdir":
                this.commandRemoteChangeDir(param);
                break;

            case "close":
                this.commandRemoteClose(param);
                break;

            case "cp":
                this.commandRemoteCopyFile(param);
                break;

            case "rm":
            case "del":
                this.commandRemoteRemoveFile(param);
                break;

            case "ls":
            case "dir":
                this.commandRemoteDir(param);
                break;

            case "find":
                this.commandRemoteFind(param);
                break;

            case "get":
                this.commandLocalGet(param);
                break;

            case "help":
                this.commandShellHelp(param);
                break;

            case "history":
                this.commandShellHistory(param);
                break;

            case "info":
                this.commandRemoteInfo(param);
                break;

            case "lcd":
                this.commandLocalChangeDir(param);
                break;

            case "lcp":
                this.commandLocalCopy(param);
                break;

            case "lrm":
            case "ldel":
                this.commandLocalDeleteFile(param);
                break;

            case "ldir":
                this.commandLocalDir(param);
                break;

            case "lfind":
                this.commandLocalFind(param);
                break;

            case "lmdir":
                this.commandLocalMakeDir(param);
                break;

            case "lrd":
            case "lrdir":
                this.commandLocalRemoveDir(param);
                break;

            case "lrn":
                this.commandLocalRenameFile(param);
                break;

            case "lwd":
                this.commandLocalWorkingDir(param);
                break;

            case "md":
            case "mkdir":
            case "mdir":
                this.commandRemoteMakeDirectory(param);
                break;

            case "open":
                this.commandRemoteOpen(param);
                break;

            case "put":
                this.commandLocalPut(param);
                break;

            case "pwd":
                this.commandRemoteWorkingDir(param);
                break;

            case "rd":
            case "rmdir":
            case "rdir":
                this.commandRemoteRemoveDirectory(param);
                break;

            case "status":
            case "ready":
                this.commandShellReady(param);
                break;

            case "mv":
            case "rn":
            case "ren":
                this.commandRemoteRenameFile(param);
                break;

            case "report":
                this.commandShellReport(param);
                break;

            case "script":
                this.commandShellScript(param);
                break;

            case "space":
                this.commandRemoteSpace(param);
                break;

            case "ver":
            case "version":
                this.commandShellVersion(param);
                break;

            default:
                this.commandShellUnknown(param);

                this.history.remove(this.history.size() - 1);

                break;

        }// end switch

    }// end process

    private final void scriptBegin(final String fileName) {

        try {

            this.outputFile = new File(fileName);

            if (outputFile.exists()) {
                this.writef("The transcript file: %s already exists!%n", fileName);
                return;
            } // end if

            FileWriter fileWriter = new FileWriter(outputFile, true);
            BufferedWriter buffWriter = new BufferedWriter(fileWriter);
            PrintWriter textWriter = new PrintWriter(buffWriter);

            this.scriptFlag = true;
            this.scriptFile = textWriter;

            this.writef("Transcript has started with next command.%n");

        } catch (IOException ex) {
            this.writef("Transcript start; file IO error occurred:     %s%n", ex.getMessage());
        } catch (Exception ex) {
            this.writef("Transcript start; file unknown error occurred:%s%n", ex.getMessage());
        } // end try

    }// end scriptBegin

    private final void scriptClose() {

        try {

            this.writef("Transcript has finished for last command.%n");
            this.writef("Transcript of shell session written to file: %s.%n", this.outputFile.getName());

            this.scriptFile.flush();
            this.scriptFile.close();
            this.scriptFlag = false;
            this.outputFile = null;

        } catch (Exception ex) {
            this.writef("Transcript close; file unknown error occurred:%s%n", ex.getMessage());
        } // end try

    }// end scriptClose

    private final void uploadToDropbox(final String fileName) // dropBoxPut
    {

        File inputFile = new File(fileName);
        FileInputStream fis;

        try {

            fis = new FileInputStream(inputFile);

            long startTime = System.currentTimeMillis();

            FileMetadata metadata = client.files().uploadBuilder("/" + fileName).uploadAndFinish(fis); // revise with
            long closeTime = System.currentTimeMillis();

            double bytesTime = (double) metadata.getSize() / (double) (closeTime - startTime);

            this.writef("Put uploaded file: '%s' total bytes: %d time: %d seconds at %4.3f bytes per second.%n",
                    metadata.getName(), metadata.getSize(), ((closeTime - startTime) / 1000), bytesTime);

            fis.close();

            this.totalDataPut = metadata.getSize() + this.totalDataPut;

        } catch (DbxException ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } catch (FileNotFoundException ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } catch (IOException ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } catch (Exception ex) {
            this.writef("Error %s : %s %n", ex.getClass().getName(), ex.getMessage());
        } // end try

    }// end uploadToDropbox

    private final void writef(final String fmt, Object... args) {

        final String result = String.format(fmt, args);

        System.out.print(result);

        if (this.scriptFlag) {
            this.scriptFile.print(result);
        } // end if

    }// end writef

}// end class DBXShell

