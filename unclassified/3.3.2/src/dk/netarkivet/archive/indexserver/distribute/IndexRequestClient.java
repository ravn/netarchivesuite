/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2007 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.netarkivet.archive.indexserver.distribute;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.netarkivet.archive.indexserver.FileBasedCache;
import dk.netarkivet.archive.indexserver.MultiFileBasedCache;
import dk.netarkivet.common.Settings;
import dk.netarkivet.common.distribute.Channels;
import dk.netarkivet.common.distribute.JMSConnectionFactory;
import dk.netarkivet.common.distribute.NetarkivetMessage;
import dk.netarkivet.common.distribute.RemoteFile;
import dk.netarkivet.common.distribute.Synchronizer;
import dk.netarkivet.common.distribute.indexserver.JobIndexCache;
import dk.netarkivet.common.distribute.indexserver.RequestType;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.IllegalState;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.StringUtils;

/**
 * Client for index request server.
 *
 * Allows to request an index of some type over a list of jobs. Factory method
 * will return the index request client of the type wished.
 */
public class IndexRequestClient extends MultiFileBasedCache<Long>
        implements JobIndexCache {
    /**
     * Synchronizer used to make requests.
     */
    private static Synchronizer synchronizer;
    /**
     * Factory method map of clients created of specific types.
     */
    private static Map<RequestType, IndexRequestClient> clients
            = new EnumMap<RequestType, IndexRequestClient>(
            RequestType.class);

    /**
     * The type of this indexRequestClient.
     */
    private RequestType requestType;
    /**
     * Logger for this indexRequestClient.
     */
    private Log log = LogFactory.getLog(getClass().getName());

    /**
     * Initialise this client, handling requests of a given type. Start
     * listening to channel if not done yet.
     *
     * @param type Type of this cache
     */
    private IndexRequestClient(RequestType type) {
        super(type.name());
        this.requestType = type;
    }

    /** Get the singleton synchronizer for sending requets.
     * @return synchronizer */
    private Synchronizer getSynchronizer() {
        if (synchronizer == null) {
            synchronizer = new Synchronizer();
            JMSConnectionFactory.getInstance().setListener(
                    Channels.getThisIndexClient(), synchronizer);
        }
        return synchronizer;
    }

    /**
     * Factory method returning an IndexRequestClient for the given type of
     * index cache.
     *
     * @param type The type of this cache.
     * @return The singleton instance dedicated to this type of index requests.
     * @throws ArgumentNotValid if type is null.
     */
    public static IndexRequestClient getInstance(RequestType type) {
        ArgumentNotValid.checkNotNull(type, "RequestType type");
        IndexRequestClient client = clients.get(type);
        if (client == null) {
            client = new IndexRequestClient(type);
            clients.put(type, client);
        }
        return client;
    }

    /**
     * This method makes sure the actual caching of underlying data is done
     * using the index server. It will convert calls into an IndexRequestMessage
     * which is sent to the server. The Set<Long> of found jobs, and the side
     * effect of caching the index, is done using this communication with the
     * server.
     *
     * This method should not be called directly! Instead call cache() or
     * getIndex().
     *
     * @param jobSet The set of job IDs.
     * @return The set of found job IDs.
     * @throws ArgumentNotValid on null argument; or on wrong parameters in
     *                          replied message.
     * @throws IOFailure        on trouble in communication or invalid reply
     *                          types.
     * @throws IllegalState     if message is not OK.
     * @see FileBasedCache#cache
     * @see FileBasedCache#getIndex
     */
    protected Set<Long> cacheData(Set<Long> jobSet) {
        ArgumentNotValid.checkNotNull(jobSet, "Set<Long> id");

        log.debug("Requesting an index of type '" + this.requestType
                 + "' for the jobs [" + StringUtils.conjoin(jobSet, ",")
                 + "]");
        //Send request to server
        IndexRequestMessage irMsg = new IndexRequestMessage(requestType, jobSet);
        NetarkivetMessage msg = getSynchronizer().sendAndWaitForOneReply(
                irMsg, getIndexTimeout());

        checkMessageValid(jobSet, msg);
        IndexRequestMessage reply = (IndexRequestMessage) msg;

        Set<Long> foundJobs = reply.getFoundJobs();
        // Only if all jobs asked for were found will the result contain files.
        if (jobSet.equals(foundJobs)) {
            log.debug("Successfully received an index of type '"
                      + this.requestType
                     + "' for the jobs [" + StringUtils.conjoin(jobSet, ",")
                     + "]");
            if (reply.isIndexIsStoredInDirectory()) {
                List<RemoteFile> files = reply.getResultFiles();
                File cacheDir = getCacheFile(jobSet);
                File tmpDir =
                        FileUtils.createUniqueTempDir(cacheDir.getParentFile(),
                                cacheDir.getName());
                try {
                    FileUtils.createDir(tmpDir);
                    for (RemoteFile f : files) {
                        File destFile = new File(tmpDir, f.getName());
                        copyAndDeleteRemoteFile(f, destFile);
                    }
                    if (!tmpDir.renameTo(cacheDir)) {
                        throw new IOFailure("Error renaming temp dir '"
                                + tmpDir + "' to target directory '"
                                + cacheDir.getAbsolutePath() + "'");
                    }
                } finally {
                    FileUtils.removeRecursively(tmpDir);
                }
            } else {
                RemoteFile remoteFile = reply.getResultFile();
                copyAndDeleteRemoteFile(remoteFile, getCacheFile(jobSet));
            }
        }


        //Return the set of found jobs
        return foundJobs;
    }

    /** Copy a RemoteFile to a given file, deleting it afterwards.  Problems
     * arising while deleting are logged, but do not cause exceptions.
     *
     * @param f A file to download. This file will be attempted deleted after
     * successfull copying.
     * @param destFile A place to put the file.
     */
    private void copyAndDeleteRemoteFile(RemoteFile f, File destFile) {
        f.copyTo(destFile);
        try {
            f.cleanup();
        } catch (IOFailure e) {
            log.debug("Trouble deleting file '"
                    + f.getName()
                    + "' from FTP server after saving it", e);
        }
    }

    /** How long should we wait for index replies?
     *
     * @return Index timeout value in milliseconds.
     */
    protected long getIndexTimeout() {
        //NOTE: It might be a good idea to make this dependant on "type"
        return Settings.getLong(Settings.INDEXREQUEST_TIMEOUT);
    }

    /**
     * Check the reply message is valid
     * @param jobSet The requested set of jobs
     * @param msg The message received
     * @throws ArgumentNotValid On wrong parameters in replied message.
     * @throws IOFailure        on trouble in communication or invalid reply
     *                          types.
     * @throws IllegalState     if message is not OK.
     */
    private void checkMessageValid(Set<Long> jobSet, NetarkivetMessage msg) {
        //Read and check reply
        if (msg == null) {
            throw new IOFailure("Timeout waiting for reply of index request "
                                + "for jobs " + StringUtils.conjoin(jobSet,
                                                                    ","));
        }
        if (!msg.isOk()) {
            throw new IllegalState("Reply message not ok. Message is: '"
                                   + msg.getErrMsg()
                                   + "' in index request for jobs "
                                   + StringUtils.conjoin(jobSet, ","));
        }
        if (!(msg instanceof IndexRequestMessage)) {
            throw new IOFailure("Unexpected type of reply message: '"
                                + msg.getClass().getName()
                                + "' in index request for jobs "
                                + StringUtils.conjoin(jobSet, ","));
        }
        IndexRequestMessage reply = (IndexRequestMessage) msg;
        Set<Long> foundJobs = reply.getFoundJobs();
        if (foundJobs == null) {
            throw new ArgumentNotValid("Missing parameter foundjobs in reply to"
                                       + " index request for jobs "
                                       + StringUtils.conjoin(jobSet, ","));
        }

        //FoundJobs should always be a subset
        if (!jobSet.containsAll(foundJobs)) {
            throw new ArgumentNotValid("foundJobs is not a subset of requested "
                    + "jobs. Requested: "
                    + StringUtils.conjoin(jobSet, ",")
                    + ". Found: "
                    + StringUtils.conjoin(foundJobs, ","));
        }

        if (jobSet.equals(foundJobs)) {
            //Files should only be present if jobSet=foundJobs
            if (reply.isIndexIsStoredInDirectory()) {
                List<RemoteFile> files;
                files = reply.getResultFiles();
                if  (files == null) {
                    throw new ArgumentNotValid("Missing files in reply to"
                            + " index request for jobs "
                            + StringUtils.conjoin(jobSet, ","));
                }
            } else {
                RemoteFile file = reply.getResultFile();
                if  (file == null) {
                    throw new ArgumentNotValid("Missing file in reply to"
                            + " index request for jobs "
                            + StringUtils.conjoin(jobSet, ","));
                }
            }

        }
    }
}